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

//mzk:MZK01:001483797
//mzk:MZK01:nkc20203177803

public class MarcCoordinatesConsistencyCheckProcess implements Process {

    private static final int PAUSE_BETWEEN_VOLUME_REQUESTS_MS = 300;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final int PAGE_SIZE = 100;
    private static final String LIBRARY = "mzk";
    //temporary var to enable warnings
    private static final Boolean ENABLE_WARNINGS = false;

    public enum BaseCode {
        MZK01,
        MZK03
    }

    public static class Params {
        public BaseCode dig_lib_base_code;
    }

    private static final String coordinate =
            "(?<degrees>[0-9]{1,3})°" +
                    "(?<minutes>[0-9]{2})['`´]" +
                    "(?<seconds>[0-9]{2})\"";

    private static final Pattern patternEN = Pattern.compile("\\s*(?<cardinal>[ENSW])\\s*" + coordinate + "\\s*");
    private static final Pattern patternCZ = Pattern.compile("\\s*" + coordinate + "\\s*(?<cardinal>v\\.d\\.|s\\.š\\.|j\\.š\\.|z\\.d\\.)\\s*");

    private static class AnakonCoordsSearchResult {

        public AnakonItems hits;

        static class AnakonItems {
            public List<Item> hits;
            public AnakonInfo total;

            static class Item {
                public ItemData _source;

                static class ItemData {
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

    /**
     * For local testing
     */
    public static void main(String[] args) throws Exception {
        UUID uuid = UUID.randomUUID();
        System.out.println("Running " + MarcCoordinatesConsistencyCheckProcess.class.getName() + " with UUID: " + uuid);
        File jobDir = new File("src/main/resources/local/jobs-data/marc_coordinates_consistency_check/" + uuid);
        jobDir.mkdirs();
        File configFile = new File("src/main/resources/local/process_config/marc_coordinates_consistency_check.config");
        String dig_lib_code = "MZK01";
        JSONObject input = new JSONObject();
        input.put("dig_lib_base_code", dig_lib_code);
        new MarcCoordinatesConsistencyCheckProcess().run(
                UUID.randomUUID(),
                "coordinates_control",
                input.toString(),
                new File(jobDir, "process.log"),
                jobDir,
                configFile,
                new AtomicBoolean(false)
        );
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            try {
                //log parameters
                logWriter.write("    Running " + MarcCoordinatesConsistencyCheckProcess.class.getName() + "...\n");
                logWriter.write("    ID: " + id + "\n");
                logWriter.write("    Process type: " + type + "\n");
                logWriter.write("    Input data: " + inputData + "\n");
                logWriter.write("    Log file: " + logFile + "\n");
                logWriter.write("    Output dir: " + outputDir + "\n");
                logWriter.write("    Config file: " + configFile + "\n");
                logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");

                Params params = parseInput(inputData);

                Properties config = loadConfig(logWriter, configFile);

                File outputFile = new File(outputDir, "export.csv");
                writeCsvHeader(outputFile);

                logWriter.write("\n");
                Counters counters = process(logWriter, params, outputFile, config);

                logWriter.write("\n");
                logWriter.write("Processed records summary:\n");
                logWriter.write("--------------------------\n");
                logWriter.write("Items total:       " + counters.total + "\n");
                logWriter.write("Items with errors: " + counters.errors + "\n");
                double errorRatio = counters.total == 0 ? 0.0 : ((double) counters.errors / (double) counters.total) * 100.0;
                logWriter.write("Error ratio:       " + String.format("%.2f %%\n", errorRatio));

                logWriter.write("Exported data to " + outputFile.getName() + "\n");
            } catch (Throwable e) {
                logWriter.write("Error: " + e.getMessage() + "\n");
                throw e;
            } finally {
                logWriter.flush();
            }
        }
    }

