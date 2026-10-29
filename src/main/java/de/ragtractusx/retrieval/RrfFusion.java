package de.ragtractusx.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion: score(d) = Σ 1 / (k + rank_i(d)). Rank-based, so the very different score scales of cosine
 * similarity and ts_rank do not need to be calibrated against each other.
 */
public final class RrfFusion {

    private RrfFusion() {
    }

    public static List<RetrievedChunk> fuse(List<RetrievedChunk> vector, List<RetrievedChunk> keyword, int k, int topK) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, RetrievedChunk> chunks = new LinkedHashMap<>();
        Map<String, Double> vectorScores = new LinkedHashMap<>();
        Map<String, Integer> keywordRanks = new LinkedHashMap<>();

        for (int i = 0; i < vector.size(); i++) {
            RetrievedChunk c = vector.get(i);
            scores.merge(c.id(), 1.0 / (k + i + 1), Double::sum);
            chunks.putIfAbsent(c.id(), c);
            vectorScores.put(c.id(), c.vectorScore());
        }
        for (int i = 0; i < keyword.size(); i++) {
            RetrievedChunk c = keyword.get(i);
            scores.merge(c.id(), 1.0 / (k + i + 1), Double::sum);
            chunks.putIfAbsent(c.id(), c);
            keywordRanks.put(c.id(), i + 1);
        }

        List<RetrievedChunk> fused = new ArrayList<>();
        scores.forEach((id, score) -> fused.add(
                chunks.get(id).withScores(vectorScores.get(id), keywordRanks.get(id), score)));
        fused.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return fused.subList(0, Math.min(topK, fused.size()));
    }
}
