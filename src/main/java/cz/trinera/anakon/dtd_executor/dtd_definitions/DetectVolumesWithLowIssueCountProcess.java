package cz.trinera.anakon.dtd_executor.dtd_definitions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardOpenOption.APPEND;

public class DetectVolumesWithLowIssueCountProcess implements Process {

    private static final int PAUSE_BETWEEN_VOLUME_REQUESTS_MS = 300;

    public static void main(String[] args) throws Exception {
        UUID uuid = UUID.randomUUID();
        System.out.println("Running " + DetectVolumesWithLowIssueCountProcess.class.getName() + " with UUID: " + uuid);
        File jobDir = new File("src/main/resources/local/detect_volumes_with_low_issue_count/" + uuid);
        jobDir.mkdirs();
        String krameriusBaseUrl = "https://api.kramerius.mzk.cz/search/api/client/v7.0/search";
        String dig_lib_code = "mzk";
        JSONObject input = new JSONObject();
        input.put("kramerius_base_url", krameriusBaseUrl);
        input.put("year_start", 2022);
        input.put("year_end", 2025);
        input.put("max_issue_count", 2);
        input.put("dig_lib_code", dig_lib_code);
        new DetectVolumesWithLowIssueCountProcess().run(
                UUID.randomUUID(),
                "detect-volumes-with-low-issue-count",
                input.toString(),
                new File(jobDir, "process.log"),
                jobDir,
                null,
                new AtomicBoolean(false)
        );
    }

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int PAGE_SIZE = 100;

