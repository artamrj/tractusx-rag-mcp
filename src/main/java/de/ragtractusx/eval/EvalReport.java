package de.ragtractusx.eval;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Renders the comparison table that goes into README / CV.
 */
final class EvalReport {

    private EvalReport() {
    }

    static String markdown(List<EvalSummary> summaries, List<EvalItem> items, Path testset, String model,
            boolean retrievalOnly, long unreviewed) {
        Map<String, Long> categories = items.stream()
                .collect(Collectors.groupingBy(EvalItem::category, TreeMap::new, Collectors.counting()));
        long german = items.stream().filter(i -> "de".equals(i.lang())).count();

        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluation ").append(LocalDate.now()).append("\n\n");
        sb.append("- Test set: `").append(testset).append("`, ").append(items.size()).append(" questions ")
                .append(categories).append(", ").append(german).append(" in German\n");
        if (unreviewed > 0) {
            sb.append("- **").append(unreviewed).append(" items are not marked `reviewed: true` yet**\n");
        }
        sb.append("- Answer model and judge: `").append(model).append("`")
                .append(retrievalOnly ? " (not used: retrieval-only run)" : "").append("\n");
        sb.append("- Retrieval metrics on answerable items; a hit means a chunk from an expected page was retrieved\n\n");

        sb.append("| Config | Hit@1 | Hit@3 | Hit@k | Recall@k | MRR |");
        if (!retrievalOnly) {
            sb.append(" Correctness | Fully correct | Faithfulness | Citation precision | Abstains when docs lack answer | False abstentions | p50 / p95 latency | Cost |");
        }
        sb.append("\n|---|---|---|---|---|---|");
        if (!retrievalOnly) {
            sb.append("---|---|---|---|---|---|---|---|");
        }
        sb.append('\n');
        for (EvalSummary s : summaries) {
            sb.append("| ").append(s.config())
                    .append(" | ").append(pct(s.hitAt1()))
                    .append(" | ").append(pct(s.hitAt3()))
                    .append(" | ").append(pct(s.hitAtK()))
                    .append(" | ").append(pct(s.recallAtK()))
                    .append(" | ").append(num(s.mrr()))
                    .append(" |");
            if (!retrievalOnly) {
                sb.append(' ').append(pct(s.correctness()))
                        .append(" | ").append(pct(s.fullyCorrect()))
                        .append(" | ").append(pct(s.faithfulness()))
                        .append(" | ").append(pct(s.citationPrecision()))
                        .append(" | ").append(pct(s.abstentionAccuracy()))
                        .append(" | ").append(pct(s.falseAbstentionRate()))
                        .append(" | ").append(String.format(Locale.ROOT, "%.1f s / %.1f s", s.latencyP50Ms() / 1000.0, s.latencyP95Ms() / 1000.0))
                        .append(" | ").append(String.format(Locale.ROOT, "$%.2f", s.costUsd()))
                        .append(" |");
            }
            sb.append('\n');
        }
        long errors = summaries.stream().mapToLong(EvalSummary::errors).sum();
        if (errors > 0) {
            sb.append("\n").append(errors).append(" item runs failed; see the JSON files.\n");
        }
        sb.append("\nCorrectness = share of reference key points covered; Faithfulness = share of answer claims "
                + "supported by the retrieved excerpts; both judged by the LLM with a fixed rubric.\n");
        return sb.toString();
    }

    private static String pct(Double v) {
        return v == null ? "-" : String.format(Locale.ROOT, "%.0f%%", v * 100);
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
