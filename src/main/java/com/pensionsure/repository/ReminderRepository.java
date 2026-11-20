package com.pensionsure.repository;

import com.pensionsure.model.ReminderLog;
import com.pensionsure.model.ReminderLog.EscalationLevel;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC DAO for the {@link ReminderLog} entity.
 *
 * Tracks every scheduled reminder evaluation run by {@link com.pensionsure.scheduler.ReminderScheduler}.
 * All operations use prepared statements to ensure parameter safety.
 */
public class ReminderRepository {

    private final Connection conn;

    public ReminderRepository(Connection conn) {
        this.conn = conn;
    }

    // ---- Write operations ------------------------------------------------

    public ReminderLog save(ReminderLog log) throws SQLException {
        String sql = "INSERT INTO reminder_log (pensioner_id, reminder_date, escalation_level, sent) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, log.getPensionerId());
            ps.setString(2, log.getReminderDate());
            ps.setString(3, log.getEscalationLevel().name());
            ps.setInt(4, log.isSent() ? 1 : 0);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) log.setId(keys.getInt(1));
            }
        }
        return log;
    }

    // ---- Read operations -------------------------------------------------

    public Optional<ReminderLog> findLatestByPensionerId(int pensionerId) throws SQLException {
        String sql = "SELECT id, pensioner_id, reminder_date, escalation_level, sent " +
                     "FROM reminder_log WHERE pensioner_id = ? ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pensionerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public List<ReminderLog> findAllByPensionerId(int pensionerId) throws SQLException {
        List<ReminderLog> logs = new ArrayList<>();
        String sql = "SELECT id, pensioner_id, reminder_date, escalation_level, sent " +
                     "FROM reminder_log WHERE pensioner_id = ? ORDER BY id DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pensionerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) logs.add(map(rs));
            }
        }
        return logs;
    }

    public List<ReminderLog> findAll() throws SQLException {
        List<ReminderLog> logs = new ArrayList<>();
        String sql = "SELECT id, pensioner_id, reminder_date, escalation_level, sent " +
                     "FROM reminder_log ORDER BY id DESC";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) logs.add(map(rs));
        }
        return logs;
    }

    // ---- Mapping ---------------------------------------------------------

    private ReminderLog map(ResultSet rs) throws SQLException {
        ReminderLog log = new ReminderLog(
            rs.getInt("pensioner_id"),
            rs.getString("reminder_date"),
            EscalationLevel.valueOf(rs.getString("escalation_level")),
            rs.getInt("sent") == 1
        );
        log.setId(rs.getInt("id"));
        return log;
    }
}
