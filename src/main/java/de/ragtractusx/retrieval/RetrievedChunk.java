package de.ragtractusx.retrieval;

import java.util.Map;

/**
 * A chunk returned by retrieval, with the scores that got it there.
 *
 * @param vectorScore cosine-based similarity in [0,1], or null if only the keyword search found it
 * @param keywordRank 1-based rank in the full-text search, or null
 * @param score       final ranking score (RRF score in hybrid mode, vector score in vector mode)
 */
public record RetrievedChunk(
        String id,
        String text,
        String url,
        String title,
        String kit,
        String kitName,
        String section,
        String path,
        Double vectorScore,
        Integer keywordRank,
        double score) {

    static RetrievedChunk of(String id, String text, Map<String, ?> metadata, Double vectorScore, Integer keywordRank,
            double score) {
        return new RetrievedChunk(id, text,
                str(metadata.get("url")), str(metadata.get("title")), str(metadata.get("kit")),
                str(metadata.get("kit_name")), str(metadata.get("section")), str(metadata.get("path")),
                vectorScore, keywordRank, score);
    }

    RetrievedChunk withScores(Double vector, Integer keyword, double fused) {
        return new RetrievedChunk(id, text, url, title, kit, kitName, section, path, vector, keyword, fused);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