    private Properties loadConfig(BufferedWriter logWriter, File configFile) throws IOException {
        if (configFile == null || !configFile.exists()) {
            throw new IllegalArgumentException("No configuration file provided");
        }

        logWriter.write("Loading config file: " + configFile.getAbsolutePath() + "\n");
        Properties properties = new Properties();
        properties.load(Files.newInputStream(configFile.toPath()));

        if (!properties.containsKey("anakon_base_url")) {
            throw new IllegalArgumentException("Missing property 'anakon_base_url' in configuration file");
        }
        if (!properties.containsKey("anakon_username")) {
            throw new IllegalArgumentException("Missing property 'anakon_username' in configuration file");
        }
        if (!properties.containsKey("anakon_password")) {
            throw new IllegalArgumentException("Missing property 'anakon_password' in configuration file");
        }

        logWriter.write("Configuration properties:\n");
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (key.equals("anakon_password")) {
                logWriter.write(key + "=" + "*".repeat(value.length()) + "\n");
            } else {
                logWriter.write(key + "=" + value + "\n");
            }
        }

        return properties;
    }

    private Counters process(BufferedWriter log, Params params, File outputFile, Properties config) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(); //default HTTP_2 causes GOAWAY
        AnakonCoordsSearchResult result;
        int size = PAGE_SIZE;
        int from = 0;

        List<String> filters = buildFilters(params);
        Counters counters = new Counters();

        do {
            String body = "{\"size\":" + size + ",\"track_total_hits\":true,\"from\":" + from + ",\"query\":{\"bool\":{\"must\":[" + String.join(",", filters) + "]}}}";
            result = postAnakonSearchRequest(httpClient, AnakonCoordsSearchResult.class, body, config);

            for (AnakonCoordsSearchResult.AnakonItems.Item item : result.hits.hits) {
                String errMessage = checkKeysSize(item._source);
                if (errMessage != null) {
                    writeSearchResult(outputFile, log, item, errMessage, -1);
                    continue;
                }

                for (int i = 0; i < item._source.df_255.size(); i++) {
                    ItemProcessor processor = new ItemProcessor();
                    boolean success = processor.process(item._source, i);
                    if (!success) {
                        writeSearchResult(outputFile, log, item, processor.getErrorMessage(i), i);
                        counters.errors += 1;
                    }
                }
            }
            counters.total = result.hits.total.value;
            counters.processed += result.hits.hits.size();
            log.write(String.format("Processed %d/%d records (%d errors)\n", counters.processed, counters.total, counters.errors));
            from += size;
        } while (result.hits.total.value > from);
        return counters;
    }

    private static String checkKeysSize(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item) {
        if (item.df_255 == null) {
            return "Missing field df_255";
        }

        if (item.df_034 == null) {
            return "Missing field df_034";
        }

        if (item.df_255.size() != item.df_034.size()) {
            return "The number of df_255s and df_034s do not match: something might be missing";
        }
        return null;
    }

    private static class ItemProcessor {
        private final List<String> errors = new ArrayList<>();
        private Coords coords1;
        private Coords coords2;
        private Coords coords3;
        private Coords coords4;

        public boolean process(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item, int index) {
            if (checkIfAbsent(item, index)) {
                return true;
            }

            if (!checkCoords(item, index)) {
                return false;
            }

            if (!checkPartedCoords(item, index)) {
                return false;
            }

            return errors.isEmpty();
        }

        private boolean checkIfAbsent(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item, int index) {
            return item.df_255.get(index).c == null &&
                    item.df_034.get(index).d == null &&
                    item.df_034.get(index).e == null &&
                    item.df_034.get(index).f == null &&
                    item.df_034.get(index).g == null;
        }

        private boolean checkCoords(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item, int index) {
            String coords = item.df_255.get(index).c;

            if (coords == null) {
                errors.add("Missing expression with coordinates in df_255");
                return false;
            }

            Matcher matcher = Pattern.compile("(^\\W+|(?<=[šd]\\.|\")\\W+$)").matcher(coords);
            while (matcher.find() && ENABLE_WARNINGS) {
                errors.add("[Warning]: Extra characters \"" + matcher.group() + "\" around the expression");
            }
            coords = matcher.replaceAll("");

            String[] parts = coords.split("/");
            if (parts.length != 2) {
                errors.add("Wrong coordinates format: failed to split by '/'");
                return false;
            }

            String[] firstPair = parts[0].split("--");
            String[] secondPair = parts[1].split("--");

            if (firstPair.length != 2 || secondPair.length != 2) {
                errors.add("Wrong coordinates format: failed to split by '--'");
                return false;
            }

            coords1 = new Coords().parse(firstPair[0]);
            errors.addAll(coords1.getErrors());
            coords2 = new Coords().parse(firstPair[1]);
            errors.addAll(coords2.getErrors());
            coords3 = new Coords().parse(secondPair[0]);
            errors.addAll(coords3.getErrors());
            coords4 = new Coords().parse(secondPair[1]);
            errors.addAll(coords4.getErrors());

            return errors.isEmpty();
        }

