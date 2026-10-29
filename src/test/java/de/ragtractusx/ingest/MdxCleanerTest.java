package de.ragtractusx.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MdxCleanerTest {

    private final MdxCleaner cleaner = new MdxCleaner();

    @Test
    void parsesFrontMatterAndStripsMdxNoise() {
        String raw = """
                ---
                id: adoption-view
                title: Adoption View
                sidebar_position: 1
                ---

                import styles from "../resources/styles.module.css";
                import BillOfMaterials from './parts/bill-of-materials.mdx'

                ![Connector kit banner](@site/static/img/kits/connector/logo.svg)

                <div className={styles.logoContainer}>
                    <h2>Success Story</h2>
                </div>

                The KIT is based on [Dataspaces][idsa-url] and the [DSP](https://example.org/dsp "spec").

                <iframe width="100%" height="550"
                  src="https://www.youtube-nocookie.com/embed/abc" allowfullscreen></iframe>

                <!-- a hidden comment -->
                {/* an mdx comment */}

                :::warning Breaking change
                Use version 0.7 or newer.
                :::

                <BillOfMaterials components={props.components} />

                [idsa-url]: https://internationaldataspaces.org
                """;

        MdxCleaner.CleanDoc doc = cleaner.clean("docs-kits/kits/connector-kit/adoption-view.mdx", raw);

        assertThat(doc.title()).isEqualTo("Adoption View");
        assertThat(doc.frontMatterString("id")).isEqualTo("adoption-view");
        assertThat(doc.body())
                .contains("Success Story")
                .contains("The KIT is based on Dataspaces and the DSP.")
                .contains("Warning: Breaking change")
                .contains("Use version 0.7 or newer.")
                .doesNotContain("import ")
                .doesNotContain("<div")
                .doesNotContain("iframe")
                .doesNotContain("youtube")
                .doesNotContain("hidden comment")
                .doesNotContain("mdx comment")
                .doesNotContain("BillOfMaterials")
                .doesNotContain("internationaldataspaces.org")
                .doesNotContain("logo.svg")
                .doesNotContain(":::");
    }

    @Test
    void keepsCodeBlocksVerbatim() {
        String raw = """
                # Setup

                ```yaml
                import: this is not an mdx import
                <Component prop="kept" />
                [link](kept)
                ```

                Text with `List<String>` stays.
                """;

        String body = cleaner.clean("docs/setup.md", raw).body();

        assertThat(body)
                .contains("import: this is not an mdx import")
                .contains("<Component prop=\"kept\" />")
                .contains("[link](kept)")
                .contains("`List<String>`");
    }

    @Test
    void mdxCodeBlocksAreTreatedAsMdx() {
        String raw = """
                ## Index

                ```mdx-code-block
                import DocCardList from '@theme/DocCardList';

                <DocCardList />
                ```

                After.
                """;
        String body = cleaner.clean("docs/release/trg-0/index.md", raw).body();
        assertThat(body).isEqualTo("## Index\n\nAfter.");
    }

    @Test
    void titleFallsBackToH1ThenFileName() {
        assertThat(cleaner.clean("docs/x.md", "# From Heading\n\ntext").title()).isEqualTo("From Heading");
        assertThat(cleaner.clean("community/sigs/01_intro.md", "just text").title()).isEqualTo("intro");
    }

    @Test
    void brokenFrontMatterDoesNotFailTheFile() {
        MdxCleaner.CleanDoc doc = cleaner.clean("docs/x.md", "---\ntitle: [unclosed\n---\nBody text");
        assertThat(doc.body()).isEqualTo("Body text");
    }
}
