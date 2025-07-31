package cz.trinera.anakon.dtd_executor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public class Config {

    public static final Integer EXECUTOR_VERSION = 2; // Increment this version every time the executor changes significantly

    private static Config instance;

    private final String jobsDir;
    private final String dbHost;
    private final int dbPort;
    private final String dbDatabase;
    private final String dbUser;
    private final String dbPassword;
    private final String dynamicConfigFile;

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
        jobsDir = getNonemptyProperty(properties, "jobs.dir");
        dbHost = getNonemptyProperty(properties, "db.host");
        dbPort = Integer.parseInt(getNonemptyProperty(properties, "db.port"));
        dbDatabase = getNonemptyProperty(properties, "db.database");
        dbUser = getNonemptyProperty(properties, "db.user");
        dbPassword = getNonemptyProperty(properties, "db.password");
        dynamicConfigFile = getNonemptyProperty(properties, "dynamic.config.file");
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

    public String getJobsDir() {
        return jobsDir;
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

    public String getDynamicConfigFile() {
        return dynamicConfigFile;
    }

    @Override
    public String toString() {
        return "Config{" +
                "executorVersion=" + EXECUTOR_VERSION +
                ", dbUser='" + dbUser + '\'' +
                ", dbDatabase='" + dbDatabase + '\'' +
                ", dbPort=" + dbPort +
                ", dbHost='" + dbHost + '\'' +
                ", jobsDir='" + jobsDir + '\'' +
                ", dynamicConfigFile='" + dynamicConfigFile + '\'' +
                '}';
    }
}
