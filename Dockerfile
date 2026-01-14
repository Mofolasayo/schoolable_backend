#
# Multi-stage build: build the jar, then run it
#

# ---- Build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy build scripts and sources
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY src src

# Build the Spring Boot fat jar
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:17-jre
LABEL service="schoolable-backend"
WORKDIR /app

# Copy the jar from the build stage
COPY --from=build /workspace/build/libs/schoolable_backend-0.0.1-SNAPSHOT.jar app.jar

# Bind Spring to the port provided by the platform (e.g., Render sets PORT)
CMD ["sh", "-c", "java -jar /app/app.jar --server.port=${PORT:-8081}"]
