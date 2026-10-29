package de.ragtractusx.eval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import de.ragtractusx.retrieval.RetrievedChunk;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM-as-judge: Claude grades one answer against the reference key points and the excerpts the answer was based on.
 * The judge only counts (covered key points, supported claims, valid citations); the scores are computed in code so
 * the rubric is transparent and stable.
 */
@Component
public class JudgeService {

    static final String SYSTEM = """
            You grade answers of a question-answering assistant for the Eclipse Tractus-X documentation.
            The assistant was given numbered documentation excerpts and had to answer only from them, citing [n].
            Grade strictly and literally; do not reward plausible statements that the excerpts do not support.

            Return only a JSON object, no prose, with exactly these fields:
            {
              "key_points": [true or false for each reference key point, in the given order: is it stated or clearly implied by the answer?],
              "claims_total": number of distinct factual claims in the answer,
              "claims_supported": how many of those claims are supported by the excerpts,
              "citations_total": number of [n] citation markers in the answer,
              "citations_valid": how many of those markers point to an excerpt that supports the sentence they are attached to,
              "abstained": true if the answer says the documentation does not cover the question (fully or mostly), else false,
              "comment": one short sentence explaining the main problem, or "ok"
            }""";

    /** Judge counts and the derived scores. Score fields are null when not applicable. */
    public record Verdict(
            List<Boolean> keyPoints,
            int claimsTotal,
            int claimsSupported,
            int citationsTotal,
            int citationsValid,
            boolean abstained,
            String comment,
            long inputTokens,
            long outputTokens) {

        public Double correctness() {
            return keyPoints.isEmpty() ? null : keyPoints.stream().filter(b -> b).count() / (double) keyPoints.size();
        }

        public Double faithfulness() {
            return claimsTotal == 0 ? null : Math.min(1.0, claimsSupported / (double) claimsTotal);
        }

        public Double citationPrecision() {
            return citationsTotal == 0 ? null : Math.min(1.0, citationsValid / (double) citationsTotal);
        }
    }

    private final ChatModel chatModel;
    private final JsonMapper json = JsonMapper.builder().build();

    public JudgeService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Verdict judge(EvalItem item, String answer, List<RetrievedChunk> excerpts) {
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(SystemMessage.from(SYSTEM), UserMessage.from(userMessage(item, answer, excerpts)))
                .build());
        long in = response.tokenUsage() == null || response.tokenUsage().inputTokenCount() == null ? 0
                : response.tokenUsage().inputTokenCount();
        long out = response.tokenUsage() == null || response.tokenUsage().outputTokenCount() == null ? 0
                : response.tokenUsage().outputTokenCount();
        return parse(response.aiMessage().text(), item.keyPoints().size(), in, out);
    }

    static String userMessage(EvalItem item, String answer, List<RetrievedChunk> excerpts) {
        StringBuilder sb = new StringBuilder("<question>\n").append(item.question()).append("\n</question>\n\n");
        if (item.answerable()) {
            sb.append("<reference_answer>\n").append(item.referenceAnswer()).append("\n</reference_answer>\n\n");
            sb.append("<key_points>\n");
            for (int i = 0; i < item.keyPoints().size(); i++) {
                sb.append(i + 1).append(". ").append(item.keyPoints().get(i)).append('\n');
            }
            sb.append("</key_points>\n\n");
        } else {
            sb.append("<note>The documentation does not answer this question; there are no key points.</note>\n\n");
        }
        sb.append("<excerpts>\n");
        for (int i = 0; i < excerpts.size(); i++) {
            sb.append("<excerpt index=\"").append(i + 1).append("\">\n").append(excerpts.get(i).text().strip())
                    .append("\n</excerpt>\n");
        }
        sb.append("</excerpts>\n\n<answer>\n").append(answer).append("\n</answer>");
        return sb.toString();
    }

    Verdict parse(String text, int keyPointCount, long in, long out) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Judge returned no JSON: " + text);
        }
        JsonNode node = json.readTree(text.substring(start, end + 1));
        List<Boolean> keyPoints = new ArrayList<>();
        for (JsonNode kp : node.path("key_points")) {
            keyPoints.add(kp.asBoolean());
        }
        // Be tolerant of a judge that returns too few/many entries: pad with false, cut extras.
        while (keyPoints.size() < keyPointCount) {
            keyPoints.add(false);
        }
        keyPoints = new ArrayList<>(keyPoints.subList(0, keyPointCount));
        return new Verdict(keyPoints,
                node.path("claims_total").asInt(0),
                node.path("claims_supported").asInt(0),
                node.path("citations_total").asInt(0),
                node.path("citations_valid").asInt(0),
                node.path("abstained").asBoolean(false),
                node.path("comment").asString(""),
                in, out);
    }
}
