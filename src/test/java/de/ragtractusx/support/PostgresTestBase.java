package de.ragtractusx.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full application context against a real pgvector database, with the in-process embedding model and the fixture
 * docs tree (copied to a temp dir so tests may modify it). No API keys needed.
 */
@Testcontainers
@SpringBootTest(properties = {
        "rag.embedding.provider=local",
        "rag.source.type=local",
        "rag.llm.api-key=",
        "rag.security.admin-token=test-admin-token"
})
public abstract class PostgresTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres"));

    protected static final Path DOCS_DIR = copyFixtures();

    @DynamicPropertySource
    static void docsDir(DynamicPropertyRegistry registry) {
        registry.add("rag.source.local-path", DOCS_DIR::toString);
    }

    private static Path copyFixtures() {
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
