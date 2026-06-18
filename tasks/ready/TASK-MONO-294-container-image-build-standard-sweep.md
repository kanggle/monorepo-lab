# TASK-MONO-294 — Container image build standard: layered-jar + shared base image + context narrowing (ADR-MONO-041 sweep)

**Status:** ready

**Type:** TASK-MONO (root — cross-project: 41 `projects/*/apps/*/Dockerfile` + a shared base-image artifact + per-project `.dockerignore`/compose `context`)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 for the mechanical per-service D1/D3 sweep (template application, behavior-preserving); **Opus 4.8** for the D2 base-image design (the gated architecture decision)

> Implements **ADR-MONO-041 (ACCEPTED 2026-06-17, full ADR incl. D2)**. D1 (layered-jar) + D3 (context narrowing) are behavior-preserving and land per-project first. **D2 (shared base image)** lands last per the rollout (after D1 is repo-wide); its ACCEPT gate was discharged by the ADR ACCEPT — not separately re-gated. Pilot (account-service) already validated and measured (818→638 MB; application layer 1.68 MB vs dependencies 94.9 MB).

---

## Progress & Resume playbook (updated 2026-06-18)

### Done
- **D1 (layered-jar) — COMPLETE across all 8 backend projects, merged.** PRs #1784 (iam) · #1785 (wms) · #1786 (scm) · #1787 (finance) · #1789 (erp) · #1790 (ecommerce) · #1791 (fan) · #1793 (platform-console). 27 Dockerfiles converted; **14 were already layered** (ecommerce 12 via the otel 3-stage pattern; scm demand-planning + inventory-visibility). 11 services live-verified (health 200, JarLauncher).
- **D2 (shared base image) — iam PILOT done, merged (#1796).** `docker/java-service-base/Dockerfile` → `monorepo/java-service-base:v1`. **Distro reversed Debian→Alpine** (ADR-MONO-041 § D2 Correction): base = `eclipse-temurin:21-jre-alpine` + `curl` + `tini` + non-root `app` user (`addgroup -S app && adduser -S`) + `WORKDIR /app`. iam's 5 Dockerfiles re-based (build stage = raw `jre-alpine` for `layertools extract`; runtime stage = `FROM monorepo/java-service-base:v1`). iam account 818→**472 MB** (−42% vs original). CI base-step added to 3 workflows; **CI-proven** (iam e2e smoke GREEN).
- **D2 full re-base — COMPLETE across the 6 remaining backend projects (23 services), merged 2026-06-18, 3-dim verified.** PRs #1800 (finance 2 + console-bff 1 + platform-console nightly base-step) · #1801 (erp 4, no CI change) · #1802 (wms 7 + ci.yml + nightly base-steps) · #1803 (scm 4, incl. the 2 already-layered minimal ones + ci.yml + nightly base-steps) · #1804 (fan 5 + ci.yml + nightly base-steps). All 5 GREEN pre-merge; **PR-time CI actually ran the wms/scm/fan docker-build e2e jobs** (base-step + re-based images) → behavior-preserving CI-proven, not just local. console-bff re-based image locally built + introspected (user=app, curl, tini from base, layered jar assembled). **Latent-gap fix:** the platform-console nightly full-stack job built the pilot's re-based iam images via `compose up --build` with no base-step → #1800 added it.
- **D2 — ecommerce (final 13 services), merged 2026-06-18, 3-dim verified (#1806).** The 3-stage otel fleet re-based: runtime `FROM` → base; drop `addgroup/adduser` + `WORKDIR`; `chown -R appuser:appgroup` → `app:app`; `USER appuser` → `app`; otel stage + javaagent COPY + wget HEALTHCHECK + `-javaagent` JarLauncher ENTRYPOINT verbatim (wget stays via alpine busybox on the base). order-service image locally built + introspected (user=app, otel agent app-owned, wget present, layered jar + otel assembled). CI base-step added before the nightly `frontend-e2e-fullstack` `compose up --build` (with `working-directory: ${{ github.workspace }}` to escape the job's ecommerce default dir). 21/21 checks GREEN pre-merge. **D2 is now COMPLETE — all 41 service images `FROM monorepo/java-service-base:v1`.**

### Remaining
- **D3 (context narrowing) — the only outstanding item (AC-4), optional / low value.** Only iam + ecommerce use a non-service-dir build context with monorepo-root-relative `COPY apps/<svc>/build/libs` (the 36 alpine services already use service-dir context). Narrowing would mean, per service, switching the context root → service dir + rewriting the `COPY` path + updating every compose `build.context` + every CI `docker build`/`compose` context — a non-trivial multi-surface refactor for marginal context-transfer savings. iam's `.dockerignore` still carries the `**/build` + `!apps/*/build/libs` negation (ecommerce's has no `**/build` exclusion at all). **Deferred as optional; pursue only if the context-transfer cost becomes a real bottleneck.** With D1 + D2 done, the standardization win (layered-jar churn + 41-way runtime-prep drift) is fully captured.

### Per-service re-base recipe (D2 full)
The 36 remaining services are **already layered Alpine** (D1), so the re-base is a small edit to the **runtime stage only**:
1. Leave the build stage unchanged (`FROM eclipse-temurin:21-jre-alpine AS build` + `layertools extract`).
2. Runtime stage: replace `FROM eclipse-temurin:21-jre-alpine` + the `RUN apk add --no-cache curl tini && addgroup -S app && adduser -S -G app app` + `WORKDIR /app` lines with a single `FROM monorepo/java-service-base:v1` (the base provides all of those).
3. Keep the 4 layer COPYs, `USER app`, `EXPOSE`, `HEALTHCHECK`, `ENTRYPOINT/CMD` verbatim.
- **ecommerce nuance**: its 12 services use the **3-stage otel** pattern and a **different user name** (`appuser:appgroup`, not `app`). Re-basing means: runtime `FROM` → base; drop its `addgroup/adduser`; change every `--chown=appuser:appgroup` → `--chown=app:app`; keep the otel `COPY` + `-javaagent` ENTRYPOINT. Higher fiddliness + lower marginal value → **consider deferring ecommerce** or doing it last.

### CI base-step map (CRITICAL — miss one job ⇒ that job goes RED)
No image registry ⇒ every CI job that builds a re-based service image must `docker build -t monorepo/java-service-base:v1 docker/java-service-base` **before** the service build. Insertion points by project:
- **`federation-hardening-e2e.yml`** — ✅ **already covered**. The D2 base-step runs once before compose Phase 1 and persists for Phase 2 (same job), so all federation services are covered with no further change.
- **`ci.yml`** — add a base-step to the per-project E2E jobs that `docker build` images:
  - wms → "E2E (gateway-master live-pair smoke)" (builds wms-master/gateway, ~L1422)
  - fan → "E2E (fan-platform v1 live-trio smoke)" (~L1524)
  - scm → "E2E (scm-platform v1 cross-service smoke)" (~L1635)
  - finance / erp / ecommerce / console-bff → **no dedicated docker-build E2E job in ci.yml** (covered only by federation, ✅). Their `Integration (…, Testcontainers)` jobs spin up infra only, not the service image → no base-step needed.
- **`nightly-e2e.yml`** — add a base-step to: wms (~L378), fan (~L437), scm (~L502) docker-build jobs; and verify the `compose … up --build` jobs (~L255, L809, L845) + the build loop (~L653) cover whichever projects you re-base.

Copy the exact step already used (search `Build shared java-service-base image (ADR-MONO-041 D2)` in the 3 workflows for the canonical snippet).

### Verification recipe (per project — same as D1, see project memory `project_container_image_build_standard_adr041`)
1. `docker build -t monorepo/java-service-base:v1 docker/java-service-base` (build base locally FIRST — F3).
2. `./gradlew :projects:<proj>:apps:<svc>:bootJar` (only for services whose jar isn't in the main checkout).
3. `docker build -f <worktree>/…/Dockerfile -t federation-hardening-e2e-<image> <context>` (alpine context = service dir; iam/ecommerce context = project root).
4. `docker compose -p federation-hardening-e2e -f docker-compose.federation-e2e.yml -f docker-compose.federation-e2e.demo.yml -f zz-console-web-timeout-override.yml -f docker-compose.federation-e2e.ecommerce.yml up -d --no-build --force-recreate <svc>` → poll `actuator/health` 200 (Alpine cold-JVM can take ~2 min).
5. Per-project atomic PR (services + CI base-steps). **Watch CI GREEN.** Merging a CI-workflow-touching PR needs an **explicit, PR-specific user instruction** (the auto-mode classifier blocks vague authorization for shared-`main` + CI changes — hand the `gh pr merge` command over if blocked).
6. Hyper-V socket timeouts are frequent under load → wrap builds/compose in a retry loop.

---

## Goal

Standardize the 41 Spring Boot service Dockerfiles per ADR-MONO-041: (D1) Spring Boot **layered-jar** extraction so a code change rebuilds ~1–2 MB instead of the whole ~80–95 MB jar; (D2) a single **versioned shared base image** carrying the common JRE + `curl` + non-root user + DNS-wait helper, ending the 41-way runtime-prep drift; (D3) **narrow the build context** to the one service's jar and require BuildKit. Behavior-preserving — every container reaches the same `actuator/health` 200 via the same entrypoint.

## Scope

**In scope** — the 41 Java-service Dockerfiles under `projects/*/apps/*/Dockerfile` (every `*-service`, `gateway-service`, `master-service`, `batch-worker`, `console-bff`).

- **D1 (per-service, behavior-preserving)** — build stage adds `java -Djarmode=layertools -jar app.jar extract`; runtime stage replaces the single `COPY app.jar` with ordered `COPY --from=build` of `dependencies/` → `spring-boot-loader/` → `snapshot-dependencies/` → `application/`; entrypoint launches `org.springframework.boot.loader.launch.JarLauncher` (not `-jar app.jar`). All other lines preserved verbatim (DNS-wait `getent`, `HEALTHCHECK`, `USER app`, `EXPOSE`, `JAVA_OPTS`).
- **D2 (shared base image)** — one `Dockerfile` under a shared path (`docker/java-service-base/`) builds `monorepo/java-service-base:v1`. **As-built (distro reversed to Alpine — see Progress playbook + ADR § D2 Correction):** `eclipse-temurin:21-jre-alpine` + `curl` + `tini` + non-root `app` user (`adduser -S`, not uid 1000) + `WORKDIR /app`. Each service's DNS-wait stays in its own ENTRYPOINT (the base carries no per-service wait helper). Every service `FROM` the pinned base; no service installs `curl`/creates the user independently. Version-pinned (`:v1`, bump on change), never `latest`.
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
- **F2 — VOID** (distro reversed to Alpine). The original concern (console-bff Alpine→Debian) no longer applies; the base IS Alpine, so console-bff stays Alpine. The actual base-OS move is the reverse — **iam moves Debian→Alpine** (done in the pilot, live-verified). New nuance: **Alpine cold-JVM start is ~2 min** (slower than Debian) but well within the HEALTHCHECK `start-period`; don't mistake the slow start for a failure during health polling.
- **F3 — stale local base image masks a regression.** Pin the base by content hash/date and rebuild it before each consumer rebuild during D2; never rely on a cached `latest`.
- **F4 — legacy builder.** D3's pure-positive context still requires BuildKit for serial one-service builds (legacy builder's concurrent-build 502 + negation incompatibility — `env_console_demo_local_redeploy` trap5). The sweep must not reintroduce a legacy-builder-only path.
- **F5 — non-jar Dockerfiles swept by mistake.** The 2 Next.js + 1 ES Dockerfile are explicitly out of scope; a sweep script keying on `bootJar`/`COPY ...build/libs` must exclude them.
