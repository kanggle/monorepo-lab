# Task ID

TASK-FE-072

# Title

ecommerce 프런트엔드(`web-store` + `admin-dashboard`) Docker 이미지 빌드 최적화 — BuildKit 캐시 마운트로 Next.js 증분 컴파일 캐시(`.next/cache`)와 pnpm store 를 빌드 간 보존하여 소스-온리 재빌드를 cold→incremental 로 전환. build-infra only; 앱 소스/HTTP API/이벤트/컨트랙트 무변경, 이미지 런타임 동작 byte-identical (platform-console TASK-PC-FE-035 검증 패턴의 ecommerce 이식)

# Status

review

# Owner

frontend (build-infra — `web-store` + `admin-dashboard` Dockerfile only; 앱 소스 무변경)

# Task Tags

- code
- deploy
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **이식 출처 / 검증된 패턴**: [TASK-PC-FE-035](../../../platform-console/tasks/done/TASK-PC-FE-035-console-web-docker-build-cache-optimization.md) (platform-console `console-web`) — 동일한 Next.js standalone Dockerfile 에 `.next/cache` + pnpm store BuildKit 캐시 마운트를 적용해 cold 229s → warm 91s (~60%↓) 실측. ecommerce 두 프런트(`web-store`/`admin-dashboard`)도 같은 `node:20-alpine` + Next standalone + pnpm 패턴이므로 동일 메커니즘 적용.
- **선행 build-infra 선례**: [TASK-PC-FE-032](../../../platform-console/tasks/done/TASK-PC-FE-032-console-web-dockerignore-windows-build-fix.md) — frontend Dockerfile build-infra fix 의 bundled spec+impl 처리 선례(`feedback_pr_bundling`).
- **producer/contract 의존 없음**: 빌드 결과 이미지는 byte-identical. HTTP API/이벤트/컨트랙트/spec/ADR 무변경. read-only build-path 최적화.
- **fan-platform-web 범위 밖**: fan-platform 프런트는 별도 Dockerfile 부재(컨테이너화 방식 상이)로 본 task 범위 밖 — 추후 cross-project 공유 base Dockerfile 통합(root TASK-MONO) 후보.

---

# Goal

ecommerce 프런트엔드 두 앱의 프로덕션 Docker 이미지 빌드를, 흔한 dev/CI 경로(소스 변경 후 재빌드)에서 빠르게 만든다 — 이미지 런타임 동작/크기는 그대로 둔 채.

현재 두 Dockerfile(`apps/web-store/Dockerfile`, `apps/admin-dashboard/Dockerfile`)은 turbo workspace 패턴(`COPY apps/<app>/ ...` → `pnpm --filter <app> build`)이고, `COPY apps/<app>/` 레이어가 소스 변경마다 무효화되어 `pnpm build`(Next.js 컴파일)가 매번 **cold** 로 재컴파일한다 — Next.js 의 증분 webpack/SWC 캐시(`.next/cache`)가 빌드 간 폐기되기 때문. BuildKit 캐시 마운트로 그 캐시를 보존하면 cold 재컴파일이 incremental 로 바뀐다. pnpm store 캐시 마운트는 lockfile 변경(deps 레이어 미스) 시 재다운로드 대신 재링크하게 한다.

platform-console `console-web` 에서 동일 패턴을 적용해 ~60% 단축을 실측(TASK-PC-FE-035)했고, 본 task 는 그 검증된 패턴을 ecommerce 두 프런트로 이식한다.

# Scope

## In Scope

build-infra only — 단일 bundled PR (spec/ADR/contract 영향 0; TASK-PC-FE-032/035 선례·`feedback_pr_bundling`):

1. **`apps/web-store/Dockerfile`**
   - `# syntax=docker/dockerfile:1` 디렉티브 추가(line 1) — `RUN --mount` 캐시 마운트 활성화.
   - builder `pnpm install --frozen-lockfile` → `RUN --mount=type=cache,target=/root/.local/share/pnpm/store ...` (pnpm store 보존).
   - builder `pnpm --filter web-store build` → `RUN --mount=type=cache,target=/app/apps/web-store/.next/cache ...` (Next.js 증분 캐시 보존; turbo 경로는 앱별 `/app/apps/web-store/.next/cache`).
   - runner 스테이지 **무변경** — `.next/standalone` + `.next/static` + `public` 만 복사, `.next/cache` 미복사 → 이미지 크기 불변.
2. **`apps/admin-dashboard/Dockerfile`** — 동일 패턴, 마운트 target `/app/apps/admin-dashboard/.next/cache`, `--filter admin-dashboard`.
3. **task md + `INDEX.md`** 등록(this file).

문서화-only(미커밋):

4. **provenance/attestation off** — `--provenance=false --sbom=false`(또는 `BUILDX_NO_DEFAULT_ATTESTATIONS=1`)는 빌드-명령 플래그라 Dockerfile 미반영; compose/CI 편입은 별건(manifest shape 변경).

## Out of Scope

