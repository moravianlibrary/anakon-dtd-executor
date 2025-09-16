package cz.trinera.anakon.dtd_executor.dtd_definitions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.client.utils.URIBuilder;

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

public class DetectEmptyVolumesProcess implements Process{

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int PAGE_SIZE = 10;
    public static final int MAX_ISSUES = 0;

    private static class Params {
        public String kramerius_base_url;
        public Year year_start  = null;
        public Year year_end = null;
    }

    @Override
    public void run(UUID id, String type, String inputData, File logFile, File outputDir, File configFile, AtomicBoolean cancelRequested) throws Exception {
        try (BufferedWriter logWriter = Files.newBufferedWriter(logFile.toPath())) {
            try {
                //log parameters
                logWriter.write("    Running " + DetectEmptyVolumesProcess.class.getName() + "...\n");
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

                logWriter.write("    Exported data to " + outputFile.getName() + "\n");

            } catch (Exception e) {
                logWriter.write("    Error: " + e.getMessage() + "\n");
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

        if (params.kramerius_base_url == null) {
            throw new IllegalArgumentException("Missing required parameter: kramerius_base_url");
        }

        if (params.year_start != null &&
                params.year_end != null &&
                params.year_start.isAfter(params.year_end)){
            throw new IllegalArgumentException("Start date is after end date");
        }

        return params;
    }

    private void process(BufferedWriter log, Params params, File outputFile) throws Exception {
        URIBuilder volumesUriBuilder = buildVolumesUri(params);

        String currentCursorMark;
        String nextCursorMark = "*";
        KrameriusVolumesSearchResult volumes;
        HttpClient httpClient = HttpClient.newHttpClient();
        do {
            currentCursorMark = nextCursorMark;
            volumesUriBuilder.setParameter("cursorMark", currentCursorMark);

            URI volumeUrl = volumesUriBuilder.build();
            volumes = getRequest(log, httpClient, KrameriusVolumesSearchResult.class, volumeUrl);
            log.write("next cursor: " + volumes.nextCursorMark + "\n");
            log.write("docs: " + volumes.response.docs.size() + "\n");

            for (var volume : volumes.response.docs) {
                URIBuilder itemsUriBuilder = buildItemsUri(params, volume.pid);
                KrameriusItemsSearchResult items = getRequest(log, httpClient, KrameriusItemsSearchResult.class, itemsUriBuilder.build());
                log.write("items: " + items.response.numFound + "\n");

                if (items.response.numFound <= MAX_ISSUES) {
                    writeSearchResult(outputFile, log, volume, params);
                }
            }

            currentCursorMark = nextCursorMark;
            nextCursorMark = volumes.nextCursorMark;
        } while (!Objects.equals(nextCursorMark, currentCursorMark));
    }

    private static URIBuilder buildVolumesUri(Params params) throws URISyntaxException {
        return new URIBuilder(params.kramerius_base_url)
                .setParameter("sort", "pid asc")
                .setParameter("rows", String.valueOf(PAGE_SIZE))
                .setParameter("q", "model:periodicalvolume " +
                        (params.year_end!= null ? String.format("AND date_range_start.year:[* TO %d] ", params.year_end.getValue()) : "") +
                        (params.year_start!= null ? String.format("AND date_range_end.year:[%d TO *] ", params.year_start.getValue()) : "")
                );
    }

    private URIBuilder buildVolumeUri(Params params, String pid) throws URISyntaxException {
        return new URIBuilder(params.kramerius_base_url)
                .setParameter("q", "model:periodicalvolume " +
                        ("AND pid:" + pid.replace(":", "\\:")));
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
        log.write(resultClass.getSimpleName() + " response: " + rawResponse + "\n");
        if (rawResponse.statusCode() != 200) {
            throw new RuntimeException("Unexpected response code " + rawResponse.statusCode() + " with body " + rawResponse.body());
        }

        return objectMapper.readValue(rawResponse.body(), resultClass);
    }

    private void writeCsvHeader(File outputFile) throws IOException {
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath())) {
            csvWriter.write("\"PID\",\"PERIODIKUM\",\"ROK\",\"URL\"\n");
            csvWriter.flush();
        }
    }

    private void writeSearchResult(File outputFile, BufferedWriter log, KrameriusVolumesSearchResult.KrameriusResponse.Docs volume, Params params) throws IOException, URISyntaxException {
        try (BufferedWriter csvWriter = Files.newBufferedWriter(outputFile.toPath(), APPEND)) {
            csvWriter.write(volume.pid + "," +
                    volume.parentModel + "," +
                    getYearRange(volume) + "," +
                    buildVolumeUri(params, volume.pid).build() + "," + "\n");

            if (!Objects.equals(volume.parentModel, "periodical")) {
                log.write("WARNING: Parent model does not match \"periodical\" for volume: " +
                        volume.pid + ", is instead: " + volume.parentModel + "\n");
            }
            csvWriter.flush();
        }
    }

    private static String getYearRange(KrameriusVolumesSearchResult.KrameriusResponse.Docs docs) {
        String yearRange = "";
        if  (docs.startYear != null) {
            yearRange += docs.startYear.toString();

            if  (docs.endYear != null && !docs.startYear.equals(docs.endYear)) {
                yearRange += " - " + docs.endYear;
            }
        } else {
            if  (docs.endYear != null) {
                yearRange += docs.endYear;
            }
        }
        return yearRange;
    }
}
