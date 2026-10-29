# Architecture

One Spring Boot application serves the REST API and the MCP server and runs ingestion. It stores everything in
PostgreSQL with pgvector.

## Components

```mermaid
flowchart TB
    subgraph sources [Source]
        GH[(eclipse-tractusx.github.io<br/>GitHub repository)]
    end

    subgraph app [Spring Boot app]
        direction TB
        subgraph ingest [Ingestion]
            SRC[GitHubDocsSource<br/>tree API + raw files] --> CLEAN[MdxCleaner] --> CHUNK[HeadingChunker] --> EMB[Embed in batches]
        end
        subgraph serve [Serving]
            REST[REST API] --> RAG[RagService]
            MCP[MCP tools] --> RAG
            RAG --> SEARCH[SearchService]
            SEARCH --> VEC[Vector search]
            SEARCH --> KW[Keyword search]
            VEC & KW --> RRF[Weighted RRF]
            RAG --> PROMPT[PromptBuilder]
        end
        MET[Micrometer metrics]
    end

    GH --> SRC
    EMB --> OAI[[OpenAI embeddings]]
    EMB --> PG[(PostgreSQL + pgvector)]
    VEC --> PG
    KW --> PG
    PROMPT --> CL[[Claude]]
    MET -.-> PROM[Prometheus]
```

## Ingestion

```mermaid
flowchart LR
    A[List repo tree<br/>1 API call] --> B{Blob SHA<br/>changed?}
    B -- no --> S[Skip]
    B -- yes --> C[Download raw file]
    C --> D[Clean MDX<br/>front matter · imports · JSX · admonitions]
    D --> E[Chunk by heading<br/>~500 tokens · breadcrumb · overlap]
    E --> F[Embed]
    F --> G[Replace page chunks<br/>in pgvector]
    A --> H[Remove pages<br/>deleted upstream]
```

- **What gets indexed:** the latest *released* KIT version (`docs-kits_versioned_docs/version-<latest>/`, which is what
  `/docs-kits/...` serves), plus `docs/`, `community/` and `documentation/`. That is 330 pages and about 3,000 chunks
  across 36 KITs.
- **Incremental:** files with the same git blob SHA are skipped; a Postgres advisory lock keeps runs from overlapping.
- **Deep links:** repo paths are mapped to the published URL using Docusaurus routing rules (number prefixes,
  front-matter `id`/`slug`, folder index pages), plus heading anchors. The mapping was checked against the live site.
- **Triggers:** `java -jar app.jar --ingest` (Kubernetes Job/CronJob) or `POST /admin/ingest`.

## Answering a question

```mermaid
sequenceDiagram
    autonumber
    participant C as Client (REST or MCP)
    participant R as RagService
    participant S as SearchService
    participant DB as pgvector
    participant E as OpenAI
    participant L as Claude

    C->>R: question
    R->>S: search(question, top-k 8)
    S->>E: embed question
    S->>DB: nearest chunks (HNSW, cosine)
    S->>DB: full-text match (tsvector, OR terms)
    S-->>R: chunks fused with weighted RRF
    R->>L: system prompt + numbered excerpts + question
    L-->>R: answer citing [n]
    R-->>C: answer, sources (cited flags), usage, latency
```

The system prompt is static (cache-friendly) and tells the model to answer only from the excerpts, cite `[n]`, say
so when the docs don't cover the question, and answer in the question's language. An answer without citations
counts as `no_answer`; a refusal returns `refused`.

## Data model

```mermaid
erDiagram
    ingested_file {
        text chunk_table PK
        text path PK
        text blob_sha
        text title
        text url
        text kit
        int chunk_count
    }
    ingestion_run {
        bigint id PK
        text chunk_table
        timestamptz finished_at
        boolean success
        int added
        int updated
        int total_chunks
    }
    chunks_openai_te3_small {
        uuid embedding_id PK
        vector embedding "1536 dims, HNSW"
        text text
        jsonb metadata "path, url, kit, section"
        tsvector tsv "generated, GIN"
    }
    ingested_file ||--o{ chunks_openai_te3_small : "metadata.path"
```

Each embedding model writes to its own chunk table, so switching models never mixes vector sizes.

## Design decisions

| Decision | Why |
|---|---|
| Read GitHub via API, no clone | The website repo is ~3 GB of images; the Markdown is a few MB |
| Index the released KIT version | `docs-kits/` is the unreleased "next" version; readers see the release |
| Own keyword search instead of LangChain4j's hybrid mode | The built-in mode needs *every* word to match, which fails for natural-language questions, and only supports IVFFlat |
| Weighted RRF (keyword weight 0.5) | Rank-based fusion needs no score calibration; measured on the test set |
| Breadcrumbs in every chunk | Short sections keep their KIT/page context in the embedding and the prompt |
| Ingestion history in the database | Ingestion runs in a Job pod that Prometheus never scrapes; app pods report freshness |
| One app for REST, MCP and ingestion | One image, one config, one set of metrics |

## Code layout

```
src/main/java/de/ragtractusx/
  ingest/     sources, MDX cleaner, chunker, URL mapper, incremental ingestion
  retrieval/  vector + keyword retrievers, weighted RRF, search service
  rag/        prompt builder, RAG pipeline (blocking + streaming)
  api/        REST controllers, API-key filter, error mapping
  mcp/        MCP tools
  metrics/    meters, token listener, database-backed gauges
  eval/       test-set loader, retrieval scores, LLM judge, runner, report
```