        private boolean checkPartedCoords(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item, int index) {
            String parted_coords_d = item.df_034.get(index).d;
            matchPartedCoord(coords1, parted_coords_d, "d", index);

            String parted_coords_e = item.df_034.get(index).e;
            matchPartedCoord(coords2, parted_coords_e, "e", index);

            String parted_coords_f = item.df_034.get(index).f;
            matchPartedCoord(coords3, parted_coords_f, "f", index);

            String parted_coords_g = item.df_034.get(index).g;
            matchPartedCoord(coords4, parted_coords_g, "g", index);

            return errors.isEmpty();
        }

        private void matchPartedCoord(Coords coords, String parted_coords, String part, int index) {
            if (!Objects.equals(parted_coords, coords.toCode())) {
                errors.add("Coordinates mismatch on df_034 " + part + ": expected \"" + coords.toCode() + "\" but found \"" + parted_coords + "\"");
            }
        }

        public String getErrorMessage(int index) {
            if (!errors.isEmpty()) {
                return "Index " + index + ": " + String.join("; ", errors);
            }

            return null;
        }
    }

    private static List<String> buildFilters(Params params) {
        List<String> filters = new ArrayList<>();
        filters.add("{\"term\": {\"library.keyword\": \"" + LIBRARY + "\"}}"); //library filter
        if (params.dig_lib_base_code != null) {
            filters.add("{\"term\": {\"base.keyword\": \"" + params.dig_lib_base_code + "\"}}"); //base filter
        }
        filters.add("{\"bool\":{\"should\":[{\"nested\":{\"path\":\"df_255\",\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"df_255.c.keyword\"}}]}}}},{\"bool\":{\"should\":[{\"nested\":{\"path\":\"df_034\",\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"df_034.d.keyword\"}}]}}}},{\"nested\":{\"path\":\"df_034\",\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"df_034.e.keyword\"}}]}}}},{\"nested\":{\"path\":\"df_034\",\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"df_034.f.keyword\"}}]}}}},{\"nested\":{\"path\":\"df_034\",\"query\":{\"bool\":{\"must\":[{\"exists\":{\"field\":\"df_034.g.keyword\"}}]}}}}],\"minimum_should_match\":1}}],\"minimum_should_match\":1}}"); //df_255.c filter
        return filters;
    }

