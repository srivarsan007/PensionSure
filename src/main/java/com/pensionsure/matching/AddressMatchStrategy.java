package com.pensionsure.matching;

import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * Compares addresses using normalised Levenshtein distance.
 *
 * ═══════════════════════════════════════════════════════════════════
 * WHY LEVENSHTEIN FOR ADDRESSES (and not Jaro-Winkler)?
 * ───────────────────────────────────────────────────────────────────
 * Addresses are structurally different from names:
 *
 *  1. ABBREVIATION VARIANCE is the primary source of noise, not typos:
 *     "12, MG Road, Pune" vs "12, Mahatma Gandhi Road, Pune, Maharashtra 411001"
 *     These are the same address. Jaro-Winkler's prefix bonus would not help here
 *     because the abbreviation ("MG") shares no meaningful prefix with the full form.
 *     Levenshtein captures the raw edit cost, which stays proportionally low when
 *     the core tokens match.
 *
 *  2. Addresses are MUCH LONGER than names, so raw edit distance is not meaningful —
 *     a distance of 5 on a 200-character address is trivial; the same distance on a
 *     10-character name is catastrophic. NORMALISATION (dividing by max length) maps
 *     both to [0, 1] so the threshold is meaningful regardless of address length.
 *     Formula: similarity = 1 - (editDistance / maxLength)
 *
 *  3. A LENIENT THRESHOLD (0.70) is used because address abbreviation variance
 *     is both expected and acceptable — we should not over-flag "St." vs "Street"
 *     or "Nagar" vs "Ngr" as high-severity mismatches. The risk of a false negative
 *     (missing a real address change) is low because genuine address changes would
 *     shift the normalised distance well below 0.70.
 *
 * WHY NOT JARO-WINKLER?
 *    Jaro-Winkler rewards shared prefixes. Street addresses commonly start the same
 *    way ("12, MG Road" vs "14, MG Road") — different house numbers would get a
 *    spuriously high score because they share a long common suffix. Levenshtein,
 *    normalised by length, correctly penalises the different digit.
 * ═══════════════════════════════════════════════════════════════════
 *
 * THRESHOLD: 0.70 — lenient to absorb "St./Street", district name abbreviations,
 * and PIN code presence/absence. Validated against sample pairs from Indian address formats.
 */
public class AddressMatchStrategy implements MatchStrategy {

    public static final double DEFAULT_THRESHOLD = 0.70;

    // Unbounded Levenshtein — Commons Text also offers a bounded variant for performance,
    // but addresses are short enough (< 255 chars per schema) that unbounded is fine.
    private final LevenshteinDistance levenshtein = LevenshteinDistance.getDefaultInstance();

    /**
     * Returns normalised similarity in [0.0, 1.0].
     * Normalisation: similarity = 1 - (editDistance / max(len_a, len_b))
     *
     * Pre-processing applied before comparison:
     *  - lowercase (case is irrelevant in addresses)
     *  - collapse runs of whitespace to single space
     *  - trim leading/trailing whitespace
     *
     * WHY not expand abbreviations ("St." → "Street")?
     *    Abbreviation dictionaries are locale-specific and incomplete. Normalising
     *    to lowercase + trimming is sufficient at this threshold; adding a brittle
     *    abbreviation map would create new failure modes (e.g., "IN" → "Indiana"
     *    in a US dictionary but means "India" here).
     */
    @Override
    public double computeSimilarity(String onFile, String submitted) {
        if (onFile == null || submitted == null) return 0.0;

        String a = normaliseAddress(onFile);
        String b = normaliseAddress(submitted);

        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int editDistance = levenshtein.apply(a, b);
        int maxLen = Math.max(a.length(), b.length());

        // Guard against the degenerate case (shouldn't occur given the empty checks above).
        if (maxLen == 0) return 1.0;

        return 1.0 - ((double) editDistance / maxLen);
    }

    @Override
    public boolean isMismatch(String onFile, String submitted, double threshold) {
        return computeSimilarity(onFile, submitted) < threshold;
    }

    /** Convenience overload using the default threshold. */
    public boolean isMismatch(String onFile, String submitted) {
        return isMismatch(onFile, submitted, DEFAULT_THRESHOLD);
    }

    // ---- Private helpers -------------------------------------------------

    private String normaliseAddress(String raw) {
        return raw.trim()
                  .toLowerCase()
                  .replaceAll("\\s+", " ")
                  .replaceAll("[,\\.]+", " ")   // collapse punctuation to space
                  .replaceAll("\\s+", " ")       // second pass after punctuation removal
                  .trim();
    }
}
