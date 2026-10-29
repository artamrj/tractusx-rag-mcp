package de.ragtractusx.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class HeadingChunkerTest {

    @Test
    void packsSmallSectionsAndAddsBreadcrumbs() {
        String body = """
                Intro paragraph.

                ## Vision & Mission

                ### Vision

                Sharing data adds value.

                ### Mission

                Data transfer needs trust.
                """;

        List<DocChunk> chunks = new HeadingChunker(2000, 200).chunk("Connector KIT › Adoption View", body);

        assertThat(chunks).hasSize(1);
        DocChunk chunk = chunks.getFirst();
        assertThat(chunk.text()).startsWith("Connector KIT › Adoption View\n\n");
        assertThat(chunk.text()).contains("Sharing data adds value.").contains("Data transfer needs trust.");
    }

    @Test
    void splitsAtHeadingsWhenSectionsAreLarge() {
        String para = "Lorem ipsum dolor sit amet. ".repeat(20); // ~560 chars
        String body = "## First\n\n" + para + "\n\n## Second\n\n" + para + "\n\n### Second Sub\n\n" + para;

        List<DocChunk> chunks = new HeadingChunker(800, 100).chunk("Root", body);

        assertThat(chunks).extracting(DocChunk::section).containsExactly("First", "Second", "Second › Second Sub");
        assertThat(chunks).extracting(DocChunk::anchor).containsExactly("first", "second", "second-sub");
        assertThat(chunks.get(2).text()).startsWith("Root › Second › Second Sub\n\n### Second Sub");
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(800 + 40));
    }

    @Test
    void neverSplitsInsideACodeBlockThatFits() {
        String code = "```bash\n" + "echo line\n".repeat(30) + "```";
        String para = "Paragraph text. ".repeat(30);
        String body = "## Install\n\n" + para + "\n\n" + code + "\n\n" + para;

        List<DocChunk> chunks = new HeadingChunker(700, 100).chunk("Root", body);

        assertThat(chunks).anySatisfy(c -> assertThat(c.text()).contains(code));
        assertThat(chunks).allSatisfy(c ->
                assertThat(c.text().split("```", -1).length % 2).as("balanced fences").isEqualTo(1));
    }

    @Test
    void largeSectionsGetOverlap() {
        String body = "## Long\n\n" + "First paragraph sentence.\n\n".repeat(60);

        List<DocChunk> chunks = new HeadingChunker(500, 120).chunk("Root", body);

        assertThat(chunks).hasSizeGreaterThan(2);
        for (int i = 1; i < chunks.size(); i++) {
            String previousTail = chunks.get(i - 1).text().substring(chunks.get(i - 1).text().length() - 25);
            assertThat(chunks.get(i).text()).contains(previousTail.strip());
        }
    }

    @Test
    void customHeadingIdsAreUsedAsAnchors() {
        List<DocChunk> chunks = new HeadingChunker(50, 0).chunk("Root",
                "## Setup the connector {#setup}\n\nSome text that is long enough to be separate.\n\n## Next\n\nMore text here as well.");
        assertThat(chunks.getFirst().anchor()).isEqualTo("setup");
        assertThat(chunks.getFirst().section()).isEqualTo("Setup the connector");
    }

    @Test
    void headingOnlyPagesProduceNoChunks() {
        assertThat(new HeadingChunker().chunk("Root", "# Title\n\n## Empty")).isEmpty();
    }
}
