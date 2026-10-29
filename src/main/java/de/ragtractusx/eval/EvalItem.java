package de.ragtractusx.eval;

import java.util.List;

/**
 * One test-set question.
 *
 * @param category        factual | procedural | cross-kit | unanswerable
 * @param expectedSources repo paths of pages that contain the answer (empty for unanswerable items)
 * @param keyPoints       atomic facts a correct answer must contain (empty for unanswerable items)
 */
public record EvalItem(
        String id,
        String question,
        String lang,
        String category,
        List<String> expectedSources,
        String referenceAnswer,
        List<String> keyPoints,
        boolean reviewed) {

    public boolean answerable() {
        return !"unanswerable".equals(category);
    }
}
