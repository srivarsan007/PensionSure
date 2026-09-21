package com.pensionsure.ui;

import com.pensionsure.exception.DuplicatePensionerException;
import com.pensionsure.exception.IncompleteRecordException;
import com.pensionsure.matching.MatchReport;
import com.pensionsure.model.Household;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Pensioner;
import com.pensionsure.model.Pensioner.PensionType;
import com.pensionsure.model.Submission;
import com.pensionsure.repository.DatabaseManager;
import com.pensionsure.service.MatchingService;
import com.pensionsure.service.PensionerService;
import com.pensionsure.util.DateFormatUtils;
import com.pensionsure.exception.InvalidDateFormatException;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller for the three-tab record entry form.
 *
 * Can be operated in two modes:
 *  1. NEW PENSIONER mode (pensioner == null): all three tabs active; saves pensioner,
 *     PDA record, and submission in sequence.
 *  2. SUBMISSION ONLY mode (pensionerMode + submissionMode): only Tab 3 is active;
 *     assumes pensioner and PDA record already exist. Used when re-submitting for
 *     a new deadline year.
 *
 * WHY keep all three tabs in one controller?
 * The three datasets are entered in the same user workflow (one sitting). Splitting
 * them into separate forms would require more window management and more state passing.
 * Tabbed progression keeps the flow linear while tab 3 remains the always-accessible
 * "quick submission" entry for returning pensioners.
 */
public class RecordEntryController {

    // ── Tab 1: Pensioner details ──────────────────────────────────────────
    @FXML private ComboBox<Household> householdCombo;
    @FXML private TextField nameField;
    @FXML private TextField dobField;
    @FXML private ComboBox<PensionType> pensionTypeCombo;
    @FXML private TextField ppoField;

    // ── Tab 2: PDA record ─────────────────────────────────────────────────
    @FXML private TextField pdaNameField;
    @FXML private TextField pdaNameOnFileField;
    @FXML private TextField pdaDobOnFileField;
    @FXML private TextArea  pdaAddressField;

    // ── Tab 3: DLC submission ─────────────────────────────────────────────
    @FXML private TextField submittedNameField;
    @FXML private TextField submittedDobField;
    @FXML private TextArea  submittedAddressField;
    @FXML private TextField deadlineYearField;

    // ── Common ────────────────────────────────────────────────────────────
    @FXML private Label validationLabel;
    @FXML private TabPane mainTabPane;
    @FXML private Label formSubtitle;

    private Pensioner pensioner;       // null for new, non-null for returning
    private boolean submissionMode;    // true = skip tabs 1 & 2
    private Runnable onSaveCallback;

    private PensionerService pensionerService;
    private MatchingService  matchingService;

    @FXML
    public void initialize() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            pensionerService = new PensionerService(conn);
            matchingService  = new MatchingService(conn);

            // Populate pension type choices from the enum
            pensionTypeCombo.setItems(FXCollections.observableArrayList(PensionType.values()));

            // Load existing households into dropdown
            refreshHouseholdCombo();

