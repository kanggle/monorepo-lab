# outbound-service runtime image.
# Build prerequisite: ./gradlew :projects:wms-platform:apps:outbound-service:bootJar
# Tagging convention: outbound-service:{git-sha} (per platform/deployment-policy.md).
#
# (Added by TASK-MONO-200 — outbound-service was the only wms app without a
#  Dockerfile; the ecommerce↔wms fulfillment forward-leg demo containerises it.)
# ADR-MONO-041 D1: Spring Boot layered-jar — a code-only change rebuilds only the
# small `application` layer; the dependencies layer stays cached.

FROM eclipse-temurin:21-jre-alpine AS build
WORKDIR /workspace
COPY build/libs/outbound-service.jar /workspace/app.jar
RUN java -Djarmode=layertools -jar app.jar extract && rm -f app.jar

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app

# ADR-MONO-041 D1: copy layers low->high change frequency (deps first, app last).
COPY --from=build --chown=app:app /workspace/dependencies/ ./
COPY --from=build --chown=app:app /workspace/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/application/ ./

USER app

EXPOSE 8084

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
# ADR-MONO-041 D1: launch via JarLauncher (layered-equivalent of -jar app.jar).
CMD ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
