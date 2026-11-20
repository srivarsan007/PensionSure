package com.pensionsure.model;

/**
 * Audit log entry for each reminder check run by the ReminderScheduler (Phase 2).
 * Included in Phase 1 to complete the schema so the database migration doesn't
 * need to be altered during Phase 2 integration.
 */
public class ReminderLog {

    /**
     * Escalation levels, escalating in urgency as the Nov 30 deadline approaches.
     * INFO    → more than 14 days remaining.
     * WARNING → 3–14 days remaining.
     * URGENT  → fewer than 3 days remaining.
     */
    public enum EscalationLevel {
        INFO, WARNING, URGENT
    }

    private int id;
    private int pensionerId;
    private String reminderDate;       // ISO-8601 date of this log entry
    private EscalationLevel escalationLevel;
    private boolean sent;              // false until actually dispatched (Phase 2)

    public ReminderLog() {}

    public ReminderLog(int pensionerId, String reminderDate,
                       EscalationLevel escalationLevel, boolean sent) {
        this.pensionerId = pensionerId;
        this.reminderDate = reminderDate;
        this.escalationLevel = escalationLevel;
        this.sent = sent;
    }

    // ---- Getters & Setters -----------------------------------------------

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPensionerId() { return pensionerId; }
    public void setPensionerId(int pensionerId) { this.pensionerId = pensionerId; }

    public String getReminderDate() { return reminderDate; }
    public void setReminderDate(String reminderDate) { this.reminderDate = reminderDate; }

    public EscalationLevel getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(EscalationLevel escalationLevel) {
        this.escalationLevel = escalationLevel;
    }

    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }

    @Override
    public String toString() {
        return "ReminderLog{pensionerId=" + pensionerId + ", date='" + reminderDate +
               "', level=" + escalationLevel + ", sent=" + sent + "}";
    }
}
