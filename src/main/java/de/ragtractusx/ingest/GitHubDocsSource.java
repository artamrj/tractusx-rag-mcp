package de.ragtractusx.ingest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.ragtractusx.config.RagProperties;

/**
 * Reads the docs straight from the Tractus-X website repository on GitHub without cloning it
 * (the repo is ~3 GB because of images; the Markdown we need is a few MB).
 * <p>
 * One call resolves the ref to a commit, one call lists the whole file tree (with blob SHAs, which makes
 * change detection free), then each changed file is downloaded from raw.githubusercontent.com.
 */
public class GitHubDocsSource implements DocsSource {

    private static final Logger log = LoggerFactory.getLogger(GitHubDocsSource.class);

    private final RestClient api;
    private final RestClient raw;
    private final RagProperties.Source props;
    private String commitSha;
    private String kitsVersion;

    public GitHubDocsSource(RestClient.Builder builder, RagProperties.Source props) {
        this.props = props;
        RestClient.Builder apiBuilder = builder.clone()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (StringUtils.hasText(props.githubToken())) {
            apiBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.githubToken());
        }
        this.api = apiBuilder.build();
        this.raw = builder.clone().baseUrl("https://raw.githubusercontent.com").build();
    }

    @Override
    public String describe() {
        String base = "github:%s/%s@%s".formatted(props.owner(), props.repo(), commitSha != null ? commitSha : props.ref());
        return kitsVersion == null ? base : base + " (kits " + kitsVersion + ")";
    }

    @Override
    public List<DocRef> list() {
        Commit commit = api.get()
                .uri("/repos/{owner}/{repo}/commits/{ref}", props.owner(), props.repo(), props.ref())
                .retrieve()
                .body(Commit.class);
        commitSha = commit.sha();

        Tree tree = api.get()
                .uri("/repos/{owner}/{repo}/git/trees/{sha}?recursive=1", props.owner(), props.repo(), commitSha)
                .retrieve()
                .body(Tree.class);
        DocPathFilter filter = filter();
        if (tree.truncated()) {
            log.warn("GitHub tree listing was truncated; some files may be missing");
        }
        List<DocRef> refs = tree.tree().stream()
                .filter(e -> "blob".equals(e.type()))
                .filter(e -> filter.accept(e.path()))
                .map(e -> new DocRef(e.path(), e.sha()))
                .toList();
        log.info("Listed {} documentation files at {}", refs.size(), describe());
        return refs;
    }

    @Override
    public String read(DocRef ref) {
        return readRaw(ref.path());
    }

    private DocPathFilter filter() {
        if (!DocPathFilter.needsKitsVersion(props.includePrefixes())) {
            return new DocPathFilter(props.includePrefixes());
        }
        kitsVersion = "latest".equalsIgnoreCase(props.kitsVersion())
                ? DocPathFilter.latestVersion(readRaw("docs-kits_versions.json"))
                : props.kitsVersion();
        return DocPathFilter.withKitsVersion(props.includePrefixes(), kitsVersion);
    }

    private String readRaw(String path) {
        String sha = commitSha != null ? commitSha : props.ref();
        // Path segments are expanded one by one so slashes stay slashes.
        return raw.get()
                .uri(b -> b.pathSegment(props.owner(), props.repo(), sha).pathSegment(path.split("/")).build())
                .retrieve()
                .body(String.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Commit(String sha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tree(String sha, List<Entry> tree, boolean truncated) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String path, String type, String sha) {
    }
}
