package de.ragtractusx.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import de.ragtractusx.ingest.IngestionService;
import de.ragtractusx.support.PostgresTestBase;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AskIT extends PostgresTestBase {

    static final Path DOCS_DIR = copyFixtures();
    static final AtomicReference<ChatRequest> LAST_REQUEST = new AtomicReference<>();
    static final AtomicReference<ChatResponse> NEXT_RESPONSE = new AtomicReference<>();

    @DynamicPropertySource
    static void docs(DynamicPropertyRegistry registry) {
        registry.add("rag.source.local-path", DOCS_DIR::toString);
    }

    @TestConfiguration
    static class StubModels {

        @Bean
        @Primary
        ChatModel stubChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    LAST_REQUEST.set(request);
                    return NEXT_RESPONSE.get();
                }
            };
        }

        @Bean
        @Primary
        StreamingChatModel stubStreamingChatModel() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    handler.onPartialResponse("A BPNL identifies ");
                    handler.onPartialResponse("a legal entity [1].");
                    handler.onCompleteResponse(response("A BPNL identifies a legal entity [1].", FinishReason.STOP));
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    IngestionService ingestion;

    @BeforeAll
    void ingest() {
        ingestion.ingest();
    }

    static ChatResponse response(String text, FinishReason reason) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .finishReason(reason)
                .tokenUsage(new TokenUsage(1200, 80))
                .modelName("claude-test")
                .build();
    }

    @Test
    void answersWithNumberedSourcesAndCitedFlags() throws Exception {
        NEXT_RESPONSE.set(response("Every participant has a BPNL [1]. It is checked during negotiation [1][9].",
                FinishReason.STOP));

        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a BPNL?\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.outcome").value("answered"))
                .andExpect(jsonPath("$.sources.length()").value(3))
                .andExpect(jsonPath("$.sources[0].n").value(1))
                .andExpect(jsonPath("$.sources[0].cited").value(true))
                .andExpect(jsonPath("$.sources[1].cited").value(false))
                .andExpect(jsonPath("$.sources[0].url").value(org.hamcrest.Matchers.startsWith("https://eclipse-tractusx.github.io/")))
                .andExpect(jsonPath("$.usage.inputTokens").value(1200))
                .andExpect(jsonPath("$.model").value("claude-test"));

        ChatRequest sent = LAST_REQUEST.get();
        assertThat(sent.messages().getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) sent.messages().getFirst()).text()).contains("Cite the excerpts");
        String user = ((UserMessage) sent.messages().get(1)).singleText();
        assertThat(user).contains("<document index=\"1\">").contains("<document index=\"3\">")
                .contains("<question>\nWhat is a BPNL?\n</question>")
                .contains("BPNL");
        assertThat(sent.parameters().temperature()).isNull();
    }

    @Test
    void answerWithoutCitationsCountsAsNoAnswer() throws Exception {
        NEXT_RESPONSE.set(response("The documentation does not cover pricing.", FinishReason.STOP));
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What does Catena-X membership cost?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false))
                .andExpect(jsonPath("$.outcome").value("no_answer"));
    }

    @Test
    void refusalIsReportedCleanly() throws Exception {
        NEXT_RESPONSE.set(response("", FinishReason.OTHER));
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x y z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("refused"))
                .andExpect(jsonPath("$.answer").value(RagService.REFUSED));
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"ok\",\"topK\":99}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamsSourcesTokensAndDone() throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/ask/stream").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is a BPNL?\",\"topK\":2}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:sources")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("a legal entity [1].")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"cited\":true")));
    }

    @Test
    void metricsAreExported() throws Exception {
        NEXT_RESPONSE.set(response("Answer [1].", FinishReason.STOP));
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"BPNL?\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rag_ask_duration_seconds_count")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rag_retrieval_duration_seconds")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rag_index_chunks")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("outcome=\"answered\"")));
    }
}
