package de.ragtractusx.rag;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.ragtractusx.config.RagProperties;
import de.ragtractusx.metrics.RagMetrics;
import de.ragtractusx.metrics.RagMetrics.Outcome;
import de.ragtractusx.rag.AnswerResponse.Source;
import de.ragtractusx.rag.AnswerResponse.Usage;
import de.ragtractusx.retrieval.RetrievedChunk;
import de.ragtractusx.retrieval.SearchRequest;
import de.ragtractusx.retrieval.SearchService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

/**
 * The RAG pipeline: retrieve excerpts → build prompt → ask Claude → attach numbered sources.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    static final String NOTHING_FOUND = "I could not find anything about this in the indexed Tractus-X documentation.";
    static final String REFUSED = "I can't help with this request.";

    private final SearchService search;
    private final PromptBuilder prompts;
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final RagMetrics metrics;
    private final String modelName;

    public RagService(SearchService search, PromptBuilder prompts, ChatModel chatModel,
            StreamingChatModel streamingChatModel, RagMetrics metrics, RagProperties props) {
        this.search = search;
        this.prompts = prompts;
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.metrics = metrics;
        this.modelName = props.llm().model();
    }

    /** Retrieval step only; exposed so the evaluation can score retrieval and generation separately. */
    public List<RetrievedChunk> retrieve(AskRequest request, SearchRequest.Mode mode) {
        int topK = request.topK() != null ? request.topK() : search.defaultTopK();
        return search.search(new SearchRequest(request.question(), request.kit(), topK,
                mode != null ? mode : search.defaultMode()));
    }

    public AnswerResponse ask(AskRequest request, String channel) {
        long start = System.nanoTime();
        List<RetrievedChunk> chunks = retrieve(request, null);
        try {
            AnswerResponse response = generate(request.question(), chunks, start);
            metrics.askCompleted(Outcome.valueOf(response.outcome().toUpperCase()), false, channel, elapsed(start));
            return response;
        } catch (RuntimeException e) {
            metrics.askCompleted(Outcome.ERROR, false, channel, elapsed(start));
            throw e;
        }
    }

    /** Generation step for already retrieved chunks (also used by the evaluation). */
    public AnswerResponse generate(String question, List<RetrievedChunk> chunks, long startNanos) {
        if (chunks.isEmpty()) {
            return new AnswerResponse(NOTHING_FOUND, false, Outcome.NO_ANSWER.tag(), List.of(), modelName, Usage.NONE,
                    elapsed(startNanos).toMillis());
        }
        ChatResponse response = chatModel.chat(chatRequest(question, chunks));
        return toAnswer(response, chunks, startNanos);
    }

    /**
     * Streaming variant. Calls back with the sources first, then text deltas, then the final response.
     */
    public void askStreaming(AskRequest request, String channel, StreamListener listener) {
        long start = System.nanoTime();
        List<RetrievedChunk> chunks;
        try {
            chunks = retrieve(request, null);
        } catch (RuntimeException e) {
            metrics.askCompleted(Outcome.ERROR, true, channel, elapsed(start));
            listener.onError(e);
            return;
        }
        listener.onSources(sources(chunks, Set.of()));
        if (chunks.isEmpty()) {
            AnswerResponse none = generate(request.question(), chunks, start);
            listener.onDelta(none.answer());
            listener.onComplete(none);
            metrics.askCompleted(Outcome.NO_ANSWER, true, channel, elapsed(start));
            return;
        }
        streamingChatModel.chat(chatRequest(request.question(), chunks), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                listener.onDelta(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                AnswerResponse answer = toAnswer(completeResponse, chunks, start);
                metrics.askCompleted(Outcome.valueOf(answer.outcome().toUpperCase()), true, channel, elapsed(start));
                listener.onComplete(answer);
            }

            @Override
            public void onError(Throwable error) {
                log.warn("Streaming answer failed: {}", error.toString());
                metrics.askCompleted(Outcome.ERROR, true, channel, elapsed(start));
                listener.onError(error);
            }
        });
    }

    public interface StreamListener {
        void onSources(List<Source> sources);

        void onDelta(String text);

        void onComplete(AnswerResponse response);

        void onError(Throwable error);
    }

    private ChatRequest chatRequest(String question, List<RetrievedChunk> chunks) {
        return ChatRequest.builder()
                .messages(SystemMessage.from(prompts.systemPrompt()),
                        UserMessage.from(prompts.userMessage(question, chunks)))
                .build();
    }

    private AnswerResponse toAnswer(ChatResponse response, List<RetrievedChunk> chunks, long startNanos) {
        String text = response.aiMessage() == null ? null : response.aiMessage().text();
        Usage usage = response.tokenUsage() == null ? Usage.NONE : new Usage(
                nz(response.tokenUsage().inputTokenCount()), nz(response.tokenUsage().outputTokenCount()));
        String model = response.modelName() != null ? response.modelName() : modelName;
        long latency = elapsed(startNanos).toMillis();

        // Anthropic's "refusal" stop reason is mapped to OTHER by LangChain4j.
        if (response.finishReason() == FinishReason.OTHER && (text == null || text.isBlank())) {
            return new AnswerResponse(REFUSED, false, Outcome.REFUSED.tag(), sources(chunks, Set.of()), model, usage, latency);
        }
        Set<Integer> cited = PromptBuilder.citations(text, chunks.size());
        Outcome outcome = cited.isEmpty() ? Outcome.NO_ANSWER : Outcome.ANSWERED;
        return new AnswerResponse(text == null ? "" : text.strip(), outcome == Outcome.ANSWERED, outcome.tag(),
                sources(chunks, cited), model, usage, latency);
    }

    private static List<Source> sources(List<RetrievedChunk> chunks, Set<Integer> cited) {
        List<Source> sources = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sources.add(new Source(i + 1, c.title(), c.url(), c.kit(), c.section(),
                    c.vectorScore() != null ? c.vectorScore() : c.score(), cited.contains(i + 1)));
        }
        return sources;
    }

    private static long nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
