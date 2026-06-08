# outbound-service runtime image.
# Build prerequisite: ./gradlew :projects:wms-platform:apps:outbound-service:bootJar
# Tagging convention: outbound-service:{git-sha} (per platform/deployment-policy.md).
#
# (Added by TASK-MONO-200 — outbound-service was the only wms app without a
#  Dockerfile; the ecommerce↔wms fulfillment forward-leg demo containerises it.)

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

COPY --chown=app:app build/libs/outbound-service.jar /app/app.jar

USER app

EXPOSE 8084

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
