package de.ragtractusx.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import de.ragtractusx.config.StoreConfig.ChunkTable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full-text search over the generated {@code tsv} column. Unlike {@code plainto_tsquery} (all words must match), the
 * query terms are OR-ed and ranked with {@code ts_rank_cd}, so a natural-language question still finds chunks that
 * contain its rare, exact terms (BPNL, EDC, DTR, urn:samm:..., API paths).
 */
@Component
public class KeywordRetriever {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final String table;
    private final JsonMapper json = JsonMapper.builder().build();

    public KeywordRetriever(JdbcClient jdbc, ChunkTable table) {
        this.jdbc = jdbc;
        this.table = table.name();
    }

    public List<RetrievedChunk> search(String query, String kit, int maxResults) {
        String tsQuery = toTsQuery(query);
        if (tsQuery.isEmpty()) {
            return List.of();
        }
        boolean filterKit = kit != null && !kit.isBlank();
        String sql = """
                SELECT embedding_id::text AS id, text, metadata::text AS metadata, ts_rank_cd(tsv, q) AS rank
                FROM %s, to_tsquery('english', :q) q
                WHERE tsv @@ q %s
                ORDER BY rank DESC
                LIMIT :limit""".formatted(table, filterKit ? "AND metadata->>'kit' = :kit" : "");
        var statement = jdbc.sql(sql).param("q", tsQuery).param("limit", maxResults);
        if (filterKit) {
            statement = statement.param("kit", kit);
        }
        List<Row> rows = statement.query((rs, i) -> new Row(rs.getString("id"), rs.getString("text"),
                rs.getString("metadata"), rs.getDouble("rank"))).list();

        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(i -> {
                    Row r = rows.get(i);
                    return RetrievedChunk.of(r.id(), r.text(), json.readValue(r.metadata(), MAP), null, i + 1, r.rank());
                })
                .toList();
    }

    /** "How do I register a twin in the DTR?" → {@code how | do | i | register | a | twin | in | the | dtr}. */
    static String toTsQuery(String query) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(query.toLowerCase(Locale.ROOT));
        while (m.find() && terms.size() < 32) {
            terms.add(m.group());
        }
        // Stop words are dropped by to_tsquery('english', ...) itself.
        return String.join(" | ", terms);
    }

    private record Row(String id, String text, String metadata, double rank) {
    }
}
