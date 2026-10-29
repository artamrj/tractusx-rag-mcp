package de.ragtractusx.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.ragtractusx.config.EmbeddingConfig.EmbeddingSpace;
import de.ragtractusx.ingest.IngestedFileRepository;
import de.ragtractusx.metrics.RagMetrics;
import de.ragtractusx.rag.AnswerResponse;
import de.ragtractusx.rag.AskRequest;
import de.ragtractusx.rag.RagService;
import de.ragtractusx.retrieval.RetrievedChunk;
import de.ragtractusx.retrieval.SearchService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * MCP tools that let AI agents (Claude Code, IDE assistants, ...) use the Tractus-X docs. All tools are read-only.
 * {@code search_tractusx_docs} costs no LLM tokens on our side: the calling agent reads the excerpts itself.
 */
@Component
public class TractusxDocsTools {

    private static final Logger log = LoggerFactory.getLogger(TractusxDocsTools.class);
    private static final int MAX_QUERY = 2000;
    private static final ToolAnnotations READ_ONLY = new ToolAnnotations(null, true, false, true, false, null);

    private final SearchService search;
    private final RagService rag;
    private final IngestedFileRepository files;
    private final EmbeddingSpace space;
    private final RagMetrics metrics;

    public TractusxDocsTools(SearchService search, RagService rag, IngestedFileRepository files, EmbeddingSpace space,
            RagMetrics metrics) {
        this.search = search;
        this.rag = rag;
        this.files = files;
        this.space = space;
        this.metrics = metrics;
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(
                tool("search_tractusx_docs",
                        "Search the official Eclipse Tractus-X documentation (KITs such as Connector, Digital Twin, "
                                + "Industry Core, PURIS, DCM, PCF, plus developer and release guidelines). Returns the "
                                + "most relevant excerpts with page URLs. Use it for any question about Tractus-X or "
                                + "Catena-X data space components, then answer from the excerpts and cite the URLs.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "What to look for, in natural language or keywords"),
                                        "kit", Map.of("type", "string", "description", "Optional KIT id to restrict the search, e.g. connector-kit (see list_tractusx_kits)"),
                                        "top_k", Map.of("type", "integer", "minimum", 1, "maximum", 20, "description", "Number of excerpts (default 6)")),
                                "required", List.of("query"),
                                "additionalProperties", false),
                        this::searchDocs),
                tool("ask_tractusx_docs",
                        "Ask a question about Eclipse Tractus-X and get a short answer generated from the official "
                                + "documentation, with numbered source links. Prefer search_tractusx_docs if you want "
                                + "to read the excerpts yourself.",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "question", Map.of("type", "string", "description", "The question, in English or German"),
                                        "kit", Map.of("type", "string", "description", "Optional KIT id to restrict the sources")),
                                "required", List.of("question"),
                                "additionalProperties", false),
                        this::askDocs),
                tool("list_tractusx_kits",
                        "List the Tractus-X KITs that are indexed, with their ids (usable as the 'kit' filter) and page counts.",
                        Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                        this::listKits));
    }

    CallToolResult searchDocs(Map<String, Object> args) {
        String query = requiredString(args, "query");
        int topK = intArg(args, "top_k", search.defaultTopK(), 1, 20);
        List<RetrievedChunk> results = search.search(query, optionalString(args, "kit"), topK);
        if (results.isEmpty()) {
            return text("No matching documentation found.");
        }
        StringBuilder sb = new StringBuilder();
        List<Map<String, Object>> structured = new java.util.ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RetrievedChunk c = results.get(i);
            sb.append("[").append(i + 1).append("] ").append(c.title()).append(" — ").append(c.url()).append("\n")
                    .append(c.text().strip()).append("\n\n");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("n", i + 1);
            item.put("title", c.title());
            item.put("url", c.url());
            item.put("kit", c.kit());
            item.put("section", c.section());
            item.put("text", c.text());
            structured.add(item);
        }
        return CallToolResult.builder()
                .addTextContent(sb.toString().strip())
                .structuredContent(Map.of("results", structured))
                .build();
    }

    CallToolResult askDocs(Map<String, Object> args) {
        AnswerResponse answer = rag.ask(new AskRequest(requiredString(args, "question"), optionalString(args, "kit"), null), "mcp");
        StringBuilder sb = new StringBuilder(answer.answer()).append("\n\nSources:\n");
        answer.sources().stream().filter(AnswerResponse.Source::cited)
                .forEach(s -> sb.append("[").append(s.n()).append("] ").append(s.title()).append(" — ").append(s.url()).append('\n'));
        return text(sb.toString().strip());
    }

    CallToolResult listKits(Map<String, Object> args) {
        StringBuilder sb = new StringBuilder();
        files.kits(space.table()).forEach(k -> sb.append(k.kit()).append(" (").append(k.pages()).append(" pages)\n"));
        return text(sb.isEmpty() ? "No KITs indexed yet." : sb.toString().strip());
    }

    private SyncToolSpecification tool(String name, String description, Map<String, Object> schema,
            Function<Map<String, Object>, CallToolResult> handler) {
        Tool tool = Tool.builder().name(name).description(description).inputSchema(schema).annotations(READ_ONLY).build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request, handler))
                .build();
    }

    private CallToolResult call(String name, CallToolRequest request, Function<Map<String, Object>, CallToolResult> handler) {
        try {
            CallToolResult result = handler.apply(request.arguments() == null ? Map.of() : request.arguments());
            metrics.mcpToolCall(name, true);
            return result;
        } catch (IllegalArgumentException e) {
            metrics.mcpToolCall(name, false);
            return CallToolResult.builder().addTextContent("Invalid arguments: " + e.getMessage()).isError(true).build();
        } catch (RuntimeException e) {
            metrics.mcpToolCall(name, false);
            log.warn("MCP tool {} failed: {}", name, e.toString());
            return CallToolResult.builder().addTextContent("The documentation service could not complete this request.")
                    .isError(true).build();
        }
    }

    private static CallToolResult text(String text) {
        return CallToolResult.builder().addTextContent(text).build();
    }

    private static String requiredString(Map<String, Object> args, String key) {
        String value = optionalString(args, key);
        if (value == null) {
            throw new IllegalArgumentException("'" + key + "' is required");
        }
        if (value.length() > MAX_QUERY) {
            throw new IllegalArgumentException("'" + key + "' is longer than " + MAX_QUERY + " characters");
        }
        return value;
    }

    private static String optionalString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString().strip();
    }

    private static int intArg(Map<String, Object> args, String key, int fallback, int min, int max) {
        Object v = args.get(key);
        if (v == null) {
            return fallback;
        }
        int value = v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString());
        if (value < min || value > max) {
            throw new IllegalArgumentException("'" + key + "' must be between " + min + " and " + max);
        }
        return value;
    }
}
