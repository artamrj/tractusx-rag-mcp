package de.ragtractusx.retrieval;

/**
 * @param query text to search for
 * @param kit   optional KIT id filter (e.g. {@code connector-kit})
 * @param topK  number of chunks to return
 * @param mode  vector or hybrid
 */
public record SearchRequest(String query, String kit, int topK, Mode mode) {

    public enum Mode {
        VECTOR, HYBRID;

        public static Mode parse(String value) {
            return Mode.valueOf(value.trim().toUpperCase());
        }

        public String tag() {
            return name().toLowerCase();
        }
    }
}
