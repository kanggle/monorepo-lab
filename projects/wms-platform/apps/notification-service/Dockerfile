# notification-service runtime image.
# Build prerequisite: ./gradlew :projects:wms-platform:apps:notification-service:bootJar
# Tagging convention: notification-service:{git-sha} (per platform/deployment-policy.md).

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

COPY --chown=app:app build/libs/notification-service.jar /app/app.jar

USER app

EXPOSE 8085

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
