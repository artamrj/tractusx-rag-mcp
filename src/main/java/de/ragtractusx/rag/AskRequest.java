package de.ragtractusx.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param question the user's question (German or English)
 * @param kit      optional KIT id to restrict retrieval to, e.g. {@code connector-kit}
 * @param topK     optional number of excerpts to give the model (default from config)
 */
public record AskRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 100) String kit,
        @Min(1) @Max(20) Integer topK) {

    public static AskRequest of(String question) {
        return new AskRequest(question, null, null);
    }
}
