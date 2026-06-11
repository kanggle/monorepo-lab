# notification-service runtime image (fan-platform).
# Build prerequisite: ./gradlew :projects:fan-platform:apps:notification-service:bootJar
# Tagging convention: notification-service:{git-sha} (per platform/deployment-policy.md).
#
# Host-prebuilt jar pattern: the COPY below pulls build/libs/notification-service.jar,
# so a local redeploy MUST run `./gradlew bootJar` before `docker compose build`,
# else the image re-copies a stale jar (see project memory
# env_gap_docker_host_prebuilt_jar_redeploy_trap).

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

COPY --chown=app:app build/libs/notification-service.jar /app/app.jar

USER app

EXPOSE 8080

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
