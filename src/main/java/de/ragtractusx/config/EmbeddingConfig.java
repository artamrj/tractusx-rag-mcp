package de.ragtractusx.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * Embedding model selection. Claude has no embedding endpoint, so OpenAI is used in production; the local ONNX model
 * keeps tests and CI free of API keys. Each model writes to its own chunk table because vector sizes differ.
 */
@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    /** Table and vector size that belong to the configured embedding model. */
    public record EmbeddingSpace(String provider, String modelName, String table, int dimension) {
    }

    @Bean
    EmbeddingSpace embeddingSpace(RagProperties props) {
        return switch (props.embedding().provider()) {
            case "openai" -> switch (props.embedding().openaiModel()) {
                case "text-embedding-3-small" -> new EmbeddingSpace("openai", "text-embedding-3-small", "chunks_openai_te3_small", 1536);
                case "text-embedding-3-large" -> new EmbeddingSpace("openai", "text-embedding-3-large", "chunks_openai_te3_large", 3072);
                default -> throw new IllegalArgumentException("Unsupported OpenAI embedding model: " + props.embedding().openaiModel());
            };
            case "local" -> new EmbeddingSpace("local", "all-minilm-l6-v2-q", "chunks_minilm_l6", 384);
            default -> throw new IllegalArgumentException("rag.embedding.provider must be 'openai' or 'local'");
        };
    }

    @Bean
    EmbeddingModel embeddingModel(RagProperties props, EmbeddingSpace space) {
        if ("local".equals(space.provider())) {
            return new AllMiniLmL6V2QuantizedEmbeddingModel();
        }
        if (!StringUtils.hasText(props.embedding().openaiApiKey())) {
            log.warn("OPENAI_API_KEY is not set - ingestion and search are disabled (set EMBEDDING_PROVIDER=local to run offline)");
            return new MissingKeyEmbeddingModel();
        }
        return OpenAiEmbeddingModel.builder()
                .apiKey(props.embedding().openaiApiKey())
                .modelName(space.modelName())
                .maxRetries(3)
                .build();
    }

    static final class MissingKeyEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            throw new LlmConfig.LlmUnavailableException("OPENAI_API_KEY is not configured");
        }
    }
}
