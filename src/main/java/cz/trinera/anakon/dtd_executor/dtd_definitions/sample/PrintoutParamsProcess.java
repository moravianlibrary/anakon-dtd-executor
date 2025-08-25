package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import cz.trinera.anakon.dtd_executor.Process;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrintoutParamsProcess implements Process {

    public void run(UUID id, String type, String inputData, Path outputPath, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("    Running " + PrintoutParamsProcess.class.getName() + "...\n");
            writer.write("    ID: " + id + "\n");
            writer.write("    Process type: " + type + "\n");
            writer.write("    Input data: " + inputData + "\n");
            writer.write("    Output path: " + outputPath + "\n");
            writer.write("    Cancel requested: " + cancelRequested.get() + "\n");
            writer.flush();
        }
    }
}
