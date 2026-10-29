package de.ragtractusx.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import de.ragtractusx.config.RagProperties;

/**
 * Downloads the real corpus from GitHub (free, no API keys), cleans and chunks every page and writes a preview to
 * target/corpus-preview.md plus the raw files to target/corpus/ (usable as DOCS_LOCAL_PATH).
 * Run with: ./mvnw test -Pmanual -Dtest=CorpusPreviewManualTest
 */
@Tag("manual")
class CorpusPreviewManualTest {

    @Test
    void previewRealCorpus() throws Exception {
        var props = new RagProperties.Source("github", "eclipse-tractusx", "eclipse-tractusx.github.io", "main", "latest",
                System.getenv("GITHUB_TOKEN"), null,
                List.of("docs-kits_versioned_docs/version-{kits-version}/", "docs/", "community/", "documentation/"),
                "https://eclipse-tractusx.github.io");
        var source = new GitHubDocsSource(RestClient.builder(), props);
        List<DocRef> refs = source.list();
        assertThat(refs).hasSizeGreaterThan(200);

        Path corpusDir = Path.of("target/corpus");
        var cleaner = new MdxCleaner();
        var chunker = new HeadingChunker();
        var urls = new DocUrlMapper(props.siteUrl());
        var lines = new ConcurrentLinkedQueue<String>();
        var chunkSizes = new ConcurrentLinkedQueue<Integer>();
        var emptyPages = new ConcurrentLinkedQueue<String>();

        try (var pool = Executors.newFixedThreadPool(8)) {
            for (DocRef ref : refs) {
                pool.submit(() -> {
                    String raw = source.read(ref);
                    write(corpusDir.resolve(ref.path()), raw);
                    var doc = cleaner.clean(ref.path(), raw);
                    String kit = KitNames.kitId(ref.path());
                    String root = kit != null ? KitNames.displayName(kit) : KitNames.areaName(ref.path());
                    var chunks = chunker.chunk(root + " › " + doc.title(), doc.body());
                    if (chunks.isEmpty()) {
                        emptyPages.add(ref.path());
                    }
                    chunks.forEach(c -> chunkSizes.add(c.text().length()));
                    String url = urls.pageUrl(ref.path(), doc.frontMatterString("id"), doc.frontMatterString("slug"));
                    StringBuilder sb = new StringBuilder("\n\n=== " + ref.path() + "\n" + url + "\n");
                    chunks.stream().limit(2).forEach(c -> sb.append("--- [").append(c.section()).append("] #")
                            .append(c.anchor()).append('\n').append(c.text(), 0, Math.min(600, c.text().length())).append('\n'));
                    lines.add(sb.toString());
                    return null;
                });
            }
        }
        write(corpusDir.resolve("docs-kits_versions.json"), "[\"" + source.describe().replaceAll(".*kits (.*)\\)", "$1") + "\"]");

        IntSummaryStatistics stats = chunkSizes.stream().mapToInt(Integer::intValue).summaryStatistics();
        List<String> report = new ArrayList<>();
        report.add("# Corpus preview: " + source.describe());
        report.add("pages=" + refs.size() + " chunks=" + stats.getCount() + " avgChars=" + (int) stats.getAverage()
                + " maxChars=" + stats.getMax() + " minChars=" + stats.getMin());
        report.add("pages without chunks: " + emptyPages);
        report.addAll(lines);
        write(Path.of("target/corpus-preview.md"), String.join("\n", report));
        System.out.println(report.get(0) + "\n" + report.get(1) + "\n" + report.get(2));
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
