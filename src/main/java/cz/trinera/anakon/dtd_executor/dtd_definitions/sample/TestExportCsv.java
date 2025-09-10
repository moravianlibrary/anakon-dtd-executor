package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestExportCsv implements Process {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static class Params {
        public LocalDate start_date = LocalDate.MIN;
        public LocalDate end_date  = LocalDate.now();
        public Boolean include_headers;
    }

    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            //log parameters
            logWriter.write("    Running " + TestExportCsv.class.getName() + "...\n");
            logWriter.write("    ID: " + id + "\n");
            logWriter.write("    Process type: " + type + "\n");
            logWriter.write("    Input data: " + inputData + "\n");
            logWriter.write("    Log file: " + logFile + "\n");
            logWriter.write("    Output dir: " + outputDir + "\n");
            logWriter.write("    Config file: " + configFile + "\n");
            logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");

            try {
                //setup object mapper
                objectMapper.registerModule(new JavaTimeModule());
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                objectMapper.setDateFormat(dateFormat);

                //load configuration
                Params params = objectMapper.readValue(inputData, Params.class);

                if (params.start_date.isAfter(params.end_date)){
                    throw new IllegalArgumentException("Start date is after end date");
                }

                //TODO: načíst include_headers.
                boolean includeHeaders = params.include_headers != null ? params.include_headers : true; //defaultně true
                File outputFile = new File(outputDir, "export.csv");
                fillCsvFileWithRandomData(outputFile, includeHeaders);
                logWriter.write("    Exported random data to " + outputFile.getName() + "\n");

            } catch (Exception e) {
                logWriter.write("    Error: " + e.getMessage() + "\n");
                throw e;
            } finally {
                logWriter.flush();
            }
        }
    }

    private void fillCsvFileWithRandomData(File outputFile, boolean includeHeaders) throws IOException {
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath())) {
            if (includeHeaders) {
                csvWriter.write("\"ID\",\"NAME\",\"VALUE\"\n");
            }
            //write 10 rows with random data
            for (int i = 0; i < 10; i++) {
                //csvWriter.write(i + ",name" + i + "," + Math.random() * 100 + "\n");
                csvWriter.write("\"" + i + "\"");
                csvWriter.write(",\"" + createRandomName() + "\"");
                csvWriter.write(",\"" + String.format("%.2f", Math.random() * 100) + "\"");
                csvWriter.write("\n");
            }
            csvWriter.flush();
        }
    }

    private String createRandomName() {
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder name = new StringBuilder();
        int length = 5 + (int) (Math.random() * 5); //name length between 5 and 10
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * alphabet.length());
            name.append(i == 0 ? Character.toUpperCase(alphabet.charAt(index)) : alphabet.charAt(index));
        }
        return name.toString();
    }
}
