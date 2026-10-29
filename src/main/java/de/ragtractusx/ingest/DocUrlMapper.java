package de.ragtractusx.ingest;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a repository path to the public URL Docusaurus generates for it, following Docusaurus defaults:
 * number prefixes ({@code 01_intro}) are stripped, a front-matter {@code id} replaces the file name, a doc named like
 * its folder (or {@code index}/{@code README}) becomes the folder URL, and a front-matter {@code slug} wins over all.
 */
public final class DocUrlMapper {

    private static final Pattern NUMBER_PREFIX = Pattern.compile("^\\d+\\s*[-_.]+\\s*");
    private static final Pattern VERSIONED_KITS = Pattern.compile("^docs-kits_versioned_docs/version-[^/]+/");

    /** Docs plugin folders and their route base paths (from docusaurus.config.js). */
    private static final List<String> ROUTED_FOLDERS = List.of("docs-kits", "docs", "community", "documentation");

    private final String siteUrl;

    public DocUrlMapper(String siteUrl) {
        this.siteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    /** Repo path → (route base path, path relative to the docs plugin folder). */
    record Routed(String root, String relPath) {
    }

    static Routed route(String path) {
        Matcher versioned = VERSIONED_KITS.matcher(path);
        if (versioned.find()) {
            // The newest released version is served at /docs-kits; we only ever index that one.
            return new Routed("docs-kits", path.substring(versioned.end()));
        }
        String root = path.substring(0, path.indexOf('/'));
        if (root.equals("docs-kits")) {
            // The docs-kits folder itself is the unreleased "next" version.
            return new Routed("docs-kits/next", path.substring(root.length() + 1));
        }
        return ROUTED_FOLDERS.contains(root) ? new Routed(root, path.substring(root.length() + 1)) : null;
    }

    public String pageUrl(String path, String frontMatterId, String frontMatterSlug) {
        Routed routed = route(path);
        if (routed == null) {
            return siteUrl;
        }
        String root = routed.root();
        List<String> segments = new ArrayList<>(Arrays.asList(routed.relPath().split("/")));
        String fileName = segments.removeLast().replaceFirst("\\.mdx?$", "");
        List<String> dirs = segments.stream().map(DocUrlMapper::stripNumberPrefix).toList();
        String dirPath = String.join("/", dirs);

        if (frontMatterSlug != null && !frontMatterSlug.isBlank()) {
            String slug = frontMatterSlug.strip();
            String route = slug.startsWith("/") ? slug : (dirPath.isEmpty() ? "/" : "/" + dirPath + "/") + slug;
            return join(root, route);
        }

        String fileSlug = stripNumberPrefix(fileName);
        boolean folderIndex = fileSlug.equalsIgnoreCase("index") || fileSlug.equalsIgnoreCase("readme")
                || (!dirs.isEmpty() && fileSlug.equalsIgnoreCase(dirs.getLast()));
        if (folderIndex) {
            // Docusaurus "category index" convention: the folder URL, regardless of a front-matter id.
            return join(root, "/" + dirPath);
        }
        String last = (frontMatterId != null && !frontMatterId.isBlank())
                ? stripNumberPrefix(frontMatterId.strip()) : fileSlug;
        return join(root, "/" + (dirPath.isEmpty() ? "" : dirPath + "/") + last);
    }

    public String sectionUrl(String pageUrl, String anchor) {
        return anchor == null || anchor.isBlank() ? pageUrl : pageUrl + "#" + anchor;
    }

    /** Docusaurus/github-slugger compatible heading id. */
    public static String anchor(String heading) {
        String s = Normalizer.normalize(heading.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        s = s.replaceAll("[`*_~]", "");
        s = s.replaceAll("[^\\p{L}\\p{N}\\s-]", "");
        return s.replace(' ', '-');
    }

    static String stripNumberPrefix(String segment) {
        String stripped = NUMBER_PREFIX.matcher(segment).replaceFirst("");
        return stripped.isEmpty() || stripped.chars().allMatch(Character::isDigit) ? segment : stripped;
    }

    private String join(String root, String route) {
        String normalized = route.replaceAll("/{2,}", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        // Docusaurus keeps ids like "Single Sourcing Scenario" verbatim; the browser URL encodes the spaces.
        return siteUrl + "/" + root + ("/".equals(normalized) ? "" : normalized.replace(" ", "%20"));
    }
}
