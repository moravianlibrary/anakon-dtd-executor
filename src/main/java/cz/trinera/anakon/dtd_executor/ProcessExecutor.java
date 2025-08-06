package cz.trinera.anakon.dtd_executor;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessExecutor {

    private static final int MAX_CONCURRENT_PROCESSES = 10; //TODO: load from dynamic config
    private static final int POLL_INTERVAL_SECONDS = 2; //TODO: load from dynamic config
    private static final boolean SILENT_MODE = false; // Set to true to suppress console output //TODO: load from dynamic config (log_level)
    private static final boolean ENABLE_RANDOM_TERMINATION = false; // For testing purposes, randomly terminate the executor

    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_PROCESSES);
    private final Map<UUID, ProcessWrapper> runningProcesses = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        new ProcessExecutor().start();
    }

    public void start() throws Exception {
        System.out.println("Starting Anakon DTD Executor...");
        while (true) {
            try (Connection conn = getConnection()) {
                loadDynamicConfiguration();
                checkForNewProcesses(conn);
                checkForKillRequests(conn);
            }
            // Check if we should randomly terminate the executor for testing purposes (later make decision to terminate based on dynamic config and executor version)
            if (ENABLE_RANDOM_TERMINATION && shouldRandomlyTerminate()) {
                executor.shutdown();
                break; // Exit the loop if the executor is terminated
            }
            System.out.println();
            Thread.sleep(POLL_INTERVAL_SECONDS * 1000L);
        }
    }

    private boolean shouldRandomlyTerminate() {
        //randomly terminate the executor for testing purposes
        int randomValue = new Random().nextInt(6) + 1; // Random value between 1 and 6
        System.out.println("Rolling the dice... And it's " + randomValue);
        if (randomValue == 6) {
            System.out.println("I got 6! Terminating execution.");
            return true; // Terminate the executor
        } else {
            System.out.println("That's not a 6, continuing execution.");
            return false;
        }
    }

    private void loadDynamicConfiguration() {
        String dynamicConfigFile = Config.instanceOf().getDynamicConfigFile();
        System.out.println("Loading dynamic configuration from file: " + dynamicConfigFile + " (not implemented yet)");
        // TODO: actually load, update MAX_CONCURRENT_PROCESSES, POLL_INTERVAL_SECONDS, and SILENT_MODE, and process registry
    }

    private void log(String message) {
        if (!SILENT_MODE) {
            System.out.println(message);
        }
    }

    private void checkForNewProcesses(Connection conn) throws SQLException {
        log("Checking for new processes...");
        int runningCount = runningProcesses.size();
        int slotsAvailable = MAX_CONCURRENT_PROCESSES - runningCount;
        log("Currently running: " + runningCount + " / " + MAX_CONCURRENT_PROCESSES);
        if (slotsAvailable <= 0) {
            log("Max running processes reached. Skipping.");
            return;
        }

        String sql = "SELECT id, type, input_data FROM dtd WHERE state = 'CREATED' ORDER BY created ASC FOR UPDATE SKIP LOCKED LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, slotsAvailable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    String type = rs.getString("type");
                    String params = rs.getString("input_data");

                    updateProcessState(conn, id, Process.State.RUNNING, Timestamp.from(Instant.now()));
                    launchProcess(id, type, params);
                }
            }
        }
    }

    private void launchProcess(UUID id, String type, String params) {
        System.out.println("Launching process: " + id + ", type: " + type);
        Path jobDir = Paths.get(Config.instanceOf().getJobsDir(), id.toString());
        jobDir.toFile().mkdirs(); // Ensure the job directory exists
        Path outputPath = jobDir.resolve("output.log");
        AtomicBoolean cancelRequested = new AtomicBoolean(false);

        Runnable task = () -> {
            try {
                Process process = ProcessFactory.create(type);
                process.run(id, type, params, outputPath, cancelRequested);
                if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
                    updateFinalProcessState(id, Process.State.CANCELED);
                } else {
                    updateFinalProcessState(id, Process.State.COMPLETED);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateFinalProcessState(id, Process.State.CANCELED);
            } catch (Exception e) {
                e.printStackTrace();
                updateFinalProcessState(id, Process.State.FAILED);
            } finally {
                runningProcesses.remove(id);
            }
        };

        Future<?> future = executor.submit(task);
        runningProcesses.put(id, new ProcessWrapper(future, cancelRequested));
    }

    private void checkForKillRequests(Connection conn) throws SQLException {
        log("Checking for kill requests...");
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

    private void updateProcessState(Connection conn, UUID id, Process.State state, Timestamp startedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE dtd SET state = ?, started = ?, last_modified = ? WHERE id = ?")) {
            ps.setString(1, state.name());
            ps.setTimestamp(2, startedAt);
            ps.setTimestamp(3, startedAt);
            ps.setObject(4, id);
            ps.executeUpdate();
        }
    }

    private void updateFinalProcessState(UUID id, Process.State state) {
        Timestamp now = Timestamp.from(Instant.now());
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE dtd SET state = ?, finished = ?, last_modified = ? WHERE id = ?")) {
            ps.setString(1, state.name());
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
        log("Connecting to database: " + url);
        //System.out.println("Using user: " + config.getDbUser() + " and password: " + config.getDbPassword());
        return DriverManager.getConnection(url, config.getDbUser(), config.getDbPassword());
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
