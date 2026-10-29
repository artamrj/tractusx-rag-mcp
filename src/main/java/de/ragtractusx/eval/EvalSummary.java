package de.ragtractusx.eval;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Aggregated metrics for one configuration. Retrieval metrics cover answerable items only; abstention accuracy covers
 * unanswerable items only. Averages ignore items where a metric does not apply.
 */
public record EvalSummary(
        String config,
        int items,
        int answerable,
        int unanswerable,
        int errors,
        int topK,
        double hitAt1,
        double hitAt3,
        double hitAtK,
        double recallAtK,
        double mrr,
        Double correctness,
        Double fullyCorrect,
        Double faithfulness,
        Double citationPrecision,
        Double abstentionAccuracy,
        Double falseAbstentionRate,
        long latencyP50Ms,
        long latencyP95Ms,
        double avgInputTokens,
        double avgOutputTokens,
        double costUsd) {

    public static EvalSummary of(String config, int topK, List<EvalResult> results, double inputPricePerM,
            double outputPricePerM) {
        List<EvalResult> ok = results.stream().filter(r -> r.error() == null).toList();
        List<EvalResult> answerable = ok.stream().filter(EvalResult::answerable).toList();
        List<EvalResult> unanswerable = ok.stream().filter(r -> !r.answerable()).toList();
        boolean generated = ok.stream().anyMatch(r -> r.outcome() != null);

        long[] latencies = ok.stream().filter(r -> r.outcome() != null).mapToLong(EvalResult::latencyMs).sorted().toArray();
        long inTokens = ok.stream().mapToLong(r -> r.answerInputTokens() + r.judgeInputTokens()).sum();
        long outTokens = ok.stream().mapToLong(r -> r.answerOutputTokens() + r.judgeOutputTokens()).sum();

        return new EvalSummary(config, results.size(), answerable.size(), unanswerable.size(),
                results.size() - ok.size(), topK,
                share(answerable, r -> rank(r) == 1),
                share(answerable, r -> rank(r) >= 1 && rank(r) <= 3),
                share(answerable, r -> rank(r) >= 1),
                mean(answerable, EvalResult::recall),
                mean(answerable, r -> rank(r) == 0 ? 0.0 : 1.0 / rank(r)),
                generated ? mean(answerable, EvalResult::correctness) : null,
                generated ? shareNullable(answerable, r -> r.correctness() == null ? null : r.correctness() >= 0.999) : null,
                generated ? mean(ok, EvalResult::faithfulness) : null,
                generated ? mean(ok, EvalResult::citationPrecision) : null,
                generated ? shareNullable(unanswerable, EvalResult::abstained) : null,
                generated ? shareNullable(answerable, EvalResult::abstained) : null,
                percentile(latencies, 0.50), percentile(latencies, 0.95),
                ok.isEmpty() ? 0 : ok.stream().mapToLong(EvalResult::answerInputTokens).average().orElse(0),
                ok.isEmpty() ? 0 : ok.stream().mapToLong(EvalResult::answerOutputTokens).average().orElse(0),
                inTokens * inputPricePerM / 1e6 + outTokens * outputPricePerM / 1e6);
    }

    private static int rank(EvalResult r) {
        return r.firstRelevantRank() == null ? 0 : r.firstRelevantRank();
    }

    private static double share(List<EvalResult> results, Predicate<EvalResult> p) {
        return results.isEmpty() ? 0 : results.stream().filter(p).count() / (double) results.size();
    }

    private static Double shareNullable(List<EvalResult> results, Function<EvalResult, Boolean> f) {
        List<Boolean> values = results.stream().map(f).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : values.stream().filter(b -> b).count() / (double) values.size();
    }

    private static Double mean(List<EvalResult> results, Function<EvalResult, Double> f) {
        double[] values = results.stream().map(f).filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        return values.length == 0 ? null : Arrays.stream(values).average().orElse(0);
    }

    private static long percentile(long[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
