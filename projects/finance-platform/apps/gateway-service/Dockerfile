# gateway-service runtime image (finance-platform).
# Build prerequisite: ./gradlew :projects:finance-platform:apps:gateway-service:bootJar
# Tagging convention: gateway-service:{git-sha} (per platform/deployment-policy.md).
# ADR-MONO-041 D1: Spring Boot layered-jar — a code-only change rebuilds only the
# small `application` layer; the dependencies layer stays cached.

FROM eclipse-temurin:21-jre-alpine AS build
WORKDIR /workspace
COPY build/libs/gateway-service.jar /workspace/app.jar
RUN java -Djarmode=layertools -jar app.jar extract && rm -f app.jar

# ADR-MONO-041 D2: runtime FROM the shared monorepo/java-service-base:v1 image
# (alpine jre + curl + tini + non-root app user + WORKDIR /app).
FROM monorepo/java-service-base:v1

# ADR-MONO-041 D1: copy layers low->high change frequency (deps first, app last).
COPY --from=build --chown=app:app /workspace/dependencies/ ./
COPY --from=build --chown=app:app /workspace/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/application/ ./

USER app

EXPOSE 8080

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
# ADR-MONO-041 D1: launch via JarLauncher (layered-equivalent of -jar app.jar).
CMD ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
