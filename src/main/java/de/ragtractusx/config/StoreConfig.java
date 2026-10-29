package de.ragtractusx.config;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import de.ragtractusx.config.EmbeddingConfig.EmbeddingSpace;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

/**
 * pgvector-backed chunk store. The table is created here (not by LangChain4j) so it can carry an HNSW index and a
 * generated full-text column that the keyword retriever uses.
 */
@Configuration
public class StoreConfig {

    /** Marker bean: the chunk table exists once this bean is created. */
    public record ChunkTable(String name) {
    }

    @Bean
    @DependsOnDatabaseInitialization
    ChunkTable chunkTable(JdbcClient jdbc, EmbeddingSpace space) {
        String t = space.table();
        if (!t.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid table name " + t);
        }
        jdbc.sql("""
                CREATE TABLE IF NOT EXISTS %1$s (
                    embedding_id UUID PRIMARY KEY,
                    embedding    vector(%2$d),
                    text         TEXT,
                    metadata     JSONB,
                    tsv          tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(text, ''))) STORED
                )""".formatted(t, space.dimension())).update();
        jdbc.sql("CREATE INDEX IF NOT EXISTS %1$s_hnsw ON %1$s USING hnsw (embedding vector_cosine_ops)".formatted(t)).update();
        jdbc.sql("CREATE INDEX IF NOT EXISTS %1$s_tsv ON %1$s USING gin (tsv)".formatted(t)).update();
        jdbc.sql("CREATE INDEX IF NOT EXISTS %1$s_path ON %1$s ((metadata->>'path'))".formatted(t)).update();
        jdbc.sql("CREATE INDEX IF NOT EXISTS %1$s_kit ON %1$s ((metadata->>'kit'))".formatted(t)).update();
        return new ChunkTable(t);
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource, EmbeddingSpace space, ChunkTable table) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(table.name())
                .dimension(space.dimension())
                .createTable(false)
                .useIndex(false)
                .skipCreateVectorExtension(true)
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COMBINED_JSONB)
                        .columnDefinitions(List.of("metadata JSONB NULL"))
                        .build())
                .build();
    }
}
