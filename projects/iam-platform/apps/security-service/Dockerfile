# syntax=docker/dockerfile:1.7
# =============================================================================
# security-service — multi-stage Docker build (context: monorepo root)
# HOST pre-build required:  ./gradlew :apps:security-service:bootJar
# ADR-MONO-041 D1: Spring Boot layered-jar (code change rebuilds only the small
# `application` layer). D2: runtime FROM the shared monorepo/java-service-base
# image (alpine jre + curl + tini + non-root `app` user + WORKDIR /app).
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS build
WORKDIR /workspace
COPY apps/security-service/build/libs/ ./libs/
RUN set -eux; cd libs; \
    jar="$(ls -1 *.jar | grep -v -E 'plain|sources|javadoc' | head -n1)"; \
    test -n "$jar" || (echo "bootJar not found. Run: ./gradlew :apps:security-service:bootJar" >&2; exit 1); \
    cp "$jar" /workspace/app.jar; \
    cd /workspace; \
    java -Djarmode=layertools -jar app.jar extract; \
    rm -f app.jar

# Runtime: shared base provides jre-alpine + curl + tini + `app` user + WORKDIR.
FROM monorepo/java-service-base:v1
# ADR-MONO-041 D1: copy layers low->high change frequency (deps first, app last).
COPY --from=build --chown=app:app /workspace/dependencies/ ./
COPY --from=build --chown=app:app /workspace/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/application/ ./
USER app
EXPOSE 8084
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
  CMD curl -fsS http://localhost:8084/actuator/health || exit 1
# TASK-BE-048: block until sibling container hostnames resolve via Docker's
# embedded DNS BEFORE the JVM starts (getent in the shell, before `exec java`,
# so the JVM never caches a negative lookup during Hikari/Flyway startup).
# ADR-MONO-041 D1: launch via JarLauncher (layered-equivalent of -jar app.jar).
ENTRYPOINT ["sh","-c","until getent hosts mysql >/dev/null && getent hosts kafka >/dev/null && getent hosts redis >/dev/null; do echo 'waiting for DNS: mysql/kafka/redis'; sleep 1; done; exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
