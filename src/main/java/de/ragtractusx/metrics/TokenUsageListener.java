package de.ragtractusx.metrics;

import org.springframework.stereotype.Component;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Records token usage and errors for every Claude call (answers and evaluation judge alike).
 */
@Component
public class TokenUsageListener implements ChatModelListener {

    private final RagMetrics metrics;

    public TokenUsageListener(RagMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        ChatResponse response = context.chatResponse();
        TokenUsage usage = response.tokenUsage();
        if (usage == null) {
            return;
        }
        long cacheRead = usage instanceof AnthropicTokenUsage anthropic && anthropic.cacheReadInputTokens() != null
                ? anthropic.cacheReadInputTokens() : 0;
        metrics.llmTokens(model(response, context.chatRequest().modelName()),
                nz(usage.inputTokenCount()), nz(usage.outputTokenCount()), cacheRead);
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        Throwable error = context.error();
        String type = switch (error) {
            case RateLimitException ignored -> "rate_limit";
            case TimeoutException ignored -> "timeout";
            case HttpException http -> "http_" + http.statusCode();
            default -> error.getClass().getSimpleName();
        };
        metrics.llmError(context.chatRequest().modelName() == null ? "unknown" : context.chatRequest().modelName(), type);
    }

    private static String model(ChatResponse response, String requested) {
        if (response.modelName() != null) {
            return response.modelName();
        }
        return requested == null ? "unknown" : requested;
    }

    private static long nz(Integer value) {
        return value == null ? 0 : value;
    }
}
