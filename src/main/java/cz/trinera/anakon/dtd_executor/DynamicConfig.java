package cz.trinera.anakon.dtd_executor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DynamicConfig {
    @JsonProperty("executor_config")
    private ExecutorConfig executorConfig;
    @JsonProperty
    private List<Process> processes;

    public static DynamicConfig create(File dynamicConfigFile) throws IOException {
        final ObjectMapper mapper = JsonMapper
                .builder(new YAMLFactory())
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
        return mapper.readValue(dynamicConfigFile, DynamicConfig.class);

    }

    public ExecutorConfig getExecutorConfig() {
        return executorConfig;
    }

    static class ExecutorConfig {
        @JsonProperty("min_supported_executor_version")
        private int minSupportedExecutorVersion;
        @JsonProperty("max_concurrent_processes")
        private int maxConcurrentProcesses;
        @JsonProperty("polling_interval")
        private int pollingInterval;
        @JsonProperty("log_level")
        private LogLevel logLevel;
        @JsonProperty("process_definition_directory")
        private String processDefinitionDirectory;

        public int getMinSupportedExecutorVersion() {
            return minSupportedExecutorVersion;
        }

        public int getMaxConcurrentProcesses() {
            return maxConcurrentProcesses;
        }

        public int getPollingInterval() {
            return pollingInterval;
        }

        public LogLevel getLogLevel() {
            return logLevel;
        }

        public String getProcessDefinitionDirectory() {
            return processDefinitionDirectory;
        }
    }

    static class Process {
        @JsonProperty("type")
        private String type;
        @JsonProperty("class_name")
        private String className;
        @JsonProperty("description")
        private String description;
        @JsonProperty("inputs")
        private List<Map<String, Object>> inputs;
        @JsonProperty("outputs")
        private List<Map<String, Object>> outputs;
    }

    enum LogLevel {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

}
