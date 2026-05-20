# console-bff runtime image (platform-console).
# Build prerequisite: ./gradlew :projects:platform-console:apps:console-bff:bootJar
# Tagging convention: console-bff:{git-sha} (per platform/deployment-policy.md).

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

COPY --chown=app:app build/libs/console-bff.jar /app/app.jar

USER app

EXPOSE 8080

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
