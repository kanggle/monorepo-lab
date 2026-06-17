# ADR-MONO-041 — Container image build standard: layered-jar extraction, a shared Java-service base image, and per-service build-context narrowing

**Status:** ACCEPTED

**Date:** 2026-06-17

**Accepted:** 2026-06-17 — user-explicit *"ACCEPT"* of the full ADR (including D2) after the PROPOSED D1–D4 + rollout were presented for the ACCEPT gate; the gate was honored (PROPOSED record presented, review awaited, **NOT a self-ACCEPT**). D1/D2/D3 **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide. The ACCEPT covers D2's *direction*; per the §2.D4 rollout, D2 (shared base image) still **lands last** (after D1 is repo-wide), but is **not separately re-gated** — this ACCEPT discharged that gate. Implementation = TASK-MONO-294 (D1+D3 swept per-project first, iam-platform leading).

**Decision driver:** The monorepo's 41 Java-service Dockerfiles each rebuild the **entire** application image layer on every code change (single fat-jar `COPY`), repeat the same runtime preparation (JRE + `curl` + non-root user) as a per-service layer, and — for the iam-family `.dockerignore` (`**/build` + `!apps/*/build/libs`) — drag **all** sibling services' jars into every build context (~220 MB). The result is slow incremental rebuilds and oversized images, which compounds on this Windows host where the Rancher/WSL2 vhdx never shrinks (project memory `env_rancher_desktop_vhdx_no_shrink`). A pilot on `iam-platform/account-service` (2026-06-17) proved the fix: Spring Boot layered-jar extraction drops the rebuilt-on-code-change bytes from the full image to a **1.68 MB** application layer (vs a cached **94.9 MB** dependencies layer — a ~50× reduction in volatile bytes) and shrinks the image **818 MB → 638 MB (−22%)**, behavior-preserving.

**Supersedes:** none. **Superseded by:** none.

**Family:** [ADR-MONO-013](ADR-MONO-013-unified-platform-console.md) (the console images this standardizes), [ADR-MONO-018](ADR-MONO-018-federation-e2e-harness.md) (the federation-e2e stack whose ~48 containers are the primary consumer of these images). Build-cache-optimization sibling at the CI layer: project memory `project_frontend_docker_build_cache_optimization` (Next.js BuildKit cache-mount work — this ADR is its backend-jar counterpart).

**Related:** every `projects/*/apps/*/Dockerfile` for a Spring Boot service (41 files); the iam-family `.dockerignore` negation pattern; `platform/deployment-policy.md` (image tagging convention `{service}:{git-sha}`); project memory `feedback_prune_old_image_after_rebuild` (dangling-prune after rebuild) and `env_rancher_desktop_vhdx_no_shrink` (why image size matters on this host); the host-jar build pattern (`./gradlew :apps:<svc>:bootJar` → `COPY build/libs/*.jar`) documented in `env_gap_docker_host_prebuilt_jar_redeploy_trap`.

---

## 1. Context

### 1.1 The as-built Dockerfile landscape (code-verified 2026-06-17)

44 `Dockerfile`s exist under `projects/*/`. They split into:

| Class | Count | Examples | In scope? |
|---|---|---|---|
| **Spring Boot Java service** | **41** | every `*-service`, `gateway-service`, `master-service`, `batch-worker`, `console-bff`, … | **YES** |
| Next.js frontend | 2 | `platform-console/console-web`, `ecommerce/web-store` | no (different toolchain; BuildKit cache-mount work already tracked separately) |
| Custom datastore image | 1 | `ecommerce/infra/elasticsearch` | no (not a jar) |

The 41 in-scope Dockerfiles are **not uniform** — they have diverged per project:

| Axis | iam-family (e.g. `account-service`) | platform-console (e.g. `console-bff`) |
|---|---|---|
| Base image | `eclipse-temurin:21-jre` (Debian) | `eclipse-temurin:21-jre-alpine` |
| Stages | multi-stage (JDK build → JRE runtime) | single-stage |
| Runtime tool install | `apt-get install curl` + `userdel ubuntu` + `groupadd`/`useradd` | `apk add curl tini` + `addgroup`/`adduser` |
| Build context | monorepo root (`COPY apps/<svc>/build/libs/`) | app dir (`COPY build/libs/console-bff.jar`) |
| DNS-wait pre-start | yes (`getent` gate, TASK-BE-048) | no |
| Init process | none | `tini` |

This divergence means a security-relevant change (JRE CVE bump, non-root hardening) must be hand-applied 41 times with no single source of truth.

