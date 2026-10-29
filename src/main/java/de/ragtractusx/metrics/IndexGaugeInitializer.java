package de.ragtractusx.metrics;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import de.ragtractusx.config.EmbeddingConfig.EmbeddingSpace;
import de.ragtractusx.ingest.IngestedFileRepository;

/**
 * Sets the indexed-chunks gauge from the database at startup, so it is correct after a restart and in pods that never
 * ran an ingestion themselves.
 */
@Component
class IndexGaugeInitializer {

    private final IngestedFileRepository files;
    private final EmbeddingSpace space;
    private final RagMetrics metrics;

    IndexGaugeInitializer(IngestedFileRepository files, EmbeddingSpace space, RagMetrics metrics) {
        this.files = files;
        this.space = space;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    void init() {
        metrics.indexedChunks(files.totalChunks(space.table()));
    }
}
