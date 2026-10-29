package de.ragtractusx.rag;

import java.util.List;

/**
 * Result of a question.
 *
 * @param answered false when the docs did not cover the question, nothing was retrieved, or the model declined
 * @param sources  all excerpts given to the model, numbered as in the answer; {@code cited} marks the ones referenced
 */
public record AnswerResponse(
        String answer,
        boolean answered,
        String outcome,
        List<Source> sources,
        String model,
        Usage usage,
        long latencyMs) {

    public record Source(int n, String title, String url, String kit, String section, double score, boolean cited) {
    }

    public record Usage(long inputTokens, long outputTokens) {
        public static final Usage NONE = new Usage(0, 0);
    }
}
