# Multi-stage build for Spring Boot services.
# Replace {service} with the app module name. Exposed port defaults to 8081.

# Stage 1: OpenTelemetry agent
FROM eclipse-temurin:21-jre-alpine AS otel
ARG OTEL_VERSION=2.12.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

# Stage 2: Extract Spring Boot layers
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
COPY apps/{service}/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Final image
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=otel /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
COPY --from=layers /app/dependencies ./
COPY --from=layers /app/spring-boot-loader ./
COPY --from=layers /app/snapshot-dependencies ./
COPY --from=layers /app/application ./
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
