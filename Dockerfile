# ==========================================
# Stage 1: Build the application
# ==========================================
FROM eclipse-temurin:21-jdk-noble AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

# ==========================================
# Stage 2: Create the lean runtime image
# ==========================================
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

RUN apt-get update && apt-get install -y unzip

COPY --from=builder /app/build/libs/*.jar app.jar

# Unpack the jar, run the Playwright CLI, and clean up!
RUN unzip app.jar -d /tmp/app-extracted && \
    java -cp "/tmp/app-extracted/BOOT-INF/classes:/tmp/app-extracted/BOOT-INF/lib/*" com.microsoft.playwright.CLI install --with-deps && \
    rm -rf /tmp/app-extracted && \
    apt-get remove -y unzip && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
