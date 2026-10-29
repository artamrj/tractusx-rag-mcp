package de.ragtractusx.rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import de.ragtractusx.retrieval.RetrievedChunk;

/**
 * Builds the prompt: a fixed system prompt (identical on every request, so it is cache-friendly) and a user message
 * that holds the numbered excerpts followed by the question.
 */
@Component
public class PromptBuilder {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    private final String systemPrompt;

    public PromptBuilder() {
        try {
            this.systemPrompt = new ClassPathResource("prompts/system.txt").getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String userMessage(String question, List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder("<documents>\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk c = chunks.get(i);
            sb.append("<document index=\"").append(i + 1).append("\">\n")
                    .append("<url>").append(c.url()).append("</url>\n")
                    .append("<content>\n").append(c.text().strip()).append("\n</content>\n")
                    .append("</document>\n");
        }
        sb.append("</documents>\n\n")
                .append("Answer the question below using the documents above, citing them as [n].\n\n")
                .append("<question>\n").append(question.strip()).append("\n</question>");
        return sb.toString();
    }

    /** Citation numbers used in an answer that refer to existing sources. */
    public static Set<Integer> citations(String answer, int sourceCount) {
        Set<Integer> cited = new TreeSet<>();
        Matcher m = CITATION.matcher(answer == null ? "" : answer);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= sourceCount) {
                cited.add(n);
            }
        }
        return cited;
    }
}
