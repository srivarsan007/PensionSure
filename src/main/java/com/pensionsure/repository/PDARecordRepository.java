package com.pensionsure.repository;

import com.pensionsure.model.PDARecord;

import java.sql.*;
import java.util.Optional;

/**
 * JDBC DAO for the {@link PDARecord} entity.
 *
 * PDARecord is a 1:1 companion to Pensioner in our system
 * (each pensioner has exactly one PDA record in the current scope).
 */
public class PDARecordRepository {

    private final Connection conn;

    public PDARecordRepository(Connection conn) {
        this.conn = conn;
    }

    // ---- Write operations ------------------------------------------------

    public PDARecord save(PDARecord r) throws SQLException {
        String sql = "INSERT INTO pda_record (pensioner_id, name_on_file, dob_on_file, " +
                     "address_on_file, pda_name) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, r.getPensionerId());
            ps.setString(2, r.getNameOnFile());
            ps.setString(3, r.getDobOnFile());
            ps.setString(4, r.getAddressOnFile());
            ps.setString(5, r.getPdaName());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) r.setId(keys.getInt(1));
            }
        }
        return r;
    }

    // ---- Read operations -------------------------------------------------

    /**
     * Finds the PDA record for a given pensioner.
     * Returns Optional.empty() if no PDA record has been entered yet.
     */
    public Optional<PDARecord> findByPensionerId(int pensionerId) throws SQLException {
        String sql = "SELECT id, pensioner_id, name_on_file, dob_on_file, " +
                     "address_on_file, pda_name FROM pda_record WHERE pensioner_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pensionerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    // ---- Mapping ---------------------------------------------------------

    private PDARecord map(ResultSet rs) throws SQLException {
        return new PDARecord(
            rs.getInt("id"),
            rs.getInt("pensioner_id"),
            rs.getString("name_on_file"),
            rs.getString("dob_on_file"),
            rs.getString("address_on_file"),
            rs.getString("pda_name")
        );
    }
}