    private void writeSearchResult(File outputFile, BufferedWriter log, AnakonCoordsSearchResult.AnakonItems.Item item, String errorMessage, int index) throws IOException {
        var missing_coords = new AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Coords();
        var missing_partial = new AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Parted_coords();

        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath(), APPEND)) {
            //TODO: uklidit, nepřehledné
            if (index == -1) {
                csvWriter.write("\"" + item._source.id + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_255 == null ? null : item._source.df_255.stream().findFirst().orElse(missing_coords).c)) //cele souradnice
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).d))
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).e))
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).f))
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).g))
                        + "\",\"" +
                        escapeCsvUnsafeChars(errorMessage)
                        + "\"" + "\n");

            } else {
                csvWriter.write("\"" + item._source.id + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_255 == null ? null : item._source.df_255.get(index).c))
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.get(index).d))
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.get(index).e))
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.get(index).f))
                        + "\",\"" +
                        escapeCsvUnsafeChars((item._source.df_034 == null ? null : item._source.df_034.get(index).g))
                        + "\",\"" +
                        escapeCsvUnsafeChars(errorMessage)
                        + "\"" + "\n");
            }

            csvWriter.flush();
        }
    }

    private String escapeCsvUnsafeChars(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("\"", "\"\"").replaceAll(",", ";");
    }

    private void writeCsvHeader(File outputFile) throws IOException {
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath())) {
            //počet řádků zde musí sedět na počet sloupců v writeSearchResult
            csvWriter.write("\"ID záznamu (instituce:aleph_báze:pole_001)\"");
            csvWriter.write(",\"Rozsah zeměpisných délek a šířek (255.c)\"");
            csvWriter.write(",\"Zeměpisná délka: nejzápadnější bod (034.d)\""); //MOST_WESTERN_LONGITUDE
            csvWriter.write(",\"Zeměpisná délka: nejvýchodnější bod(034.e)\""); //MOST_EASTERN_LONGITUDE
            csvWriter.write(",\"Zeměpisná šířka: nejsevernější bod (034.f)\""); //MOST_NORTHERN_LATITUDE
            csvWriter.write(",\"Zeměpisná šířka: nejjižnější bod (034.g)\""); //MOST_SOUTHERN_LATITUDE
            csvWriter.write(",\"Chybové hlášky\"\n");
            csvWriter.flush();
        }
    }

    private Params parseInput(String inputData) throws JsonProcessingException {
        //load configuration
        Params params = objectMapper.readValue(inputData, Params.class);
        //check here for required parameters, if there are any
        /*if (params.anakon_base_url == null) {
            throw new IllegalArgumentException("Missing required parameter: anakon_base_url");
        }*/
        return params;
    }

    private static <T> T postAnakonSearchRequest(HttpClient httpClient, Class<T> resultClass, String body, Properties config) throws IOException, InterruptedException {
        String anakonSearchUrl = config.getProperty("anakon_base_url") + "/search";
        String authHeader = httpBasicAuth(config.getProperty("anakon_username"), config.getProperty("anakon_password"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(anakonSearchUrl))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> rawResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (rawResponse.statusCode() != 200) {
            throw new RuntimeException("Unexpected response code " + rawResponse.statusCode() + " from " + anakonSearchUrl + " with body " + rawResponse.body());
        }

        return objectMapper.readValue(rawResponse.body(), resultClass);
    }

    private static String httpBasicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }


    private static class Coords {
        private String cardinal;
        private int degrees;
        private int minutes;
        private int seconds;
        private final List<String> errors = new ArrayList<>();

        static final Map<String, String> CARDINAL_MAP = Map.of(
                "v.d.", "E",
                "s.š.", "N",
                "j.š.", "S",
                "z.d.", "W"
        );

        public Coords() {
        }

        private static String normalizeCardinal(String cardinal) {
            if (CARDINAL_MAP.containsKey(cardinal)) {
                return CARDINAL_MAP.get(cardinal);
            }
            return cardinal;
        }

        public Coords parse(String inputData) {
            //needs to allow white space
            Matcher matcher = patternEN.matcher(inputData);
            if (!matcher.matches()) {
                matcher = patternCZ.matcher(inputData);
            }
            if (!matcher.matches()) {
                errors.add("Expression does not match pattern on: \"" + inputData + "\"");
                return this;
            }

            int degrees = Integer.parseInt(matcher.group("degrees"));
            int minutes = Integer.parseInt(matcher.group("minutes"));
            int seconds = Integer.parseInt(matcher.group("seconds"));
            String cardinal = matcher.group("cardinal");

            createCoords(cardinal, degrees, minutes, seconds);
            return this;
        }

        private void createCoords(String cardinal, int degrees, int minutes, int seconds) {
            this.cardinal = normalizeCardinal(cardinal);
            List<String> latitude = List.of("N", "S");
            List<String> longitude = List.of("E", "W");

            if (latitude.contains(this.cardinal) && degrees > 90) {
                errors.add("Degrees out of range: " + degrees);
            }

            if (longitude.contains(this.cardinal) && degrees > 180) {
                errors.add("Degrees out of range: " + degrees);
            }
            this.degrees = degrees;

            if (minutes > 59) {
                errors.add("Minutes out of range: " + minutes);
            }
            this.minutes = minutes;

            if (seconds > 59) {
                errors.add("Seconds out of range: " + seconds);
            }
            this.seconds = seconds;

        }

        public String toCode() {
            DecimalFormat df = new DecimalFormat("0000000");
            return cardinal + df.format(seconds + minutes * 100L + degrees * 10000L);
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    private static class Counters {
        public int total = 0;
        public int processed = 0;
        public int errors = 0;
    }
}
