package de.ragtractusx.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;

/**
 * Calls the real Claude API once to prove the LangChain4j request shape is accepted by the configured model
 * (no sampling parameters, refusal-fallback beta, adaptive thinking blocks in the response).
 * Run with: ./mvnw test -Plive
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeSmokeTest {

    @Test
    void claudeAnswersThroughLangChain4j() {
        var llm = new RagProperties.Llm(
                System.getenv("ANTHROPIC_API_KEY"),
                System.getenv().getOrDefault("ANTHROPIC_MODEL", "claude-opus-5-5"),
                1024, Duration.ofSeconds(60), 1, "low", true);
        var props = new RagProperties(llm, null, null, null, null);

        ChatModel model = new LlmConfig().chatModel(props, List.of());
        ChatResponse response = model.chat(
                UserMessage.from("In one sentence: what is Eclipse Tractus-X?"));

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
    }
}
