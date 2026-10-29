package de.ragtractusx.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Guards eval/testset.yaml: it must parse, have the documented size and mix, and (when the corpus has been downloaded
 * by CorpusPreviewManualTest into target/corpus) every expected source must exist.
 */
class TestsetFileTest {

    private static final Path TESTSET = Path.of("eval/testset.yaml");
    private static final Path CORPUS = Path.of("target/corpus");

    @Test
    void testsetHasDocumentedShape() throws Exception {
        List<EvalItem> items = TestsetLoader.load(TESTSET);

        assertThat(items).hasSize(60);
        Map<String, Long> categories = items.stream()
                .collect(Collectors.groupingBy(EvalItem::category, Collectors.counting()));
        assertThat(categories.get("unanswerable")).isEqualTo(6);
        assertThat(categories.get("cross-kit")).isGreaterThanOrEqualTo(6);
        assertThat(items.stream().filter(i -> "de".equals(i.lang())).count()).isGreaterThanOrEqualTo(4);
        assertThat(items).filteredOn(EvalItem::answerable)
                .allSatisfy(i -> assertThat(i.keyPoints()).hasSizeBetween(1, 6));
    }

    @Test
    void expectedSourcesExistInCorpus() throws Exception {
        if (!Files.isDirectory(CORPUS)) {
            return; // run ./mvnw test -Pmanual -Dtest=CorpusPreviewManualTest first to download it
        }
        List<String> missing = TestsetLoader.load(TESTSET).stream()
                .flatMap(i -> i.expectedSources().stream().map(s -> i.id() + ": " + s))
                .filter(s -> !Files.exists(CORPUS.resolve(s.substring(s.indexOf(": ") + 2))))
                .toList();
        assertThat(missing).isEmpty();
    }
}
