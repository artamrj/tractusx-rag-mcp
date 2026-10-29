package de.ragtractusx.ingest;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.ragtractusx.config.EmbeddingConfig.EmbeddingSpace;
import de.ragtractusx.config.RagProperties;
import de.ragtractusx.metrics.RagMetrics;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Incremental ingestion: list files, skip unchanged ones (same blob SHA), clean + chunk + embed the rest, and remove
 * pages that disappeared upstream. A Postgres advisory lock makes sure only one pod or job ingests at a time.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final long LOCK_KEY = 0x7261675f74782L; // "rag_tx"

    private final DocsSource source;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final IngestedFileRepository files;
    private final EmbeddingSpace space;
    private final DataSource dataSource;
    private final RagMetrics metrics;
    private final IngestionRunRepository runs;
    private final int batchSize;
    private final MdxCleaner cleaner = new MdxCleaner();
    private final HeadingChunker chunker = new HeadingChunker();
    private final DocUrlMapper urls;

    public IngestionService(DocsSource source, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store,
            IngestedFileRepository files, EmbeddingSpace space, DataSource dataSource, RagMetrics metrics,
            IngestionRunRepository runs, RagProperties props) {
        this.runs = runs;
        this.source = source;
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.files = files;
        this.space = space;
        this.dataSource = dataSource;
        this.metrics = metrics;
        this.batchSize = props.embedding().batchSize();
        this.urls = new DocUrlMapper(props.source().siteUrl());
    }

    public static class IngestionInProgressException extends RuntimeException {
        IngestionInProgressException() {
            super("Another ingestion run is in progress");
        }
    }

    public IngestionReport ingest() {
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryLock(lockConnection)) {
                throw new IngestionInProgressException();
            }
            java.time.Instant startedAt = java.time.Instant.now();
            try {
                IngestionReport report = run();
                metrics.ingestionFinished(report, true);
                runs.record(space.table(), startedAt, report, null);
                return report;
            } catch (RuntimeException e) {
                metrics.ingestionFinished(null, false);
                runs.record(space.table(), startedAt, null, e.toString());
                throw e;
            } finally {
                unlock(lockConnection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not obtain ingestion lock", e);
        }
    }

    private IngestionReport run() {
        long start = System.nanoTime();
        String table = space.table();
        List<DocRef> refs = source.list();
        String sourceName = source.describe();
        Map<String, String> known = new HashMap<>(files.shasByPath(table));

        int added = 0, updated = 0, unchanged = 0, failed = 0, chunksWritten = 0;
        List<String> failures = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (DocRef ref : refs) {
            seen.add(ref.path());
            String knownSha = known.get(ref.path());
            if (ref.blobSha().equals(knownSha)) {
                unchanged++;
                continue;
            }
            try {
                chunksWritten += ingestFile(ref, sourceName, table);
                if (knownSha == null) {
                    added++;
                } else {
                    updated++;
                }
            } catch (RuntimeException e) {
                failed++;
                failures.add(ref.path() + ": " + e.getMessage());
                log.warn("Failed to ingest {}: {}", ref.path(), e.toString());
            }
        }

        int deleted = 0;
        for (String path : known.keySet()) {
            if (!seen.contains(path)) {
                store.removeAll(metadataKey("path").isEqualTo(path));
                files.delete(table, path);
                deleted++;
            }
        }

        var report = new IngestionReport(sourceName, refs.size(), added, updated, unchanged, deleted, failed,
                chunksWritten, files.totalChunks(table), Duration.ofNanos(System.nanoTime() - start), failures);
        log.info("Ingestion finished: {}", report);
        return report;
    }

    /** Clean, chunk, embed and store one file; returns the number of chunks written. */
    int ingestFile(DocRef ref, String sourceName, String table) {
        MdxCleaner.CleanDoc doc = cleaner.clean(ref.path(), source.read(ref));
        String kit = KitNames.kitId(ref.path());
        String root = kit != null ? KitNames.displayName(kit) : KitNames.areaName(ref.path());
        String breadcrumbRoot = doc.title().equalsIgnoreCase(root) ? root : root + HeadingChunker.SEPARATOR + doc.title();
        String pageUrl = urls.pageUrl(ref.path(), doc.frontMatterString("id"), doc.frontMatterString("slug"));
        List<DocChunk> chunks = chunker.chunk(breadcrumbRoot, doc.body());

        List<TextSegment> segments = new ArrayList<>(chunks.size());
        List<String> ids = new ArrayList<>(chunks.size());
        for (DocChunk chunk : chunks) {
            Metadata metadata = new Metadata()
                    .put("path", ref.path())
                    .put("url", urls.sectionUrl(pageUrl, chunk.anchor()))
                    .put("page_url", pageUrl)
                    .put("title", doc.title())
                    .put("area", KitNames.area(ref.path()))
                    .put("section", chunk.section())
                    .put("chunk_index", chunk.index())
                    .put("blob_sha", ref.blobSha())
                    .put("source", sourceName);
            if (kit != null) {
                metadata.put("kit", kit).put("kit_name", KitNames.displayName(kit));
            }
            segments.add(TextSegment.from(chunk.text(), metadata));
            ids.add(UUID.nameUUIDFromBytes((table + ":" + ref.path() + "#" + chunk.index())
                    .getBytes(StandardCharsets.UTF_8)).toString());
        }

        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i += batchSize) {
            List<TextSegment> batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
            embeddings.addAll(embeddingModel.embedAll(batch).content());
            metrics.embeddingRequest("ingest");
        }

        // Replace the previous version of the page.
        store.removeAll(metadataKey("path").isEqualTo(ref.path()));
        if (!segments.isEmpty()) {
            store.addAll(ids, embeddings, segments);
        }
        files.upsert(table, ref, doc.title(), pageUrl, kit, segments.size(), sourceName);
        log.debug("Ingested {} ({} chunks)", ref.path(), segments.size());
        return segments.size();
    }

    private static boolean tryLock(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            ps.setLong(1, LOCK_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static void unlock(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            ps.setLong(1, LOCK_KEY);
            ps.execute();
        }
    }
}
