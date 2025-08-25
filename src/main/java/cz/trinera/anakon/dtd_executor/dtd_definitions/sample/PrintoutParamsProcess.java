package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrintoutParamsProcess implements Process {

    public void run(UUID id, String type, String inputData, File logFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(logFile.toPath())) {
            writer.write("    Running " + PrintoutParamsProcess.class.getName() + "...\n");
            writer.write("    ID: " + id + "\n");
            writer.write("    Process type: " + type + "\n");
            writer.write("    Input data: " + inputData + "\n");
            writer.write("    Log file: " + logFile + "\n");
            writer.write("    Cancel requested: " + cancelRequested.get() + "\n");
            writer.flush();
        }
    }
}
