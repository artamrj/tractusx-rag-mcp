package de.ragtractusx.retrieval;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;

import de.ragtractusx.config.RagProperties;
import de.ragtractusx.metrics.RagMetrics;
import de.ragtractusx.retrieval.SearchRequest.Mode;

/**
 * Entry point for retrieval, used by the REST API, the RAG pipeline, MCP tools and the evaluation.
 */
@Service
public class SearchService {

    private final VectorRetriever vector;
    private final KeywordRetriever keyword;
    private final RagMetrics metrics;
    private final RagProperties.Retrieval config;

    public SearchService(VectorRetriever vector, KeywordRetriever keyword, RagMetrics metrics, RagProperties props) {
        this.vector = vector;
        this.keyword = keyword;
        this.metrics = metrics;
        this.config = props.retrieval();
    }

    public Mode defaultMode() {
        return Mode.parse(config.mode());
    }

    public int defaultTopK() {
        return config.topK();
    }

    /** Search with configured defaults for mode and topK. */
    public List<RetrievedChunk> search(String query, String kit, Integer topK) {
        return search(new SearchRequest(query, kit, topK != null ? topK : config.topK(), defaultMode()));
    }

    public List<RetrievedChunk> search(SearchRequest request) {
        long start = System.nanoTime();
        int candidates = Math.max(config.candidates(), request.topK());
        List<RetrievedChunk> result = switch (request.mode()) {
            case VECTOR -> vector.search(request.query(), request.kit(), request.topK(), config.minVectorScore());
            case HYBRID -> RrfFusion.fuse(
                    vector.search(request.query(), request.kit(), candidates, config.minVectorScore()),
                    keyword.search(request.query(), request.kit(), candidates),
                    config.rrfK(), request.topK());
        };
        double topScore = result.stream()
                .map(RetrievedChunk::vectorScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0);
        metrics.retrievalCompleted(request.mode().tag(), Duration.ofNanos(System.nanoTime() - start), result.size(), topScore);
        return result;
    }
}
