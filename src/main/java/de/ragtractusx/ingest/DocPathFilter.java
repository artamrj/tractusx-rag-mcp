package de.ragtractusx.ingest;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which repository files are documentation pages worth indexing.
 */
public final class DocPathFilter {

    public static final String KITS_VERSION_PLACEHOLDER = "{kits-version}";
    private static final Pattern FIRST_JSON_STRING = Pattern.compile("\"([^\"]+)\"");

    private final List<String> includePrefixes;

    public DocPathFilter(List<String> includePrefixes) {
        this.includePrefixes = List.copyOf(includePrefixes);
    }

    /** Substitutes {kits-version} in the configured prefixes. */
    public static DocPathFilter withKitsVersion(List<String> prefixes, String kitsVersion) {
        return new DocPathFilter(prefixes.stream().map(p -> p.replace(KITS_VERSION_PLACEHOLDER, kitsVersion)).toList());
    }

    public static boolean needsKitsVersion(List<String> prefixes) {
        return prefixes.stream().anyMatch(p -> p.contains(KITS_VERSION_PLACEHOLDER));
    }

    /** Newest version from the content of docs-kits_versions.json (a JSON array, newest first). */
    public static String latestVersion(String versionsJson) {
        Matcher m = FIRST_JSON_STRING.matcher(versionsJson);
        if (!m.find()) {
            throw new IllegalStateException("docs-kits_versions.json contains no versions");
        }
        return m.group(1);
    }

    public boolean accept(String path) {
        if (!(path.endsWith(".md") || path.endsWith(".mdx"))) {
            return false;
        }
        if (includePrefixes.stream().noneMatch(path::startsWith)) {
            return false;
        }
        // Docusaurus excludes the KIT template and treats _-prefixed files as partials.
        if (path.contains("/kit-template/")) {
            return false;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return !fileName.startsWith("_") && !fileName.equalsIgnoreCase("changelog.md");
    }

    public List<String> includePrefixes() {
        return includePrefixes;
    }
}
