FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the Spring Boot jar built by: ./gradlew bootJar
COPY build/libs/schoolable_backend-0.0.1-SNAPSHOT.jar app.jar

# Bind Spring to the port provided by the platform (e.g., Render sets PORT)
CMD ["sh", "-c", "java -jar /app/app.jar --server.port=${PORT}"]
