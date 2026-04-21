FROM eclipse-temurin:21-jre-alpine AS otel
ARG OTEL_VERSION=2.12.0
ARG OTEL_SHA256=bc7da8dd8789e396cb9547b671ee4eb64fdde0417a8393fe1f9279aa233b5599
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN echo "${OTEL_SHA256}  /otel/opentelemetry-javaagent.jar" | sha256sum -c -

FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
COPY apps/order-service/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

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
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8086/actuator/health || exit 1
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
