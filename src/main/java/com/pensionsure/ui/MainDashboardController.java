package com.pensionsure.ui;

import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Pensioner;
import com.pensionsure.repository.DatabaseManager;
import com.pensionsure.service.MatchingService;
import com.pensionsure.service.PensionerService;
import com.pensionsure.service.ReminderService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the main dashboard — the application's entry point view.
 *
 * Responsibilities:
 *  - Loads and displays the list of registered pensioners.
 *  - Shows per-pensioner info cards when one is selected.
 *  - Launches RecordEntryController for add/edit flows.
 *  - Launches MatchReportController to view the latest match results.
 *
 * All DB access goes through PensionerService, not directly to the repositories.
 * This keeps the controller thin: it handles UI events and delegates business logic.
 */
public class MainDashboardController {

    @FXML private ListView<Pensioner> pensionerListView;
    @FXML private Label selectedPensionerLabel;
    @FXML private Label ppoLabel;
    @FXML private Label typeLabel;
    @FXML private Label pdaLabel;
    @FXML private Label escalationLabel;
    @FXML private Label statusLabel;
    @FXML private Label statusBarLabel;
    @FXML private javafx.scene.layout.HBox summaryRow;
    @FXML private javafx.scene.layout.HBox actionButtonsRow;

    private PensionerService pensionerService;
    private MatchingService matchingService;
    private ReminderService reminderService;
    private Pensioner selectedPensioner;

    @FXML
    public void initialize() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            pensionerService = new PensionerService(conn);
            matchingService  = new MatchingService(conn);
            reminderService  = new ReminderService(conn);
            refreshPensionerList();

