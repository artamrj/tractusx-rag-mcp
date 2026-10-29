package de.ragtractusx.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EvalScoringTest {

    @Test
    void retrievalScoresArePageLevelAndVersionIndependent() {
        List<String> retrieved = List.of(
                "docs/getting-started.md",
                "docs-kits_versioned_docs/version-26.09/kits/connector-kit/adoption-view/adoption-view.md",
                "docs-kits_versioned_docs/version-26.09/kits/connector-kit/adoption-view/adoption-view.md",
                "docs-kits_versioned_docs/version-26.09/kits/digital-twin-kit/x.md");
        List<String> expected = List.of(
                "docs-kits_versioned_docs/version-26.06/kits/connector-kit/adoption-view/adoption-view.md",
                "docs-kits_versioned_docs/version-26.09/kits/connector-kit/operation-view/operation-view.md");

        RetrievalScores scores = RetrievalScores.score(retrieved, expected);

        assertThat(scores.firstRelevantRank()).isEqualTo(2);
        assertThat(scores.recall()).isEqualTo(0.5);
        assertThat(scores.hitAt(1)).isFalse();
        assertThat(scores.hitAt(3)).isTrue();
        assertThat(scores.reciprocalRank()).isEqualTo(0.5);
        assertThat(RetrievalScores.score(List.of("docs/a.md"), expected).hitAt(10)).isFalse();
    }

    @Test
    void judgeJsonIsParsedTolerantly() {
        var judge = new JudgeService(null);
        String text = """
                Here is my grading:
                {"key_points": [true, false, true], "claims_total": 4, "claims_supported": 3,
                 "citations_total": 2, "citations_valid": 2, "abstained": false, "comment": "misses one point"}
                """;

        JudgeService.Verdict v = judge.parse(text, 4, 1000, 100);

        assertThat(v.keyPoints()).containsExactly(true, false, true, false);
        assertThat(v.correctness()).isEqualTo(0.5);
        assertThat(v.faithfulness()).isEqualTo(0.75);
        assertThat(v.citationPrecision()).isEqualTo(1.0);
        assertThat(v.abstained()).isFalse();

        JudgeService.Verdict abstain = judge.parse("{\"key_points\":[],\"claims_total\":0,\"citations_total\":0,"
                + "\"abstained\":true}", 0, 0, 0);
        assertThat(abstain.correctness()).isNull();
        assertThat(abstain.faithfulness()).isNull();
        assertThat(abstain.abstained()).isTrue();

        assertThatThrownBy(() -> judge.parse("no json", 1, 0, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void summaryAggregatesOnlyApplicableItems() {
        List<EvalResult> results = List.of(
                result("A", true, 1, 1.0, 1.0, 1.0, false, 1000),
                result("B", true, 3, 0.5, 0.5, 0.8, false, 3000),
                result("C", true, 0, 0.0, 0.0, null, true, 2000),
                result("U", false, null, null, null, null, true, 1500));

        EvalSummary s = EvalSummary.of("hybrid-k6", 6, results, 4, 20);

        assertThat(s.answerable()).isEqualTo(3);
        assertThat(s.hitAt1()).isEqualTo(1 / 3.0);
        assertThat(s.hitAt3()).isEqualTo(2 / 3.0);
        assertThat(s.mrr()).isEqualTo((1 + 1 / 3.0) / 3);
        assertThat(s.correctness()).isEqualTo(0.5);
        assertThat(s.fullyCorrect()).isEqualTo(1 / 3.0);
        assertThat(s.faithfulness()).isEqualTo(0.9);
        assertThat(s.abstentionAccuracy()).isEqualTo(1.0);
        assertThat(s.falseAbstentionRate()).isEqualTo(1 / 3.0);
        assertThat(s.latencyP50Ms()).isEqualTo(1500);
        assertThat(s.latencyP95Ms()).isEqualTo(3000);
        assertThat(s.costUsd()).isEqualTo(4 * (2000 * 4 / 1e6 + 200 * 20 / 1e6), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void testsetValidation() {
        var valid = List.of(
                Map.of("id", "A01", "question", "q?", "category", "factual", "expected_sources", List.of("docs/a.md"),
                        "reference_answer", "r", "key_points", List.of("k")),
                Map.of("id", "U01", "question", "q2?", "category", "unanswerable"));
        assertThat(TestsetLoader.parse(valid)).hasSize(2).last().extracting(EvalItem::answerable).isEqualTo(false);

        assertThatThrownBy(() -> TestsetLoader.parse(List.of(Map.of("id", "A", "question", "q", "category", "factual"))))
                .hasMessageContaining("expected_sources");
        assertThatThrownBy(() -> TestsetLoader.parse(List.of(valid.get(1), valid.get(1))))
                .hasMessageContaining("Duplicate");
    }

    private static EvalResult result(String id, boolean answerable, Integer rank, Double recall, Double correctness,
            Double faithfulness, Boolean abstained, long latency) {
        return new EvalResult(id, answerable ? "factual" : "unanswerable", "en", answerable, List.of(), rank, recall,
                "answer", "answered", correctness, faithfulness, null, abstained, "", latency,
                1000, 100, 1000, 100, null);
    }
}
