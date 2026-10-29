package de.ragtractusx.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.ragtractusx.ingest.IngestionService;
import de.ragtractusx.retrieval.SearchRequest.Mode;
import de.ragtractusx.support.PostgresTestBase;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchIT extends PostgresTestBase {

    static final Path DOCS_DIR = copyFixtures();

    @DynamicPropertySource
    static void docs(DynamicPropertyRegistry registry) {
        registry.add("rag.source.local-path", DOCS_DIR::toString);
    }

    @Autowired
    SearchService search;
    @Autowired
    IngestionService ingestion;
    @Autowired
    MockMvc mvc;

    @BeforeAll
    void ingest() {
        ingestion.ingest();
    }

    @Test
    void paraphrasedQuestionFindsTheRightPage() {
        List<RetrievedChunk> results = search.search(
                new SearchRequest("How can I add a new digital twin to the registry?", null, 3, Mode.HYBRID));
        assertThat(results.getFirst().kit()).isEqualTo("digital-twin-kit");
        assertThat(results.getFirst().vectorScore()).isNotNull();
    }

    @Test
    void exactAcronymIsFoundByKeywordSearch() {
        List<RetrievedChunk> results = search.search(new SearchRequest("BPNL", null, 3, Mode.HYBRID));
        assertThat(results.getFirst().text()).contains("BPNL");
        assertThat(results.getFirst().keywordRank()).isEqualTo(1);
    }

    @Test
    void kitFilterRestrictsResults() {
        List<RetrievedChunk> results = search.search(new SearchRequest("data exchange", "connector-kit", 5, Mode.HYBRID));
        assertThat(results).isNotEmpty().allSatisfy(r -> assertThat(r.kit()).isEqualTo("connector-kit"));
    }

    @Test
    void vectorModeReturnsSimilarityScores() {
        List<RetrievedChunk> results = search.search(new SearchRequest("Helm chart local cluster", null, 2, Mode.VECTOR));
        assertThat(results).isNotEmpty().allSatisfy(r -> assertThat(r.score()).isBetween(0.0, 1.0));
        assertThat(results.getFirst().path()).isEqualTo("docs/getting-started.md");
    }

    @Test
    void searchEndpoint() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "shell descriptor lookup").param("k", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("hybrid"))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].url").exists());
        mvc.perform(get("/api/v1/search").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/kits")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kit == 'connector-kit')].pages").value(1));
    }

    @Test
    void askWithoutAnthropicKeyIsServiceUnavailable() throws Exception {
        mvc.perform(post("/api/v1/ask").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a BPNL?\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void adminEndpointNeedsToken() throws Exception {
        mvc.perform(post("/admin/ingest")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/ingest").header("X-Admin-Token", "test-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unchanged").value(3));
    }
}
