package de.ragtractusx.ingest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * History of ingestion runs (see V2__ingestion_run.sql).
 */
@Repository
public class IngestionRunRepository {

    /** The latest run, used for gauges. */
    public record LastRun(Instant finishedAt, boolean success, int added, int updated, int deleted, int failed) {
    }

    private final JdbcClient jdbc;

    public IngestionRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String chunkTable, Instant startedAt, IngestionReport report, String error) {
        boolean success = report != null;
        jdbc.sql("""
                INSERT INTO ingestion_run (chunk_table, source, started_at, success, added, updated, deleted, failed,
                    total_chunks, error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(chunkTable, success ? report.source() : null, Timestamp.from(startedAt), success,
                        success ? report.added() : 0, success ? report.updated() : 0,
                        success ? report.deleted() : 0, success ? report.failed() : 0,
                        success ? report.totalChunks() : 0, error)
                .update();
    }

    public Optional<Instant> lastSuccess(String chunkTable) {
        return jdbc.sql("SELECT max(finished_at) FROM ingestion_run WHERE chunk_table = ? AND success")
                .param(chunkTable)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    public Optional<LastRun> lastRun(String chunkTable) {
        return jdbc.sql("""
                SELECT finished_at, success, added, updated, deleted, failed FROM ingestion_run
                WHERE chunk_table = ? ORDER BY finished_at DESC LIMIT 1""")
                .param(chunkTable)
                .query((rs, i) -> new LastRun(rs.getTimestamp(1).toInstant(), rs.getBoolean(2), rs.getInt(3),
                        rs.getInt(4), rs.getInt(5), rs.getInt(6)))
                .optional();
    }
}
