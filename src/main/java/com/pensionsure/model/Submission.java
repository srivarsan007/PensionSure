package com.pensionsure.model;

/**
 * Represents a single DLC (Digital Life Certificate) submission draft entered
 * by or on behalf of a pensioner.
 *
 * A Submission holds the *submitted* values that the pensioner intends to send.
 * These are what the MismatchEngine compares against the corresponding PDARecord.
 * One pensioner can have at most one active submission per deadline year.
 */
public class Submission {

    private int id;
    private int pensionerId;

    // The three fields that are compared against PDARecord during matching.
    private String submittedName;
    private String submittedDob;     // ISO-8601: "YYYY-MM-DD"
    private String submittedAddress;

    // Date the submission was entered into this system (not the DLC submission date to UIDAI).
    private String submissionDate;   // ISO-8601: "YYYY-MM-DD"

    // Year of the DLC deadline this submission targets (e.g., 2024 for the Nov 30 2024 cutoff).
    // Stored as int rather than a Date to avoid confusion with submissionDate.
    private int deadlineYear;

    public Submission() {}

    public Submission(int id, int pensionerId, String submittedName, String submittedDob,
                      String submittedAddress, String submissionDate, int deadlineYear) {
        this.id = id;
        this.pensionerId = pensionerId;
        this.submittedName = submittedName;
        this.submittedDob = submittedDob;
        this.submittedAddress = submittedAddress;
        this.submissionDate = submissionDate;
        this.deadlineYear = deadlineYear;
    }

    // ---- Getters & Setters -----------------------------------------------

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPensionerId() { return pensionerId; }
    public void setPensionerId(int pensionerId) { this.pensionerId = pensionerId; }

    public String getSubmittedName() { return submittedName; }
    public void setSubmittedName(String submittedName) { this.submittedName = submittedName; }

    public String getSubmittedDob() { return submittedDob; }
    public void setSubmittedDob(String submittedDob) { this.submittedDob = submittedDob; }

    public String getSubmittedAddress() { return submittedAddress; }
    public void setSubmittedAddress(String submittedAddress) { this.submittedAddress = submittedAddress; }

    public String getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(String submissionDate) { this.submissionDate = submissionDate; }

    public int getDeadlineYear() { return deadlineYear; }
    public void setDeadlineYear(int deadlineYear) { this.deadlineYear = deadlineYear; }

    @Override
    public String toString() {
        return "Submission{id=" + id + ", pensionerId=" + pensionerId +
               ", year=" + deadlineYear + ", name='" + submittedName + "'}";
    }
}
