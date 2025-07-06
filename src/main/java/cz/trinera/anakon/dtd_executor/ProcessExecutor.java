package cz.trinera.anakon.dtd_executor;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessExecutor {

    private static final int MAX_CONCURRENT_PROCESSES = 2;
    private static final int POLL_INTERVAL_SECONDS = 5;

    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_PROCESSES);
    private final Map<UUID, ProcessWrapper> runningProcesses = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        new ProcessExecutor().start();
    }

    public void start() throws Exception {
        while (true) {
            try (Connection conn = getConnection()) {
                checkForNewProcesses(conn);
                checkForKillRequests(conn);
            }
            Thread.sleep(POLL_INTERVAL_SECONDS * 1000L);
        }
    }

    private void checkForNewProcesses(Connection conn) throws SQLException {
        System.out.println("Checking for new processes...");
        int runningCount = runningProcesses.size();
        int slotsAvailable = MAX_CONCURRENT_PROCESSES - runningCount;
        System.out.println("Currently running: " + runningCount + " / " + MAX_CONCURRENT_PROCESSES);
        if (slotsAvailable <= 0) {
            System.out.println("Max running processes reached. Skipping.");
            return;
        }

        String sql = "SELECT id, type, input_data FROM dtd WHERE status = 'CREATED' ORDER BY created ASC FOR UPDATE SKIP LOCKED LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, slotsAvailable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    String type = rs.getString("type");
                    String params = rs.getString("input_data");

                    updateStatus(conn, id, "RUNNING", Timestamp.from(Instant.now()));
                    launchProcess(id, type, params);
                }
            }
        }
    }

    private void launchProcess(UUID id, String type, String params) {
        System.out.println("Launching process: " + id + ", type: " + type);
        Path jobsDir = Paths.get(Config.instanceOf().getJobsDir(), id.toString());
        File jobDir = jobsDir.toFile();
        jobDir.mkdirs(); // Ensure the job directory exists
        Path outputPath = Paths.get(jobDir.getAbsolutePath(), "output.log");
        AtomicBoolean cancelRequested = new AtomicBoolean(false);

        Runnable task = () -> {
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                writer.write("Process " + id + " started\n");
                for (int i = 0; i < 30; i++) {
                    if (cancelRequested.get()) {
                        writer.write("Process " + id + " cancelled\n");
                        updateFinalStatus(id, "CANCELED");
                        return;
                    }
                    writer.write("Tick " + i + "\n");
                    writer.flush();
                    Thread.sleep(1000);
                }
                writer.write("Process " + id + " finished\n");
                updateFinalStatus(id, "COMPLETE");
            } catch (InterruptedException e) {
                System.out.println("Process " + id + " was interrupted");
                updateFinalStatus(id, "CANCELED");
                Thread.currentThread().interrupt(); // zachovej flag
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Process " + id + " failed: " + e.getMessage());
                updateFinalStatus(id, "FAILED");
            } finally {
                runningProcesses.remove(id);
            }
        };

        Future<?> future = executor.submit(task);
        runningProcesses.put(id, new ProcessWrapper(future, cancelRequested));
    }

    private void checkForKillRequests(Connection conn) throws SQLException {
        System.out.println("Checking for kill requests...");
        String sql = "SELECT dtd_id FROM dtd_kill_request";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID processId = UUID.fromString(rs.getString("dtd_id"));
                ProcessWrapper pw = runningProcesses.get(processId);
                if (pw != null) {
                    System.out.println("Cancelling process: " + processId);
                    pw.cancelRequested.set(true);
                    pw.future.cancel(true);
                    deleteKillRequest(conn, processId);
                }
            }
        }
    }

    private void deleteKillRequest(Connection conn, UUID processId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM dtd_kill_request WHERE dtd_id = ?")) {
            ps.setObject(1, processId);
            ps.executeUpdate();
        }
    }

    private void updateStatus(Connection conn, UUID id, String status, Timestamp startedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE dtd SET status = ?, started = ?, last_modified = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setTimestamp(2, startedAt);
            ps.setTimestamp(3, startedAt);
            ps.setObject(4, id);
            ps.executeUpdate();
        }
    }

    private void updateFinalStatus(UUID id, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE dtd SET status = ?, finished = ?, last_modified = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setTimestamp(2, now);
            ps.setTimestamp(3, now);
            ps.setObject(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        Config config = Config.instanceOf();
        String url = "jdbc:postgresql://" + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbDatabase();
        //System.out.println("Connecting to database: " + url);
        return DriverManager.getConnection(url, config.getDbUser(), config.getDbHost());
    }

    private static class ProcessWrapper {
        final Future<?> future;
        final AtomicBoolean cancelRequested;

        ProcessWrapper(Future<?> future, AtomicBoolean cancelRequested) {
            this.future = future;
            this.cancelRequested = cancelRequested;
        }
    }

}
