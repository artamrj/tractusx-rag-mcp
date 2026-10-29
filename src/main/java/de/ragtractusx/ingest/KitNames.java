package de.ragtractusx.ingest;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Derives the KIT id and a readable area name from a repository path.
 */
public final class KitNames {

    private static final Set<String> UPPER = Set.of("kit", "pcf", "puris", "cbam", "esdscom", "dcm", "ai");
    private static final Pattern KIT_PATH =
            Pattern.compile("^(?:docs-kits|docs-kits_versioned_docs/version-[^/]+)/kits/([^/]+)/");

    private KitNames() {
    }

    /** {@code .../kits/connector-kit/...} → {@code connector-kit}; null for non-KIT pages. */
    public static String kitId(String path) {
        Matcher m = KIT_PATH.matcher(path);
        return m.find() ? m.group(1) : null;
    }

    /** {@code connector-kit} → {@code Connector KIT}. */
    public static String displayName(String kitId) {
        return Arrays.stream(kitId.split("-"))
                .filter(w -> !w.isEmpty())
                .map(w -> UPPER.contains(w) ? w.toUpperCase(Locale.ROOT)
                        : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** Short area id stored as metadata: docs-kits, docs, community or documentation. */
    public static String area(String path) {
        String root = path.substring(0, path.indexOf('/'));
        return root.startsWith("docs-kits") ? "docs-kits" : root;
    }

    /** Top-level label used as breadcrumb root for non-KIT pages. */
    public static String areaName(String path) {
        return switch (area(path)) {
            case "docs-kits" -> "KITs";
            case "docs" -> "Developer Docs";
            case "community" -> "Community";
            case "documentation" -> "Documentation";
            default -> area(path);
        };
    }
}
