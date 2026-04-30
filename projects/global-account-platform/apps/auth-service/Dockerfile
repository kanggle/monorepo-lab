# syntax=docker/dockerfile:1.7
# =============================================================================
# auth-service — multi-stage Docker build (context: monorepo root)
# HOST pre-build required:  ./gradlew :apps:auth-service:bootJar
# =============================================================================
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY apps/auth-service/build/libs/ ./libs/
RUN set -eux; cd libs; \
    jar="$(ls -1 *.jar | grep -v -E 'plain|sources|javadoc' | head -n1)"; \
    test -n "$jar" || (echo "bootJar not found. Run: ./gradlew :apps:auth-service:bootJar" >&2; exit 1); \
    cp "$jar" /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && (userdel -r ubuntu 2>/dev/null || true) \
 && groupadd -g 1000 app \
 && useradd -u 1000 -g 1000 -s /usr/sbin/nologin -M app
COPY --from=build /workspace/app.jar /app/app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8081
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1
# TASK-BE-048: block until sibling container hostnames resolve via Docker's
# embedded DNS BEFORE the JVM starts. JAVA_TOOL_OPTIONS (TTL=0) alone cannot
# defeat the JDK-internal InetAddress$CachedLookup path, which caches the
# first NXDOMAIN for the lifetime of the process and surfaces as
# `java.net.UnknownHostException: mysql` during Hikari/Flyway startup. Doing
# the resolution check in the shell (getent) before `exec java` guarantees
# the JVM never sees a negative lookup in the first place.
ENTRYPOINT ["sh","-c","until getent hosts mysql >/dev/null && getent hosts kafka >/dev/null && getent hosts redis >/dev/null; do echo 'waiting for DNS: mysql/kafka/redis'; sleep 1; done; exec java $JAVA_OPTS -jar /app/app.jar"]
