package cz.trinera.anakon.dtd_executor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public class Config {

    public static final Integer EXECUTOR_VERSION = 6; // Increment this version every time the executor changes significantly

    private static Config instance;

    private final String processExecutionDir;
    private final String dbHost;
    private final int dbPort;
    private final String dbDatabase;
    private final String dbUser;
    private final String dbPassword;
    private final String dynamicConfigFile;
    private final String processesDefinitionDir;

    public static void init(File propertiesFile) throws IOException {
        instance = new Config(propertiesFile);
    }

    public static Config instanceOf() {
        if (instance == null) {
            throw new RuntimeException("Config not initialized");
        }
        return instance;
    }

    private Config(File propertiesFile) throws IOException {
        Properties properties = new Properties();
        properties.load(Files.newInputStream(propertiesFile.toPath()));
        processExecutionDir = getNonemptyProperty(properties, "process.execution.dir");
        dbHost = getNonemptyProperty(properties, "db.host");
        dbPort = Integer.parseInt(getNonemptyProperty(properties, "db.port"));
        dbDatabase = getNonemptyProperty(properties, "db.database");
        dbUser = getNonemptyProperty(properties, "db.user");
        dbPassword = getNonemptyProperty(properties, "db.password");
        dynamicConfigFile = getNonemptyProperty(properties, "dynamic.config.file");
        processesDefinitionDir = getNonemptyProperty(properties, "processes.definition.dir");
    }


    private String getNonemptyProperty(Properties properties, String key) {
        if (!properties.containsKey(key)) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Empty property: " + key);
        }
        return value;
    }

    public String getProcessExecutionDir() {
        return processExecutionDir;
    }

    public String getDbHost() {
        return dbHost;
    }

    public int getDbPort() {
        return dbPort;
    }

    public String getDbDatabase() {
        return dbDatabase;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getDynamicConfigFile() throws IOException {
        return dynamicConfigFile;
    }

    public String getProcessesDefinitionDir() throws IOException {
        return processesDefinitionDir;
    }

    @Override
    public String toString() {
        return "Config{" +
                "executorVersion=" + EXECUTOR_VERSION +
                ", dbUser='" + dbUser + '\'' +
                ", dbDatabase='" + dbDatabase + '\'' +
                ", dbPort=" + dbPort +
                ", dbHost='" + dbHost + '\'' +
                ", processExecutionDir='" + processExecutionDir + '\'' +
                ", dynamicConfigFile='" + dynamicConfigFile + '\'' +
                ", processesDefinitionDir='" + processesDefinitionDir + '\'' +
                '}';
    }

    public static final class Utils {

        /**
         * Get existing readable directory from the given path.
         *
         * @param fileName
         * @return the existing readable directory
         * @throws IllegalArgumentException if the directory does not exist or is not a directory or is not readable
         */
        public static File getExistingReadableDir(String fileName) throws IOException {
            File dirFile = new File(fileName).getAbsoluteFile();
            if (!dirFile.exists()) {
                throw new IOException("Directory does not exist: " + dirFile);
            }
            if (!dirFile.isDirectory()) {
                throw new IOException("Not a directory: " + dirFile);
            }
            if (!Files.isReadable(dirFile.toPath())) {
                throw new IOException("Directory is not readable: " + dirFile);
            }

            return dirFile;
        }

        /**
         * Get existing readable file from the given path.
         *
         * @param fileName
         * @return the existing readable file
         * @throws IllegalArgumentException if the file does not exist or is not a file or is not readable
         */
        public static File getExistingReadableFile(String fileName) throws IOException {
            File file = new File(fileName).getAbsoluteFile();
            if (!file.exists()) {
                throw new IOException("File does not exist: " + file);
            }
            if (!file.isFile()) {
                throw new IOException("Not a file: " + file);
            }
            if (!Files.isReadable(file.toPath())) {
                throw new IOException("File is not readable: " + file);
            }

            return file;
        }
    }

}
