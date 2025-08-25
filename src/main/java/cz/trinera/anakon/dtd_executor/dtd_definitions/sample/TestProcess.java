package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestProcess implements Process {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static class Params {
        public Integer max_duration;
        public String failure_strategy;
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {

            //log parameters
            logWriter.write("    Running " + TestProcess.class.getName() + "...\n");
            logWriter.write("    ID: " + id + "\n");
            logWriter.write("    Process type: " + type + "\n");
            logWriter.write("    Input data: " + inputData + "\n");
            logWriter.write("    Log file: " + logFile + "\n");
            logWriter.write("    Output dir: " + outputDir + "\n");
            logWriter.write("    Config file: " + configFile + "\n");
            logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");

            Params params = objectMapper.readValue(inputData, Params.class);

            //current time in milliseconds
            long startTime = System.currentTimeMillis();
            logWriter.write("Process " + id + " started (type=Test)\n");
            if (params.max_duration != null) {
                logWriter.write("Max duration set to " + params.max_duration + " seconds\n");
            } else {
                logWriter.write("No max duration set\n");
            }
            if (params.failure_strategy != null) {
                logWriter.write("Failure strategy: " + params.failure_strategy + "\n");
            } else {
                logWriter.write("No failure strategy set\n");
            }

            for (int i = 0; i < 60; i++) {
                //canceled?
                if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
                    logWriter.write("Process " + id + " cancelled\n");
                    return;
                }

                //running too long?
                if (params.max_duration != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    if (duration > params.max_duration * 1000L) {
                        if ("will".equals(params.failure_strategy)) {
                            logWriter.write("Process " + id + " failed due to exceeding max duration of " + params.max_duration + " seconds and failure strategy 'will_fail'\n");
                            throw new Exception("Process " + id + " failed due to exceeding max duration of " + params.max_duration + " seconds and failure strategy 'will_fail'");
                        } else {
                            logWriter.write("Process " + id + " exceeded max duration of " + params.max_duration + " seconds\n");
                        }
                        return;
                    }
                }

                //simulate failure?
                if (params.failure_strategy != null) {
                    if ("will".equals(params.failure_strategy) || "can".equals(params.failure_strategy)) {
                        Random random = new Random();
                        if (random.nextInt(100) < 10) { // 10% chance of failure on each tick
                            logWriter.write("Process " + id + " failed on tick " + i + "\n");
                            throw new Exception("Process " + id + " failed on tick " + i);
                        }
                    } else {
                        System.out.println("not now");
                    }
                }

                logWriter.write("Test tick " + i + "\n");
                logWriter.flush();
                Thread.sleep(1000); // Simulate work for 1 second
            }
            if ("will".equals(params.failure_strategy)) {
                logWriter.write("Process " + id + " finished but was supposed to fail due to 'will_fail' strategy\n");
                throw new Exception("Process " + id + " finished but was supposed to fail due to 'will_fail' strategy");
            }
            logWriter.write("Process " + id + " finished successfully\n");
        }
    }
}
