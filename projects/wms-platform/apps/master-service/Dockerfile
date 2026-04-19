# master-service runtime image.
# Build prerequisite: ./gradlew :projects:wms-platform:apps:master-service:bootJar
# Tagging convention: master-service:{git-sha} (per platform/deployment-policy.md).

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

COPY --chown=app:app build/libs/master-service.jar /app/app.jar

USER app

EXPOSE 8081

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
