package com.pensionsure;

import com.pensionsure.repository.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.SQLException;

/**
 * Application entry point.
 *
 * JavaFX applications must extend Application and implement start().
 * The launch() call in main() bootstraps the JavaFX runtime, then calls start()
 * on the FX Application Thread.
 *
 * Initialisation order:
 *  1. DatabaseManager initialises the SQLite connection and runs schema migrations.
 *  2. The main_dashboard FXML is loaded, which instantiates MainDashboardController.
 *  3. MainDashboardController.initialize() completes pensioner list loading.
 *
 * WHY initialise the DB here (before FXML) rather than lazily inside controllers?
 * If the DB file is locked, missing, or the schema migration fails, we want to fail
 * fast with a clear error before any UI is shown, rather than crashing mid-interaction.
 */
public class Main extends Application {

    private com.pensionsure.scheduler.ReminderScheduler reminderScheduler;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Pre-initialise DB so schema is ready before any controller calls it
        try {
            DatabaseManager.getInstance();
        } catch (SQLException e) {
            showFatalError("Could not initialise database: " + e.getMessage());
            return;
        }

        // Start background deadline reminder scheduler (runs check immediately on start + periodic)
        reminderScheduler = new com.pensionsure.scheduler.ReminderScheduler();
        reminderScheduler.start();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_dashboard.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setTitle("PensionSure — DLC Readiness Assistant");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(820);
        primaryStage.setMinHeight(500);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        // Gracefully terminate the background reminder scheduler
        if (reminderScheduler != null) {
            reminderScheduler.stop();
        }

        // Gracefully close the SQLite connection when the window is closed
        try {
            DatabaseManager.getInstance().close();
        } catch (SQLException ignored) {}
    }

    private void showFatalError(String msg) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("PensionSure — Fatal Error");
        alert.setHeaderText("Application cannot start");
        alert.setContentText(msg);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
