package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestAggregateMetricsProcess implements Process {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static class Params {
        public String time_window;
        public List<String> metrics;
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            //log parameters
            logWriter.write("    Running " + TestAggregateMetricsProcess.class.getName() + "...\n");
            logWriter.write("    ID: " + id + "\n");
            logWriter.write("    Process type: " + type + "\n");
            logWriter.write("    Input data: " + inputData + "\n");
            logWriter.write("    Log file: " + logFile + "\n");
            logWriter.write("    Output dir: " + outputDir + "\n");
            logWriter.write("    Config file: " + configFile + "\n");
            logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");

            try {
                //load configuration
                Params params = objectMapper.readValue(inputData, Params.class);
                String timeWindow = params.time_window;
                List<String> metrics = params.metrics;
                if (metrics == null || metrics.isEmpty()) {
                    throw new Exception("At least one metric must be specified in 'metrics' parameter");
                }
                Map<String, Integer> metricValues = generateRandomMetricValues(metrics);

                //summary.csv
                File summaryFile = new File(outputDir, "summary.csv");
                buildSummaryCsv(summaryFile, timeWindow, metrics, metricValues);
                logWriter.write("    Exported summary to " + summaryFile.getName() + "\n");

                //metadata.json
                File metadataJsonFile = new File(outputDir, "metadata.json");
                buildMetadataJson(metadataJsonFile, timeWindow, metrics, metricValues);
                logWriter.write("    Exported metadata to " + metadataJsonFile.getName() + "\n");

            } catch (Exception e) {
                logWriter.write("    Error: " + e.getMessage() + "\n");
                throw e;
            } finally {
                logWriter.flush();
            }
        }
    }

    private Map<String, Integer> generateRandomMetricValues(List<String> metrics) {
        Random random = new Random();
        return metrics.stream().collect(java.util.stream.Collectors.toMap(metric -> metric, metric -> random.nextInt(1000)));
    }

    private void buildMetadataJson(File outputFile, String timeWindow, List<String> metrics, Map<String, Integer> metricValues) throws IOException {
        JSONObject report = new JSONObject();
        report.put("time_windows", timeWindow);
        JSONArray metricsArray = new JSONArray();
        for (String metric : metrics) {
            JSONObject metricObj = new JSONObject();
            metricObj.put("name", metric);
            metricObj.put("value", metricValues.get(metric)); //random value for demo purposes
            metricsArray.put(metricObj);
        }
        report.put("metrics", metricsArray);
        report.put("report_generated_at", System.currentTimeMillis());
        report.put("status", "OK");

        try (BufferedWriter jsonWriter = Files.newBufferedWriter(outputFile.toPath())) {
            jsonWriter.write(report.toString(3));
        }
    }

    private void buildSummaryCsv(File outputFile, String timeWindow, List<String> metrics, Map<String, Integer> metricValues) throws IOException {
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath())) {
            //headers
            csvWriter.write("\"ID\",\"NAME\",\"VALUE\"\n");
            //data
            for (int i = 0; i < metrics.size(); i++) {
                csvWriter.write("\"" + i + "\"");
                String metricName = metrics.get(i);
                csvWriter.write(",\"" + metricName + "\"");
                csvWriter.write(",\"" + metricValues.get(metricName) + "\"");
                csvWriter.write("\n");
            }
            csvWriter.flush();
        }
    }
}
