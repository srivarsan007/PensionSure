package com.pensionsure.model;

/**
 * Central entity representing a single pensioner.
 *
 * Fields map 1:1 to the `pensioner` table in the blueprint schema.
 * {@link PensionType} is kept as an enum rather than a raw String because:
 *  1. It prevents invalid values from reaching the database.
 *  2. The matching engine and UI can switch on it without brittle string comparisons.
 */
public class Pensioner {

    /**
     * Pension type as prescribed by Government of India's DLC system.
     * CENTRAL – central government employees / CPAO pensioners.
     * STATE   – state government pensioners.
     * DEFENCE – ex-servicemen / defence personnel.
     */
    public enum PensionType {
        CENTRAL, STATE, DEFENCE
    }

    private int id;
    private int householdId;
    private String name;

    // Stored as "YYYY-MM-DD" String to remain independent of java.sql.Date in the model layer.
    // Conversion to/from SQL types is the repository's responsibility.
    private String dob;

    private PensionType pensionType;

    // PPO = Pension Payment Order number – government-assigned unique identifier.
    // Nullable in spec (some pensioners may not have it yet during first registration).
    private String ppoNumber;

    public Pensioner() {}

    public Pensioner(int id, int householdId, String name, String dob,
                     PensionType pensionType, String ppoNumber) {
        this.id = id;
        this.householdId = householdId;
        this.name = name;
        this.dob = dob;
        this.pensionType = pensionType;
        this.ppoNumber = ppoNumber;
    }

    // ---- Getters & Setters -----------------------------------------------

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getHouseholdId() { return householdId; }
    public void setHouseholdId(int householdId) { this.householdId = householdId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public PensionType getPensionType() { return pensionType; }
    public void setPensionType(PensionType pensionType) { this.pensionType = pensionType; }

    public String getPpoNumber() { return ppoNumber; }
    public void setPpoNumber(String ppoNumber) { this.ppoNumber = ppoNumber; }

    @Override
    public String toString() {
        return "Pensioner{id=" + id + ", name='" + name + "', dob='" + dob +
               "', type=" + pensionType + ", ppo='" + ppoNumber + "'}";
    }
}
