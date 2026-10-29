package de.ragtractusx.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import de.ragtractusx.rag.AnswerResponse;
import de.ragtractusx.rag.AskRequest;
import de.ragtractusx.rag.RagService;
import jakarta.validation.Valid;

/**
 * Question answering over the Tractus-X docs.
 */
@RestController
@RequestMapping("/api/v1")
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);
    private static final long STREAM_TIMEOUT_MS = 180_000;

    private final RagService rag;

    public AskController(RagService rag) {
        this.rag = rag;
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@Valid @RequestBody AskRequest request) {
        log.debug("ask: {}", request.question());
        return rag.ask(request, "rest");
    }

    /**
     * Server-sent events: {@code sources} (numbered excerpts) → many {@code token} events → {@code done} (full answer
     * with cited flags and usage) or {@code error}.
     */
    @PostMapping(path = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Thread.startVirtualThread(() -> rag.askStreaming(request, "rest", new RagService.StreamListener() {
            @Override
            public void onSources(List<AnswerResponse.Source> sources) {
                send(emitter, "sources", sources);
            }

            @Override
            public void onDelta(String text) {
                send(emitter, "token", Map.of("text", text));
            }

            @Override
            public void onComplete(AnswerResponse response) {
                send(emitter, "done", response);
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                send(emitter, "error", Map.of("message", "Answer generation failed"));
                emitter.complete();
            }
        }));
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // Client went away; nothing else to do.
            log.debug("SSE send failed: {}", e.toString());
        }
    }
}
