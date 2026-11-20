package com.pensionsure.exception;

/**
 * Thrown when a Submission is attempted with one or more required fields missing.
 *
 * WHY a checked exception rather than a runtime exception?
 * Incomplete submissions are a *foreseeable* user error (not a programmer bug),
 * so callers should be forced by the compiler to handle or declare it.
 * This prevents silent swallowing of bad input — the UI controller catches it
 * and shows a specific validation message instead of a generic error dialog.
 */
public class IncompleteRecordException extends Exception {

    private final String missingField;

    public IncompleteRecordException(String missingField) {
        super("Required field is missing or blank: " + missingField);
        this.missingField = missingField;
    }

    public IncompleteRecordException(String missingField, String detail) {
        super("Required field '" + missingField + "' is missing: " + detail);
        this.missingField = missingField;
    }

    /** @return the name of the field that was missing (e.g. "submittedName"). */
    public String getMissingField() { return missingField; }
}
