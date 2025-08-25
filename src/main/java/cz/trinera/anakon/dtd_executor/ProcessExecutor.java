package cz.trinera.anakon.dtd_executor;


import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static cz.trinera.anakon.dtd_executor.DynamicConfig.LogLevel.*;

public class ProcessExecutor {

    private int minSupportedExecutorVersion;
    private int maxConcurrentProcesses;
    private int pollIntervalSeconds;
    private boolean silentMode; // Set to true to suppress console output

    private ExecutorService executor;
    private final Map<UUID, ProcessWrapper> runningProcesses = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        new ProcessExecutor().start();
    }

    public void start() throws Exception {
        System.out.println("Starting Anakon DTD Executor...");
        while (true) {
            loadDynamicConfiguration();
            try (Connection conn = getConnection()) {
                if (!runningOutdatedVersion()) {
                    checkForNewProcesses(conn);
                }
                checkForKillRequests(conn);
            }
            //check if executor is not outdated
            boolean runningOutdatedVersion = Config.EXECUTOR_VERSION < minSupportedExecutorVersion;
            if (runningOutdatedVersion) {
                System.out.println("Executor is running an outdated version (" + Config.EXECUTOR_VERSION + "), which is lower than the minimum supported version (" + minSupportedExecutorVersion + ").");
                //System.out.println("Minimum supported executor version is: " + minSupportedExecutorVersion);
                if (runningProcesses.isEmpty()) {
                    System.out.println("No processes are currently running. Exiting this outdated executor.");
                    return;
                } else {
                    System.out.println("There are still some running processes. Continuing to run this outdated executor.");
                }
            }
            //wait for the next poll interval
            Thread.sleep(pollIntervalSeconds * 1000L);
            System.out.println();
        }
    }

    private boolean runningOutdatedVersion() {
        return Config.EXECUTOR_VERSION < minSupportedExecutorVersion;
    }

    private void loadDynamicConfiguration() throws IOException {
        File dynamicConfigFile = Config.Utils.getExistingReadableFile(Config.instanceOf().getDynamicConfigFile());
        DynamicConfig dynamicConfig = DynamicConfig.create(dynamicConfigFile);
        DynamicConfig.ExecutorConfig executorConfig = dynamicConfig.getExecutorConfig();

        minSupportedExecutorVersion = executorConfig.getMinSupportedExecutorVersion();
        maxConcurrentProcesses = executorConfig.getMaxConcurrentProcesses();
        pollIntervalSeconds = executorConfig.getPollingInterval();
        silentMode = List.of(WARNING, ERROR, CRITICAL).contains(executorConfig.getLogLevel());

        if (executor == null) {
            executor = Executors.newCachedThreadPool();
        }

        System.out.println("Loading dynamic configuration from file: " + dynamicConfigFile.getAbsolutePath());
        System.out.println("Loaded minSupportedExecutorVersion: " + minSupportedExecutorVersion);
        System.out.println("Loaded maxConcurrentProcesses: " + maxConcurrentProcesses);
        System.out.println("Loaded pollIntervalSeconds: " + pollIntervalSeconds);
        System.out.println("Loaded silentMode: " + silentMode);

    }

    private void log(String message) {
        if (!silentMode) {
            System.out.println(message);
        }
    }

    private void checkForNewProcesses(Connection conn) throws Exception {
        log("Checking for new processes...");
        int runningCount = runningProcesses.size();
        int slotsAvailable = maxConcurrentProcesses - runningCount;
        log("Currently running: " + runningCount + " / " + maxConcurrentProcesses);
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

    private void launchProcess(UUID id, String type, String params) throws Exception {
        AtomicBoolean cancelRequested = new AtomicBoolean(false);
        Runnable task = () -> {
            try {
                System.out.println("Launching process: " + id + ", type: " + type);
                Path jobDir = Paths.get(Config.instanceOf().getJobsDir(), id.toString());
                jobDir.toFile().mkdirs(); // Ensure the job directory exists
                Path outputPath = jobDir.resolve("output.log");
                Process process = ProcessFactory.load(type);
                process.run(id, type, params, outputPath, cancelRequested);
                if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
                    updateFinalProcessState(id, Process.State.CANCELED);
                } else {
                    updateFinalProcessState(id, Process.State.COMPLETED);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateFinalProcessState(id, Process.State.CANCELED);
            } catch (Throwable e) {
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
