package de.ragtractusx.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Page-level retrieval metrics: a retrieved chunk is relevant if it belongs to one of the expected pages.
 *
 * @param firstRelevantRank 1-based rank of the first relevant chunk, 0 if none
 * @param recall            share of expected pages that appear among the retrieved chunks
 */
public record RetrievalScores(int firstRelevantRank, double recall) {

    public boolean hitAt(int k) {
        return firstRelevantRank > 0 && firstRelevantRank <= k;
    }

    public double reciprocalRank() {
        return firstRelevantRank == 0 ? 0 : 1.0 / firstRelevantRank;
    }

    public static RetrievalScores score(List<String> retrievedPaths, List<String> expectedPaths) {
        Set<String> expected = expectedPaths.stream().map(RetrievalScores::normalize).collect(Collectors.toSet());
        int first = 0;
        Set<String> found = new HashSet<>();
        for (int i = 0; i < retrievedPaths.size(); i++) {
            String p = normalize(retrievedPaths.get(i));
            if (expected.contains(p)) {
                found.add(p);
                if (first == 0) {
                    first = i + 1;
                }
            }
        }
        return new RetrievalScores(first, expected.isEmpty() ? 0 : (double) found.size() / expected.size());
    }

    /** Makes paths independent of the KIT release folder, so a test set survives version bumps. */
    static String normalize(String path) {
        return path == null ? "" : path.strip()
                .replaceFirst("^docs-kits_versioned_docs/version-[^/]+/", "kits-docs/")
                .replaceFirst("^docs-kits/", "kits-docs/");
    }
}
