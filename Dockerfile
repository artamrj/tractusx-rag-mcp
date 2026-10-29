# syntax=docker/dockerfile:1.7

# ---- build ----
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/rag-tractusx.jar extract --layers --launcher --destination target/extracted

# ---- runtime ----
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
# Spring Boot layers, least to most frequently changing, for better image layer caching.
COPY --from=build /src/target/extracted/dependencies/ ./
COPY --from=build /src/target/extracted/spring-boot-loader/ ./
COPY --from=build /src/target/extracted/snapshot-dependencies/ ./
COPY --from=build /src/target/extracted/application/ ./
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    LOG_FORMAT=ecs
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
