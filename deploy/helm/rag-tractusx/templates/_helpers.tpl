{{- define "rag.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "rag.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else if contains (include "rag.name" .) .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "rag.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "rag.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
app.kubernetes.io/name: {{ include "rag.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "rag.selectorLabels" -}}
app.kubernetes.io/name: {{ include "rag.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: app
{{- end }}

{{- define "rag.secretName" -}}
{{- default (include "rag.fullname" .) .Values.secrets.existingSecret }}
{{- end }}

{{- define "rag.postgresHost" -}}
{{- printf "%s-postgres" (include "rag.fullname" .) }}
{{- end }}

{{- define "rag.dbUrl" -}}
{{- if .Values.postgres.enabled }}
{{- printf "jdbc:postgresql://%s:5432/%s" (include "rag.postgresHost" .) .Values.postgres.database }}
{{- else }}
{{- required "externalDatabase.url is required when postgres.enabled=false" .Values.externalDatabase.url }}
{{- end }}
{{- end }}

{{- define "rag.dbUser" -}}
{{- if .Values.postgres.enabled }}{{ .Values.postgres.username }}{{ else }}{{ .Values.externalDatabase.username }}{{ end }}
{{- end }}

{{/* Environment shared by the app, the ingestion Job and the CronJob. */}}
{{- define "rag.env" -}}
- name: DB_URL
  value: {{ include "rag.dbUrl" . | quote }}
- name: DB_USER
  value: {{ include "rag.dbUser" . | quote }}
- name: ANTHROPIC_MODEL
  value: {{ .Values.config.anthropicModel | quote }}
- name: ANTHROPIC_EFFORT
  value: {{ .Values.config.anthropicEffort | quote }}
- name: EMBEDDING_PROVIDER
  value: {{ .Values.config.embeddingProvider | quote }}
- name: DOCS_REF
  value: {{ .Values.config.docsRef | quote }}
- name: DOCS_KITS_VERSION
  value: {{ .Values.config.kitsVersion | quote }}
- name: RETRIEVAL_MODE
  value: {{ .Values.config.retrievalMode | quote }}
- name: RETRIEVAL_TOP_K
  value: {{ .Values.config.retrievalTopK | quote }}
- name: RETRIEVAL_KEYWORD_WEIGHT
  value: {{ .Values.config.retrievalKeywordWeight | quote }}
- name: LOG_FORMAT
  value: {{ .Values.config.logFormat | quote }}
- name: JAVA_TOOL_OPTIONS
  value: {{ .Values.config.javaToolOptions | quote }}
{{- range $env, $key := dict "ANTHROPIC_API_KEY" "ANTHROPIC_API_KEY" "OPENAI_API_KEY" "OPENAI_API_KEY" "RAG_API_KEY" "RAG_API_KEY" "ADMIN_TOKEN" "ADMIN_TOKEN" "GITHUB_TOKEN" "GITHUB_TOKEN" "DB_PASSWORD" "DB_PASSWORD" }}
- name: {{ $env }}
  valueFrom:
    secretKeyRef:
      name: {{ include "rag.secretName" $ }}
      key: {{ $key }}
      optional: true
{{- end }}
{{- end }}

{{- define "rag.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: 10001
runAsGroup: 10001
fsGroup: 10001
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{- define "rag.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end }}

{{/* Waits until PostgreSQL accepts connections (only with the built-in database). */}}
{{- define "rag.waitForDb" -}}
{{- if .Values.postgres.enabled }}
- name: wait-for-db
  image: {{ .Values.postgres.image }}
  command: ["sh", "-c", "until pg_isready -h {{ include "rag.postgresHost" . }} -p 5432 -U {{ .Values.postgres.username }}; do sleep 2; done"]
  securityContext:
    {{- include "rag.containerSecurityContext" . | nindent 4 }}
    runAsUser: 999
{{- end }}
{{- end }}
