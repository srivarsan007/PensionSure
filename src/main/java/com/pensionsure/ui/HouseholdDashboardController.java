package com.pensionsure.ui;

import com.pensionsure.model.Household;
import com.pensionsure.model.MismatchFlag;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Pensioner;
import com.pensionsure.model.ReminderLog;
import com.pensionsure.model.ReminderLog.EscalationLevel;
import com.pensionsure.model.Submission;
import com.pensionsure.repository.DatabaseManager;
import com.pensionsure.service.MatchingService;
import com.pensionsure.service.PensionerService;
import com.pensionsure.service.ReminderService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for Module 4: Household Aggregation Dashboard.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PURPOSE & BEHAVIOR:
 * ───────────────────────────────────────────────────────────────────
 * Aggregates all pensioners residing in the same household into a single view.
 * Solves the real-world problem where families manage multiple pensioners'
 * DLC submissions simultaneously.
 *
 * For each pensioner in the household, this view aggregates:
 *  1. Demographics & PPO Number
 *  2. PDA on File status
 *  3. Latest DLC submission status
 *  4. Fuzzy Match Report outcome (using existing Phase 1 MismatchEngine output)
 *  5. Deadline Escalation Level (INFO / WARNING / URGENT) from Module 5
 * ═══════════════════════════════════════════════════════════════════
 */
public class HouseholdDashboardController {

    @FXML private ComboBox<Household> householdSelector;
    @FXML private Label headNameLabel;
    @FXML private Label totalMembersLabel;
    @FXML private Label submissionProgressLabel;
    @FXML private Label householdUrgencyLabel;
    @FXML private Label deadlineDaysLabel;
    @FXML private Label tableStatusLabel;
    @FXML private Label statusBarLabel;

    @FXML private TableView<HouseholdMemberRow> membersTable;
    @FXML private TableColumn<HouseholdMemberRow, String> nameCol;
    @FXML private TableColumn<HouseholdMemberRow, String> ppoCol;
    @FXML private TableColumn<HouseholdMemberRow, String> pensionTypeCol;
    @FXML private TableColumn<HouseholdMemberRow, String> pdaStatusCol;
    @FXML private TableColumn<HouseholdMemberRow, String> submissionCol;
    @FXML private TableColumn<HouseholdMemberRow, String> matchResultCol;
    @FXML private TableColumn<HouseholdMemberRow, String> escalationCol;

    @FXML private Button enterSubmissionBtn;
    @FXML private Button viewMatchReportBtn;

    private PensionerService pensionerService;
    private MatchingService matchingService;
    private ReminderService reminderService;

    private Household currentHousehold;

    @FXML
    public void initialize() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            pensionerService = new PensionerService(conn);
            matchingService  = new MatchingService(conn);
            reminderService  = new ReminderService(conn);

            setupTableColumns();
            refreshHouseholdList();

