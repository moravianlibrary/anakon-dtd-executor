package cz.trinera.anakon.dtd_executor.dtd_definitions;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class UndefinedProcess implements Process {

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(logFile.toPath())) {
            writer.write("Unknown process type '" + type + "'\n");
            writer.flush();
            throw new Exception("Unknown process type '" + type + "'");
        }
    }
}