### 1.2 The three inefficiencies

1. **Fat-jar single layer.** Every service does `COPY app.jar` as one ~80–95 MB layer. A one-line code change re-pushes/re-caches the **whole** jar, even though >97% of it (framework + library deps) is unchanged.
2. **Repeated runtime preparation.** The JRE + `curl` + non-root-user setup is an identical ~5–15 MB layer rebuilt and stored once per service (41×), differing only trivially (apt vs apk).
3. **Bloated build context.** The iam-family `.dockerignore` re-includes `!apps/*/build/libs` after excluding `**/build`. That negation (a) only works under BuildKit (legacy builder cannot re-include a child of an excluded dir — observed `COPY failed: ... file does not exist`, recorded in `env_console_demo_local_redeploy` trap5) and (b) ships **all** sibling services' jars into **every** service's build context (~220 MB), even though each build needs exactly one.

### 1.3 Pilot evidence (account-service, 2026-06-17, isolated test image — running stack untouched)

- Spring Boot 3.4.1 layered-jar via `java -Djarmode=layertools -jar app.jar extract` → 4 layers: `dependencies` (94.9 MB), `spring-boot-loader`, `snapshot-dependencies`, `application` (**1.68 MB**).
- Launch via `org.springframework.boot.loader.launch.JarLauncher` (layered-equivalent of `-jar app.jar`).
- Image **818 MB → 638 MB (−22%)**; on a code-only change, only the 1.68 MB `application` layer rebuilds — the 94.9 MB `dependencies` layer stays cached (~50× fewer volatile bytes).
- `rc=0`, build 14.5 s, behavior-preserving (same actuator health, same entrypoint semantics).

## 2. Decision

> **ACCEPTED 2026-06-17** (user-explicit, full ADR incl. D2). D2 (shared base image) is a genuine architecture decision (HARDSTOP-09 class); its ACCEPT gate was honored and **discharged by this ACCEPT** (self-ACCEPT prohibited; staged-child pattern — ADR-019/020/021/023/024/032/034/035/036/037/038/039/040). D1 + D3 are behavior-preserving and land per-project first; D2 lands last per the §D4 rollout (staged, not separately re-gated).

- **D1 — Layered-jar extraction is the standard for every Spring Boot service image (behavior-preserving).**
  Each Java-service Dockerfile replaces the single fat-jar `COPY` with: in the build stage, `java -Djarmode=layertools -jar app.jar extract`; in the runtime stage, four ordered `COPY --from=build` of `dependencies/` → `spring-boot-loader/` → `snapshot-dependencies/` → `application/` (low→high change frequency); and an entrypoint that launches `org.springframework.boot.loader.launch.JarLauncher` instead of `-jar app.jar`. All other lines (DNS-wait `getent` gate, `HEALTHCHECK`, `USER app`, `EXPOSE`, `JAVA_OPTS`) are preserved verbatim. This is a pure build-layout optimization — no runtime behavior changes.

- **D2 — A single versioned shared base image carries the common runtime preparation (ARCHITECTURE DECISION — separately ACCEPT-gated).**
  Extract the repeated JRE + `curl` + non-root `app` user (uid/gid 1000) + DNS-wait helper into one base image `monorepo/java-service-base:<version>` built once from a single `Dockerfile` under a shared path. Every service `FROM monorepo/java-service-base:<pinned>` and adds only its layer COPYs + service-specific entrypoint. This collapses 41 near-duplicate runtime-prep layers to one, gives a single point to apply JRE CVE patches / hardening, and ends the apt-vs-apk drift by standardizing on **one** distro family (proposal: `eclipse-temurin:21-jre` Debian-slim, since the iam family — the larger set with the DNS-wait requirement — already uses it; `tini` added to the base so console-bff keeps its init process). The base image is version-pinned (not `latest`) so a base change is an explicit, reviewable bump across consumers.

- **D3 — Build context is narrowed to the single service's jar (behavior-preserving).**
  Standardize every Java service's compose `build.context` (or `.dockerignore`) so only that service's `build/libs/` enters the context. Replace the fragile `**/build` + `!apps/*/build/libs` negation with a pure-positive include of the one service path, and require BuildKit (`DOCKER_BUILDKIT=1`) repo-wide for image builds (already the de-facto requirement; the legacy builder is incompatible with the negation and with one-service-at-a-time serial builds that avoid the documented concurrent-build 502 crash).

