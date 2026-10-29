package de.ragtractusx.api;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.ragtractusx.config.EmbeddingConfig.EmbeddingSpace;
import de.ragtractusx.ingest.IngestedFileRepository;
import de.ragtractusx.ingest.IngestedFileRepository.KitSummary;
import de.ragtractusx.retrieval.RetrievedChunk;
import de.ragtractusx.retrieval.SearchRequest;
import de.ragtractusx.retrieval.SearchRequest.Mode;
import de.ragtractusx.retrieval.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Retrieval-only endpoints: no LLM call, no Claude cost. Useful for debugging relevance and for agents.
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class SearchController {

    public record SearchResponse(String query, String mode, List<RetrievedChunk> results) {
    }

    private final SearchService search;
    private final IngestedFileRepository files;
    private final EmbeddingSpace space;

    public SearchController(SearchService search, IngestedFileRepository files, EmbeddingSpace space) {
        this.search = search;
        this.files = files;
        this.space = space;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam("q") @NotBlank @Size(max = 2000) String query,
            @RequestParam(required = false) @Size(max = 100) String kit,
            @RequestParam(name = "k", required = false) @Min(1) @Max(50) Integer topK,
            @RequestParam(required = false) String mode) {
        Mode m = mode == null ? search.defaultMode() : Mode.parse(mode);
        int k = topK == null ? search.defaultTopK() : topK;
        return new SearchResponse(query, m.tag(), search.search(new SearchRequest(query, kit, k, m)));
    }

    @GetMapping("/kits")
    public List<KitSummary> kits() {
        return files.kits(space.table());
    }
}
