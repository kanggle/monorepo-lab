# TASK-MONO-294 — Container image build standard: layered-jar + shared base image + context narrowing (ADR-MONO-041 sweep)

**Status:** ready

**Type:** TASK-MONO (root — cross-project: 41 `projects/*/apps/*/Dockerfile` + a shared base-image artifact + per-project `.dockerignore`/compose `context`)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 for the mechanical per-service D1/D3 sweep (template application, behavior-preserving); **Opus 4.8** for the D2 base-image design (the gated architecture decision)

> Implements **ADR-MONO-041**. D1 (layered-jar) + D3 (context narrowing) are behavior-preserving and may land per-project. **D2 (shared base image) is gated on a separate user-explicit ACCEPT of ADR-MONO-041** before implementation — self-ACCEPT prohibited. Pilot (account-service) already validated and measured (818→638 MB; application layer 1.68 MB vs dependencies 94.9 MB).

---

## Goal

Standardize the 41 Spring Boot service Dockerfiles per ADR-MONO-041: (D1) Spring Boot **layered-jar** extraction so a code change rebuilds ~1–2 MB instead of the whole ~80–95 MB jar; (D2) a single **versioned shared base image** carrying the common JRE + `curl` + non-root user + DNS-wait helper, ending the 41-way runtime-prep drift; (D3) **narrow the build context** to the one service's jar and require BuildKit. Behavior-preserving — every container reaches the same `actuator/health` 200 via the same entrypoint.

## Scope

**In scope** — the 41 Java-service Dockerfiles under `projects/*/apps/*/Dockerfile` (every `*-service`, `gateway-service`, `master-service`, `batch-worker`, `console-bff`).

- **D1 (per-service, behavior-preserving)** — build stage adds `java -Djarmode=layertools -jar app.jar extract`; runtime stage replaces the single `COPY app.jar` with ordered `COPY --from=build` of `dependencies/` → `spring-boot-loader/` → `snapshot-dependencies/` → `application/`; entrypoint launches `org.springframework.boot.loader.launch.JarLauncher` (not `-jar app.jar`). All other lines preserved verbatim (DNS-wait `getent`, `HEALTHCHECK`, `USER app`, `EXPOSE`, `JAVA_OPTS`).
- **D2 (shared base image — ACCEPT-gated)** — one `Dockerfile` under a shared path builds `monorepo/java-service-base:<pinned>` (Debian-slim `eclipse-temurin:21-jre` + `curl` + `tini` + `app` uid/gid 1000 + DNS-wait helper). Every service `FROM` the pinned base; no service installs `curl`/creates the user independently. Version-pinned (content-hash or date tag), never `latest`.
- **D3 (per-project, behavior-preserving)** — replace the `**/build` + `!apps/*/build/libs` negation with a pure-positive single-service include; require `DOCKER_BUILDKIT=1` repo-wide.

**Out of scope** — the 2 Next.js frontends (`console-web`, `web-store`; separate toolchain, cache-mount work tracked separately), the `ecommerce/infra/elasticsearch` image, and any Jib/Buildpacks migration (ADR-041 alt-D, rejected).

## Rollout (staged — atomic PR per project to contain blast radius)

0. **Pilot** — account-service (✅ done, measured, artifacts removed).
1. **iam-platform** D1+D3 → rebuild + re-verify all iam containers against the live federation stack (health 200 + DNS-wait) → atomic PR.
2. Repeat per project: **wms → scm → finance → erp → ecommerce → fan → platform-console**, each its own atomic PR, each re-verified against the live stack.
3. **D2** — once D1 is repo-wide **and** ADR-041 is ACCEPTED: land the base image + repoint all 41 `FROM` in one atomic change; rebuild all; full-stack health sweep.

## Acceptance Criteria

- **AC-1 (D1 behavior-preserving)** — each swept service container reaches `actuator/health` 200 via its unchanged entrypoint/DNS-wait, identical to pre-sweep.
- **AC-2 (layer win)** — `docker history`/buildx progress shows `application` ≪ `dependencies`; a no-op code change rebuilds only `application` (cache hit on `dependencies`).
- **AC-3 (image size)** — each swept image materially smaller than pre-sweep (pilot −22%).
- **AC-4 (D3 context)** — buildx transfers only the one service's jar (~220 MB → single-jar context); no negation pattern remains; BuildKit required.
- **AC-5 (D2 base — gated)** — every consumer `FROM monorepo/java-service-base:<pinned>`; a base bump rebuilds all consumers; no per-service `curl`/user setup; base never `latest`.
- **AC-6 (no CI regression)** — each project's `./gradlew check` + image build GREEN; the federation-e2e nightly stays green per swept project.

## Related Specs / Contracts

- `docs/adr/ADR-MONO-041-container-image-build-standard.md` (the deciding ADR — D2 ACCEPT-gated).
- `platform/deployment-policy.md` (image tagging `{service}:{git-sha}` — base-image pin convention must align).
- No API/event contract change (build-layout only).

## Edge Cases / Failure Scenarios

- **F1 — Spring Boot version skew.** Layered-jar layer names + `JarLauncher` FQCN assume Boot 3.4.x (`org.springframework.boot.loader.launch.JarLauncher`). A service on a different Boot minor must be verified for the launcher package before sweeping (older Boot used `org.springframework.boot.loader.JarLauncher`).
- **F2 — console-bff base-OS change.** D2 moves console-bff Alpine→Debian-slim (musl→glibc); behavior-equivalent for a JRE workload but call it out in its sweep PR and re-verify health.
- **F3 — stale local base image masks a regression.** Pin the base by content hash/date and rebuild it before each consumer rebuild during D2; never rely on a cached `latest`.
- **F4 — legacy builder.** D3's pure-positive context still requires BuildKit for serial one-service builds (legacy builder's concurrent-build 502 + negation incompatibility — `env_console_demo_local_redeploy` trap5). The sweep must not reintroduce a legacy-builder-only path.
- **F5 — non-jar Dockerfiles swept by mistake.** The 2 Next.js + 1 ES Dockerfile are explicitly out of scope; a sweep script keying on `bootJar`/`COPY ...build/libs` must exclude them.
