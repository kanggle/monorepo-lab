# gateway-service runtime image.
# Build prerequisite: ./gradlew :projects:wms-platform:apps:gateway-service:bootJar
# Tagging convention: gateway-service:{git-sha} (per platform/deployment-policy.md).

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

COPY --chown=app:app build/libs/gateway-service.jar /app/app.jar

USER app

EXPOSE 8080

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