            // Default deadline year to current year
            deadlineYearField.setText(String.valueOf(LocalDate.now().getYear()));

        } catch (SQLException e) {
            setValidationError("DB error during initialisation: " + e.getMessage());
        }
    }

    // ── Called by MainDashboardController before showing this window ──────

    public void setPensioner(Pensioner p) {
        this.pensioner = p;
        if (p != null) {
            // Pre-fill pensioner fields for editing context
            nameField.setText(p.getName());
            dobField.setText(p.getDob());
            pensionTypeCombo.setValue(p.getPensionType());
            ppoField.setText(p.getPpoNumber());
            submittedNameField.setText(p.getName());
            submittedDobField.setText(p.getDob());
            formSubtitle.setText("Editing: " + p.getName());
        }
    }

    public void setSubmissionMode(boolean mode) {
        this.submissionMode = mode;
        if (mode && mainTabPane != null) {
            // Jump directly to the submission tab for returning pensioners
            mainTabPane.getSelectionModel().select(2);
            formSubtitle.setText("New DLC Submission for: " +
                (pensioner != null ? pensioner.getName() : ""));
        }
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }

    // ── FXML event handlers ───────────────────────────────────────────────

    @FXML
    private void onCreateHousehold() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Household");
        dialog.setHeaderText("Enter head-of-household name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                try {
                    pensionerService.createHousehold(name.trim());
                    refreshHouseholdCombo();
                } catch (SQLException e) {
                    setValidationError("Could not create household: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void onBack() {
        closeWindow();
    }

    /**
     * Main save action — saves pensioner → PDA record → submission in order,
     * then triggers the match engine and closes this window.
     *
     * Each step can fail with a domain exception, which is caught and displayed
     * as a validation message (not a crash dialog). This is the real invalid-input
     * path that {@link IncompleteRecordException} was designed for.
     */
    @FXML
    private void onSaveAndRunMatch() {
        clearValidationError();
        try {
            // ── Step 1: Ensure pensioner exists ──────────────────────────
            if (pensioner == null) {
                pensioner = buildAndSavePensioner();
                if (pensioner == null) return; // validation already showed error
            }

            // ── Step 2: Save PDA record if filled in ─────────────────────
            String pdaName    = pdaNameField.getText().trim();
            String nameOnFile = pdaNameOnFileField.getText().trim();
            String dobOnFile  = pdaDobOnFileField.getText().trim();
            String addrOnFile = pdaAddressField.getText().trim();

            if (!nameOnFile.isBlank() || !dobOnFile.isBlank() || !addrOnFile.isBlank()) {
                PDARecord rec = new PDARecord();
                rec.setPensionerId(pensioner.getId());
                rec.setPdaName(pdaName);
                rec.setNameOnFile(nameOnFile);
                rec.setDobOnFile(normaliseDobOrRaw(dobOnFile));
                rec.setAddressOnFile(addrOnFile);
                pensionerService.savePDARecord(rec);
            }

            // ── Step 3: Build and match the submission ────────────────────
            String submName = submittedNameField.getText().trim();
            String submDob  = submittedDobField.getText().trim();
            String submAddr = submittedAddressField.getText().trim();
            String yearStr  = deadlineYearField.getText().trim();

            if (submName.isBlank() && submDob.isBlank() && submAddr.isBlank()) {
                // User didn't fill in Tab 3 — save pensioner/PDA only, no match run
                if (onSaveCallback != null) onSaveCallback.run();
                closeWindow();
                return;
            }

            if (yearStr.isBlank()) {
                setValidationError("Deadline year is required.");
                mainTabPane.getSelectionModel().select(2);
                return;
            }

            int deadlineYear;
            try {
                deadlineYear = Integer.parseInt(yearStr);
            } catch (NumberFormatException e) {
                setValidationError("Deadline year must be a 4-digit year (e.g. 2024).");
                return;
            }

            Submission submission = new Submission();
            submission.setPensionerId(pensioner.getId());
            submission.setSubmittedName(submName);
            submission.setSubmittedDob(normaliseDobOrRaw(submDob));
            submission.setSubmittedAddress(submAddr);
            submission.setDeadlineYear(deadlineYear);

            // matchingService.submitAndAnalyse validates, persists, and runs the engine
            MatchReport report = matchingService.submitAndAnalyse(submission);

            if (onSaveCallback != null) onSaveCallback.run();

            // Show a quick summary in the validation label before closing
            String summary = report.hasAnyMismatch()
                ? String.format("⚠ %d field(s) flagged. Check the Match Report.",
                    report.getMismatchCount())
                : "✓ All fields matched. Submission looks good.";
            validationLabel.setText(summary);

            // Give the user a moment to read it, then close
            new Thread(() -> {
                try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
                javafx.application.Platform.runLater(this::closeWindow);
            }).start();

        } catch (IncompleteRecordException e) {
            setValidationError("Required field missing: " + e.getMissingField());
        } catch (DuplicatePensionerException e) {
            setValidationError(e.getMessage());
        } catch (IllegalStateException e) {
            setValidationError(e.getMessage() +
                " — Fill in the PDA Record (Tab 2) first.");
            mainTabPane.getSelectionModel().select(1);
        } catch (SQLException e) {
            setValidationError("Database error: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private Pensioner buildAndSavePensioner() throws SQLException,
            IncompleteRecordException, DuplicatePensionerException {
        Household hh = householdCombo.getValue();
        if (hh == null) {
            setValidationError("Please select or create a household first.");
            mainTabPane.getSelectionModel().select(0);
            return null;
        }
        PensionType type = pensionTypeCombo.getValue();
        if (type == null) {
            setValidationError("Please select a pension type.");
            mainTabPane.getSelectionModel().select(0);
            return null;
        }

        String dob = dobField.getText().trim();
        String normalDob = normaliseDobOrRaw(dob);

        Pensioner p = new Pensioner();
        p.setHouseholdId(hh.getId());
        p.setName(nameField.getText().trim());
        p.setDob(normalDob);
        p.setPensionType(type);
        p.setPpoNumber(ppoField.getText().trim());
        return pensionerService.registerPensioner(p);
    }

    private String normaliseDobOrRaw(String dob) {
        if (dob == null || dob.isBlank()) return dob;
        try {
            return DateFormatUtils.normalise(dob);
        } catch (InvalidDateFormatException e) {
            // Return raw; DateMatchStrategy will handle the unparseable case
            return dob;
        }
    }

    private void refreshHouseholdCombo() throws SQLException {
        List<Household> households = pensionerService.getAllHouseholds();
        householdCombo.setItems(FXCollections.observableArrayList(households));
        householdCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Household h) {
                return h == null ? "" : h.getHeadOfHouseholdName();
            }
            @Override public Household fromString(String s) { return null; }
        });
    }

    private void setValidationError(String msg) {
        validationLabel.setText("⚠ " + msg);
    }

    private void clearValidationError() {
        validationLabel.setText("");
    }

    private void closeWindow() {
        Stage stage = (Stage) validationLabel.getScene().getWindow();
        stage.close();
    }
}
