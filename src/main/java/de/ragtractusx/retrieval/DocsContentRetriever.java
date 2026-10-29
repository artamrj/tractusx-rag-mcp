package de.ragtractusx.retrieval;

import java.util.List;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

/**
 * Adapter that exposes the hybrid search as a LangChain4j {@link ContentRetriever}, so it can be plugged into
 * LangChain4j AI services / retrieval augmentors if needed.
 */
public class DocsContentRetriever implements ContentRetriever {

    private final SearchService search;
    private final String kit;

    public DocsContentRetriever(SearchService search, String kit) {
        this.search = search;
        this.kit = kit;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return search.search(query.text(), kit, null).stream()
                .map(c -> Content.from(TextSegment.from(c.text(), new Metadata()
                        .put("url", c.url())
                        .put("title", c.title() == null ? "" : c.title()))))
                .toList();
    }
}
