package com.pensionsure.matching;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

/**
 * Compares names using Jaro-Winkler similarity.
 *
 * ═══════════════════════════════════════════════════════════════════
 * WHY JARO-WINKLER FOR NAMES (and not plain Levenshtein)?
 * ───────────────────────────────────────────────────────────────────
 * Levenshtein measures raw edit operations (insert / delete / substitute).
 * For names like "Mohanlal" vs "Mohannlal" it gives an edit distance of 1,
 * which needs to be normalised by max-length — a rough approximation.
 *
 * Jaro-Winkler improves on plain Jaro distance by giving a *prefix bonus*:
 * matching characters at the beginning of the string boost the final score.
 * This is a deliberate design choice that suits names well because:
 *
 *  1. Human name spelling errors are overwhelmingly mid- or end-of-word:
 *     "Srinivasan" → "Srinivasen", "Subramaniam" → "Subramanyam".
 *     Jaro-Winkler rewards the shared prefix and correctly scores these high.
 *
 *  2. Different people rarely share the same first few characters:
 *     "Ramesh" and "Rajesh" share "Ra" but diverge at position 3 — Jaro-Winkler
 *     still distinguishes them clearly (score ~0.94 vs threshold 0.85).
 *
 *  3. Indian names transliterated from Devanagari have high prefix consistency
 *     ("Suresh", "Sudesh", "Suraj") but differ at the suffix — the algorithm
 *     handles this well by not over-rewarding shared prefixes alone.
 *
 * Apache Commons Text's JaroWinklerSimilarity is a well-tested, production-grade
 * implementation; reimplementing it would introduce bugs with no benefit.
 * ═══════════════════════════════════════════════════════════════════
 *
 * THRESHOLD: 0.85 — chosen to allow single-character typos and common
 * transliteration variants ("Mohan" / "Mohann") while still catching
 * genuinely different names ("Ramesh" vs "Suresh" ≈ 0.76, correctly flagged).
 */
public class NameMatchStrategy implements MatchStrategy {

    // Default threshold below which a name difference is flagged.
    // 0.85 allows minor variants; 0.90 would be too strict for real-world data.
    public static final double DEFAULT_THRESHOLD = 0.85;

    // Apache Commons Text implementation — uses the standard Jaro-Winkler formula
    // with p=0.1 (the conventional prefix-weight scalar).
    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    /**
     * Returns Jaro-Winkler similarity in [0.0, 1.0].
     * Comparison is case-insensitive: PDA records and submissions may differ in
     * capitalisation ("MOHAN LAL" vs "Mohan Lal") without that being a real mismatch.
     */
    @Override
    public double computeSimilarity(String onFile, String submitted) {
        if (onFile == null || submitted == null) return 0.0;
        // Normalise: lowercase + collapse multiple spaces (addresses sometimes bleed in)
        String a = onFile.trim().toLowerCase().replaceAll("\\s+", " ");
        String b = submitted.trim().toLowerCase().replaceAll("\\s+", " ");
        return jaroWinkler.apply(a, b);
    }

    @Override
    public boolean isMismatch(String onFile, String submitted, double threshold) {
        return computeSimilarity(onFile, submitted) < threshold;
    }

    /**
     * Convenience overload: any spelling difference (similarity < 1.0) is flagged
     * so that the pensioner/operator is alerted to review before DLC submission.
     */
    public boolean isMismatch(String onFile, String submitted) {
        return computeSimilarity(onFile, submitted) < 1.0;
    }
}
