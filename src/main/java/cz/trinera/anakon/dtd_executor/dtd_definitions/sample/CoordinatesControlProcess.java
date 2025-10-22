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

    private static void checkCoords(AnakonCoordsSearchResult.AnakonItems.Item item) {
        try {
            String coords = item._source.df_255.stream().findFirst().get().c;

            //needs to hand of warning
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

            try {
                checkPartedCoords(item, coords1, coords2, coords3, coords4);
            } catch (NullPointerException e) {
                throw new IllegalArgumentException("Missing parted coordinates in df_034", e);
            }

        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Missing expression with coordinates in df_255", e);
        }
    }

    private static void checkPartedCoords(AnakonCoordsSearchResult.AnakonItems.Item item, Coords coords1, Coords coords2, Coords coords3, Coords coords4) {
        String parted_coords_d = item._source.df_034.stream().findFirst().get().d;
        partedCoordMatch(coords1, parted_coords_d, "d");

        String parted_coords_e = item._source.df_034.stream().findFirst().get().e;
        partedCoordMatch(coords2, parted_coords_e, "e");

        String parted_coords_f = item._source.df_034.stream().findFirst().get().f;
        partedCoordMatch(coords3, parted_coords_f, "f");

        } catch (NullPointerException ex) {
            throw new IllegalArgumentException("Missing coordinates", ex);
        String parted_coords_g = item._source.df_034.stream().findFirst().get().g;
        partedCoordMatch(coords4, parted_coords_g, "g");
    }

    private static void partedCoordMatch(Coords coords, String parted_coords, String part) {
        if (!parted_coords.equals(coords.toCode())){
            throw new IllegalArgumentException("Coordinates mismatch on df_034 " + part + ": expected \"" + coords.toCode() + "\" but found \"" + parted_coords + "\"");
        }
    }

    private void writeSearchResult(File outputFile, BufferedWriter log, AnakonCoordsSearchResult.AnakonItems.Item item, String typeOfError) throws IOException {
        log.write("Marking item " + item._source.id + "\n");

        var missing_coords = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Coords();
        var missing_partial = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Parted_coords();

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
        var EN_extra_spaces = fillForTest("E15°20'17\"  --E  16°05'27\" /N   50°48'55\"--  N 50°30'23\"","E0152017","E0160527","N0504855","N0503023");
        try {
            checkCoords(EN_extra_spaces);
            System.out.println("EN_extra_spaces: true");
        } catch (IllegalArgumentException e) {
            System.out.println("EN_extra_spaces: false " + e.getMessage());
        }

        var EN_extra_char = fillForTest("E 16°20´38\"--E 16°40´34\"./N 49°29´50\"--N 49°10´53\"",null,null,null,null);
        try {
            checkCoords(EN_extra_char);
            System.out.println("EN_extra_char: false");
        } catch (IllegalArgumentException e) {
            System.out.println("EN_extra_char: " + e.getMessage().equals("Expression does not match pattern on: \"E 16°40´34\".\"") + " " + e.getMessage());
        }

        var empty = fillForTest(null,null,null,null,null);
        try {
            checkCoords(empty);
            System.out.println("empty: false");
        } catch (IllegalArgumentException e) {
            System.out.println("empty: " + e.getMessage().equals("Missing expression with coordinates in df_255") + " " + e.getMessage());
        }

        var missing_divider = fillForTest("E0121806","E0121806","E0132511","N0502322","N0500416"); //nkc20162856337
        try {
            checkCoords(missing_divider);
            System.out.println("missing_divider: false");
        } catch (IllegalArgumentException e) {
            System.out.println("missing_divider: " + e.getMessage().equals("Wrong coordinates format: failed to split by '/'") + " " + e.getMessage());
        }

        var missing_dashes = fillForTest("E012/1806",null,null,null,null);
        try {
            checkCoords(missing_dashes);
            System.out.println("missing_dashes: false");
        } catch (IllegalArgumentException e) {
            System.out.println("missing_dashes: " + e.getMessage().equals("Wrong coordinates format: failed to split by '--'") + " " + e.getMessage());
        }

        var EN_mising_nums = fillForTest("E 16°20´8\"--E 16°40´34\"/N 49°29´50\"--N 49°10´53\"",null,null,null,null);
        try {
            checkCoords(EN_mising_nums);
            System.out.println("EN_missing_nums: false");
        } catch (IllegalArgumentException e) {
            System.out.println("EN_missing_nums: " + e.getMessage().equals("Expression does not match pattern on: \"E 16°20´8\"\"") + " " + e.getMessage());
        }

        var CZ_has_brackets = fillForTest("(006°07´01\" z.d.--010°38´05\" v.d./051°38´43\" s.š.--042°00´59\" s.š.)].",null,null,null,null);
        try {
            checkCoords(CZ_has_brackets);
            System.out.println("CZ_has_brackets: true");
        } catch (IllegalArgumentException e) {
            System.out.println("CZ_has_brackets: false " + e.getMessage());
        }

        var EN_parted_coords_mismatch = fillForTest("E 15°20'17\"--E 16°05'27\"/N 50°48'55\"--N 50°30'23\"","E0152017","E0160527","N0504455","N0503023");
        try {
            checkCoords(EN_parted_coords_mismatch);
            System.out.println("EN_parted_coords_mismatch: false");
        } catch (IllegalArgumentException e) {
            System.out.println("EN_parted_coords_mismatch:  " + e.getMessage().equals("Coordinates mismatch on df_034 f: expected \"N0504855\" but found \"N0504455\"") + " " + e.getMessage());
        }

        var EN_missing_parted_coords = fillForTest("E 15°20'17\"--E 16°05'27\"/N 50°48'55\"--N 50°30'23\"","E0152017",null,"N0504455","N0503023");
        try {
            checkCoords(EN_missing_parted_coords);
            System.out.println("EN_missing_parted_coords: false");
        } catch (IllegalArgumentException e) {
            System.out.println("EN_missing_parted_coords:  " + e.getMessage().equals("Missing parted coordinates in df_034") + " " + e.getMessage());
        }
    }

    //temporary
    private static AnakonCoordsSearchResult.AnakonItems.Item fillForTest(String c_val, String d_val, String e_val, String f_val, String g_val) {
        var item = new AnakonCoordsSearchResult.AnakonItems.Item();
        var code = new AnakonCoordsSearchResult.AnakonItems.Item.Code();
        item._source = code;

        var c = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Coords();
        c.c = c_val;
        code.df_255 = new ArrayList<>();
        code.df_255.add(c);

        code.df_034 = new ArrayList<>();
        var coords = new AnakonCoordsSearchResult.AnakonItems.Item.Code.Parted_coords();
        coords.d = d_val;

        coords.e = e_val;

        coords.f = f_val;

        coords.g = g_val;
        code.df_034.add(coords);

        return item;
    }
}
