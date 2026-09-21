package com.pensionsure.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a household grouping of pensioners.
 *
 * WHY this entity exists: in India, joint households commonly have multiple
 * pensioners (e.g., both spouses drawing central/state pensions). Grouping
 * them under one household lets the Phase-2 dashboard aggregate deadlines and
 * send a single reminder per household rather than spamming each member.
 * Phase 1 uses it only for foreign-key normalisation; the dashboard is Phase 2.
 */
public class Household {

    private int id;
    private String headOfHouseholdName;

    // One household can contain multiple pensioners (1:N).
    // Using ArrayList so ordering is preserved (useful for dashboard display).
    private List<Pensioner> pensioners = new ArrayList<>();

    public Household() {}

    public Household(int id, String headOfHouseholdName) {
        this.id = id;
        this.headOfHouseholdName = headOfHouseholdName;
    }

    // ---- Getters & Setters -----------------------------------------------

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getHeadOfHouseholdName() { return headOfHouseholdName; }
    public void setHeadOfHouseholdName(String headOfHouseholdName) {
        this.headOfHouseholdName = headOfHouseholdName;
    }

    public List<Pensioner> getPensioners() { return pensioners; }
    public void setPensioners(List<Pensioner> pensioners) {
        this.pensioners = pensioners;
    }

    public void addPensioner(Pensioner p) { this.pensioners.add(p); }

    @Override
    public String toString() {
        return "Household{id=" + id + ", head='" + headOfHouseholdName + "'}";
    }
}
