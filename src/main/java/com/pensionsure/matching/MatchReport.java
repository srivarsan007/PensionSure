package com.pensionsure.matching;

import com.pensionsure.model.MismatchFlag;

import java.util.List;

/**
 * The result of running MismatchEngine over one (PDARecord, Submission) pair.
 *
 * Returned as a plain value object so the caller (MatchingService / UI) gets
 * a complete, immutable picture of the comparison without needing to re-query
 * individual flags or recompute scores.
 *
 * The overall confidence score is a simple average of per-field scores.
 * WHY average and not min?
 *  - min would be dominated by the address field's inherent noisiness.
 *  - A weighted average (future improvement) could up-weight name and DOB,
 *    but a simple average is transparent and sufficient for Phase 1.
 */
public class MatchReport {

    private final List<MismatchFlag> flags;
    private final double overallConfidence;

    public MatchReport(List<MismatchFlag> flags) {
        this.flags = List.copyOf(flags); // defensive immutable copy
        this.overallConfidence = flags.isEmpty() ? 1.0 :
            flags.stream().mapToDouble(MismatchFlag::getConfidenceScore).average().orElse(1.0);
    }

    /** @return ordered list of per-field match results */
    public List<MismatchFlag> getFlags() { return flags; }

    /** @return overall confidence score, the mean of all per-field scores */
    public double getOverallConfidence() { return overallConfidence; }

    /**
     * @return true if ANY field was flagged as a mismatch — the caller
     *         can use this as a quick "submission safe to proceed?" check.
     */
    public boolean hasAnyMismatch() {
        return flags.stream().anyMatch(MismatchFlag::isFlagged);
    }

    /** @return count of flagged fields */
    public long getMismatchCount() {
        return flags.stream().filter(MismatchFlag::isFlagged).count();
    }

    @Override
    public String toString() {
        return String.format("MatchReport{overallConfidence=%.3f, mismatches=%d/%d}",
            overallConfidence, getMismatchCount(), flags.size());
    }
}
