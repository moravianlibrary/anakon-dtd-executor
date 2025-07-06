package cz.trinera.anakon.dtd_executor.dtd_definitions;

import cz.trinera.anakon.dtd_executor.DtdProcess;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestProcess implements DtdProcess {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static class TestParams {
        public Integer max_duration;
        public String failure_strategy;
    }

    @Override
    public void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {

            TestParams params = objectMapper.readValue(inputData, TestParams.class);

            //current time in milliseconds
            long startTime = System.currentTimeMillis();
            writer.write("Process " + id + " started (type=Test)\n");
            if (params.max_duration != null) {
                writer.write("Max duration set to " + params.max_duration + " seconds\n");
            } else {
                writer.write("No max duration set\n");
            }
            if (params.failure_strategy != null) {
                writer.write("Failure strategy: " + params.failure_strategy + "\n");
            } else {
                writer.write("No failure strategy set\n");
            }

            for (int i = 0; i < 60; i++) {
                //canceled?
                if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
                    writer.write("Process " + id + " cancelled\n");
                    return;
                }

                //running too long?
                if (params.max_duration != null) {
                    long duration = System.currentTimeMillis() - startTime;
                    if (duration > params.max_duration * 1000L) {
                        if ("will".equals(params.failure_strategy)) {
                            writer.write("Process " + id + " failed due to exceeding max duration of " + params.max_duration + " seconds and failure strategy 'will_fail'\n");
                            throw new Exception("Process " + id + " failed due to exceeding max duration of " + params.max_duration + " seconds and failure strategy 'will_fail'");
                        } else {
                            writer.write("Process " + id + " exceeded max duration of " + params.max_duration + " seconds\n");
                        }
                        return;
                    }
                }

                //simulate failure?
                if (params.failure_strategy != null) {
                    if ("will".equals(params.failure_strategy) || "can".equals(params.failure_strategy)) {
                        Random random = new Random();
                        if (random.nextInt(100) < 10) { // 10% chance of failure on each tick
                            writer.write("Process " + id + " failed on tick " + i + "\n");
                            throw new Exception("Process " + id + " failed on tick " + i);
                        }
                    } else {
                        System.out.println("not now");
                    }
                }

                writer.write("Test tick " + i + "\n");
                writer.flush();
                Thread.sleep(1000); // Simulate work for 1 second
            }
            if ("will".equals(params.failure_strategy)) {
                writer.write("Process " + id + " finished but was supposed to fail due to 'will_fail' strategy\n");
                throw new Exception("Process " + id + " finished but was supposed to fail due to 'will_fail' strategy");
            }
            writer.write("Process " + id + " finished successfully\n");
        }
    }
}
