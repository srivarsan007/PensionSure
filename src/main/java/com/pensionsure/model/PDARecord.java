package com.pensionsure.model;

/**
 * Mirrors the data a Pension Disbursing Agency (PDA) holds on file for a pensioner.
 *
 * PRIVACY NOTE (blueprint §3): Do NOT store full bank account numbers.
 * If account linkage is ever added, persist only the last four digits.
 * This class deliberately omits an account-number field to enforce that rule
 * at the model layer — harder to accidentally add than a runtime check.
 *
 * WHY a separate entity from Pensioner?
 * The PDA's record is the authoritative source of truth that DLC submissions
 * are compared against. It may diverge from the pensioner's self-reported data
 * (spelling differences, address changes not propagated to PDA, etc.) — that
 * divergence is precisely what the matching engine detects.
 */
public class PDARecord {

    private int id;
    private int pensionerId;

    // These are the *canonical* values from the PDA's system.
    // The matching engine compares submitted values against these.
    private String nameOnFile;
    private String dobOnFile;       // ISO-8601: "YYYY-MM-DD"
    private String addressOnFile;

    // Pension Disbursing Agency name (e.g., "State Bank of India, Pune Branch").
    // Stored for display; not used in matching.
    private String pdaName;

    public PDARecord() {}

    public PDARecord(int id, int pensionerId, String nameOnFile, String dobOnFile,
                     String addressOnFile, String pdaName) {
        this.id = id;
        this.pensionerId = pensionerId;
        this.nameOnFile = nameOnFile;
        this.dobOnFile = dobOnFile;
        this.addressOnFile = addressOnFile;
        this.pdaName = pdaName;
    }

    // ---- Getters & Setters -----------------------------------------------
    // Fields are private; access is only through these methods (encapsulation
    // requirement from blueprint §8). The matching engine reads via getters,
    // keeping direct field access out of business logic.

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPensionerId() { return pensionerId; }
    public void setPensionerId(int pensionerId) { this.pensionerId = pensionerId; }

    public String getNameOnFile() { return nameOnFile; }
    public void setNameOnFile(String nameOnFile) { this.nameOnFile = nameOnFile; }

    public String getDobOnFile() { return dobOnFile; }
    public void setDobOnFile(String dobOnFile) { this.dobOnFile = dobOnFile; }

    public String getAddressOnFile() { return addressOnFile; }
    public void setAddressOnFile(String addressOnFile) { this.addressOnFile = addressOnFile; }

    public String getPdaName() { return pdaName; }
    public void setPdaName(String pdaName) { this.pdaName = pdaName; }

    @Override
    public String toString() {
        return "PDARecord{id=" + id + ", pensionerId=" + pensionerId +
               ", nameOnFile='" + nameOnFile + "'}";
    }
}
