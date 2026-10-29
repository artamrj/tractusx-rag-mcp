package de.ragtractusx.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import de.ragtractusx.config.EmbeddingConfig.EmbeddingSpace;
import de.ragtractusx.support.PostgresTestBase;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IngestionServiceIT extends PostgresTestBase {

    @Autowired
    IngestionService ingestion;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    EmbeddingSpace space;

    @Test
    @Order(1)
    void firstRunIngestsOnlyTheLatestKitVersionAndGeneralDocs() {
        IngestionReport report = ingestion.ingest();

        assertThat(report.listed()).isEqualTo(3);
        assertThat(report.added()).isEqualTo(3);
        assertThat(report.failed()).isZero();
        assertThat(report.totalChunks()).isPositive();

        List<String> paths = jdbc.sql("SELECT DISTINCT metadata->>'path' FROM " + space.table()).query(String.class).list();
        assertThat(paths).noneMatch(p -> p.contains("version-26.06"));

        Map<String, Object> row = jdbc.sql("""
                SELECT metadata->>'url' AS url, metadata->>'kit' AS kit, text FROM %s
                WHERE text LIKE '%%BPNL%%'""".formatted(space.table())).query().singleRow();
        // Small sections are packed into one chunk, so the link points at the page (or its first section).
        assertThat((String) row.get("url")).startsWith(
                "https://eclipse-tractusx.github.io/docs-kits/kits/connector-kit/adoption-view");
        assertThat(row.get("kit")).isEqualTo("connector-kit");
        assertThat((String) row.get("text")).startsWith("Connector KIT › Adoption View");

        Long tsvRows = jdbc.sql("SELECT count(*) FROM " + space.table()
                + " WHERE tsv @@ to_tsquery('english', 'shell & descriptor')").query(Long.class).single();
        assertThat(tsvRows).isPositive();
    }

    @Test
    @Order(2)
    void secondRunSkipsUnchangedFiles() {
        IngestionReport report = ingestion.ingest();

        assertThat(report.unchanged()).isEqualTo(3);
        assertThat(report.added() + report.updated() + report.deleted()).isZero();
        assertThat(report.chunksWritten()).isZero();
    }

    @Test
    @Order(3)
    void changedAndDeletedFilesAreReplacedAndRemoved() throws Exception {
        Path gettingStarted = DOCS_DIR.resolve("docs/getting-started.md");
        Files.writeString(gettingStarted, Files.readString(gettingStarted) + "\n\n## Troubleshooting\n\nRestart minikube.\n");
        Files.delete(DOCS_DIR.resolve(
                "docs-kits_versioned_docs/version-26.09/kits/digital-twin-kit/software-development-view/software-development-view.md"));

        IngestionReport report = ingestion.ingest();

        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.unchanged()).isEqualTo(1);
        Long dtrChunks = jdbc.sql("SELECT count(*) FROM " + space.table() + " WHERE metadata->>'kit' = 'digital-twin-kit'")
                .query(Long.class).single();
        assertThat(dtrChunks).isZero();
        Long troubleshooting = jdbc.sql("SELECT count(*) FROM " + space.table() + " WHERE text LIKE '%Restart minikube%'")
                .query(Long.class).single();
        assertThat(troubleshooting).isEqualTo(1);
    }
}
