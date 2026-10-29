package de.ragtractusx.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Turns Docusaurus Markdown/MDX into plain Markdown that is good for embedding and for quoting to the LLM:
 * front matter is parsed, MDX imports/exports, JSX/HTML tags, images and link targets are removed, admonitions become
 * plain paragraphs. Fenced code blocks and tables are kept verbatim.
 */
public final class MdxCleaner {

    /** Result of cleaning one file. */
    public record CleanDoc(String title, Map<String, Object> frontMatter, String body) {

        public String frontMatterString(String key) {
            Object value = frontMatter.get(key);
            return value == null ? null : value.toString();
        }
    }

    private static final Pattern FENCE = Pattern.compile("^\\s*(```+|~~~+)");
    private static final Pattern ESM = Pattern.compile(
            "^(import\\s+.+\\s+from\\s+['\"].+['\"];?|import\\s+['\"].+['\"];?|export\\s+(const|default|function|let)\\b.*)\\s*$");
    private static final Pattern ADMONITION_OPEN = Pattern.compile("^\\s*:::+\\s*([a-zA-Z]+)\\s*(.*)$");
    private static final Pattern ADMONITION_CLOSE = Pattern.compile("^\\s*:::+\\s*$");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)|!\\[[^\\]]*]\\[[^\\]]*]");
    private static final Pattern INLINE_LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]*)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern REF_LINK = Pattern.compile("\\[([^\\]]+)]\\[[^\\]]*]");
    private static final Pattern REF_DEFINITION = Pattern.compile("^\\s*\\[[^\\]]+]:\\s+\\S+.*$");
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern MDX_COMMENT = Pattern.compile("\\{/\\*.*?\\*/}", Pattern.DOTALL);
    private static final Pattern IFRAME = Pattern.compile("<iframe\\b.*?(</iframe>|/>)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    /** JSX components (capitalised) and common HTML tags; deliberately does not match things like {@code List<String>}. */
    private static final Pattern TAG = Pattern.compile(
            "</?([A-Z][A-Za-z0-9.]*|div|span|img|br|hr|p|a|h[1-6]|details|summary|table|thead|tbody|tr|td|th|ul|ol|li"
                    + "|b|i|em|strong|sup|sub|center|figure|figcaption|video|source|section|small|u|font|style)"
                    + "(\\s[^<>]*)?/?>");
    private static final Pattern JSX_EXPRESSION_LINE = Pattern.compile("^\\s*\\{[^{}]*}\\s*$");
    private static final Pattern INLINE_CODE = Pattern.compile("(`+)[^`].*?\\1");
    private static final Pattern H1 =Pattern.compile("^#\\s+(.+?)\\s*#*\\s*$");

    public CleanDoc clean(String path, String raw) {
        String text = raw.replace("\r\n", "\n").replace(' ', ' ');
        Map<String, Object> frontMatter = new LinkedHashMap<>();
        if (text.startsWith("---\n")) {
            int end = text.indexOf("\n---", 4);
            if (end > 0) {
                frontMatter = parseYaml(text.substring(4, end));
                int bodyStart = text.indexOf('\n', end + 4);
                text = bodyStart < 0 ? "" : text.substring(bodyStart + 1);
            }
        }

        String body = cleanBody(text);
        String title = asString(frontMatter.get("title"));
        if (title == null || title.isBlank()) {
            title = asString(frontMatter.get("sidebar_label"));
        }
        if (title == null || title.isBlank()) {
            title = firstH1(body);
        }
        if (title == null || title.isBlank()) {
            String file = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.mdx?$", "");
            title = DocUrlMapper.stripNumberPrefix(file).replace('-', ' ').replace('_', ' ');
        }
        return new CleanDoc(title.strip(), frontMatter, body);
    }

    String cleanBody(String text) {
        // Multi-line constructs first (outside code fences only matters little for these; code rarely contains them).
        text = HTML_COMMENT.matcher(text).replaceAll("");
        text = MDX_COMMENT.matcher(text).replaceAll("");
        text = IFRAME.matcher(text).replaceAll("");

        List<String> out = new ArrayList<>();
        String fence = null;
        String mdxBlockFence = null;
        boolean inMultiLineTag = false;
        for (String line : text.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            // ```mdx-code-block holds MDX that Docusaurus renders, so its content is cleaned like normal text.
            if (fence == null && mdxBlockFence == null && fenceMatcher.find()
                    && line.strip().substring(fenceMatcher.group(1).length()).strip().equals("mdx-code-block")) {
                mdxBlockFence = fenceMatcher.group(1);
                continue;
            }
            if (mdxBlockFence != null && line.strip().equals(mdxBlockFence)) {
                mdxBlockFence = null;
                continue;
            }
            fenceMatcher.reset();
            if (fence != null) {
                out.add(line);
                if (fenceMatcher.find() && fenceMatcher.group(1).charAt(0) == fence.charAt(0)
                        && fenceMatcher.group(1).length() >= fence.length() && line.strip().length() == fenceMatcher.group(1).length()) {
                    fence = null;
                }
                continue;
            }
            if (fenceMatcher.find()) {
                fence = fenceMatcher.group(1);
                out.add(line.strip().startsWith(fence) ? line.stripLeading() : line);
                continue;
            }
            if (inMultiLineTag) {
                if (line.contains(">")) {
                    inMultiLineTag = false;
                    line = line.substring(line.indexOf('>') + 1);
                } else {
                    continue;
                }
            }
            if (ESM.matcher(line).matches() || REF_DEFINITION.matcher(line).matches()
                    || JSX_EXPRESSION_LINE.matcher(line).matches()) {
                continue;
            }
            Matcher admonition = ADMONITION_OPEN.matcher(line);
            if (admonition.matches()) {
                String kind = capitalize(admonition.group(1));
                String admonitionTitle = admonition.group(2).strip();
                out.add(admonitionTitle.isEmpty() ? kind + ":" : kind + ": " + admonitionTitle);
                continue;
            }
            if (ADMONITION_CLOSE.matcher(line).matches()) {
                continue;
            }
            // A JSX/HTML tag whose attributes continue on the next lines.
            if (line.stripLeading().matches("^<[A-Za-z][^>]*$")) {
                inMultiLineTag = true;
                continue;
            }
            out.add(cleanInline(line));
        }
        return collapseBlankLines(String.join("\n", out)).strip();
    }

    /** Cleans a line but leaves inline code spans ({@code `List<String>`}) untouched. */
    static String cleanInline(String line) {
        if (line.indexOf('`') < 0) {
            return cleanText(line);
        }
        StringBuilder out = new StringBuilder();
        Matcher code = INLINE_CODE.matcher(line);
        int last = 0;
        while (code.find()) {
            out.append(cleanText(line.substring(last, code.start()))).append(code.group());
            last = code.end();
        }
        out.append(cleanText(line.substring(last)));
        return out.toString().isBlank() ? "" : out.toString().stripTrailing();
    }

    private static String cleanText(String line) {
        String s = IMAGE.matcher(line).replaceAll("");
        s = INLINE_LINK.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        s = REF_LINK.matcher(s).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        // Heading ids like "## Setup {#custom-id}" are kept; the chunker turns them into anchors.
        return TAG.matcher(s).replaceAll("");
    }

    private static String collapseBlankLines(String s) {
        return s.replaceAll("\n{3,}", "\n\n");
    }

    private static String firstH1(String body) {
        for (String line : body.split("\n")) {
            Matcher m = H1.matcher(line);
            if (m.matches()) {
                return m.group(1);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYaml(String yaml) {
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
            if (parsed instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        } catch (RuntimeException ignored) {
            // Broken front matter should not stop ingestion of the page body.
        }
        return new LinkedHashMap<>();
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
