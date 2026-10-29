package de.ragtractusx.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import de.ragtractusx.ingest.IngestionReport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Application-level metrics, exported at /actuator/prometheus. Names follow Prometheus conventions after Micrometer's
 * renaming (e.g. {@code rag.ask.duration} → {@code rag_ask_duration_seconds}).
 */
@Component
public class RagMetrics {

    /** How an /ask request ended; used as a tag. */
    public enum Outcome {
        ANSWERED, NO_ANSWER, REFUSED, ERROR;

        public String tag() {
            return name().toLowerCase();
        }
    }

    private final MeterRegistry registry;
    private final AtomicLong lastIngestionSuccess = new AtomicLong();
    private final AtomicLong indexedChunks = new AtomicLong();

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("rag.ingestion.last.success.timestamp.seconds", lastIngestionSuccess);
        registry.gauge("rag.index.chunks", indexedChunks);
    }

    public void askCompleted(Outcome outcome, boolean stream, String channel, Duration duration) {
        Timer.builder("rag.ask.duration")
                .description("End-to-end time to answer a question (retrieval + LLM)")
                .tag("outcome", outcome.tag())
                .tag("stream", Boolean.toString(stream))
                .tag("channel", channel)
                .register(registry)
                .record(duration);
    }

    public void retrievalCompleted(String mode, Duration duration, int chunks, double topScore) {
        Timer.builder("rag.retrieval.duration")
                .description("Time to embed the query and fetch context chunks")
                .tag("mode", mode)
                .register(registry)
                .record(duration);
        DistributionSummary.builder("rag.retrieval.chunks")
                .description("Number of chunks passed to the LLM")
                .register(registry)
                .record(chunks);
        DistributionSummary.builder("rag.retrieval.top.score")
                .description("Vector similarity of the best chunk (0-1)")
                .register(registry)
                .record(topScore);
    }

    public void llmTokens(String model, long input, long output, long cacheRead) {
        Counter.builder("rag.llm.tokens").tag("model", model).tag("type", "input").register(registry).increment(input);
        Counter.builder("rag.llm.tokens").tag("model", model).tag("type", "output").register(registry).increment(output);
        if (cacheRead > 0) {
            Counter.builder("rag.llm.tokens").tag("model", model).tag("type", "cache_read").register(registry)
                    .increment(cacheRead);
        }
    }

    public void llmError(String model, String type) {
        Counter.builder("rag.llm.errors").tag("model", model).tag("type", type).register(registry).increment();
    }

    public void embeddingRequest(String purpose) {
        Counter.builder("rag.embedding.requests").tag("purpose", purpose).register(registry).increment();
    }

    public void mcpToolCall(String tool, boolean success) {
        Counter.builder("rag.mcp.tool.calls")
                .tag("tool", tool)
                .tag("result", success ? "success" : "error")
                .register(registry)
                .increment();
    }

    public void ingestionFinished(IngestionReport report, boolean success) {
        Counter.builder("rag.ingestion.runs").tag("result", success ? "success" : "error").register(registry).increment();
        if (success && report != null) {
            lastIngestionSuccess.set(System.currentTimeMillis() / 1000);
            indexedChunks.set(report.totalChunks());
            Counter.builder("rag.ingestion.files").tag("change", "added").register(registry).increment(report.added());
            Counter.builder("rag.ingestion.files").tag("change", "updated").register(registry).increment(report.updated());
            Counter.builder("rag.ingestion.files").tag("change", "deleted").register(registry).increment(report.deleted());
            Counter.builder("rag.ingestion.files").tag("change", "failed").register(registry).increment(report.failed());
        }
    }

    public void indexedChunks(long chunks) {
        indexedChunks.set(chunks);
    }
}
