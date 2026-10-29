package de.ragtractusx.retrieval;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.util.List;

import org.springframework.stereotype.Component;

import de.ragtractusx.metrics.RagMetrics;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Semantic search: embeds the query and asks pgvector (HNSW, cosine distance) for the nearest chunks.
 */
@Component
public class VectorRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final RagMetrics metrics;

    public VectorRetriever(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store, RagMetrics metrics) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.metrics = metrics;
    }

    public List<RetrievedChunk> search(String query, String kit, int maxResults, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        metrics.embeddingRequest("query");
        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(kit == null || kit.isBlank() ? null : metadataKey("kit").isEqualTo(kit))
                .build();
        return store.search(request).matches().stream()
                .map(m -> RetrievedChunk.of(m.embeddingId(), m.embedded().text(), m.embedded().metadata().toMap(),
                        m.score(), null, m.score()))
                .toList();
    }
}
