package com.pensionsure.ui;

import com.pensionsure.matching.MatchReport;
import com.pensionsure.matching.MismatchEngine;
import com.pensionsure.model.Household;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Pensioner;
import com.pensionsure.model.Pensioner.PensionType;
import com.pensionsure.model.Submission;
import com.pensionsure.repository.DatabaseManager;
import com.pensionsure.repository.PDARecordRepository;
import com.pensionsure.repository.PensionerRepository;
import com.pensionsure.repository.SubmissionRepository;
import com.pensionsure.service.PensionerService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;

/**
 * Utility to generate real UI screenshot captures of MatchReport and HouseholdDashboard
 * for documentation and verification.
 */
public class ScreenshotGenerator {

    public static void main(String[] args) throws Exception {
        File dir = new File("screenshots");
        if (!dir.exists()) dir.mkdirs();

        CountDownLatch initLatch = new CountDownLatch(1);
        try {
            Platform.startup(initLatch::countDown);
        } catch (IllegalStateException e) {
            initLatch.countDown();
        }
        initLatch.await();

        CountDownLatch renderLatch = new CountDownLatch(2);

        Platform.runLater(() -> {
            try {
                // Populate sample data via DatabaseManager
                Connection conn = DatabaseManager.getInstance().getConnection();
                try (Statement st = conn.createStatement()) {
                    st.execute("DELETE FROM reminder_log;");
                    st.execute("DELETE FROM mismatch_flag;");
                    st.execute("DELETE FROM submission;");
                    st.execute("DELETE FROM pda_record;");
                    st.execute("DELETE FROM pensioner;");
                    st.execute("DELETE FROM household;");
                }

                PensionerService pService = new PensionerService(conn);
                PensionerRepository pRepo = new PensionerRepository(conn);
                PDARecordRepository pdaRepo = new PDARecordRepository(conn);
                SubmissionRepository subRepo = new SubmissionRepository(conn);

                // Create Sharma Household
                Household h = pService.createHousehold("Sharma Household");

                // Member 1: Ramesh Kumar Sharma (Submitted with minor spelling difference in draft)
                Pensioner p1 = new Pensioner(0, h.getId(), "Ramesh Kumar Sharma", "1955-03-15", PensionType.CENTRAL, "PPO/CPAO/2015/88392");
                pRepo.save(p1);

                PDARecord pda1 = new PDARecord(0, p1.getId(), "Ramesh K Sharma", "1955-03-15", "Flat 402, Shanti Vihar, Pune 411001", "State Bank of India, Main Branch");
                pdaRepo.save(pda1);

                Submission sub1 = new Submission(0, p1.getId(), "Ramesh Kumar Sharma", "15/03/1955", "Flat 402, Shanti Vihar, Pune 411001", LocalDate.now().toString(), LocalDate.now().getYear());
                subRepo.save(sub1);

                MismatchEngine engine = new MismatchEngine();
                MatchReport report1 = engine.analyse(pda1, sub1);
                for (var flag : report1.getFlags()) {
                    flag.setSubmissionId(sub1.getId());
                    subRepo.saveMismatchFlag(flag);
                }

                // Member 2: Sunita Sharma (Unsubmitted)
                Pensioner p2 = new Pensioner(0, h.getId(), "Sunita Sharma", "1958-08-22", PensionType.STATE, "PPO/MAH/2018/10492");
                pRepo.save(p2);

                PDARecord pda2 = new PDARecord(0, p2.getId(), "Sunita Sharma", "1958-08-22", "Flat 402, Shanti Vihar, Pune 411001", "Bank of Maharashtra");
                pdaRepo.save(pda2);

                // ── Render Match Report Screenshot ────────────────────────
                FXMLLoader reportLoader = new FXMLLoader(ScreenshotGenerator.class.getResource("/fxml/match_report.fxml"));
                Parent reportRoot = reportLoader.load();
                MatchReportController reportCtrl = reportLoader.getController();
                Scene reportScene = new Scene(reportRoot, 920, 600);
                reportScene.getStylesheets().add(ScreenshotGenerator.class.getResource("/css/styles.css").toExternalForm());
                reportCtrl.loadReportForPensioner(p1);

                reportRoot.applyCss();
                reportRoot.layout();

                WritableImage reportImg = reportScene.snapshot(null);
                saveWritableImageToPng(reportImg, new File("screenshots/match_report.png"));
                System.out.println("✓ Saved screenshots/match_report.png");
                renderLatch.countDown();

                // ── Render Household Dashboard Screenshot ──────────────────
                FXMLLoader hhLoader = new FXMLLoader(ScreenshotGenerator.class.getResource("/fxml/household_dashboard.fxml"));
                Parent hhRoot = hhLoader.load();
                HouseholdDashboardController hhCtrl = hhLoader.getController();
                Scene hhScene = new Scene(hhRoot, 1100, 680);
                hhScene.getStylesheets().add(ScreenshotGenerator.class.getResource("/css/styles.css").toExternalForm());
                hhCtrl.loadHousehold(h);

                hhRoot.applyCss();
                hhRoot.layout();

                WritableImage hhImg = hhScene.snapshot(null);
                saveWritableImageToPng(hhImg, new File("screenshots/household_dashboard.png"));
                System.out.println("✓ Saved screenshots/household_dashboard.png");
                renderLatch.countDown();

            } catch (Exception e) {
                e.printStackTrace();
                renderLatch.countDown();
                renderLatch.countDown();
            }
        });

        renderLatch.await();
        Platform.exit();
        System.out.println("Screenshot generation complete.");
    }

    private static void saveWritableImageToPng(WritableImage fxImage, File targetFile) throws Exception {
        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }

        ImageIO.write(bufferedImage, "png", targetFile);
    }
}
