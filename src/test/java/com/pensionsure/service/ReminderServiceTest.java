package com.pensionsure.service;

import com.pensionsure.model.ReminderLog.EscalationLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ReminderService} deadline calculation and escalation rules.
 *
 * ═══════════════════════════════════════════════════════════════════
 * SPECIFICATION (Blueprint §6):
 * ───────────────────────────────────────────────────────────────────
 * Annual DLC Submission Deadline is November 30.
 * For unsubmitted pensioners:
 *   - > 14 days remaining → INFO
 *   - 3 to 14 days remaining → WARNING
 *   - < 3 days remaining (including deadline day and overdue) → URGENT
 *
 * These tests evaluate PURE LOGIC with deterministic fixed dates — zero I/O,
 * no threads or scheduler runtime required.
 * ═══════════════════════════════════════════════════════════════════
 */
class ReminderServiceTest {

    private static final int DEADLINE_YEAR = 2024; // Target: 2024-11-30

    @Test
    @DisplayName("Escalation: More than 14 days remaining returns INFO")
    void testEscalationInfo_moreThan14Days() {
        // 2024-11-01 is 29 days before Nov 30
        LocalDate checkDate = LocalDate.of(2024, 11, 1);
        EscalationLevel level = ReminderService.calculateEscalation(checkDate, DEADLINE_YEAR);
        assertEquals(EscalationLevel.INFO, level, "29 days remaining should be INFO");

        // 2024-11-15 is exactly 15 days before Nov 30 (> 14)
        LocalDate boundaryDate = LocalDate.of(2024, 11, 15);
        EscalationLevel boundaryLevel = ReminderService.calculateEscalation(boundaryDate, DEADLINE_YEAR);
        assertEquals(EscalationLevel.INFO, boundaryLevel, "15 days remaining should be INFO");
    }

    @Test
    @DisplayName("Escalation: 14 to 3 days remaining returns WARNING")
    void testEscalationWarning_between14And3Days() {
        // 2024-11-16 is exactly 14 days before Nov 30
        LocalDate upperBoundary = LocalDate.of(2024, 11, 16);
        EscalationLevel upperLevel = ReminderService.calculateEscalation(upperBoundary, DEADLINE_YEAR);
        assertEquals(EscalationLevel.WARNING, upperLevel, "14 days remaining should be WARNING");

        // 2024-11-20 is 10 days before Nov 30
        LocalDate midDate = LocalDate.of(2024, 11, 20);
        EscalationLevel midLevel = ReminderService.calculateEscalation(midDate, DEADLINE_YEAR);
        assertEquals(EscalationLevel.WARNING, midLevel, "10 days remaining should be WARNING");

        // 2024-11-27 is exactly 3 days before Nov 30
        LocalDate lowerBoundary = LocalDate.of(2024, 11, 27);
        EscalationLevel lowerLevel = ReminderService.calculateEscalation(lowerBoundary, DEADLINE_YEAR);
        assertEquals(EscalationLevel.WARNING, lowerLevel, "3 days remaining should be WARNING");
    }

    @Test
    @DisplayName("Escalation: Fewer than 3 days remaining, deadline day, and overdue return URGENT")
    void testEscalationUrgent_fewerThan3DaysAndPast() {
        // 2024-11-28 is 2 days before Nov 30 (< 3)
        LocalDate twoDaysLeft = LocalDate.of(2024, 11, 28);
        assertEquals(EscalationLevel.URGENT, ReminderService.calculateEscalation(twoDaysLeft, DEADLINE_YEAR),
            "2 days remaining should be URGENT");

        // 2024-11-29 is 1 day before Nov 30
        LocalDate oneDayLeft = LocalDate.of(2024, 11, 29);
        assertEquals(EscalationLevel.URGENT, ReminderService.calculateEscalation(oneDayLeft, DEADLINE_YEAR),
            "1 day remaining should be URGENT");

        // 2024-11-30 is deadline day (0 days left)
        LocalDate deadlineDay = LocalDate.of(2024, 11, 30);
        assertEquals(EscalationLevel.URGENT, ReminderService.calculateEscalation(deadlineDay, DEADLINE_YEAR),
            "Deadline day (0 days) should be URGENT");

        // 2024-12-01 is past deadline (-1 days)
        LocalDate overdue = LocalDate.of(2024, 12, 1);
        assertEquals(EscalationLevel.URGENT, ReminderService.calculateEscalation(overdue, DEADLINE_YEAR),
            "Past deadline should remain URGENT");
    }

    @ParameterizedTest(name = "Date {0} against Nov 30 {1} -> Expected days: {2}")
    @CsvSource({
        "2024-11-01, 2024, 29",
        "2024-11-16, 2024, 14",
        "2024-11-27, 2024, 3",
        "2024-11-30, 2024, 0",
        "2024-12-01, 2024, -1"
    })
    @DisplayName("Days remaining calculation is mathematically exact")
    void testDaysRemainingCalculation(String dateStr, int year, long expectedDays) {
        LocalDate date = LocalDate.parse(dateStr);
        long days = ReminderService.getDaysRemaining(date, year);
        assertEquals(expectedDays, days);
    }
}
