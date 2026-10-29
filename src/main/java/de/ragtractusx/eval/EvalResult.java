package de.ragtractusx.eval;

import java.util.List;

/**
 * Everything measured for one question under one configuration.
 */
public record EvalResult(
        String id,
        String category,
        String lang,
        boolean answerable,
        List<String> retrievedPaths,
        Integer firstRelevantRank,
        Double recall,
        String answer,
        String outcome,
        Double correctness,
        Double faithfulness,
        Double citationPrecision,
        Boolean abstained,
        String judgeComment,
        long latencyMs,
        long answerInputTokens,
        long answerOutputTokens,
        long judgeInputTokens,
        long judgeOutputTokens,
        String error) {
}
