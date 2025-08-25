package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

public class TestGenerateReportJsonProcess implements Process {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static class Params {
        public String kramerius_base_url;
        public String object_pid;
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            //log parameters
            logWriter.write("    Running " + TestGenerateReportJsonProcess.class.getName() + "...\n");
            logWriter.write("    ID: " + id + "\n");
            logWriter.write("    Process type: " + type + "\n");
            logWriter.write("    Input data: " + inputData + "\n");
            logWriter.write("    Log file: " + logFile + "\n");
            logWriter.write("    Output dir: " + outputDir + "\n");
            logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");

            try {
                //load configuration
                Params params = objectMapper.readValue(inputData, Params.class);
                if (params.kramerius_base_url == null || params.kramerius_base_url.isBlank()) {
                    throw new Exception("kramerius_base_url is required");
                }
                if (params.object_pid == null || params.object_pid.isBlank()) {
                    throw new Exception("object_pid is required");
                }

                File outputFile = new File(outputDir, "report.json");
                buildReport(outputFile, params.kramerius_base_url, params.object_pid);
                logWriter.write("    Exported report to " + outputFile.getName() + "\n");

            } catch (Exception e) {
                logWriter.write("    Error: " + e.getMessage() + "\n");
                throw e;
            } finally {
                logWriter.flush();
            }
        }
    }

    private void buildReport(File outputFile, String krameriusBaseUrl, String objectPid) throws IOException {
        JSONObject report = new JSONObject();
        report.put("kramerius_base_url", krameriusBaseUrl);
        report.put("object_pid", objectPid);
        report.put("report_generated_at", System.currentTimeMillis());
        report.put("status", "OK");
        report.put("model", getRandomModel());

        //create simple JSON report
        try (BufferedWriter jsonWriter = Files.newBufferedWriter(outputFile.toPath())) {
            jsonWriter.write(report.toString(3));
        }
    }

    private String getRandomModel() {
        String[] models = {"monograph", "periodical", "article", "manuscript"};
        return models[(int) (Math.random() * models.length)];
    }
}
