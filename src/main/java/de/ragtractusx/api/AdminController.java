package de.ragtractusx.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.ragtractusx.ingest.IngestionReport;
import de.ragtractusx.ingest.IngestionService;

/**
 * Admin operations, protected by X-Admin-Token (see {@link ApiKeyFilter}).
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final IngestionService ingestion;

    public AdminController(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /** Runs an incremental ingestion synchronously (typically 10-60 s; unchanged files are skipped). */
    @PostMapping("/ingest")
    public IngestionReport ingest() {
        return ingestion.ingest();
    }
}
