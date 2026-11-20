package com.pensionsure.exception;

/**
 * Thrown when a date string cannot be parsed into any of the recognised formats.
 *
 * DateMatchStrategy accepts multiple formats (DD/MM/YYYY, MM/DD/YYYY, YYYY-MM-DD …)
 * and normalises to ISO-8601 before comparing. If none of the supported patterns match,
 * this exception signals that the input is fundamentally malformed — not just differently
 * formatted — so the engine should halt rather than produce a meaningless similarity score.
 */
public class InvalidDateFormatException extends Exception {

    private final String rawDate;

    public InvalidDateFormatException(String rawDate) {
        super("Cannot parse date string into any recognised format: '" + rawDate + "'");
        this.rawDate = rawDate;
    }

    /** @return the raw unparseable date string that caused the failure. */
    public String getRawDate() { return rawDate; }
}
