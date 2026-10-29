package de.ragtractusx.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All application settings under the {@code rag.*} prefix. Defaults live in application.yml.
 */
@ConfigurationProperties("rag")
public record RagProperties(Llm llm, Embedding embedding, Source source, Retrieval retrieval, Security security) {

    /**
     * Claude settings. Sampling parameters (temperature, top_p, top_k) are intentionally absent:
     * current Claude models reject them.
     *
     * @param effort          optional output effort (low|medium|high|xhigh|max); blank = model default
     * @param refusalFallback let the API retry refused requests on a fallback model
     */
    public record Llm(
            String apiKey,
            String model,
            int maxTokens,
            Duration timeout,
            int maxRetries,
            String effort,
            boolean refusalFallback) {
    }

    /**
     * @param provider "openai" (text-embedding-3-small) or "local" (in-process all-MiniLM-L6-v2, for tests/offline)
     */
    public record Embedding(String provider, String openaiApiKey, String openaiModel, int batchSize) {
    }

    /**
     * Where the documentation comes from.
     *
     * @param type            "github" (Tractus-X website repo) or "local" (a directory, e.g. a local clone)
     * @param ref             branch, tag or commit SHA of the website repo
     * @param kitsVersion     released KIT docs version to index (e.g. "26.09"), or "latest" (first entry of
     *                        docs-kits_versions.json); substituted for {kits-version} in includePrefixes
     * @param includePrefixes repo folders that are ingested
     * @param siteUrl         public base URL of the published docs
     */
    public record Source(
            String type,
            String owner,
            String repo,
            String ref,
            String kitsVersion,
            String githubToken,
            String localPath,
            List<String> includePrefixes,
            String siteUrl) {
    }

    /**
     * @param mode       "vector" or "hybrid" (vector + full-text, fused with reciprocal rank fusion)
     * @param topK       chunks passed to the LLM
     * @param candidates chunks fetched per retriever before fusion
     * @param keywordWeight weight of the full-text ranking in the fusion (vector ranking has weight 1)
     */
    public record Retrieval(String mode, int topK, int candidates, double minVectorScore, int rrfK,
            double keywordWeight) {
    }

    /**
     * @param apiKey     if set, /api/** and /mcp require header X-Api-Key
     * @param adminToken if set, /admin/** requires header X-Admin-Token (admin endpoints are disabled otherwise)
     */
    public record Security(String apiKey, String adminToken) {
    }
}
