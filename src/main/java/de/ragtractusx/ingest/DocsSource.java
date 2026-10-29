package de.ragtractusx.ingest;

import java.util.List;

/**
 * Lists and reads the Markdown/MDX files that make up the documentation corpus.
 */
public interface DocsSource {

    /** Human-readable origin, stored with every chunk (e.g. {@code github:owner/repo@3f2a…}). */
    String describe();

    /** All documentation files that should be ingested (already filtered). */
    List<DocRef> list();

    /** Raw file content (UTF-8). */
    String read(DocRef ref);
}
