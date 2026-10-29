package de.ragtractusx.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full application context against a real pgvector database and the in-process embedding model. No API keys needed.
 * The container is shared by all test classes (started once by Spring Boot, removed when the JVM exits). Each test
 * class registers its own copy of the fixture docs via {@code @DynamicPropertySource} and {@link #copyFixtures()}.
 */
@SpringBootTest(properties = {
        "rag.embedding.provider=local",
        "rag.source.type=local",
        "rag.llm.api-key=",
        "rag.security.admin-token=test-admin-token"
})
public abstract class PostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres"));

    /** Copies src/test/resources/fixture-docs to a fresh temp dir. */
    protected static Path copyFixtures() {
        try {
            Path source = Path.of("src/test/resources/fixture-docs");
            Path target = Files.createTempDirectory("fixture-docs");
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path dest = target.resolve(source.relativize(file));
                    Files.createDirectories(dest.getParent());
                    Files.copy(file, dest);
                }
            }
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
