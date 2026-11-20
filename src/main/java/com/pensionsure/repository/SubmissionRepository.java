package com.pensionsure.repository;

import com.pensionsure.model.MismatchFlag;
import com.pensionsure.model.Submission;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO for {@link Submission} and {@link MismatchFlag} entities.
 *
 * These two are co-located in one repository because MismatchFlags are tightly
 * coupled to their parent Submission (they are created together, loaded together,
 * and never queried independently in Phase 1).
 */
public class SubmissionRepository {

    private final Connection conn;

    public SubmissionRepository(Connection conn) {
        this.conn = conn;
    }

    // ---- Submission write operations -------------------------------------

    public Submission save(Submission s) throws SQLException {
        String sql = "INSERT INTO submission (pensioner_id, submitted_name, submitted_dob, " +
                     "submitted_address, submission_date, deadline_year) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getPensionerId());
            ps.setString(2, s.getSubmittedName());
            ps.setString(3, s.getSubmittedDob());
            ps.setString(4, s.getSubmittedAddress());
            ps.setString(5, s.getSubmissionDate());
            ps.setInt(6, s.getDeadlineYear());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) s.setId(keys.getInt(1));
            }
        }
        return s;
    }

    // ---- Submission read operations --------------------------------------

    public Optional<Submission> findLatestByPensionerId(int pensionerId) throws SQLException {
        // "Latest" = highest id (most recently inserted) for this pensioner.
        String sql = "SELECT id, pensioner_id, submitted_name, submitted_dob, submitted_address, " +
                     "submission_date, deadline_year FROM submission " +
                     "WHERE pensioner_id = ? ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pensionerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapSubmission(rs));
            }
        }
        return Optional.empty();
    }

    public List<Submission> findAllByPensionerId(int pensionerId) throws SQLException {
        List<Submission> list = new ArrayList<>();
        String sql = "SELECT id, pensioner_id, submitted_name, submitted_dob, submitted_address, " +
                     "submission_date, deadline_year FROM submission WHERE pensioner_id = ? ORDER BY id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pensionerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapSubmission(rs));
            }
        }
        return list;
    }

    // ---- MismatchFlag write operations -----------------------------------

    public void saveMismatchFlag(MismatchFlag flag) throws SQLException {
        String sql = "INSERT INTO mismatch_flag (submission_id, field_name, confidence_score, " +
                     "flagged, recommendation) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, flag.getSubmissionId());
            ps.setString(2, flag.getFieldName());
            ps.setDouble(3, flag.getConfidenceScore());
            // SQLite stores boolean as integer: 1 = true, 0 = false
            ps.setInt(4, flag.isFlagged() ? 1 : 0);
            ps.setString(5, flag.getRecommendation());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) flag.setId(keys.getInt(1));
            }
        }
    }

    // ---- MismatchFlag read operations ------------------------------------

    public List<MismatchFlag> findFlagsBySubmissionId(int submissionId) throws SQLException {
        List<MismatchFlag> flags = new ArrayList<>();
        String sql = "SELECT id, submission_id, field_name, confidence_score, flagged, recommendation " +
                     "FROM mismatch_flag WHERE submission_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) flags.add(mapFlag(rs));
            }
        }
        return flags;
    }

    // ---- Mapping helpers -------------------------------------------------

    private Submission mapSubmission(ResultSet rs) throws SQLException {
        return new Submission(
            rs.getInt("id"),
            rs.getInt("pensioner_id"),
            rs.getString("submitted_name"),
            rs.getString("submitted_dob"),
            rs.getString("submitted_address"),
            rs.getString("submission_date"),
            rs.getInt("deadline_year")
        );
    }

    private MismatchFlag mapFlag(ResultSet rs) throws SQLException {
        MismatchFlag f = new MismatchFlag(
            rs.getInt("submission_id"),
            rs.getString("field_name"),
            rs.getDouble("confidence_score"),
            rs.getInt("flagged") == 1,
            rs.getString("recommendation")
        );
        f.setId(rs.getInt("id"));
        return f;
    }
}
