package com.pensionsure.ui;

import com.pensionsure.model.MismatchFlag;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Pensioner;
import com.pensionsure.model.Submission;
import com.pensionsure.repository.DatabaseManager;
import com.pensionsure.service.MatchingService;
import com.pensionsure.service.PensionerService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the match report screen.
 *
 * Displays the result of running MismatchEngine over the pensioner's latest
 * submission vs. their PDA record. Each row in the table represents one field
 * (name / dob / address) with its confidence score, flag status, and recommendation.
 *
 * The controller is read-only — it loads and displays data but does not modify it.
 * All the analytical work has already been done (and persisted) by MatchingService.
 */
public class MatchReportController {

    @FXML private Label   reportSubtitle;
    @FXML private Label   overallScoreLabel;
    @FXML private Label   flagCountLabel;
    @FXML private Label   verdictLabel;

    @FXML private TableView<ReportRow>             flagsTable;
    @FXML private TableColumn<ReportRow, String>   fieldCol;
    @FXML private TableColumn<ReportRow, String>   onFileCol;
    @FXML private TableColumn<ReportRow, String>   submittedCol;
    @FXML private TableColumn<ReportRow, String>   scoreCol;
    @FXML private TableColumn<ReportRow, String>   flaggedCol;
    @FXML private TableColumn<ReportRow, String>   recommendCol;

    private PensionerService pensionerService;
    private MatchingService  matchingService;
    private Pensioner        currentPensioner;

    @FXML
    public void initialize() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            pensionerService = new PensionerService(conn);
            matchingService  = new MatchingService(conn);
        } catch (SQLException e) {
            verdictLabel.setText("DB error: " + e.getMessage());
        }

        // ── Bind table columns to ReportRow properties ──────────────────
        fieldCol    .setCellValueFactory(cd -> cd.getValue().fieldName);
        onFileCol   .setCellValueFactory(cd -> cd.getValue().onFile);
        submittedCol.setCellValueFactory(cd -> cd.getValue().submitted);
        scoreCol    .setCellValueFactory(cd -> cd.getValue().score);
        flaggedCol  .setCellValueFactory(cd -> cd.getValue().status);
        recommendCol.setCellValueFactory(cd -> cd.getValue().recommendation);

        // Colour-code the Status cell: green for OK, red for FLAGGED
        flaggedCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("✓")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Wrap recommendation text
        recommendCol.setCellFactory(col -> new TableCell<>() {
            { setWrapText(true); setMaxHeight(Double.MAX_VALUE); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
    }

    /**
     * Loads the latest submission and its persisted flags for the given pensioner,
     * then populates the report table.
     * Called by MainDashboardController before the window is shown.
     */
    public void loadReportForPensioner(Pensioner pensioner) {
        this.currentPensioner = pensioner;
        reportSubtitle.setText("Pensioner: " + pensioner.getName());

        try {
            // Load the most recent submission
            List<Submission> submissions =
                matchingService.getSubmissionsForPensioner(pensioner.getId());

            if (submissions.isEmpty()) {
                verdictLabel.setText("No submissions found. Please enter a DLC submission draft first.");
                overallScoreLabel.setText("—");
                flagCountLabel.setText("—");
                return;
            }

            Submission latest = submissions.get(0);

            // Load stored flags (these were persisted by MatchingService at submission time)
            List<MismatchFlag> flags =
                matchingService.loadFlagsForSubmission(latest.getId());

            // Load corresponding PDA record for display
            Optional<PDARecord> pdaOpt =
                pensionerService.getPDARecordForPensioner(pensioner.getId());
            PDARecord pda = pdaOpt.orElse(null);

            // Build display rows
            List<ReportRow> rows = flags.stream().map(f -> {
                String onFile    = resolveOnFile(f.getFieldName(), pda);
                String submitted = resolveSubmitted(f.getFieldName(), latest);
                String score     = String.format("%.1f%%", f.getConfidenceScore() * 100);
                String status    = f.isFlagged() ? "⚠ FLAGGED" : "✓ OK";
                return new ReportRow(
                    capitalise(f.getFieldName()),
                    onFile, submitted, score, status, f.getRecommendation()
                );
            }).toList();

            flagsTable.setItems(FXCollections.observableArrayList(rows));

            // Compute overall stats
            double avg = flags.isEmpty() ? 1.0 :
                flags.stream().mapToDouble(MismatchFlag::getConfidenceScore).average().orElse(1.0);
            long flagged = flags.stream().filter(MismatchFlag::isFlagged).count();

            overallScoreLabel.setText(String.format("%.1f%%", avg * 100));
            flagCountLabel.setText(flagged + " / " + flags.size());

            if (flagged == 0) {
                verdictLabel.setText("✓ All fields match — submission looks ready.");
                verdictLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                verdictLabel.setText("⚠ " + flagged + " mismatch(es) detected — review before submitting.");
                verdictLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
            }

        } catch (SQLException e) {
            verdictLabel.setText("Error loading report: " + e.getMessage());
        }
    }

    // ── FXML event handlers ───────────────────────────────────────────────

    @FXML
    private void onBack() {
        closeWindow();
    }

    @FXML
    private void onNewSubmission() {
        closeWindow();
        // MainDashboardController handles relaunching the submission flow
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String resolveOnFile(String fieldName, PDARecord pda) {
        if (pda == null) return "(no PDA record)";
        return switch (fieldName) {
            case "name"    -> pda.getNameOnFile();
            case "dob"     -> pda.getDobOnFile();
            case "address" -> pda.getAddressOnFile();
            default        -> "—";
        };
    }

    private String resolveSubmitted(String fieldName, Submission s) {
        return switch (fieldName) {
            case "name"    -> s.getSubmittedName();
            case "dob"     -> s.getSubmittedDob();
            case "address" -> s.getSubmittedAddress();
            default        -> "—";
        };
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void closeWindow() {
        Stage stage = (Stage) flagsTable.getScene().getWindow();
        stage.close();
    }

    // ── Inner data class for TableView rows ───────────────────────────────

    /**
     * Simple property-backed row model for the TableView.
     * SimpleStringProperty lets JavaFX bind and update cells automatically.
     */
    static class ReportRow {
        final SimpleStringProperty fieldName;
        final SimpleStringProperty onFile;
        final SimpleStringProperty submitted;
        final SimpleStringProperty score;
        final SimpleStringProperty status;
        final SimpleStringProperty recommendation;

        ReportRow(String fieldName, String onFile, String submitted,
                  String score, String status, String recommendation) {
            this.fieldName      = new SimpleStringProperty(fieldName);
            this.onFile         = new SimpleStringProperty(onFile != null ? onFile : "—");
            this.submitted      = new SimpleStringProperty(submitted != null ? submitted : "—");
            this.score          = new SimpleStringProperty(score);
            this.status         = new SimpleStringProperty(status);
            this.recommendation = new SimpleStringProperty(recommendation != null ? recommendation : "");
        }
    }
}
