package de.ragtractusx.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RrfFusionTest {

    private static RetrievedChunk chunk(String id, Double vectorScore) {
        return RetrievedChunk.of(id, "text " + id, Map.of("url", "https://x/" + id), vectorScore, null,
                vectorScore == null ? 0 : vectorScore);
    }

    @Test
    void documentsFoundByBothRetrieversRankFirst() {
        List<RetrievedChunk> vector = List.of(chunk("a", 0.9), chunk("b", 0.8), chunk("c", 0.7));
        List<RetrievedChunk> keyword = List.of(chunk("c", null), chunk("d", null));

        List<RetrievedChunk> fused = RrfFusion.fuse(vector, keyword, 60, 10);

        assertThat(fused).extracting(RetrievedChunk::id).containsExactly("c", "a", "b", "d");
        RetrievedChunk c = fused.getFirst();
        assertThat(c.vectorScore()).isEqualTo(0.7);
        assertThat(c.keywordRank()).isEqualTo(1);
        assertThat(c.score()).isEqualTo(1.0 / 63 + 1.0 / 61);
    }

    @Test
    void respectsTopK() {
        List<RetrievedChunk> vector = List.of(chunk("a", 0.9), chunk("b", 0.8), chunk("c", 0.7));
        assertThat(RrfFusion.fuse(vector, List.of(), 60, 2)).extracting(RetrievedChunk::id).containsExactly("a", "b");
    }

    @Test
    void tsQueryOrsTermsAndDropsPunctuation() {
        assertThat(KeywordRetriever.toTsQuery("How do I register a twin in the DTR?"))
                .isEqualTo("how | do | i | register | a | twin | in | the | dtr");
        assertThat(KeywordRetriever.toTsQuery("urn:samm:io.catenax & (BPNL)!")).isEqualTo("urn | samm | io | catenax | bpnl");
        assertThat(KeywordRetriever.toTsQuery("?!")).isEmpty();
    }
}