    private static class Params {
        public String kramerius_base_url;
        public Year year_start = null;
        public Year year_end = null;
        public Integer max_issue_count;
        public String dig_lib_code;
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            try {
                //log parameters
                logWriter.write("Running " + DetectVolumesWithLowIssueCountProcess.class.getName() + "...\n");
                logWriter.write("ID: " + id + "\n");
                logWriter.write("Process type: " + type + "\n");
                logWriter.write("Input data: " + inputData + "\n");
                logWriter.write("Log file: " + logFile + "\n");
                logWriter.write("Output dir: " + outputDir + "\n");
                //logWriter.write("    Config file: " + configFile + "\n");
                //logWriter.write("    Cancel requested: " + cancelRequested.get() + "\n");
                logWriter.newLine();

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

    private static class KrameriusVolumesSearchResult {

        public KrameriusResponse response;
        public String nextCursorMark;

        static class KrameriusResponse {
            public List<Docs> docs;

            static class Docs {
                @JsonProperty("pid")
                public String pid;

                @JsonProperty("own_parent.model")
                public String parentModel;

                @JsonProperty("date_range_start.year")
                public Year startYear;

                @JsonProperty("date_range_end.year")
                public Year endYear;
            }
        }
    }

    private static class KrameriusItemsSearchResult {

        public KrameriusResponse response;

        static class KrameriusResponse {
            public List<Docs> docs;
            public int numFound;

            static class Docs {
                public String pid;
            }
        }
    }

    private static Params parseInput(String inputData) throws JsonProcessingException {
        //load configuration
        Params params = objectMapper.readValue(inputData, Params.class);

        checkRequired(params);

        if (params.max_issue_count < 0) {
            throw new IllegalArgumentException("Max issue count must be greater than or equal to 0");
        }

        if (params.year_start != null &&
                params.year_end != null &&
                params.year_start.isAfter(params.year_end)) {
            throw new IllegalArgumentException("Start date is after end date");
        }

        return params;
    }

    private static void checkRequired(Params params) {
        if (params.kramerius_base_url == null) {
            throw new IllegalArgumentException("Missing required parameter: kramerius_base_url");
        }

        if (params.max_issue_count == null) {
            throw new IllegalArgumentException("Missing required parameter: max_issue_count");
        }

        if (params.dig_lib_code == null) {
            throw new IllegalArgumentException("Missing required parameter: dig_lib_code");
        }
    }

    private void process(BufferedWriter log, Params params, File outputFile) throws Exception {
        URIBuilder volumesUriBuilder = buildVolumesUri(params);

        String currentCursorMark;
        String nextCursorMark = "*";
        KrameriusVolumesSearchResult volumes;
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(); //default HTTP_2 causes GOAWAY
        int volumeCount = 0;
        int markedVolumeCount = 0;
        do {
            currentCursorMark = nextCursorMark;
            volumesUriBuilder.setParameter("cursorMark", currentCursorMark);

            URI volumeUrl = volumesUriBuilder.build();
            volumes = getRequest(log, httpClient, KrameriusVolumesSearchResult.class, volumeUrl);
            //log.write("next cursor: " + volumes.nextCursorMark + "\n");
            //log.write("per-volumes: " + volumes.response.docs.size() + "\n");
            volumeCount += volumes.response.docs.size();

            for (var volume : volumes.response.docs) {
                URIBuilder itemsUriBuilder = buildItemsUri(params, volume.pid);
                KrameriusItemsSearchResult items = getRequest(log, httpClient, KrameriusItemsSearchResult.class, itemsUriBuilder.build());
                int numOfIssues = items.response.numFound;
                //log.write("per-items: " + numOfIssues + "\n");

                if (numOfIssues <= params.max_issue_count) {
                    writeSearchResult(outputFile, log, volume, numOfIssues, params);
                    markedVolumeCount++;
                }
            }

            currentCursorMark = nextCursorMark;
            nextCursorMark = volumes.nextCursorMark;
            if (volumeCount % 100 == 0) {
                log.write("Volumes processed: " + volumeCount + ", marked: " + markedVolumeCount + "\n");
            }
            Thread.sleep(PAUSE_BETWEEN_VOLUME_REQUESTS_MS); //to avoid overwhelming the Kramerius server
        } while (!Objects.equals(nextCursorMark, currentCursorMark));
        log.write("Total volumes processed: " + volumeCount + ", marked: " + markedVolumeCount + "\n");
    }

    private static URIBuilder buildVolumesUri(Params params) throws URISyntaxException {
        return new URIBuilder(params.kramerius_base_url)
                .setParameter("sort", "pid asc")
                .setParameter("rows", String.valueOf(PAGE_SIZE))
                .setParameter("q", "model:periodicalvolume " +
                        (params.year_end != null ? String.format("AND date_range_start.year:[* TO %d] ", params.year_end.getValue()) : "") +
                        (params.year_start != null ? String.format("AND date_range_end.year:[%d TO *] ", params.year_start.getValue()) : "")
                );
    }

    private String buildVolumeUrl(Params params, String pid) {
        return "https://www.digitalniknihovna.cz/" + params.dig_lib_code + "/uuid/" + pid;
    }

    private URIBuilder buildItemsUri(Params params, String pid) throws URISyntaxException {
        return new URIBuilder(params.kramerius_base_url)
                .setParameter("rows", String.valueOf(1))
                .setParameter("q", "model:periodicalitem " +
                        ("AND own_parent.pid:" + pid.replace(":", "\\:")));
    }

    private static <T> T getRequest(BufferedWriter log, HttpClient httpClient, Class<T> resultClass, URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().GET().uri(uri).build();

        HttpResponse<String> rawResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        //log.write(resultClass.getSimpleName() + " response: " + rawResponse + "\n");
        if (rawResponse.statusCode() != 200) {
            throw new RuntimeException("Unexpected response code " + rawResponse.statusCode() + " from " + uri + " with body " + rawResponse.body());
        }

        return objectMapper.readValue(rawResponse.body(), resultClass);
    }

    private void writeCsvHeader(File outputFile) throws IOException {
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath())) {
            csvWriter.write("\"PID\",\"PARENT_MODEL\",\"YEAR\",\"ISSUE_COUNT\",\"URL\"\n");
            csvWriter.flush();
        }
    }

    private void writeSearchResult(File outputFile, BufferedWriter log, KrameriusVolumesSearchResult.KrameriusResponse.Docs volume, int numOfIssues, Params params) throws IOException {
        log.write("Marking volume " + volume.pid + "\n");
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath(), APPEND)) {
            csvWriter.write("\"" + volume.pid + "\",\"" +
                    volume.parentModel + "\",\"" +
                    getYearRange(volume) + "\",\"" +
                    numOfIssues + "\",\"" +
                    buildVolumeUrl(params, volume.pid) + "\"" + "\n");

            if (!Objects.equals(volume.parentModel, "periodical")) {
                log.write("WARNING: Parent model does not match \"periodical\" for volume: " +
                        volume.pid + ", is instead: " + volume.parentModel + "\n");
            }
            csvWriter.flush();
        }
    }

    private static String getYearRange(KrameriusVolumesSearchResult.KrameriusResponse.Docs docs) {
        String yearRange = "";
        if (docs.startYear != null) {
            yearRange += docs.startYear.toString();

            if (docs.endYear != null && !docs.startYear.equals(docs.endYear)) {
                yearRange += " - " + docs.endYear;
            }
        } else {
            if (docs.endYear != null) {
                yearRange += docs.endYear;
            }
        }
        return yearRange;
    }
}
