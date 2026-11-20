package com.pensionsure.exception;

/**
 * Thrown when an attempt is made to insert a Pensioner whose PPO number already
 * exists in the database, or whose (name + DOB + householdId) combination
 * would be an obvious duplicate.
 *
 * Keeping this separate from a generic SQL unique-constraint violation lets the
 * service layer produce a user-friendly message ("A pensioner with this PPO number
 * is already registered") rather than surfacing a raw JDBC exception to the UI.
 */
public class DuplicatePensionerException extends Exception {

    public DuplicatePensionerException(String ppoNumber) {
        super("A pensioner with PPO number '" + ppoNumber + "' already exists.");
    }

    public DuplicatePensionerException(String field, String value) {
        super("Duplicate pensioner detected: " + field + "='" + value + "' already registered.");
    }
}
