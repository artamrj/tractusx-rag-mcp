package de.ragtractusx.ingest;

import java.time.Duration;
import java.util.List;

/**
 * Outcome of one ingestion run.
 */
public record IngestionReport(
        String source,
        int listed,
        int added,
        int updated,
        int unchanged,
        int deleted,
        int failed,
        int chunksWritten,
        int totalChunks,
        Duration duration,
        List<String> failures) {
}
