package cz.trinera.anakon.dtd_executor.dtd_definitions.sample;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import cz.trinera.anakon.dtd_executor.dtd_definitions.Process;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import static java.nio.file.StandardOpenOption.APPEND;

public class CoordinatesControlProcess implements Process {

    private static final int PAUSE_BETWEEN_VOLUME_REQUESTS_MS = 300;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int PAGE_SIZE = 100;

    public static class Params {
        public String anakon_base_url;
        public String dig_lib_code;
    }

    private static class AnakonCoordsSearchResult {

        public AnakonItems hits;
        public AnakonInfo total;

        static class AnakonItems {
            public List<Item> hits;

            static class Item {
                public Code _source;

                static class Code {
                    public List<Coords> df_255;
                    public List<Partial_coords> df_034;
                    public String id;

                    static class Coords {
                        public String c;
                    }

                    static class Partial_coords {
                        public String d;
                        public String e;
                        public String f;
                        public String g;
                    }
                }
            }
        }

        static class AnakonInfo {
            public int value;
        }
    }

    public static void main(String[] args) throws Exception {
        UUID uuid = UUID.randomUUID();
        System.out.println("Running " + CoordinatesControlProcess.class.getName() + " with UUID: " + uuid);
        File jobDir = new File("src/main/resources/local/coordinates_control/" + uuid);
        jobDir.mkdirs();
        String anakonBaseUrl = "https://anakon.test.api.trinera.cloud/api/v3/search";
        String dig_lib_code = "mzk";
        JSONObject input = new JSONObject();
        input.put("anakon_base_url", anakonBaseUrl);
        input.put("dig_lib_code", dig_lib_code);
        new CoordinatesControlProcess().run(
                UUID.randomUUID(),
                "coordinates_control",
                input.toString(),
                new File(jobDir, "process.log"),
                jobDir,
                null,
                new AtomicBoolean(false)
        );
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            try {
                //log parameters
                logWriter.write("    Running " + TestGenerateReportJsonProcess.class.getName() + "...\n");
                logWriter.write("    ID: " + id + "\n");
                logWriter.write("    Process type: " + type + "\n");
                logWriter.write("    Input data: " + inputData + "\n");
                logWriter.write("    Log file: " + logFile + "\n");
                logWriter.write("    Output dir: " + outputDir + "\n");
                logWriter.write("    Config file: " + configFile + "\n");
                logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");

                Params params = parseInput(inputData);

                File outputFile = new File(outputDir, "export.csv");
                writeCsvHeader(outputFile);

                process(logWriter, params, outputFile);

                logWriter.write("Exported data to " + outputFile.getName() + "\n");

            } catch (Exception e) {
                logWriter.write("Error: " + e.getMessage() + "\n");
                throw e;
            } finally {
                logWriter.flush();
            }
        }
    }

    private void process(BufferedWriter log, Params params, File outputFile) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(); //default HTTP_2 causes GOAWAY
        AnakonCoordsSearchResult result = null;
        int size = PAGE_SIZE;
        int from = 0;

        do {
            String body = "{\"size\":100,\"track_total_hits\":true,\"from\":0,\"query\":{\"bool\":{\"must\":[{\"term\":{\"library.keyword\":\"mzk\"}},{\"nested\":{\"path\":\"df_255\",\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"df_255.c.keyword\"}}]}}}}]}}}";
            result = postRequest(httpClient, AnakonCoordsSearchResult.class, URI.create(params.anakon_base_url), body);

            for (AnakonCoordsSearchResult.AnakonItems.Item item: result.hits.hits){
                //check coords
                writeSearchResult(outputFile, log, item, "type of error");
            }
            
            from += size;
        } while (false); //(result.total.value > from + size);
    }

    private void writeSearchResult(File outputFile, BufferedWriter log, AnakonCoordsSearchResult.AnakonItems.Item item, String typeOfError) throws IOException {
        log.write("Marking item " + item._source.id + "\n");

        var missing_coords = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Coords();
        var missing_partial = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Partial_coords();

        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath(), APPEND)) {
            csvWriter.write("\"" + item._source.id + "\",\"" +
                    item._source.df_255.stream().findFirst().orElse(missing_coords).c + "\",\"" +
                    item._source.df_034.stream().findFirst().orElse(missing_partial).d + "\",\"" +
                    item._source.df_034.stream().findFirst().orElse(missing_partial).e + "\",\"" +
                    item._source.df_034.stream().findFirst().orElse(missing_partial).f + "\",\"" +
                    item._source.df_034.stream().findFirst().orElse(missing_partial).g + "\",\"" +
                    typeOfError + "\"" + "\n");

            csvWriter.flush();
        }
    }

    private void writeCsvHeader(File outputFile) throws IOException {
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath())) {
            csvWriter.write("\"ID\",\"COORDINATES\",\"D\",\"E\",\"F\",\"G\",\"ERROR\"\n");
            csvWriter.flush();
        }
    }

    private Params parseInput(String inputData) throws JsonProcessingException {
        //load configuration
        Params params = objectMapper.readValue(inputData, Params.class);

        if (params.anakon_base_url == null) {
            throw new IllegalArgumentException("Missing required parameter: anakon_base_url");
        }

        return params;
    }

    private static <T> T postRequest(HttpClient httpClient, Class<T> resultClass, URI uri, String body) throws IOException, InterruptedException {
        String authHeader = null;
        //authHeader = httpBasicAuth("username", "password");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> rawResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (rawResponse.statusCode() != 200) {
            throw new RuntimeException("Unexpected response code " + rawResponse.statusCode() + " from " + uri + " with body " + rawResponse.body());
        }

        return objectMapper.readValue(rawResponse.body(), resultClass);
    }

    private static String httpBasicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }






}
