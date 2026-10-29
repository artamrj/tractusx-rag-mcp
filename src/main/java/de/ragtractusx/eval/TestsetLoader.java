package de.ragtractusx.eval;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads and validates eval/testset.yaml.
 */
public final class TestsetLoader {

    private static final Set<String> CATEGORIES = Set.of("factual", "procedural", "cross-kit", "unanswerable");

    private TestsetLoader() {
    }

    public static List<EvalItem> load(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return parse(new Yaml(new SafeConstructor(new LoaderOptions())).load(reader));
        }
    }

    @SuppressWarnings("unchecked")
    static List<EvalItem> parse(Object yaml) {
        if (!(yaml instanceof List<?> list)) {
            throw new IllegalArgumentException("Test set must be a YAML list");
        }
        List<EvalItem> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Object entry : list) {
            Map<String, Object> m = (Map<String, Object>) entry;
            String id = str(m, "id", true);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate id " + id);
            }
            String category = str(m, "category", true);
            if (!CATEGORIES.contains(category)) {
                throw new IllegalArgumentException(id + ": unknown category " + category);
            }
            List<String> sources = strings(m.get("expected_sources"));
            List<String> keyPoints = strings(m.get("key_points"));
            boolean answerable = !"unanswerable".equals(category);
            if (answerable && (sources.isEmpty() || keyPoints.isEmpty())) {
                throw new IllegalArgumentException(id + ": answerable items need expected_sources and key_points");
            }
            items.add(new EvalItem(id, str(m, "question", true), m.getOrDefault("lang", "en").toString(), category,
                    sources, str(m, "reference_answer", answerable), keyPoints,
                    Boolean.TRUE.equals(m.get("reviewed"))));
        }
        return items;
    }

    private static String str(Map<String, Object> m, String key, boolean required) {
        Object v = m.get(key);
        if (v == null || v.toString().isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Missing '" + key + "' in " + m.get("id"));
            }
            return "";
        }
        return v.toString().strip();
    }

    private static List<String> strings(Object v) {
        if (v == null) {
            return List.of();
        }
        return ((List<?>) v).stream().map(Object::toString).map(String::strip).toList();
    }
}
