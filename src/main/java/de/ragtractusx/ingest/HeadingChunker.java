package de.ragtractusx.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a cleaned Markdown page along its headings (H1-H3), packs small neighbouring sections together and splits
 * large sections by paragraph with a little overlap. Fenced code blocks are never split unless a single block is larger
 * than a chunk. Every chunk starts with a breadcrumb ({@code Connector KIT › Adoption View › Mission}) so short
 * sections still carry their context into the embedding and into the prompt.
 */
public final class HeadingChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern CUSTOM_ID = Pattern.compile("\\s*\\{#([A-Za-z0-9_-]+)}\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(```+|~~~+)");
    static final String SEPARATOR = " › ";

    private final int maxChars;
    private final int overlapChars;

    public HeadingChunker(int maxChars, int overlapChars) {
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    /** Default: about 500 tokens per chunk, about 60 tokens of overlap. */
    public HeadingChunker() {
        this(2000, 250);
    }

    public List<DocChunk> chunk(String breadcrumbRoot, String body) {
        List<Section> sections = splitIntoSections(body);
        List<DocChunk> chunks = new ArrayList<>();

        Section pending = null;
        for (Section section : sections) {
            if (section.content().isBlank() && section.headingLine() == null) {
                continue;
            }
            if (pending == null) {
                pending = section;
            } else if (pending.length() + section.length() + 2 <= maxChars) {
                pending = pending.merge(section);
            } else {
                emit(pending, breadcrumbRoot, chunks);
                pending = section;
            }
        }
        if (pending != null) {
            emit(pending, breadcrumbRoot, chunks);
        }
        return chunks;
    }

    private void emit(Section section, String breadcrumbRoot, List<DocChunk> out) {
        String fullText = section.fullText().strip();
        if (fullText.isEmpty() || isOnlyHeadings(fullText)) {
            return;
        }
        String breadcrumb = section.path().isEmpty() ? breadcrumbRoot : breadcrumbRoot + SEPARATOR + section.path();
        for (String piece : splitLarge(fullText)) {
            out.add(new DocChunk(out.size(), section.path(), section.anchor(), breadcrumb + "\n\n" + piece));
        }
    }

    private static boolean isOnlyHeadings(String text) {
        return text.lines().allMatch(l -> l.isBlank() || HEADING.matcher(l).matches());
    }

    /** Parse the page into heading-delimited sections, keeping track of the heading path. */
    private List<Section> splitIntoSections(String body) {
        List<Section> sections = new ArrayList<>();
        String[] path = new String[3];
        StringBuilder current = new StringBuilder();
        String currentHeading = null;
        String currentPath = "";
        String currentAnchor = null;
        String fence = null;

        for (String line : body.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            if (fence != null) {
                current.append(line).append('\n');
                if (fenceMatcher.find() && line.strip().startsWith(fence)) {
                    fence = null;
                }
                continue;
            }
            if (fenceMatcher.find()) {
                fence = fenceMatcher.group(1);
                current.append(line).append('\n');
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                sections.add(new Section(currentHeading, currentPath, currentAnchor, current.toString()));
                current.setLength(0);

                int level = heading.group(1).length();
                String text = heading.group(2);
                Matcher customId = CUSTOM_ID.matcher(text);
                String anchor;
                if (customId.find()) {
                    anchor = customId.group(1);
                    text = text.substring(0, customId.start()).strip();
                } else {
                    anchor = DocUrlMapper.anchor(text);
                }
                path[level - 1] = text;
                for (int i = level; i < path.length; i++) {
                    path[i] = null;
                }
                currentHeading = "#".repeat(level) + " " + text;
                currentPath = joinPath(path, level);
                currentAnchor = level == 1 ? null : anchor;
                continue;
            }
            current.append(line).append('\n');
        }
        sections.add(new Section(currentHeading, currentPath, currentAnchor, current.toString()));
        return sections;
    }

    private static String joinPath(String[] path, int level) {
        List<String> parts = new ArrayList<>();
        // H1 is normally the page title, which is already part of the breadcrumb root.
        for (int i = 1; i < level; i++) {
            if (path[i] != null) {
                parts.add(path[i]);
            }
        }
        if (level > 1) {
            parts.removeIf(p -> p.equals(path[level - 1]));
            parts.add(path[level - 1]);
        }
        return String.join(SEPARATOR, parts);
    }

    /** Split text above maxChars into paragraph-aligned pieces with overlap; code blocks stay whole if they fit. */
    List<String> splitLarge(String text) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }
        List<String> units = paragraphs(text);
        List<String> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String unit : units) {
            if (unit.length() > maxChars) {
                if (!buffer.isEmpty()) {
                    pieces.add(buffer.toString().strip());
                    buffer.setLength(0);
                }
                pieces.addAll(hardSplit(unit));
                continue;
            }
            if (buffer.length() + unit.length() + 2 > maxChars && !buffer.isEmpty()) {
                String done = buffer.toString().strip();
                pieces.add(done);
                buffer.setLength(0);
                String overlap = tail(done);
                if (!overlap.isEmpty() && overlap.length() + unit.length() + 2 <= maxChars) {
                    buffer.append(overlap).append("\n\n");
                }
            }
            buffer.append(unit).append("\n\n");
        }
        if (!buffer.toString().isBlank()) {
            pieces.add(buffer.toString().strip());
        }
        return pieces;
    }

    /** Paragraphs separated by blank lines; a fenced code block counts as one paragraph. */
    private static List<String> paragraphs(String text) {
        List<String> units = new ArrayList<>();
        StringBuilder unit = new StringBuilder();
        String fence = null;
        for (String line : text.split("\n", -1)) {
            Matcher fenceMatcher = FENCE.matcher(line);
            if (fence != null) {
                unit.append(line).append('\n');
                if (fenceMatcher.find() && line.strip().startsWith(fence)) {
                    fence = null;
                }
                continue;
            }
            if (fenceMatcher.find()) {
                fence = fenceMatcher.group(1);
                unit.append(line).append('\n');
                continue;
            }
            if (line.isBlank()) {
                if (!unit.toString().isBlank()) {
                    units.add(unit.toString().strip());
                }
                unit.setLength(0);
            } else {
                unit.append(line).append('\n');
            }
        }
        if (!unit.toString().isBlank()) {
            units.add(unit.toString().strip());
        }
        return units;
    }

    private List<String> hardSplit(String unit) {
        List<String> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String line : unit.split("\n")) {
            while (line.length() > maxChars) {
                if (!buffer.isEmpty()) {
                    pieces.add(buffer.toString().strip());
                    buffer.setLength(0);
                }
                pieces.add(line.substring(0, maxChars));
                line = line.substring(maxChars);
            }
            if (buffer.length() + line.length() + 1 > maxChars) {
                pieces.add(buffer.toString().strip());
                buffer.setLength(0);
            }
            buffer.append(line).append('\n');
        }
        if (!buffer.toString().isBlank()) {
            pieces.add(buffer.toString().strip());
        }
        return pieces;
    }

    /** Last whole paragraphs of a piece that fit in the overlap budget. */
    private String tail(String piece) {
        List<String> paras = paragraphs(piece);
        StringBuilder tail = new StringBuilder();
        for (int i = paras.size() - 1; i >= 0; i--) {
            String p = paras.get(i);
            if (tail.length() + p.length() + 2 > overlapChars || FENCE.matcher(p).find()) {
                break;
            }
            tail.insert(0, p + "\n\n");
        }
        return tail.toString().strip();
    }

    private record Section(String headingLine, String path, String anchor, String content) {

        String fullText() {
            return headingLine == null ? content : headingLine + "\n\n" + content;
        }

        int length() {
            return fullText().length();
        }

        Section merge(Section next) {
            return new Section(headingLine, path, anchor, content.stripTrailing() + "\n\n" + next.fullText());
        }
    }
}
