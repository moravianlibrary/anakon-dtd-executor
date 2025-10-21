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
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import static java.nio.file.StandardOpenOption.APPEND;

public class CoordinatesControlProcess implements Process {

    private static final int PAUSE_BETWEEN_VOLUME_REQUESTS_MS = 300;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int PAGE_SIZE = 100;
    private static final String LIBRARY = "mzk";

    public static class Params {
        public String anakon_base_url;
        public String dig_lib_base_code;
    }

    private static final String coordinate =
            "(?<degrees>[0-9]{1,3})°" +
            "(?<minutes>[0-9]{2})['`´]" +
            "(?<seconds>[0-9]{2})\"";

    private static final Pattern patternEN = Pattern.compile("(?<cardinal>[ENSW])\\s" + coordinate);
    private static final Pattern patternCZ = Pattern.compile(coordinate + "\\s(?<cardinal>v\\.d\\.|s\\.š\\.|j\\.š\\.|z\\.d\\.)");

    private static class AnakonCoordsSearchResult {

        public AnakonItems hits;

        static class AnakonItems {
            public List<Item> hits;
            public AnakonInfo total;

            static class Item {
                public Code _source;

                static class Code {
                    public List<Coords> df_255;
                    public List<Parted_coords> df_034;
                    public String id;

                    static class Coords {
                        public String c;
                    }

                    static class Parted_coords {
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
        String dig_lib_code = "MZK01";
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

        List<String> filters = buildFilters(params);

        do {
            String body = "{\"size\":" + size + ",\"track_total_hits\":true,\"from\":" + from + ",\"query\":{\"bool\":{\"must\":[" + String.join(",", filters) + "]}}}";
            result = postRequest(httpClient, AnakonCoordsSearchResult.class, URI.create(params.anakon_base_url), body);

            for (AnakonCoordsSearchResult.AnakonItems.Item item: result.hits.hits){
                try {
                    checkCoords(item);
                } catch (Exception ex) {
                    writeSearchResult(outputFile, log, item, ex.getMessage());
                }
            }
            
            from += size;
        } while (result.hits.total.value > from);
    }

    private static List<String> buildFilters(Params params) {
        List<String> filters = new ArrayList<>();
        filters.add("{\"term\": {\"library.keyword\": \"" + LIBRARY + "\"}}"); //library filter
        if (params.dig_lib_base_code != null) {
            filters.add("{\"term\": {\"base.keyword\": \"" + params.dig_lib_base_code + "\"}}"); //base filter
        }
        filters.add("{\"nested\": {\"path\": \"df_255\", \"query\": {\"bool\": {\"must\": [{\"exists\": {\"field\": \"df_255.c.keyword\"}}]}}}}"); //df_255.c filter
        return filters;
    }

    private void checkCoords(AnakonCoordsSearchResult.AnakonItems.Item item) {
        try {
            String coords = item._source.df_255.stream().findFirst().get().c;

            Matcher matcher = Pattern.compile("^[(\\[]*([^])]*)[])]*$").matcher(coords);
            if (matcher.matches()) {
                coords = matcher.group(1);
            }

            String[] parts = coords.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Wrong coordinates format: failed to split by '/'");
            }

            String[] firstPair = parts[0].split("--");
            String[] secondPair = parts[1].split("--");

            if (firstPair.length != 2 || secondPair.length != 2) {
                throw new IllegalArgumentException("Wrong coordinates format: failed to split by '--'");
            }

            Coords coords1 = Coords.parse(firstPair[0]);
            Coords coords2 = Coords.parse(firstPair[1]);
            Coords coords3 = Coords.parse(secondPair[0]);
            Coords coords4 = Coords.parse(secondPair[1]);

            //System.out.println(coords1 + "," + coords2 + "," + coords3 + "," + coords4);
            //check parted coords


        } catch (NullPointerException ex) {
            throw new IllegalArgumentException("Missing coordinates", ex);
        }
    }

    private void writeSearchResult(File outputFile, BufferedWriter log, AnakonCoordsSearchResult.AnakonItems.Item item, String typeOfError) throws IOException {
        log.write("Marking item " + item._source.id + "\n");

        var missing_coords = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Coords();
        var missing_partial = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Parted_coords();

        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath(), APPEND)) {
            csvWriter.write("\"" + item._source.id + "\",\"" +
                    item._source.df_255.stream().findFirst().orElse(missing_coords).c + "\",\"" +
                    (item._source.df_034 == null ? missing_partial : item._source.df_034.stream().findFirst().orElse(missing_partial).d) + "\",\"" +
                    (item._source.df_034 == null ? missing_partial : item._source.df_034.stream().findFirst().orElse(missing_partial).e) + "\",\"" +
                    (item._source.df_034 == null ? missing_partial : item._source.df_034.stream().findFirst().orElse(missing_partial).f) + "\",\"" +
                    (item._source.df_034 == null ? missing_partial : item._source.df_034.stream().findFirst().orElse(missing_partial).g) + "\",\"" +
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


    static class Coords {
        String cardinal;
        int degrees;
        int minutes;
        int seconds;

        static final Map<String, String> CARDINAL_MAP = Map.of(
                "v.d.", "E",
                "s.š.", "N",
                "j.š.", "S",
                "z.d.", "W"
        );

        public Coords(String cardinal, int degrees, int minutes, int seconds) {
            this.cardinal = normalizeCardinal(cardinal);

            if (degrees > 180){
                throw new IllegalArgumentException("Degrees out of range");
            }
            this.degrees = degrees;

            if (minutes > 59){
                throw new IllegalArgumentException("Minutes out of range");
            }
            this.minutes = minutes;

            if (seconds > 59){
                throw new IllegalArgumentException("Seconds out of range");
            }
            this.seconds = seconds;
        }

        private static String normalizeCardinal(String cardinal) {
            if (CARDINAL_MAP.containsKey(cardinal)) {
                return CARDINAL_MAP.get(cardinal);
            }
            return cardinal;
        }

        public static Coords parse(String inputData) {
            Matcher matcher = patternEN.matcher(inputData);
            if (!matcher.matches()) {
                matcher = patternCZ.matcher(inputData);
            }
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Expression does not match pattern on: \"" + inputData + "\"");
            }

            int degrees = Integer.parseInt(matcher.group("degrees"));
            int minutes = Integer.parseInt(matcher.group("minutes"));
            int seconds = Integer.parseInt(matcher.group("seconds"));
            String cardinal = matcher.group("cardinal");

            return new Coords(cardinal, degrees, minutes, seconds);
        }

        public String toCode() {
            DecimalFormat df = new DecimalFormat("0000000");
            return cardinal + df.format(seconds + minutes * 100L + degrees * 10000L);
        }
    }
}
