# syntax=docker/dockerfile:1.7
# =============================================================================
# monorepo/java-service-base — shared runtime base for every Spring Boot service
# image in the monorepo (ADR-MONO-041 D2).
#
# Collapses the per-service runtime preparation (JRE + curl + tini + non-root
# `app` user + WORKDIR) — previously duplicated and DRIFTED across ~41 service
# Dockerfiles (debian/apt vs alpine/apk) — into ONE versioned image. A service
# Dockerfile now does:
#
#     FROM eclipse-temurin:21-jre-alpine AS build       # extract layered jar
#     ...
#     FROM monorepo/java-service-base:<pinned>          # runtime
#     COPY --from=build --chown=app:app ... ./          # the 4 Spring layers
#     ENTRYPOINT/CMD ...                                 # service-specific
#
# Standardized on Alpine (the majority distro across the fleet — 36 of 41
# services were already alpine; only iam was debian). See ADR-MONO-041 § 4
# (alternative B accepted-with-correction) for the debian→alpine reversal.
#
# Pin consumers to a content/date tag (e.g. :v1), NEVER :latest, so a base bump
# is an explicit, reviewable change across all consumers.
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

# curl  — used by every service HEALTHCHECK (wget-based services also have it).
# tini  — PID 1 init (reaps zombies; some services set it as ENTRYPOINT).
# app   — non-root runtime user, shared by all consumers.
RUN apk add --no-cache curl tini \
    && addgroup -S app \
    && adduser -S -G app app

WORKDIR /app
