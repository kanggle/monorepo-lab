# Task ID

TASK-MONO-233

# Title

`.github/workflows/nightly-e2e.yml` — platform-console e2e jar-restore 경로를 `iam/` → `iam-platform/` 로 정정. gap→iam-platform 리네임(#1149)이 federation-hardening-e2e.yml 만 고치고 누락한 잔재로 platform-console 나이틀리 잡이 2026-06-06부터 매일 RED.

# Status

done

# Owner

infra (Opus 4.8 analysis / Sonnet 4.6 impl). monorepo-level shared (`.github/workflows/`). No project code change.

# Task Tags

- code
- deploy

---

# Dependency Markers

- **relates**: `refactor!: rename global-account-platform (gap) → iam-platform (iam)` (#1149) — 동일 jar-restore 버그를 `.github/workflows/federation-hardening-e2e.yml` 에서는 고쳤으나(코드에 주석으로 명시) `nightly-e2e.yml` 에는 같은 fix 가 누락됨.
- **선행 없음**. 독립 1-파일 fix.

# Goal

`nightly-e2e.yml` 의 "Platform Console E2E full-stack" 잡 → "Restore boot jar paths" 스텝에서, `actions/upload-artifact@v4` 가 공통 prefix(`projects/`) 를 제거해 아티팩트 내부 구조가 `iam-platform/apps/<svc>/...` 인데, restore `mv` 의 소스가 옛 디렉터리명 `artifact-staging/iam/apps/...` 를 가리켜 `mv: cannot stat ... No such file or directory` → exit 1 로 잡이 죽는다(Playwright 실행 전). 소스 경로 3줄(auth/account/admin-service)을 `iam-platform/apps/...` 로 정정해 jar 복원이 성공하고 docker compose 스택이 정상 기동되도록 한다.

# Scope

## In Scope

- **`.github/workflows/nightly-e2e.yml`** — "Restore boot jar paths" 스텝(platform-console 잡)의 `mv` 소스 경로 3줄:
  - `artifact-staging/iam/apps/auth-service/...` → `artifact-staging/iam-platform/apps/auth-service/...`
  - `artifact-staging/iam/apps/account-service/...` → `artifact-staging/iam-platform/apps/account-service/...`
  - `artifact-staging/iam/apps/admin-service/...` → `artifact-staging/iam-platform/apps/admin-service/...`
- 회귀 맥락(왜 `iam-platform` 인지 + #1149 누락 경위) 설명 주석 추가.

## Out of Scope

- `mkdir` 타깃 경로(이미 `iam-platform/...` 으로 정확) 불변.
- finance-platform / platform-console 라인(이미 정확) 불변.
- fan-platform live-trio 잡 실패(별건 — TASK-FAN-INT-002).
- web-store Frontend E2E 잡 실패(별건 — "Start docker compose stack" 단계 원인, 본 task 범위 아님).
- 업로드 경로·아티팩트 이름·잡 구조 불변.

# Acceptance Criteria

- [ ] restore `mv` 소스 3줄이 `artifact-staging/iam-platform/apps/...` 를 가리킨다.
- [ ] `nightly-e2e.yml` 에 `artifact-staging/iam/apps` (옛 경로) 잔재가 0건.
- [ ] 다음 nightly-e2e 실행에서 "Platform Console E2E full-stack" 잡의 "Restore boot jar paths" 스텝이 성공(`mv` 비실패)하고 docker compose 스택 기동 단계로 진행.
- [ ] YAML 파싱 유효(actionlint 또는 GitHub 파서 수용).

# Related Specs

- 없음 (CI 워크플로, 계약 무관).

# Related Contracts

- 변경 없음.

# Target Service

- 없음 (repo-root `.github/workflows/nightly-e2e.yml` 단일 파일).

# Architecture

- `upload-artifact@v4` 는 지정한 path 목록의 최장 공통 prefix(`projects/`)를 제거하고 아티팩트 내부에 `<project>/apps/<service>/build/libs/<jar>` 구조를 보존한다. IAM 프로젝트 디렉터리는 `iam-platform`(업로드 경로가 풀네임 유지) 이므로 다운로드 후 staging 내부도 `iam-platform/...` 다. gap→iam-platform 리네임 시 업로드 경로는 자동으로 풀네임을 따라갔지만, 수기 restore `mv` 의 소스 문자열이 옛 `iam/` 로 남아 불일치가 발생했다. federation-hardening-e2e.yml 은 동일 버그를 고치며 코드에 경위 주석까지 남겼으나 nightly-e2e.yml 은 누락되었다.

# Edge Cases

- finance-platform / platform-console restore 라인은 디렉터리명이 리네임 대상이 아니라 이미 정확 → 변경 불필요.
- 아티팩트가 0 파일이면 `if-no-files-found: warn` 으로 업로드는 통과하나 restore `mv` 가 실패한다 — 단, Package 잡 로그상 5개 jar 모두 빌드·업로드되므로 본 fix 후 정상 복원된다.

# Failure Scenarios

- `mkdir` 타깃만 고치고 `mv` 소스를 안 고침(현 상태) → `mv` 가 stale 소스에서 실패. AC 가 소스 3줄 명시.
- 일부 라인만 고침 → 첫 미수정 라인에서 `mv` 실패(`set -e` 로 즉시 중단). AC 가 잔재 0건 명시.

# Definition of Done

- [ ] restore `mv` 소스 3줄 `iam-platform/...` 정정 + 경위 주석
- [ ] `artifact-staging/iam/apps` 잔재 0건
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
