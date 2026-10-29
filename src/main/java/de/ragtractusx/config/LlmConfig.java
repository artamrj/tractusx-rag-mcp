package de.ragtractusx.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * Claude chat models (blocking + streaming) via LangChain4j.
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    /** Beta header that enables server-side refusal fallbacks ({@code fallbacks: "default"}). */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    @Bean
    ChatModel chatModel(RagProperties props, List<ChatModelListener> listeners) {
        RagProperties.Llm llm = props.llm();
        if (!StringUtils.hasText(llm.apiKey())) {
            log.warn("ANTHROPIC_API_KEY is not set - answer generation is disabled");
            return new MissingKeyChatModel();
        }
        var builder = AnthropicChatModel.builder()
                .apiKey(llm.apiKey())
                .modelName(llm.model())
                .maxTokens(llm.maxTokens())
                .timeout(llm.timeout())
                .maxRetries(llm.maxRetries())
                .customParameters(customParameters(llm))
                .listeners(listeners);
        if (llm.refusalFallback()) {
            builder.beta(FALLBACK_BETA);
        }
        return builder.build();
    }

    @Bean
    StreamingChatModel streamingChatModel(RagProperties props, List<ChatModelListener> listeners) {
        RagProperties.Llm llm = props.llm();
        if (!StringUtils.hasText(llm.apiKey())) {
            return new MissingKeyStreamingChatModel();
        }
        var builder = AnthropicStreamingChatModel.builder()
                .apiKey(llm.apiKey())
                .modelName(llm.model())
                .maxTokens(llm.maxTokens())
                .timeout(llm.timeout())
                .customParameters(customParameters(llm))
                .listeners(listeners);
        if (llm.refusalFallback()) {
            builder.beta(FALLBACK_BETA);
        }
        return builder.build();
    }

    /** Extra top-level request fields that LangChain4j has no typed setter for. */
    static Map<String, Object> customParameters(RagProperties.Llm llm) {
        Map<String, Object> params = new HashMap<>();
        if (llm.refusalFallback()) {
            params.put("fallbacks", "default");
        }
        if (StringUtils.hasText(llm.effort())) {
            params.put("output_config", Map.of("effort", llm.effort()));
        }
        return params;
    }

    /** Placeholder used when no API key is configured, so the app (search, MCP search, ingestion) still starts. */
    static final class MissingKeyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            throw new LlmUnavailableException("ANTHROPIC_API_KEY is not configured");
        }
    }

    static final class MissingKeyStreamingChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            handler.onError(new LlmUnavailableException("ANTHROPIC_API_KEY is not configured"));
        }
    }

    public static class LlmUnavailableException extends RuntimeException {
        public LlmUnavailableException(String message) {
            super(message);
        }
    }
}