            householdSelector.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        loadHousehold(newVal);
                    }
                }
            );

            // Calculate cutoff info
            long daysLeft = ReminderService.getDaysRemaining(LocalDate.now(), LocalDate.now().getYear());
            deadlineDaysLabel.setText(daysLeft >= 0 ? daysLeft + " days (Nov 30)" : "Past Deadline");

        } catch (SQLException e) {
            statusBarLabel.setText("Database error: " + e.getMessage());
        }
    }

    private void setupTableColumns() {
        nameCol.setCellValueFactory(cd -> cd.getValue().name);
        ppoCol.setCellValueFactory(cd -> cd.getValue().ppo);
        pensionTypeCol.setCellValueFactory(cd -> cd.getValue().pensionType);
        pdaStatusCol.setCellValueFactory(cd -> cd.getValue().pdaStatus);
        submissionCol.setCellValueFactory(cd -> cd.getValue().submissionStatus);
        matchResultCol.setCellValueFactory(cd -> cd.getValue().matchResult);
        escalationCol.setCellValueFactory(cd -> cd.getValue().escalationStatus);

        // Colour-code match result
        matchResultCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("✓")) {
                        setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                    } else if (item.startsWith("⚠")) {
                        setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #7a8ab0;");
                    }
                }
            }
        });

        // Style escalation badges
        escalationCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    getStyleClass().removeAll("escalation-info", "escalation-warning", "escalation-urgent", "escalation-done");
                    switch (item) {
                        case "SUBMITTED" -> getStyleClass().add("escalation-done");
                        case "INFO"      -> getStyleClass().add("escalation-info");
                        case "WARNING"   -> getStyleClass().add("escalation-warning");
                        case "URGENT"    -> getStyleClass().add("escalation-urgent");
                        default          -> setStyle("");
                    }
                }
            }
        });
    }

    public void selectHouseholdById(int householdId) {
        for (Household h : householdSelector.getItems()) {
            if (h.getId() == householdId) {
                householdSelector.getSelectionModel().select(h);
                break;
            }
        }
    }

    private void refreshHouseholdList() throws SQLException {
        List<Household> households = pensionerService.getAllHouseholds();
        householdSelector.setItems(FXCollections.observableArrayList(households));
        householdSelector.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Household h) {
                return h == null ? "" : h.getHeadOfHouseholdName() + " (ID: " + h.getId() + ")";
            }
            @Override public Household fromString(String s) { return null; }
        });

        if (!households.isEmpty() && householdSelector.getSelectionModel().isEmpty()) {
            householdSelector.getSelectionModel().selectFirst();
        }
    }

    public void loadHousehold(Household household) {
        this.currentHousehold = household;
        headNameLabel.setText(household.getHeadOfHouseholdName());

        try {
            List<Pensioner> members = pensionerService.getPensionersByHousehold(household.getId());
            totalMembersLabel.setText(String.valueOf(members.size()));

            List<HouseholdMemberRow> rows = new ArrayList<>();
            int submittedCount = 0;
            int currentYear = LocalDate.now().getYear();
            EscalationLevel highestUrgency = null;

            for (Pensioner p : members) {
                Optional<PDARecord> pdaOpt = pensionerService.getPDARecordForPensioner(p.getId());
                List<Submission> submissions = matchingService.getSubmissionsForPensioner(p.getId());

                String pdaStatus = pdaOpt.map(PDARecord::getPdaName).filter(s -> !s.isBlank()).orElse("⚠ Not Entered");

                String submissionStatus;
                String matchResult;
                String escalationStatus;

                Optional<Submission> latestSubOpt = submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.get(0));

                if (latestSubOpt.isPresent()) {
                    Submission s = latestSubOpt.get();
                    submissionStatus = s.getSubmissionDate() + " (" + s.getDeadlineYear() + ")";
                    if (s.getDeadlineYear() == currentYear) {
                        submittedCount++;
                    }

                    // Load match flags from existing Phase 1 output
                    List<MismatchFlag> flags = matchingService.loadFlagsForSubmission(s.getId());
                    if (flags.isEmpty()) {
                        matchResult = "✓ Ready (100%)";
                    } else {
                        long mismatchCount = flags.stream().filter(MismatchFlag::isFlagged).count();
                        double avgScore = flags.stream().mapToDouble(MismatchFlag::getConfidenceScore).average().orElse(1.0);
                        if (mismatchCount == 0) {
                            matchResult = String.format("✓ Ready (%.0f%%)", avgScore * 100);
                        } else {
                            matchResult = String.format("⚠ %d Flag(s) (%.0f%%)", mismatchCount, avgScore * 100);
                        }
                    }
                    escalationStatus = "SUBMITTED";
                } else {
                    submissionStatus = "Not Submitted";
                    matchResult = "—";
                    EscalationLevel level = ReminderService.calculateEscalation(LocalDate.now(), currentYear);
                    escalationStatus = level.name();

                    if (highestUrgency == null || isHigherUrgency(level, highestUrgency)) {
                        highestUrgency = level;
                    }
                }

                rows.add(new HouseholdMemberRow(p, p.getName(), p.getPpoNumber(), p.getPensionType().name(),
                    pdaStatus, submissionStatus, matchResult, escalationStatus));
            }

            membersTable.setItems(FXCollections.observableArrayList(rows));
            submissionProgressLabel.setText(submittedCount + " / " + members.size());

            if (members.isEmpty()) {
                householdUrgencyLabel.setText("No Members");
                householdUrgencyLabel.setStyle("-fx-text-fill: #7a8ab0;");
            } else if (submittedCount == members.size()) {
                householdUrgencyLabel.setText("✓ All Submitted");
                householdUrgencyLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
            } else if (highestUrgency != null) {
                householdUrgencyLabel.setText("⚠ " + highestUrgency.name());
                switch (highestUrgency) {
                    case URGENT  -> householdUrgencyLabel.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
                    case WARNING -> householdUrgencyLabel.setStyle("-fx-text-fill: #f57f17; -fx-font-weight: bold;");
                    case INFO    -> householdUrgencyLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-weight: bold;");
                }
            }

            statusBarLabel.setText("Loaded " + members.size() + " member(s) for household '" + household.getHeadOfHouseholdName() + "'.");

        } catch (SQLException e) {
            statusBarLabel.setText("Error loading household members: " + e.getMessage());
        }
    }

    private boolean isHigherUrgency(EscalationLevel a, EscalationLevel b) {
        return a.ordinal() > b.ordinal(); // INFO (0) < WARNING (1) < URGENT (2)
    }

    // ── FXML Actions ──────────────────────────────────────────────────────

    @FXML
    private void onBackToMain() {
        Stage stage = (Stage) householdSelector.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void onCreateHousehold() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Household");
        dialog.setHeaderText("Create a new household group");
        dialog.setContentText("Head of Household Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                try {
                    Household newHh = pensionerService.createHousehold(name.trim());
                    refreshHouseholdList();
                    householdSelector.getSelectionModel().select(newHh);
                } catch (SQLException e) {
                    statusBarLabel.setText("Could not create household: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void onAddMember() {
        if (currentHousehold == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/record_entry.fxml"));
            Parent root = loader.load();

            RecordEntryController ctrl = loader.getController();
            ctrl.setOnSaveCallback(() -> loadHousehold(currentHousehold));

            Stage stage = new Stage();
            stage.setTitle("Add Member to Household — " + currentHousehold.getHeadOfHouseholdName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();

            loadHousehold(currentHousehold);
        } catch (IOException e) {
            statusBarLabel.setText("Failed to open add member form: " + e.getMessage());
        }
    }

    @FXML
    private void onRunDeadlineCheckNow() {
        try {
            int year = LocalDate.now().getYear();
            List<ReminderLog> logs = reminderService.checkAndLogReminders(year);
            statusBarLabel.setText("⚡ Deadline check completed. " + logs.size() + " reminder(s) recorded in audit log.");
            if (currentHousehold != null) {
                loadHousehold(currentHousehold);
            }
        } catch (SQLException e) {
            statusBarLabel.setText("Failed to run deadline check: " + e.getMessage());
        }
    }

    @FXML
    private void onEnterSubmissionForSelected() {
        HouseholdMemberRow selected = membersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusBarLabel.setText("Please select a pensioner from the table first.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/record_entry.fxml"));
            Parent root = loader.load();

            RecordEntryController ctrl = loader.getController();
            ctrl.setPensioner(selected.pensioner);
            ctrl.setSubmissionMode(true);
            ctrl.setOnSaveCallback(() -> loadHousehold(currentHousehold));

            Stage stage = new Stage();
            stage.setTitle("DLC Submission — " + selected.pensioner.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();

            loadHousehold(currentHousehold);
        } catch (IOException e) {
            statusBarLabel.setText("Could not open submission form: " + e.getMessage());
        }
    }

    @FXML
    private void onViewMatchReportForSelected() {
        HouseholdMemberRow selected = membersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusBarLabel.setText("Please select a pensioner from the table first.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/match_report.fxml"));
            Parent root = loader.load();

            MatchReportController ctrl = loader.getController();
            ctrl.loadReportForPensioner(selected.pensioner);

            Stage stage = new Stage();
            stage.setTitle("Match Report — " + selected.pensioner.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.getScene().getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.showAndWait();
        } catch (IOException e) {
            statusBarLabel.setText("Could not open match report: " + e.getMessage());
        }
    }

    // ── Row Data Model for TableView ──────────────────────────────────────

    public static class HouseholdMemberRow {
        public final Pensioner pensioner;
        public final SimpleStringProperty name;
        public final SimpleStringProperty ppo;
        public final SimpleStringProperty pensionType;
        public final SimpleStringProperty pdaStatus;
        public final SimpleStringProperty submissionStatus;
        public final SimpleStringProperty matchResult;
        public final SimpleStringProperty escalationStatus;

        public HouseholdMemberRow(Pensioner pensioner, String name, String ppo, String pensionType,
                                  String pdaStatus, String submissionStatus, String matchResult, String escalationStatus) {
            this.pensioner = pensioner;
            this.name = new SimpleStringProperty(name != null ? name : "—");
            this.ppo = new SimpleStringProperty(ppo != null && !ppo.isBlank() ? ppo : "—");
            this.pensionType = new SimpleStringProperty(pensionType != null ? pensionType : "—");
            this.pdaStatus = new SimpleStringProperty(pdaStatus != null ? pdaStatus : "—");
            this.submissionStatus = new SimpleStringProperty(submissionStatus != null ? submissionStatus : "—");
            this.matchResult = new SimpleStringProperty(matchResult != null ? matchResult : "—");
            this.escalationStatus = new SimpleStringProperty(escalationStatus != null ? escalationStatus : "—");
        }
    }
}