            // Show info cards when the user selects a pensioner from the list
            pensionerListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> onPensionerSelected(selected));

            // Custom cell renderer: show "Name (PPO: 12345)" in list
            pensionerListView.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Pensioner p, boolean empty) {
                    super.updateItem(p, empty);
                    if (empty || p == null) {
                        setText(null);
                    } else {
                        String ppo = (p.getPpoNumber() != null && !p.getPpoNumber().isBlank())
                            ? " (PPO: " + p.getPpoNumber() + ")" : "";
                        setText(p.getName() + ppo);
                    }
                }
            });

        } catch (SQLException e) {
            showError("Database initialisation failed: " + e.getMessage());
        }
    }

    // ── Event handlers ────────────────────────────────────────────────────

    @FXML
    private void onAddPensioner() {
        openRecordEntryWindow(null);
    }

    @FXML
    private void onEnterPDARecord() {
        if (selectedPensioner == null) return;
        openRecordEntryWindow(selectedPensioner);
    }

    @FXML
    private void onNewSubmission() {
        if (selectedPensioner == null) return;
        openSubmissionWindow(selectedPensioner);
    }

    @FXML
    private void onViewReport() {
        if (selectedPensioner == null) return;
        openMatchReportWindow(selectedPensioner);
    }

    @FXML
    private void onOpenHouseholdDashboard() {
        openHouseholdDashboardWindow(null);
    }

    @FXML
    private void onViewPensionerHousehold() {
        if (selectedPensioner == null) return;
        openHouseholdDashboardWindow(selectedPensioner.getHouseholdId());
    }

    // ── Helper: pensioner selection ───────────────────────────────────────

    private void onPensionerSelected(Pensioner p) {
        if (p == null) return;
        selectedPensioner = p;
        selectedPensionerLabel.setText("Selected: " + p.getName());
        ppoLabel.setText(p.getPpoNumber() != null && !p.getPpoNumber().isBlank() ? p.getPpoNumber() : "—");
        typeLabel.setText(p.getPensionType().name());

        // Try to load PDA record to show PDA name
        try {
            Optional<PDARecord> rec = pensionerService.getPDARecordForPensioner(p.getId());
            pdaLabel.setText(rec.map(PDARecord::getPdaName)
                                .filter(s -> s != null && !s.isBlank())
                                .orElse("Not entered yet"));

            // Check latest submission & escalation level
            int currentYear = java.time.LocalDate.now().getYear();
            var submissions = matchingService.getSubmissionsForPensioner(p.getId());
            boolean hasCurrentSubmission = submissions.stream().anyMatch(s -> s.getDeadlineYear() == currentYear);

            if (hasCurrentSubmission) {
                escalationLabel.setText("✓ Submitted (" + currentYear + ")");
                escalationLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
            } else {
                var level = ReminderService.calculateEscalation(java.time.LocalDate.now(), currentYear);
                escalationLabel.setText("⚠ " + level.name() + " (Pending)");
                switch (level) {
                    case URGENT  -> escalationLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    case WARNING -> escalationLabel.setStyle("-fx-text-fill: #f57f17; -fx-font-weight: bold;");
                    case INFO    -> escalationLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");
                }
            }
        } catch (SQLException e) {
            pdaLabel.setText("Error loading");
            escalationLabel.setText("—");
        }

        summaryRow.setVisible(true);
        summaryRow.setManaged(true);
        actionButtonsRow.setVisible(true);
        actionButtonsRow.setManaged(true);
        statusBarLabel.setText("Pensioner: " + p.getName());
    }

    // ── Window launchers ──────────────────────────────────────────────────

    private void openRecordEntryWindow(Pensioner existing) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/record_entry.fxml"));
            Parent root = loader.load();

            RecordEntryController ctrl = loader.getController();
            ctrl.setPensioner(existing);
            ctrl.setOnSaveCallback(this::refreshPensionerList);

            Stage stage = new Stage();
            stage.setTitle(existing == null ? "Add Pensioner" : "Edit PDA Record");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();

        } catch (IOException e) {
            showError("Could not open entry form: " + e.getMessage());
        }
    }

    private void openSubmissionWindow(Pensioner pensioner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/record_entry.fxml"));
            Parent root = loader.load();

            RecordEntryController ctrl = loader.getController();
            ctrl.setPensioner(pensioner);
            ctrl.setSubmissionMode(true);

            Stage stage = new Stage();
            stage.setTitle("New DLC Submission — " + pensioner.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();

            // After submitting, open the report immediately
            openMatchReportWindow(pensioner);

        } catch (IOException e) {
            showError("Could not open submission form: " + e.getMessage());
        }
    }

    private void openMatchReportWindow(Pensioner pensioner) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/match_report.fxml"));
            Parent root = loader.load();

            MatchReportController ctrl = loader.getController();
            ctrl.loadReportForPensioner(pensioner);

            Stage stage = new Stage();
            stage.setTitle("Match Report — " + pensioner.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();

        } catch (IOException e) {
            showError("Could not open match report: " + e.getMessage());
        }
    }

    private void openHouseholdDashboardWindow(Integer householdId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/household_dashboard.fxml"));
            Parent root = loader.load();

            HouseholdDashboardController ctrl = loader.getController();
            if (householdId != null) {
                ctrl.selectHouseholdById(householdId);
            }

            Stage stage = new Stage();
            stage.setTitle("PensionSure — Household Dashboard");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();

            refreshPensionerList();
            if (selectedPensioner != null) {
                onPensionerSelected(selectedPensioner);
            }

        } catch (IOException e) {
            showError("Could not open household dashboard: " + e.getMessage());
        }
    }

    // ── Data refresh ──────────────────────────────────────────────────────

    private void refreshPensionerList() {
        try {
            List<Pensioner> all = pensionerService.getAllPensioners();
            ObservableList<Pensioner> items = FXCollections.observableArrayList(all);
            pensionerListView.setItems(items);
            statusBarLabel.setText("Loaded " + all.size() + " pensioner(s).");
        } catch (SQLException e) {
            showError("Failed to load pensioners: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        statusLabel.setText("⚠ " + msg);
    }
}
