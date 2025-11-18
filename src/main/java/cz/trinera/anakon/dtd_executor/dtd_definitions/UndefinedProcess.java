package cz.trinera.anakon.dtd_executor.dtd_definitions;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class UndefinedProcess implements Process {

    private final String errorMessage;

    public UndefinedProcess(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {

        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            logWriter.write("    ID: " + id + "\n");
            logWriter.write("    Process type: " + type + "\n");
            logWriter.write("    Input data: " + inputData + "\n");
            logWriter.write("    Log file: " + logFile + "\n");
            logWriter.write("    Output dir: " + outputDir + "\n");
            logWriter.write("    Config file: " + configFile + "\n");
            logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");
            logWriter.write("\n");

            logWriter.write("    FAILED: " + errorMessage + "\n");

            logWriter.flush();

        }

        throw new Exception("Process type '" + type + "' failed with error message: " + errorMessage);
    }
}
