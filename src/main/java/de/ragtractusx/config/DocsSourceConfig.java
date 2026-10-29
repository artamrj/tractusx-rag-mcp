package de.ragtractusx.config;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import de.ragtractusx.ingest.DocsSource;
import de.ragtractusx.ingest.GitHubDocsSource;
import de.ragtractusx.ingest.LocalDirectorySource;

@Configuration
public class DocsSourceConfig {

    @Bean
    DocsSource docsSource(RagProperties props) {
        RagProperties.Source source = props.source();
        return switch (source.type()) {
            case "github" -> {
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                var factory = new JdkClientHttpRequestFactory(http);
                factory.setReadTimeout(Duration.ofSeconds(30));
                yield new GitHubDocsSource(RestClient.builder().requestFactory(factory), source);
            }
            case "local" -> {
                if (!StringUtils.hasText(source.localPath())) {
                    throw new IllegalArgumentException("rag.source.local-path (DOCS_LOCAL_PATH) is required for type 'local'");
                }
                yield new LocalDirectorySource(Path.of(source.localPath()), source.includePrefixes(), source.kitsVersion());
            }
            default -> throw new IllegalArgumentException("rag.source.type must be 'github' or 'local'");
        };
    }
}
