package com.pensionsure.repository;

import com.pensionsure.model.Pensioner;
import com.pensionsure.model.Pensioner.PensionType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO for the {@link Pensioner} entity.
 *
 * All SQL is plain JDBC — no ORM. Prepared statements are used throughout to
 * prevent SQL-injection and to let SQLite cache the compiled statement plan.
 */
public class PensionerRepository {

    private final Connection conn;

    public PensionerRepository(Connection conn) {
        this.conn = conn;
    }

    // ---- Write operations ------------------------------------------------

    /**
     * Inserts a new pensioner and sets the generated ID back onto the object.
     * @return the inserted Pensioner with its database-assigned id
     */
    public Pensioner save(Pensioner p) throws SQLException {
        String sql = "INSERT INTO pensioner (household_id, name, dob, pension_type, ppo_number) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getHouseholdId());
            ps.setString(2, p.getName());
            ps.setString(3, p.getDob());
            ps.setString(4, p.getPensionType().name());
            ps.setString(5, p.getPpoNumber());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) p.setId(keys.getInt(1));
            }
        }
        return p;
    }

    // ---- Read operations -------------------------------------------------

    public List<Pensioner> findAll() throws SQLException {
        List<Pensioner> results = new ArrayList<>();
        String sql = "SELECT id, household_id, name, dob, pension_type, ppo_number FROM pensioner ORDER BY name";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) results.add(map(rs));
        }
        return results;
    }

    public Optional<Pensioner> findById(int id) throws SQLException {
        String sql = "SELECT id, household_id, name, dob, pension_type, ppo_number FROM pensioner WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<Pensioner> findByPpoNumber(String ppoNumber) throws SQLException {
        String sql = "SELECT id, household_id, name, dob, pension_type, ppo_number " +
                     "FROM pensioner WHERE ppo_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ppoNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public boolean existsByPpoNumber(String ppoNumber) throws SQLException {
        return findByPpoNumber(ppoNumber).isPresent();
    }

    public List<Pensioner> findByHouseholdId(int householdId) throws SQLException {
        List<Pensioner> list = new ArrayList<>();
        String sql = "SELECT id, household_id, name, dob, pension_type, ppo_number " +
                     "FROM pensioner WHERE household_id = ? ORDER BY name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // ---- Mapping ---------------------------------------------------------

    /** Maps a ResultSet row to a Pensioner object. */
    private Pensioner map(ResultSet rs) throws SQLException {
        return new Pensioner(
            rs.getInt("id"),
            rs.getInt("household_id"),
            rs.getString("name"),
            rs.getString("dob"),
            PensionType.valueOf(rs.getString("pension_type")),
            rs.getString("ppo_number")
        );
    }
}