- **앱 소스 변경**(`apps/web-store/src/**`, `apps/admin-dashboard/src/**`). 이미지 런타임 동작 byte-identical.
- **HTTP API / 이벤트 / 컨트랙트 변경**. `specs/contracts/**` 무변경.
- **`.dockerignore` 테스트 제외** — 두 Dockerfile 은 selective COPY(`COPY apps/<app>/`)라 `COPY . .` 가 없어 `.dockerignore` 의 캐시-무효화 절감 효과가 작음. 저위험 유지 위해 본 task 는 캐시 마운트(검증된 최대 레버)만. (필요 시 별 task.)
- **fan-platform-web / 공유 base Dockerfile 통합** — cross-project 성격(root TASK-MONO) 별건.
- **다른 ecommerce 백엔드 서비스 Dockerfile** — Spring boot jar 패턴이라 별개; 범위 밖.

# Acceptance Criteria

- [ ] **AC-1** `apps/web-store/Dockerfile` + `apps/admin-dashboard/Dockerfile` 둘 다 `# syntax=docker/dockerfile:1`(line 1) + 두 `RUN --mount=type=cache,...`(pnpm store on install, 앱별 `.next/cache` on build) 보유. runner 스테이지 COPY 세트 무변경.
- [ ] **AC-2** 각 Dockerfile 의 `.next/cache` 마운트 target 이 그 앱 경로(`/app/apps/web-store/.next/cache` / `/app/apps/admin-dashboard/.next/cache`)와 정확히 일치(turbo workspace 빌드 출력 경로).
- [ ] **AC-3** 최적화 Dockerfile 로 `docker buildx build`(두 앱) rc=0 — 캐시 마운트/syntax 디렉티브가 빌드를 깨뜨리지 않음. **CI Frontend E2E smoke(web-store 빌드) + Frontend lint & build 가 GREEN 으로 자동 게이트**.
- [ ] **AC-4** 소스-온리 재빌드가 cold 대비 빠름이 측정됨(cold=빈 `.next/cache` vs warm=마운트 hot). **실측 2026-06-02 (web-store, `docker buildx --provenance=false`): cold 445s → warm(소스 1줄) 79s (~82%↓, 366s 절감); 무변경 4s(full layer cache).** (admin-dashboard 는 구조 동일 → 동일 메커니즘.)
- [ ] **AC-5** 이미지 크기 불변(`.next/cache` 미복사). 빌드된 이미지가 정상 기동(기존 compose 기동 동일).
- [ ] **AC-6** diff scope = `apps/web-store/Dockerfile` + `apps/admin-dashboard/Dockerfile` + task md + `INDEX.md` 만. `src/**`/producer/contract/spec/ADR 무변경(grep 검증).

# Related Specs

- [`specs/services/web-store/architecture.md`](../../specs/services/web-store/architecture.md) — `web-store` 는 Next.js standalone-output 프런트; 빌드는 멀티스테이지 Dockerfile. 아키텍처 결정 무변경(빌드 경로만 가속).
- [`specs/services/admin-dashboard/architecture.md`](../../specs/services/admin-dashboard/architecture.md) — 동일(`admin-dashboard`).
- 이식 출처: TASK-PC-FE-035 (platform-console `console-web`).

# Related Contracts

- **없음.** build-infra only — HTTP API/이벤트/`specs/contracts/**` 무변경. 빌드 산출물(이미지)은 byte-identical, 빌드 경로만 최적화.

# Target Service

- `web-store` (`projects/ecommerce-microservices-platform/apps/web-store`)
- `admin-dashboard` (`projects/ecommerce-microservices-platform/apps/admin-dashboard`)

# Edge Cases

- **BuildKit 비활성/미지원** — `# syntax=docker/dockerfile:1` 은 BuildKit 필요(현대 Docker / `docker buildx` 기본). 레거시 `DOCKER_BUILDKIT=0` 에서 `RUN --mount` 는 에러. compose/buildx 경로는 BuildKit 기본.
- **cache prune 직후 첫 빌드** — `docker builder prune` 으로 마운트 비면 cold(== 오늘의 매-빌드 비용). 예상 동작; 최적화는 steady-state 재빌드 루프 대상.
- **Next 메이저 업그레이드 후 stale `.next/cache`** — Next 가 버전 변경 시 자체 캐시 무효화 → 마운트는 재populate. 정확성 위험 없음(캐시는 최적화이지 진실의 원천 아님).
- **turbo workspace 경로 오타** — `.next/cache` 마운트 target 이 앱 경로와 불일치하면 마운트가 no-op(빌드는 여전히 정상, 가속만 없음). AC-2/AC-4 가 탐지.

# Failure Scenarios

- **마운트 target 경로 오류 → 가속 없음** — turbo 빌드는 `/app/apps/<app>/.next/cache` 에 쓰는데 `/app/.next/cache` 로 마운트하면 no-op. 완화: AC-2(앱별 경로 일치) + AC-4(전후 타이밍 기록)로 미가속 신호 탐지.
- **이미지 비대화** — runner 가 `.next/cache` 를 복사하면 이미지 팽창. 완화: AC-1 이 runner COPY 세트 동결(standalone/static/public), AC-5 가 크기 불변 단언.
- **provenance 플래그 drift** — `--provenance=false` 문서화하나 compose/CI 미적용 시 로컬/CI manifest shape 차이(앱 동작 차이 아님). 완화: 범위 밖/미커밋으로 분리.

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (검증된 PC-FE-035 패턴의 기계적 이식, 두 Dockerfile build-infra; 측정+CI 게이트로 안전성 실증). bundled spec-less PR.
