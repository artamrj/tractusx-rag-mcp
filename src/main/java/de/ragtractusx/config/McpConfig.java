package de.ragtractusx.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.ragtractusx.mcp.TractusxDocsTools;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

/**
 * MCP server over Streamable HTTP at /mcp, served by the same Tomcat as the REST API.
 * Connect e.g. with: {@code claude mcp add --transport http tractusx-docs http://localhost:8080/mcp}
 */
@Configuration
public class McpConfig {

    static final String ENDPOINT = "/mcp";

    @Bean
    HttpServletStreamableServerTransportProvider mcpTransport() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint(ENDPOINT)
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        var registration = new ServletRegistrationBean<>(transport, ENDPOINT, ENDPOINT + "/*");
        registration.setAsyncSupported(true);
        registration.setName("mcp");
        return registration;
    }

    @Bean(destroyMethod = "closeGracefully")
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, TractusxDocsTools tools,
            ObjectProvider<BuildProperties> build) {
        String version = build.getIfAvailable() != null ? build.getIfAvailable().getVersion() : "dev";
        return McpServer.sync(transport)
                .serverInfo("tractusx-docs", version)
                .instructions("Search and question answering over the official Eclipse Tractus-X documentation "
                        + "(KITs, developer docs, release guidelines). Cite the returned URLs when you use the content.")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.specifications())
                .build();
    }
}