- **D4 — Scope and rollout.** In scope: the 41 Spring Boot service Dockerfiles. Out of scope: the 2 Next.js frontends (separate toolchain, cache-mount work already tracked) and the elasticsearch image. Rollout is staged: pilot (done) → D1+D3 swept **per-project as atomic PRs** (iam first, re-verify against the live federation stack, then wms/scm/finance/erp/ecommerce/fan/platform-console) → D2 base image landed once D1 is repo-wide and D2 is ACCEPTED.

## 3. Consequences

**Positive**
- Incremental rebuild on a code change rewrites ~1–2 MB instead of ~80–95 MB per service → faster local redeploys of the federation stack and smaller dangling-image churn (compounds with `feedback_prune_old_image_after_rebuild`).
- −22% image size per service (~180 MB × 41 ≈ several GB less resident in the vhdx that never shrinks — `env_rancher_desktop_vhdx_no_shrink`).
- One base image = one place to patch the JRE / non-root posture / DNS-wait helper; the 41-way drift (§1.1) collapses to a single reviewable artifact.
- A consistent, documented build standard for every new service (template-able Dockerfile).

**Negative / cost**
- One-time sweep of 41 files + per-project re-verification against the running stack (non-trivial; staged to contain blast radius).
- Base-image versioning discipline: a base bump must be built and the pin updated across consumers in one atomic change; a stale local base image can mask a regression (mitigate by tagging base with a content hash / date, never `latest`).
- BuildKit becomes a hard repo-wide requirement for image builds (already de-facto true).
- `console-bff` moves Debian-slim ← Alpine (musl→glibc); behavior-equivalent for a JRE workload but a base-OS change worth noting in its sweep PR.

## 4. Alternatives considered

- **A — Status quo (do nothing).** Rejected: the inefficiencies in §1.2 are measured and compound on this host.
- **B — Layered-jar only, no shared base (D1+D3, drop D2).** Viable and the lowest-risk subset; captures the rebuild-speed win but leaves the 41-way runtime-prep drift and the per-service patch burden. This is exactly why D2 is split out and separately gated — B is the fallback if D2 is not accepted.
- **C — Shared base only, no layered-jar (D2, drop D1).** Rejected: leaves the fat-jar single-layer rebuild cost (the larger of the two wins) on the table.
- **D — Replace host-jar build with Jib / Cloud Native Buildpacks.** Rejected for this ADR: the host-prebuilt-jar pattern (`bootJar` → `COPY`) is entrenched across CI, the federation-e2e harness, and the redeploy playbooks (`env_gap_docker_host_prebuilt_jar_redeploy_trap`); a build-tool migration is a far larger, orthogonal decision. Layered-jar achieves most of Jib's layering benefit without changing the toolchain.

## 5. Verification (to be satisfied by the implementation task)

1. **Behavior-preserving** — each swept service's container reaches `actuator/health` 200 and passes its existing entrypoint DNS-wait, identical to pre-sweep (re-verify against the live federation stack per project).
2. **Layer win** — `docker history` / `docker buildx --progress` shows the `application` layer ≪ `dependencies` layer, and a no-op code change rebuilds only `application` (cache hit on `dependencies`).
3. **Image size** — each swept image is materially smaller than its pre-sweep size (pilot: −22%).
4. **Context** — `docker buildx build` transfers only the one service's jar (context size drops from ~220 MB to ~single-jar).
5. **Base image (D2)** — every consumer `FROM` the pinned base; a base bump rebuilds all consumers; no service installs `curl`/creates the `app` user independently.

## 6. Acceptance log

| Date | Status | Note |
|---|---|---|
| 2026-06-17 | PROPOSED | D1/D3 behavior-preserving; D2 (shared base image) awaits user-explicit ACCEPT before implementation. Pilot (account-service) measured and validated; pilot artifacts removed (running stack untouched). |
| 2026-06-17 | ACCEPTED | User-explicit ACCEPT of the full ADR (incl. D2 direction). D1/D2/D3 byte-unchanged. Implementation = TASK-MONO-294; D1+D3 swept per-project (iam-platform first), D2 base image lands last per the §D4 rollout. |

## 7. Provenance

- Pilot: `iam-platform/account-service`, isolated test image `acct-layered:test`, 2026-06-17 — measured 818→638 MB, application layer 1.68 MB vs dependencies 94.9 MB; image + temp Dockerfile removed after measurement.
- Landscape audit: `Glob projects/**/Dockerfile` (44 files) + read of `account-service/Dockerfile`, `console-bff/Dockerfile`, `iam-platform/.dockerignore`, 2026-06-17.
- Implementation tracked by **TASK-MONO-294** (root `tasks/`).
