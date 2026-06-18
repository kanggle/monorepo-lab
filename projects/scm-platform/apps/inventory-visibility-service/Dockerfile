# Multi-stage build: Eclipse Temurin 21 JRE
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY build/libs/inventory-visibility-service.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ADR-MONO-041 D2: runtime FROM the shared monorepo/java-service-base:v1 image.
FROM monorepo/java-service-base:v1
COPY --from=builder /workspace/dependencies/ ./
COPY --from=builder /workspace/spring-boot-loader/ ./
COPY --from=builder /workspace/snapshot-dependencies/ ./
COPY --from=builder /workspace/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
