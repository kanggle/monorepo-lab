# Task ID

TASK-MONO-203

# Title

scripts/sync-portfolio.sh — git-filter-repo 러너의 `apk add git` 사일런트 실패를 시끄럽게(`set -e` + git 설치 확인). 컨테이너 git 미설치 시 filter-repo 가 unfiltered workdir 를 남기고 push 미도달하던 미궁 제거.

# Status

ready

# Owner

infra (Opus 4.8 analysis / Sonnet 4.6 impl). monorepo-level shared (`scripts/`). No project code change.

# Task Tags

- code
- infra

---

# Dependency Markers

- **2026-06-10 standalone publish 중 실발생**: `./scripts/sync-portfolio.sh iam-platform` 가 조용히 실패(어떤 표준판도 push 안 됨)했고, temp workdir 가 unfiltered 풀 monorepo 로 남아 진단으로 원인 규명.
- **relates**: portfolio sync 운영(`scripts/sync-portfolio.sh`), 프로젝트 메모리 `project_portfolio_submission_strategy`.

# Goal

git-filter-repo 러너 생성부에서 `apk add --no-cache git >/dev/null 2>&1`(silenced) → `set -e` + unsilenced `apk add` + `command -v git` 확인으로 교체. 컨테이너에 git 설치 실패 시 러너가 즉시 `FATAL...` 로 중단되어 docker run 이 non-zero 반환 → 호출 스크립트가 push 이전에 명확한 원인과 함께 멈춘다.

# Scope

## In Scope

- **`scripts/sync-portfolio.sh`** — `sync_project()` 의 러너(`_filter_repo_run.sh`) 생성 블록(`printf '#!/bin/sh\n'` 직후):
  - `printf 'apk add --no-cache git >/dev/null 2>&1\n'` (silenced) 제거
  - `set -e` 추가 → 러너가 첫 실패 단계에서 즉시 중단
  - `apk add --no-cache git` (unsilenced) → apk 출력/에러 가시화
  - `command -v git >/dev/null 2>&1 || { echo "FATAL: git not installed ..."; exit 1; }` → git 미설치 시 명확한 실패
- 진단 주석(왜 이 가드가 필요한지) 추가.

## Out of Scope

- filter-repo 추출 로직·SHARED_PATHS·PROJECT_REMOTES·post-process·force-push 로직 불변.
- pip install 단계(이미 `--quiet`; PyPI 도달은 별개 — apk 와 독립적으로 동작 확인됨).
- Docker→호스트 filter-repo 전환(별도 검토 사안, 본 task 범위 아님).

# Acceptance Criteria

- [ ] 러너가 `set -e` + `apk add`(unsilenced) + `command -v git` 가드를 포함.
- [ ] 컨테이너 git 설치 실패를 가정한 경로에서 러너가 `FATAL...` + 비-zero exit → 호출 스크립트가 push 전 중단(사일런트 unfiltered-workdir 미발생).
- [ ] 정상 경로(apk 성공)에서 기존과 동일하게 filter-repo 2-pass + post-process + force-push 완주.
- [ ] `bash -n scripts/sync-portfolio.sh` 구문 OK; `--dry-run` 정상 동작.

# Related Specs

- 없음 (운영 스크립트, 계약 무관).

# Related Contracts

- 변경 없음.

# Target Service

- 없음 (repo-root `scripts/sync-portfolio.sh` 단일 파일).

# Architecture

- 러너는 ephemeral alpine 컨테이너에서 매 실행마다 `apk add git` + `pip install git-filter-repo` 로 도구를 설치한다. git 은 filter-repo 의 런타임 의존(래퍼). 설치 실패를 silent 처리하면 후속 filter-repo 가 `FileNotFoundError: 'git'` 로 죽고, `set -e` 부재로 러너가 비정상 종료 없이 흘러 unfiltered workdir + 미도달 push 라는 무징후 실패를 만든다. fail-fast(`set -e` + 명시적 가드)로 실패를 호출 계층까지 즉시 전파.

# Edge Cases

- apk 일시 네트워크 실패(2026-06-10 실발생) → 이제 `FATAL` 즉시 중단(이전엔 무징후 unfiltered workdir).
- pip 성공 / apk 실패 분기(PyPI 도달되나 alpine CDN 미도달) → git 가드가 명확히 적발.
- 정상 경로 회귀 없음 — 가드는 실패 시에만 발동.

# Failure Scenarios

- 가드만 넣고 `set -e` 누락 → apk 실패해도 러너가 계속 흘러 동일 미궁. AC 가 `set -e` 명시.
- `apk add` 를 다시 silence → 함정 재발. AC 가 unsilenced 명시.

# Definition of Done

- [ ] 러너 생성부 `set -e` + unsilenced apk + git 가드
- [ ] `bash -n` 구문 OK + `--dry-run` 정상
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
