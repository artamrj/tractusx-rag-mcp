package de.ragtractusx.ingest;

/**
 * One retrievable piece of a documentation page.
 *
 * @param index   position within the page (0-based)
 * @param section heading path, e.g. {@code Vision & Mission › Mission}; empty for text before the first heading
 * @param anchor  Docusaurus heading id of the first heading in the chunk, for deep links
 * @param text    chunk text including a breadcrumb line, ready for embedding and for the prompt
 */
public record DocChunk(int index, String section, String anchor, String text) {
}
