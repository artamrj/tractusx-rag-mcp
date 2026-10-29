package de.ragtractusx.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Expected URLs were checked against https://eclipse-tractusx.github.io (HTTP 200) on 2026-10-29.
 */
class DocUrlMapperTest {

    private final DocUrlMapper mapper = new DocUrlMapper("https://eclipse-tractusx.github.io");

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            community/intro.md | - | /community/intro
            community/sigs/01_intro.md | - | /community/sigs/intro
            docs/getting-started.md | - | /docs/getting-started
            docs/oss/how-to-contribute.md | - | /docs/oss/how-to-contribute
            docs/release/trg-1/trg-1-06.md | - | /docs/release/trg-1/trg-1-06
            documentation/kit-framework.mdx | - | /documentation/kit-framework
            docs-kits_versioned_docs/version-26.09/kits/connector-kit/adoption-view/adoption-view.md | adoption-view | /docs-kits/kits/connector-kit/adoption-view
            docs-kits_versioned_docs/version-26.09/kits/industry-core-kit/adoption-view.mdx | adoption-view | /docs-kits/kits/industry-core-kit/adoption-view
            docs-kits_versioned_docs/version-26.09/kits/industry-core-kit/software-operation-view.mdx | software-operation-view | /docs-kits/kits/industry-core-kit/software-operation-view
            docs-kits_versioned_docs/version-26.09/kits/digital-twin-kit/software-development-view/software-development-view.md | software-development-view | /docs-kits/kits/digital-twin-kit/software-development-view
            docs-kits_versioned_docs/version-26.09/kits/behaviour-twin-kit/adoption-view/overview.md | - | /docs-kits/kits/behaviour-twin-kit/adoption-view/overview
            docs-kits_versioned_docs/version-26.09/kits/behaviour-twin-kit/use-cases/rul/adoption-view/introduction.md | - | /docs-kits/kits/behaviour-twin-kit/use-cases/rul/adoption-view/introduction
            docs-kits_versioned_docs/version-26.09/kits/data-chain-kit/documentation/admin-guide.md | admin-guide | /docs-kits/kits/data-chain-kit/documentation/admin-guide
            docs-kits_versioned_docs/version-26.09/kits/demand-and-capacity-management-kit/software-development-view/model-capacity-group.md | model-WeekBasedCapacityGroup | /docs-kits/kits/demand-and-capacity-management-kit/software-development-view/model-WeekBasedCapacityGroup
            docs-kits_versioned_docs/version-26.09/kits/environmental-and-social-standards-kit/adoption-view.md | adoption-view | /docs-kits/kits/environmental-and-social-standards-kit/adoption-view
            docs-kits_versioned_docs/version-26.09/kits/logistics-kit/documentation.md | documentation | /docs-kits/kits/logistics-kit/documentation
            docs-kits_versioned_docs/version-26.09/kits/puris-kit/adoption-view/integration-scenarios/single-sourcing-scenario.md | Single Sourcing Scenario | /docs-kits/kits/puris-kit/adoption-view/integration-scenarios/Single%20Sourcing%20Scenario
            docs-kits/kits/unit-process-as-a-service-kit/development-view/development-view.md | development-view | /docs-kits/next/kits/unit-process-as-a-service-kit/development-view
            """)
    void mapsRepoPathsToPublishedUrls(String path, String id, String expectedPath) {
        assertThat(mapper.pageUrl(path, id, null)).isEqualTo("https://eclipse-tractusx.github.io" + expectedPath);
    }

    @Test
    void slugOverridesEverything() {
        assertThat(mapper.pageUrl("docs/oss/some-file.md", "ignored", "/custom/place"))
                .isEqualTo("https://eclipse-tractusx.github.io/docs/custom/place");
        assertThat(mapper.pageUrl("docs/oss/some-file.md", null, "relative"))
                .isEqualTo("https://eclipse-tractusx.github.io/docs/oss/relative");
    }

    @Test
    void anchorsFollowGithubSlugger() {
        assertThat(DocUrlMapper.anchor("Vision & Mission")).isEqualTo("vision--mission");
        assertThat(DocUrlMapper.anchor("Step 1: Install the EDC")).isEqualTo("step-1-install-the-edc");
        assertThat(DocUrlMapper.anchor("`Asset` API (v3)")).isEqualTo("asset-api-v3");
    }

    @Test
    void sectionUrlAppendsAnchor() {
        assertThat(mapper.sectionUrl("https://x/y", "mission")).isEqualTo("https://x/y#mission");
        assertThat(mapper.sectionUrl("https://x/y", null)).isEqualTo("https://x/y");
    }
}
