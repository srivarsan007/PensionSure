package com.pensionsure.matching;

/**
 * Strategy interface for comparing a single field between a PDARecord and a Submission.
 *
 * WHY the Strategy pattern here, not simple if-else or a utility class?
 * Each field type (name / date / address) requires a fundamentally different comparison
 * algorithm with its own threshold and its own semantics for what "close enough" means.
 * Strategy lets us:
 *  1. Swap or tune individual algorithms without touching MismatchEngine.
 *  2. Unit-test each algorithm in isolation.
 *  3. Add future strategies (e.g., phone number matching) without modifying existing code.
 *
 * An interface rather than an abstract class because the strategies share no state —
 * they are pure functions over two strings. Keeping them stateless also means a single
 * strategy instance can be reused across many comparisons without concurrency issues.
 */
public interface MatchStrategy {

    /**
     * Computes a similarity score between the PDA-side and submitted strings.
     *
     * @param onFile    the canonical value from the PDA record
     * @param submitted the value entered in the DLC submission draft
     * @return similarity score in [0.0, 1.0] where 1.0 is identical
     */
    double computeSimilarity(String onFile, String submitted);

    /**
     * Decides whether the similarity score constitutes a mismatch.
     *
     * @param onFile    canonical PDA value
     * @param submitted submitted value
     * @param threshold minimum acceptable similarity (strategy-specific)
     * @return true if a mismatch should be flagged
     */
    boolean isMismatch(String onFile, String submitted, double threshold);
}
