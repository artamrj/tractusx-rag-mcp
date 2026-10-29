package de.ragtractusx.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import de.ragtractusx.ingest.IngestionService;
import de.ragtractusx.support.PostgresTestBase;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Talks to the running app with the MCP SDK's own client over Streamable HTTP, like Claude Code would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "rag.embedding.provider=local",
        "rag.source.type=local",
        "rag.llm.api-key=",
        "rag.security.api-key=test-api-key"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpServerIT extends PostgresTestBase {

    static final Path DOCS_DIR = copyFixtures();

    @DynamicPropertySource
    static void docs(DynamicPropertyRegistry registry) {
        registry.add("rag.source.local-path", DOCS_DIR::toString);
    }

    @LocalServerPort
    int port;
    @Autowired
    IngestionService ingestion;

    McpSyncClient client;

    @BeforeAll
    void setUp() {
        ingestion.ingest();
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .requestBuilder(java.net.http.HttpRequest.newBuilder().header("X-Api-Key", "test-api-key"))
                .build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterAll
    void tearDown() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    void listsReadOnlyTools() {
        assertThat(client.listTools().tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("search_tractusx_docs", "ask_tractusx_docs", "list_tractusx_kits");
        assertThat(client.listTools().tools()).allSatisfy(t -> assertThat(t.annotations().readOnlyHint()).isTrue());
    }

    @Test
    void searchReturnsExcerptsWithUrls() {
        CallToolResult result = client.callTool(new CallToolRequest("search_tractusx_docs",
                Map.of("query", "register a digital twin shell descriptor", "top_k", 2)));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        String text = ((TextContent) result.content().getFirst()).text();
        assertThat(text).startsWith("[1] ").contains("https://eclipse-tractusx.github.io/docs-kits/kits/digital-twin-kit");
        assertThat(result.structuredContent()).isNotNull();
    }

    @Test
    void listsKits() {
        CallToolResult result = client.callTool(new CallToolRequest("list_tractusx_kits", Map.of()));
        assertThat(((TextContent) result.content().getFirst()).text()).contains("connector-kit (1 pages)");
    }

    @Test
    void invalidArgumentsAndMissingLlmAreToolErrors() {
        CallToolResult badArgs = client.callTool(new CallToolRequest("search_tractusx_docs",
                Map.of("query", "x", "top_k", 99)));
        assertThat(badArgs.isError()).isTrue();

        CallToolResult noKey = client.callTool(new CallToolRequest("ask_tractusx_docs", Map.of("question", "What is a BPNL?")));
        assertThat(noKey.isError()).isTrue();
    }
}
