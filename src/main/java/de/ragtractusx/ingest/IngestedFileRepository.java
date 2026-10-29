package de.ragtractusx.ingest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Bookkeeping of which file version is in which chunk table.
 */
@Repository
public class IngestedFileRepository {

    public record KitSummary(String kit, int pages, int chunks) {
    }

    private final JdbcClient jdbc;

    public IngestedFileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** path → blob SHA for one chunk table. */
    public Map<String, String> shasByPath(String chunkTable) {
        return jdbc.sql("SELECT path, blob_sha FROM ingested_file WHERE chunk_table = ?")
                .param(chunkTable)
                .query((rs, i) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void upsert(String chunkTable, DocRef ref, String title, String url, String kit, int chunkCount, String source) {
        jdbc.sql("""
                INSERT INTO ingested_file (chunk_table, path, blob_sha, title, url, kit, chunk_count, source, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (chunk_table, path) DO UPDATE SET blob_sha = excluded.blob_sha, title = excluded.title,
                    url = excluded.url, kit = excluded.kit, chunk_count = excluded.chunk_count,
                    source = excluded.source, ingested_at = now()""")
                .params(chunkTable, ref.path(), ref.blobSha(), title, url, kit, chunkCount, source)
                .update();
    }

    public void delete(String chunkTable, String path) {
        jdbc.sql("DELETE FROM ingested_file WHERE chunk_table = ? AND path = ?").params(chunkTable, path).update();
    }

    public int totalChunks(String chunkTable) {
        return jdbc.sql("SELECT coalesce(sum(chunk_count), 0) FROM ingested_file WHERE chunk_table = ?")
                .param(chunkTable).query(Integer.class).single();
    }

    public List<KitSummary> kits(String chunkTable) {
        return jdbc.sql("""
                SELECT kit, count(*) AS pages, sum(chunk_count) AS chunks FROM ingested_file
                WHERE chunk_table = ? AND kit IS NOT NULL GROUP BY kit ORDER BY kit""")
                .param(chunkTable)
                .query((rs, i) -> new KitSummary(rs.getString("kit"), rs.getInt("pages"), rs.getInt("chunks")))
                .list();
    }
}
