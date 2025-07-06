package cz.trinera.anakon.dtd_executor.dtd_definitions;

import cz.trinera.anakon.dtd_executor.DtdProcess;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestProcess implements DtdProcess {

    @Override
    public void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("Process " + id + " started (type=Test)\n");
            for (int i = 0; i < 30; i++) {
                if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
                    writer.write("Process " + id + " cancelled\n");
                    return;
                }
                writer.write("Test tick " + i + "\n");
                writer.flush();
                Thread.sleep(1000);
            }
            writer.write("Process " + id + " finished successfully\n");
        }
    }
}
