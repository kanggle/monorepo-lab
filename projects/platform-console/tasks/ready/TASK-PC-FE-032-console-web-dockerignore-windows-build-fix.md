# Task ID

TASK-PC-FE-032

# Title

`console-web` 에 누락된 `.dockerignore` 추가 — Dockerfile builder stage 의 `COPY . .` 가 host `node_modules`/`.next` 를 컨테이너로 복사하는 것을 차단한다. Dockerfile line 7 주석(`".dockerignore should exclude node_modules and .next"`)이 이미 이 파일의 존재를 전제하나 실제로는 누락돼 있어, **Windows 로컬에서 `docker compose ... build console-web` 시 host 의 Windows-native `node_modules` 가 deps stage 의 alpine `node_modules` 를 덮어써 `Cannot find module '/app/node_modules/next/dist/bin/next'` 로 빌드 실패**한다. CI(Linux runner)에선 host node_modules 가 없거나 Linux-native라 표면화되지 않던 갭.

# Status

ready

# Owner

frontend

# Task Tags

- infra
- fix

---

# Dependency Markers

- **depends on**: 없음 (독립적 build-infra fix).
- **prerequisite of**: 없음. (Windows 로컬에서 `docker-compose.e2e.yml` full-stack 을 빌드하려는 모든 개발 흐름이 본 fix 의 수혜자 — nightly CI 는 영향 없음.)

---

# Goal

`console-web` 에 표준 Next.js `.dockerignore` 가 존재하여:
- Windows 로컬에서 `docker compose -f projects/platform-console/docker-compose.e2e.yml build console-web` 가 성공한다 (host `node_modules` 오염 없이 deps stage 의 alpine `node_modules` 보존).
- CI(Linux) 빌드는 회귀 0 (기존에 통과하던 빌드 동일하게 통과).
- 빌드 컨텍스트에서 `node_modules`/`.next`/`.git` 등 불필요 산출물이 제외되어 빌드 전송량·시간이 감소한다.

## Root cause evidence

Dockerfile builder stage (`apps/console-web/Dockerfile`):

```dockerfile
COPY --from=deps /app/node_modules ./node_modules   # alpine-native (pnpm install --frozen-lockfile)
COPY . .                                             # ← .dockerignore 없으면 host node_modules 가 위를 덮어씀
```

`.dockerignore` 부재 시 `COPY . .` 가 Windows host 의 `node_modules`(pnpm `.pnpm` symlink 구조 = Windows 경로)를 컨테이너에 복사 → alpine 용 `next` 바이너리 구조 오염 → `pnpm build`(`next build`)가 `next/dist/bin/next` 를 찾지 못해 `MODULE_NOT_FOUND` exit 1. 실측: 2026-05-29 로컬 빌드에서 재현 + `.dockerignore` 추가 후 빌드 성공 확인.

# Scope

## In scope

- `projects/platform-console/apps/console-web/.dockerignore` 신규 추가 (node_modules, .next, .git, Dockerfile, 테스트/리포트 산출물 등 표준 제외 목록).

## Out of scope

- Dockerfile 변경 (현 구조 그대로 — `.dockerignore` 만 보완).
- 다른 프로젝트의 frontend Dockerfile (`ecommerce`/`fan-platform`) — 각자 별도 점검 대상(본 task 는 console-web 한정).
- CI workflow 변경 (Linux 빌드는 영향 없음).

# Acceptance Criteria

- **AC-1**: `projects/platform-console/apps/console-web/.dockerignore` 가 존재하고 최소 `node_modules`, `.next` 를 제외한다 (Dockerfile line 7 주석 전제 충족).
- **AC-2**: Windows 로컬 `docker compose -f projects/platform-console/docker-compose.e2e.yml build console-web` 가 성공한다 (MODULE_NOT_FOUND 미발생).
- **AC-3**: console-web 이미지가 정상 기동하고 `GET /api/health` 가 200 + `{"status":"ok"}` 반환 (standalone 산출물 무결).
- **AC-4**: zero-retrofit — `git diff --stat origin/main -- 'projects/{wms,scm,erp,fan,ecommerce-microservices,finance,global-account-platform}-platform/' 'libs/'` = empty (console-web 한정 변경).

# Related Specs

- 해당 없음 (빌드 인프라 파일 — 아키텍처 결정/계약 변경 아님, HARDSTOP-09 무관).
- 참고: `apps/console-web/Dockerfile` line 7 주석이 본 파일 존재를 전제.

# Related Contracts

- 해당 없음 (API/이벤트 계약 무관).

# Edge Cases

- **`.next` 잔존**: host 에 빌드 산출물 `.next` 가 있으면 `node_modules` 와 동일하게 stale 복사 위험 → `.dockerignore` 에 포함.
- **`.env*.local`**: host 의 로컬 시크릿이 이미지에 새지 않도록 제외.
- **CI(Linux) 영향**: Linux runner 는 host node_modules 가 없거나 Linux-native라 기존엔 문제 없었음 — `.dockerignore` 추가가 CI 빌드를 깨지 않음(제외 대상이 빌드에 불필요한 것들).

# Failure Scenarios

- **과도한 제외로 빌드 누락**: `.dockerignore` 가 빌드에 필요한 소스(`src/`, `public/`, `package.json`, `pnpm-lock.yaml`, `next.config.*`)를 제외하면 빌드 실패 → 제외 목록을 산출물/캐시/VCS 메타로 한정(소스 불포함).
- **standalone 산출물 누락**: runner stage 의 `COPY .next/standalone` 등이 영향받지 않도록 `.dockerignore` 는 빌드 컨텍스트(host→builder) 전송만 제어하고 stage 간 COPY 는 불변.

---

# Notes

- 발견 경위: 2026-05-29 `docker-compose.e2e.yml` full-stack 을 Windows 로컬에서 빌드하던 중 console-web 이미지 빌드가 `MODULE_NOT_FOUND` 로 실패. `.dockerignore` 추가로 즉시 해소(빌드 성공 + 8-컨테이너 full-stack 기동 + localhost:3000 로그인 페이지 렌더 확인).
- Dockerfile 은 이미 표준 Next.js standalone 패턴이고 `.dockerignore` 존재를 주석으로 전제 — 본 task 는 그 누락된 전제 파일을 보완하는 것일 뿐, Dockerfile/빌드 로직 변경 없음.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 또는 Haiku (단일 파일 추가, 빌드 인프라 — 단순).
