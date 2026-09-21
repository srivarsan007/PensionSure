package com.pensionsure.service;

import com.pensionsure.exception.DuplicatePensionerException;
import com.pensionsure.exception.IncompleteRecordException;
import com.pensionsure.model.Household;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Pensioner;
import com.pensionsure.repository.DatabaseManager;
import com.pensionsure.repository.PDARecordRepository;
import com.pensionsure.repository.PensionerRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Business logic layer for Pensioner and PDARecord operations.
 *
 * Sits between the UI controllers and the JDBC repositories.
 * Responsible for:
 *  - Orchestrating multi-step DB operations (create household → create pensioner)
 *  - Enforcing business rules (no duplicate PPO numbers)
 *  - Translating repository-level SQLExceptions into domain exceptions that
 *    the UI can display meaningfully (no raw "UNIQUE constraint failed" to users)
 */
public class PensionerService {

    private final PensionerRepository pensionerRepo;
    private final PDARecordRepository pdaRecordRepo;
    private final Connection conn;

    public PensionerService(Connection conn) {
        this.conn = conn;
        this.pensionerRepo = new PensionerRepository(conn);
        this.pdaRecordRepo = new PDARecordRepository(conn);
    }

    // ---- Household operations --------------------------------------------

    /**
     * Creates a new household with the given head-of-household name.
     * @return the created Household with its generated ID
     */
    public Household createHousehold(String headName) throws SQLException {
        String sql = "INSERT INTO household (head_of_household_name) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, headName);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Household h = new Household(keys.getInt(1), headName);
                    return h;
                }
            }
        }
        throw new SQLException("Failed to create household.");
    }

    /**
     * Returns all households (used to populate dropdowns in the UI).
     */
    public List<Household> getAllHouseholds() throws SQLException {
        List<Household> list = new java.util.ArrayList<>();
        String sql = "SELECT id, head_of_household_name FROM household ORDER BY head_of_household_name";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Household(rs.getInt("id"), rs.getString("head_of_household_name")));
            }
        }
        return list;
    }

    public Optional<Household> getHouseholdById(int id) throws SQLException {
        String sql = "SELECT id, head_of_household_name FROM household WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Household h = new Household(rs.getInt("id"), rs.getString("head_of_household_name"));
                    h.setPensioners(pensionerRepo.findByHouseholdId(id));
                    return Optional.of(h);
                }
            }
        }
        return Optional.empty();
    }

    public List<Pensioner> getPensionersByHousehold(int householdId) throws SQLException {
        return pensionerRepo.findByHouseholdId(householdId);
    }

    // ---- Pensioner operations --------------------------------------------

    /**
     * Registers a new pensioner after checking for duplicate PPO numbers.
     *
     * @throws IncompleteRecordException   if required fields are blank
     * @throws DuplicatePensionerException if the PPO number is already registered
     */
    public Pensioner registerPensioner(Pensioner p)
            throws SQLException, IncompleteRecordException, DuplicatePensionerException {

        // Validate required fields before hitting the DB
        if (p.getName() == null || p.getName().isBlank())
            throw new IncompleteRecordException("name");
        if (p.getDob() == null || p.getDob().isBlank())
            throw new IncompleteRecordException("dob");
        if (p.getPensionType() == null)
            throw new IncompleteRecordException("pensionType");

        // PPO number uniqueness check — gives a clean error message vs. SQL constraint violation
        if (p.getPpoNumber() != null && !p.getPpoNumber().isBlank()) {
            if (pensionerRepo.existsByPpoNumber(p.getPpoNumber())) {
                throw new DuplicatePensionerException(p.getPpoNumber());
            }
        }

        return pensionerRepo.save(p);
    }

    /** Returns all registered pensioners (used to populate the main dashboard list). */
    public List<Pensioner> getAllPensioners() throws SQLException {
        return pensionerRepo.findAll();
    }

    public Optional<Pensioner> findPensionerById(int id) throws SQLException {
        return pensionerRepo.findById(id);
    }

    // ---- PDA Record operations -------------------------------------------

    /**
     * Saves a PDA record. Validates that mandatory fields are present before persisting.
     */
    public PDARecord savePDARecord(PDARecord r)
            throws SQLException, IncompleteRecordException {
        if (r.getNameOnFile() == null || r.getNameOnFile().isBlank())
            throw new IncompleteRecordException("nameOnFile");
        if (r.getDobOnFile() == null || r.getDobOnFile().isBlank())
            throw new IncompleteRecordException("dobOnFile");
        if (r.getAddressOnFile() == null || r.getAddressOnFile().isBlank())
            throw new IncompleteRecordException("addressOnFile");

        return pdaRecordRepo.save(r);
    }

    public Optional<PDARecord> getPDARecordForPensioner(int pensionerId) throws SQLException {
        return pdaRecordRepo.findByPensionerId(pensionerId);
    }
}
