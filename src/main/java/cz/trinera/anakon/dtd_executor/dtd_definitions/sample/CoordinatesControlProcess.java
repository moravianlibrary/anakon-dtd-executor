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
    //temporary var to enable warnings
    private static final Boolean ENABLE_WARNINGS = true;

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
        //test();
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
        AnakonCoordsSearchResult result;
        int size = PAGE_SIZE;
        int from = 0;

        List<String> filters = buildFilters(params);

        do {
            String body = "{\"size\":" + size + ",\"track_total_hits\":true,\"from\":" + from + ",\"query\":{\"bool\":{\"must\":[" + String.join(",", filters) + "]}}}";
            result = postRequest(httpClient, AnakonCoordsSearchResult.class, URI.create(params.anakon_base_url), body);

            boolean success;
            for (AnakonCoordsSearchResult.AnakonItems.Item item: result.hits.hits){
                ItemProcessor processor = new ItemProcessor();
                success = processor.process(item._source);
                if (!success) {
                    writeSearchResult(outputFile, log, item, processor.getErrorMessage());
                }
            }

            from += size;
        } while (result.hits.total.value > from);
    }

    private static class ItemProcessor{
        private final Map<Integer, List<String>> errors = new HashMap<>();
        private Coords coords1;
        private Coords coords2;
        private Coords coords3;
        private Coords coords4;

        public boolean process(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item) {
            if (!checkKeysSize(item)){
                return false;
            }

            for (int i = 0; i < item.df_255.size(); i++) {
                if (!checkCoords(item, i)){
                    return false;
                }
                if (!checkPartedCoords(item, i)) {
                    return false;
                }
            }

            return errors.isEmpty();
        }

        private boolean checkKeysSize(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item) {
            if (item.df_034 == null) {
                errors.put(-1, List.of("Missing field df_034"));
                return false;
            }

            if (item.df_255.size() != item.df_034.size()) {
                errors.put(-1, List.of("The number of df_255s and df_034s do not match: something might be missing"));
                return false;
            }
            return true;
        }

        private boolean checkCoords(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item, int index) {
            errors.put(index, new ArrayList<>());
            List<String> errorMessages = errors.get(index);

            String coords;

            coords = item.df_255.get(index).c;

            if (coords == null) {
                errorMessages.add("Missing expression with coordinates in df_255");
                return false;
            }

            Matcher matcher = Pattern.compile("(^\\W+|(?<=[šd]\\.|\")\\W+$)").matcher(coords);
            while (matcher.find() && ENABLE_WARNINGS) {
                errorMessages.add("[Warning]: Extra characters \"" + matcher.group() + "\" around the expression");
            }
            coords = matcher.replaceAll("");

            String[] parts = coords.split("/");
            if (parts.length != 2) {
                errorMessages.add("Wrong coordinates format: failed to split by '/'");
                return false;
            }

            String[] firstPair = parts[0].split("--");
            String[] secondPair = parts[1].split("--");

            if (firstPair.length != 2 || secondPair.length != 2) {
                errorMessages.add("Wrong coordinates format: failed to split by '--'");
                return false;
            }

            try {
                coords1 = Coords.parse(firstPair[0]);
                coords2 = Coords.parse(firstPair[1]);
                coords3 = Coords.parse(secondPair[0]);
                coords4 = Coords.parse(secondPair[1]);
            } catch (IllegalArgumentException e) {
                errorMessages.add(index, e.getMessage());
                return false;
            }

            return true;
        }

        private boolean checkPartedCoords(AnakonCoordsSearchResult.AnakonItems.Item.ItemData item, int index) {
            boolean success;

            String parted_coords_d = item.df_034.get(index).d;
            success = matchPartedCoord(coords1, parted_coords_d, "d", index);

            String parted_coords_e = item.df_034.get(index).e;
            success = success && matchPartedCoord(coords2, parted_coords_e, "e", index);

            String parted_coords_f = item.df_034.get(index).f;
            success = success && matchPartedCoord(coords3, parted_coords_f, "f", index);

            String parted_coords_g = item.df_034.get(index).g;
            success = success && matchPartedCoord(coords4, parted_coords_g, "g", index);

            return success;
        }

        private boolean matchPartedCoord(Coords coords, String parted_coords, String part, int index) {
            if (!Objects.equals(parted_coords, coords.toCode())){
                errors.get(index).add("Coordinates mismatch on df_034 " + part + ": expected \"" + coords.toCode() + "\" but found \"" + parted_coords + "\"");
                return false;
            }
            return true;
        }

        public String getErrorMessage(){
            if (errors.containsKey(-1)){
                 return String.join(",", errors.remove(-1));
            }

            StringBuilder errorMessage = new StringBuilder();
            for (int index: errors.keySet()){
                if (!errors.get(index).isEmpty()) {
                    errorMessage.append("index: ").append(index).append(": ").append(String.join("; ", errors.get(index))).append("| ");
                }
            }

            return errorMessage.toString();
        }
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

    private void writeSearchResult(File outputFile, BufferedWriter log, AnakonCoordsSearchResult.AnakonItems.Item item, String typeOfError) throws IOException {
        log.write("Marking item " + item._source.id + "\n");

        var missing_coords = new AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Coords();
        var missing_partial = new AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Parted_coords();

        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath(), APPEND)) {
            csvWriter.write("\"" + item._source.id + "\",\"" +
                    item._source.df_255.stream().findFirst().orElse(missing_coords).c + "\",\"" +
                    (item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).d) + "\",\"" +
                    (item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).e) + "\",\"" +
                    (item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).f) + "\",\"" +
                    (item._source.df_034 == null ? null : item._source.df_034.stream().findFirst().orElse(missing_partial).g) + "\",\"" +
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

            //these may be wrong CHECK!!
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
            //needs to allow white space
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



    //temporary tests for regex
    private static void test(){
        ItemProcessor processor = new ItemProcessor();
        var EN_extra_spaces = fillForTest("E15°20'17\"  --E  16°05'27\" /N   50°48'55\"--  N 50°30'23\"","E0152017","E0160527","N0504855","N0503023");
        System.out.println("EN_extra_spaces: " + processor.process(EN_extra_spaces) + "  -" + processor.getErrorMessage());


        var EN_extra_char = fillForTest("E 16°20´38\"--E 16°40´34\"./N 49°29´50\"--N 49°10´53\"",null,null,null,null);
        System.out.println("EN_extra_char: " + !processor.process(EN_extra_char) + "  -" + processor.getErrorMessage());


        var empty = fillForTest(null,null,null,null,null);
            System.out.println("empty: " + !processor.process(empty) + "  -" + processor.getErrorMessage());


        var missing_divider = fillForTest("E0121806","E0121806","E0132511","N0502322","N0500416"); //nkc20162856337
        System.out.println("missing_divider: " + !processor.process(missing_divider) + "  -" + processor.getErrorMessage());


        var missing_dashes = fillForTest("E012/1806",null,null,null,null);
        System.out.println("missing_dashes: " + !processor.process(missing_dashes) + "  -" + processor.getErrorMessage());


        var EN_mising_nums = fillForTest("E 16°20´8\"--E 16°40´34\"/N 49°29´50\"--N 49°10´53\"",null,null,null,null);
        System.out.println("EN_missing_nums: " + !processor.process(EN_mising_nums) + "  -" + processor.getErrorMessage());


        var CZ_has_brackets = fillForTest("(006°07´01\" z.d.--010°38´05\" v.d./051°38´43\" s.š.--042°00´59\" s.š.)].",null,null,null,null);
        System.out.println("CZ_has_brackets: " + !processor.process(CZ_has_brackets) + "  -" + processor.getErrorMessage());


        var EN_parted_coords_mismatch = fillForTest("E 15°20'17\"--E 16°05'27\"/N 50°48'55\"--N 50°30'23\"","E0152017","E0160527","N0504455","N0503023");
        System.out.println("EN_parted_coords_mismatch: " + !processor.process(EN_parted_coords_mismatch) + "  -" + processor.getErrorMessage());


        var EN_missing_parted_coords = fillForTest("E 15°20'17\"--E 16°05'27\"/N 50°48'55\"--N 50°30'23\"","E0152017",null,"N0504455","N0503023");
        System.out.println("EN_missing_parted_coords: " + !processor.process(EN_missing_parted_coords) + "  -" + processor.getErrorMessage());

    }

    //temporary
    private static AnakonCoordsSearchResult.AnakonItems.Item.ItemData fillForTest(String c_val, String d_val, String e_val, String f_val, String g_val) {
        var data = new AnakonCoordsSearchResult.AnakonItems.Item.ItemData();

        var c = new AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Coords();
        c.c = c_val;
        data.df_255 = new ArrayList<>();
        data.df_255.add(c);

        data.df_034 = new ArrayList<>();
        var coords = new AnakonCoordsSearchResult.AnakonItems.Item.ItemData.Parted_coords();
        coords.d = d_val;
        coords.e = e_val;
        coords.f = f_val;
        coords.g = g_val;
        data.df_034.add(coords);

        return data;
    }
}
