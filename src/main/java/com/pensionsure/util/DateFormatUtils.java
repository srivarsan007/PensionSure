package com.pensionsure.util;

import com.pensionsure.exception.InvalidDateFormatException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Utility class for normalising dates to ISO-8601 ("YYYY-MM-DD").
 *
 * WHY normalise before comparing?
 * Pensioners and PDAs use many different date notations: DD/MM/YYYY (India default),
 * MM/DD/YYYY (US-influenced systems), DD-MM-YYYY, YYYY/MM/DD, and ISO-8601 itself.
 * Comparing raw strings would flag "15/03/1955" vs "1955-03-15" as a mismatch even
 * though they represent the same date. Normalising first lets DateMatchStrategy
 * distinguish format mismatches (same date, different notation) from genuine date
 * discrepancies (actually different dates), which have very different severity levels.
 *
 * The order of formats in SUPPORTED_PATTERNS matters: more specific patterns are
 * tried first so that "03/15/1955" (MM/DD) doesn't get misread as "03/15" under
 * DD/MM when the day would be out of range anyway.
 */
public class DateFormatUtils {

    // All date formats commonly seen in Indian pension paperwork, in try-order.
    // ISO-8601 is tried first because it is unambiguous; ambiguous patterns come later.
    private static final List<DateTimeFormatter> SUPPORTED_PATTERNS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),   // ISO-8601 — unambiguous, try first
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),   // Indian standard
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),   // US-influenced systems
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),   // Hyphen variant of Indian
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),   // Some government portals
        DateTimeFormatter.ofPattern("d/M/yyyy"),     // Without leading zeros
        DateTimeFormatter.ofPattern("d-M-yyyy")
    );

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Private constructor — this is a pure utility class, not meant to be instantiated. */
    private DateFormatUtils() {}

    /**
     * Attempts to parse {@code raw} using every supported pattern in order,
     * and returns the date normalised to "yyyy-MM-dd".
     *
     * @param raw the date string to normalise (may be in any supported format)
     * @return ISO-8601 date string
     * @throws InvalidDateFormatException if no supported pattern matches
     */
    public static String normalise(String raw) throws InvalidDateFormatException {
        if (raw == null || raw.isBlank()) {
            throw new InvalidDateFormatException(raw);
        }
        String trimmed = raw.trim();
        for (DateTimeFormatter fmt : SUPPORTED_PATTERNS) {
            try {
                LocalDate date = LocalDate.parse(trimmed, fmt);
                return date.format(ISO);
            } catch (DateTimeParseException ignored) {
                // Try the next format in the list.
            }
        }
        throw new InvalidDateFormatException(trimmed);
    }

    /**
     * Returns true if {@code raw} can be parsed by at least one supported pattern.
     * Useful for pre-flight validation before calling normalise().
     */
    public static boolean isValidDate(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            normalise(raw);
            return true;
        } catch (InvalidDateFormatException e) {
            return false;
        }
    }

    /**
     * Convenience method: normalises both strings and checks if they represent
     * the same calendar date, ignoring formatting differences.
     *
     * @return true if both strings parse to the same date
     * @throws InvalidDateFormatException if either string cannot be parsed
     */
    public static boolean sameDate(String a, String b) throws InvalidDateFormatException {
        return normalise(a).equals(normalise(b));
    }
}
