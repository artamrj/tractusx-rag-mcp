package de.ragtractusx.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class KitNamesTest {

    @Test
    void kitIdAndDisplayName() {
        assertThat(KitNames.kitId("docs-kits_versioned_docs/version-26.09/kits/connector-kit/adoption-view.md"))
                .isEqualTo("connector-kit");
        assertThat(KitNames.kitId("docs-kits/kits/puris-kit/x.md")).isEqualTo("puris-kit");
        assertThat(KitNames.kitId("docs/getting-started.md")).isNull();
        assertThat(KitNames.displayName("product-carbon-footprint-exchange-kit"))
                .isEqualTo("Product Carbon Footprint Exchange KIT");
        assertThat(KitNames.displayName("puris-kit")).isEqualTo("PURIS KIT");
        assertThat(KitNames.area("docs-kits_versioned_docs/version-26.09/kits/a/b.md")).isEqualTo("docs-kits");
    }

    @Test
    void pathFilterResolvesKitsVersion() {
        DocPathFilter filter = DocPathFilter.withKitsVersion(
                List.of("docs-kits_versioned_docs/version-{kits-version}/", "docs/"), "26.09");

        assertThat(filter.accept("docs-kits_versioned_docs/version-26.09/kits/a/page.md")).isTrue();
        assertThat(filter.accept("docs-kits_versioned_docs/version-26.06/kits/a/page.md")).isFalse();
        assertThat(filter.accept("docs-kits_versioned_docs/version-26.09/kit-template/README.md")).isFalse();
        assertThat(filter.accept("docs/_partial.md")).isFalse();
        assertThat(filter.accept("docs/image.png")).isFalse();
        assertThat(filter.accept("docs/kits/x/changelog.md")).isFalse();
        assertThat(DocPathFilter.latestVersion("[\n  \"26.09\",\n  \"26.06\"\n]")).isEqualTo("26.09");
    }
}
