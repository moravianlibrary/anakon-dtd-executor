# Anakon DTD Executor

Anakon DTD Executor is a Java-based application designed to execute Anakon's DTDs (long running processes).
It requires a Java 11 runtime environment and is built using Gradle.


## Run
To run the project, you can use the following command:

```shell
./gradlew run --args="--config_file <path_to_config_file>"
```

## Build & Run
### Building the Project
To build the project, you can use the following command:
```shell
./gradlew clean build
```

### Running from the built JAR
After building the project, you can run the generated JAR file using the following command:
```shell
java -jar build/libs/anakon-dtd-executor-VERSION.jar --config_file <path_to_config_file>
``` 
Replace `VERSION` with the actual version number of the built JAR file.

## Configuration
See the sample configuration file in `src/main/resources/config-sample.properties` for details on how to configure the application.

## Docker Build & Run
### Building the Docker Image
To build the Docker image, use the following command:
```shell
docker build -t trinera/anakon-dtd-executor .
```

### Running the Docker Container
To run the Docker container, use the following command:
```shell
docker run \
  -e APP_DB_HOST=localhost \
  -e APP_DB_PORT=5432 \
  -e APP_DB_NAME=anakon_db \
  -e APP_DB_USERNAME=anakon_user \
  -e APP_DB_PASSWORD=anakon_password \
  -e APP_DYNAMIC_CONFIG_FILE=/my/dynamic/config/file.yaml \
  -e APP_PROCESSES_DEFINITION_DIR=/my/processes/definitions \
  -e APP_PROCESS_EXECUTION_DIR=/my/jobs/data \
trinera/anakon-dtd-executor
```


