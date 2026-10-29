package de.ragtractusx.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import de.ragtractusx.config.EmbeddingConfig.EmbeddingSpace;
import de.ragtractusx.ingest.IngestedFileRepository;
import de.ragtractusx.ingest.IngestionRunRepository;
import de.ragtractusx.ingest.IngestionRunRepository.LastRun;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Index size and freshness, read from the database (cached briefly). Ingestion usually runs in a Kubernetes Job whose
 * pod is never scraped, so the long-running app pods report these values instead.
 */
@Component
class IndexGauges {

    private static final Duration TTL = Duration.ofSeconds(30);

    IndexGauges(MeterRegistry registry, IngestedFileRepository files, IngestionRunRepository runs, EmbeddingSpace space) {
        String table = space.table();
        Supplier<Integer> chunks = cached(() -> files.totalChunks(table));
        Supplier<Optional<Instant>> lastSuccess = cached(() -> runs.lastSuccess(table));
        Supplier<Optional<LastRun>> lastRun = cached(() -> runs.lastRun(table));

        Gauge.builder("rag.index.chunks", () -> chunks.get())
                .description("Chunks in the vector index").register(registry);
        Gauge.builder("rag.ingestion.last.success.timestamp.seconds",
                        () -> lastSuccess.get().map(Instant::getEpochSecond).orElse(0L))
                .description("Unix time of the last successful ingestion run").register(registry);
        for (String change : new String[] {"added", "updated", "deleted", "failed"}) {
            Gauge.builder("rag.ingestion.last.run.files", () -> lastRun.get().map(r -> switch (change) {
                        case "added" -> r.added();
                        case "updated" -> r.updated();
                        case "deleted" -> r.deleted();
                        default -> r.failed();
                    }).orElse(0))
                    .tag("change", change)
                    .description("Files changed by the most recent ingestion run").register(registry);
        }
    }

    /** Memoises a database read for a short time so scrapes do not hit the database every time. */
    private static <T> Supplier<T> cached(Supplier<T> loader) {
        return new Supplier<>() {
            private T value;
            private Instant loadedAt = Instant.EPOCH;

            @Override
            public synchronized T get() {
                if (Instant.now().isAfter(loadedAt.plus(TTL))) {
                    try {
                        value = loader.get();
                    } catch (RuntimeException e) {
                        // Keep the previous value if the database is briefly unavailable.
                    }
                    loadedAt = Instant.now();
                }
                return value;
            }
        };
    }
}
