package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestConfigurationFile implements Process {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            logWriter.write("    Running " + PrintoutParamsProcess.class.getName() + "...\n");
            logWriter.write("    ID: " + id + "\n");
            logWriter.write("    Process type: " + type + "\n");
            logWriter.write("    Input data: " + inputData + "\n");
            logWriter.write("    Log file: " + logFile + "\n");
            logWriter.write("    Output dir: " + outputDir + "\n");
            logWriter.write("    Config file: " + configFile + "\n");
            logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");
            logWriter.flush();

            //load config as properties file
            if (configFile != null && configFile.exists()) {
                logWriter.write("Loading config file: " + configFile.getAbsolutePath() + "\n");
                Properties properties = new Properties();
                properties.load(Files.newInputStream(configFile.toPath()));
                logWriter.write("Configuration properties:\n");
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key);
                    logWriter.write(key + "=" + value + "\n");
                }
            } else {
                logWriter.write("No configuration file provided\n");
            }
        }
    }
}
