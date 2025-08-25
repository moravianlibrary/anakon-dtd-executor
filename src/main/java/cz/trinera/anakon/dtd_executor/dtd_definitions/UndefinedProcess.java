package cz.trinera.anakon.dtd_executor.dtd_definitions;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class UndefinedProcess implements Process {

    @Override
    public void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("Unknown process type '" + type + "'\n");
            writer.flush();
            throw new Exception("Unknown process type '" + type + "'");
        }
    }
}
