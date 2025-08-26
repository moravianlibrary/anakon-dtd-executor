# ===== Build stage =====
FROM gradle:8.11-jdk11 AS build

# Set working directory
WORKDIR /home/gradle/project

# Copy build files and source code
COPY build.gradle settings.gradle gradle* ./
COPY src ./src

# Build jar
RUN gradle clean build -x test --no-daemon --console=plain --stacktrace


# ===== Run stage =====
FROM openjdk:11-jdk-slim AS runtime

# Set working directory
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /home/gradle/project/build/libs/*.jar anakon-dtd-executor.jar

# Copy sample config file
# TODO: vybuildi ze vstupních proměnných
COPY src/main/resources/config-sample.properties ./config.properties

# Run the application
CMD ["java", "-jar", "anakon-dtd-executor.jar", "-c", "config.properties"]
