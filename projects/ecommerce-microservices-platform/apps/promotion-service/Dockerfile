FROM eclipse-temurin:21-jre-alpine AS otel
ARG OTEL_VERSION=2.12.0
ARG OTEL_SHA256=bc7da8dd8789e396cb9547b671ee4eb64fdde0417a8393fe1f9279aa233b5599
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN echo "${OTEL_SHA256}  /otel/opentelemetry-javaagent.jar" | sha256sum -c -

FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
COPY apps/promotion-service/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ADR-MONO-041 D2: runtime FROM the shared monorepo/java-service-base:v1 image
# (alpine jre + curl + tini + non-root app user + WORKDIR /app); the base provides
# the `app` user, so per-service appgroup/appuser creation is dropped.
FROM monorepo/java-service-base:v1
COPY --from=otel /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
COPY --from=layers /app/dependencies ./
COPY --from=layers /app/spring-boot-loader ./
COPY --from=layers /app/snapshot-dependencies ./
COPY --from=layers /app/application ./
RUN chown -R app:app /app
USER app
EXPOSE 8092
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8092/actuator/health || exit 1
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
