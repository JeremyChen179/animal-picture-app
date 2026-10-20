# --- Build stage: compile and test with the Maven wrapper ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy the build definition first so dependency downloads are cached between code changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B package

# --- Runtime stage: JRE only, non-root user ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /app/data && chown -R app:app /app
COPY --from=build /build/target/animal-picture-app-*.jar /app/app.jar

USER app
EXPOSE 8080
# Lets Docker (and docker compose ps) report whether the app is actually up.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
# The embedded H2 database lives in /app/data; mount a volume there to keep pictures between runs.
VOLUME /app/data
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
