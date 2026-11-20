package com.pensionsure.matching;

import com.pensionsure.util.DateFormatUtils;
import com.pensionsure.exception.InvalidDateFormatException;

/**
 * Compares dates, distinguishing between FORMAT mismatches and IDENTITY mismatches.
 *
 * ═══════════════════════════════════════════════════════════════════
 * WHY TWO LEVELS OF MISMATCH FOR DATES?
 * ───────────────────────────────────────────────────────────────────
 * A date mismatch has very different severity depending on its cause:
 *
 *  FORMAT mismatch (same date, different notation):
 *    "15/03/1955" vs "1955-03-15" → both represent 15 March 1955.
 *    This is NOT a real discrepancy — it's a data-entry formatting difference
 *    that the system can auto-correct. Flagging it as a high-severity mismatch
 *    would produce false alarms and erode user trust in the tool.
 *    Confidence score: 0.90 (slightly below 1.0 to signal "review formatting").
 *
 *  IDENTITY mismatch (genuinely different dates):
 *    "15/03/1955" vs "20/07/1957" — these are different dates. This is likely
 *    a real data entry error or a PDA record that hasn't been updated after a
 *    government records correction. Must be flagged with high severity.
 *    Confidence score: 0.0 (binary — dates are either the same or they aren't).
 *
 * WHY NOT JARO-WINKLER OR LEVENSHTEIN FOR DATES?
 *    Date strings are not free text — their structure is fully deterministic once
 *    parsed. A raw edit-distance between "15/03/1955" and "1955-03-15" would give
 *    a misleadingly low similarity score even though they are the same date.
 *    Parsing to a canonical form first makes the comparison semantically correct.
 * ═══════════════════════════════════════════════════════════════════
 */
public class DateMatchStrategy implements MatchStrategy {

    /**
     * Similarity score returned when dates are identical but formatted differently.
     * Slightly below 1.0 to distinguish from a truly identical string match, but
     * above the threshold so it is NOT flagged as a mismatch by default.
     */
    public static final double FORMAT_MISMATCH_SCORE = 0.90;

    /**
     * Similarity score for a genuine date identity mismatch (different dates).
     * Hard zero — dates are semantically either the same or they are not.
     */
    public static final double IDENTITY_MISMATCH_SCORE = 0.0;

    /** Threshold for the date strategy. Any score below this triggers a flag. */
    public static final double DEFAULT_THRESHOLD = 0.85;

    /**
     * Returns:
     *  1.0  — strings are identical (same format, same date)
     *  0.90 — different format, same calendar date (FORMAT mismatch)
     *  0.0  — genuinely different dates (IDENTITY mismatch)
     *
     * If either string cannot be parsed, returns 0.0 (worst-case: flag it).
     */
    @Override
    public double computeSimilarity(String onFile, String submitted) {
        if (onFile == null || submitted == null || onFile.isBlank() || submitted.isBlank()) {
            return IDENTITY_MISMATCH_SCORE;
        }

        // Fast path: exact string match (both already in the same format)
        if (onFile.trim().equals(submitted.trim())) return 1.0;

        try {
            String normOnFile   = DateFormatUtils.normalise(onFile);
            String normSubmitted = DateFormatUtils.normalise(submitted);

            if (normOnFile.equals(normSubmitted)) {
                // Same calendar date — the only difference was formatting.
                return FORMAT_MISMATCH_SCORE;
            } else {
                // Different dates entirely.
                return IDENTITY_MISMATCH_SCORE;
            }
        } catch (InvalidDateFormatException e) {
            // If we can't parse one or both strings, treat it as a hard mismatch —
            // an unparseable date should never silently pass validation.
            return IDENTITY_MISMATCH_SCORE;
        }
    }

    @Override
    public boolean isMismatch(String onFile, String submitted, double threshold) {
        return computeSimilarity(onFile, submitted) < threshold;
    }

    /**
     * Convenience overload: any difference (format discrepancy 0.90 or identity mismatch 0.0)
     * is flagged so the user can verify the date before final submission.
     */
    public boolean isMismatch(String onFile, String submitted) {
        return computeSimilarity(onFile, submitted) < 1.0;
    }

    /**
     * Convenience method that provides semantic context: did the mismatch stem from
     * formatting or from genuinely different dates?
     *
     * @return true only if both parse successfully AND they represent the same date
     *         but with different notation
     */
    public boolean isFormatMismatchOnly(String onFile, String submitted) {
        double score = computeSimilarity(onFile, submitted);
        // FORMAT_MISMATCH_SCORE (0.90) is the only score between 0.0 and 1.0 this method returns
        return score == FORMAT_MISMATCH_SCORE;
    }
}
