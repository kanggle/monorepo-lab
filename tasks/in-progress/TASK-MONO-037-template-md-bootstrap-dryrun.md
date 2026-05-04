# Task ID

TASK-MONO-037

# Title

TEMPLATE.md 부트스트랩 instruction dry-run 검증

# Status

ready

# Owner

backend

# Task Tags

- audit
- template
- dry-run

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-MONO-033 (TEMPLATE.md audit, PR #166) 가 신규 프로젝트 부트스트랩 instruction 을 ~300줄 수정 + 신설했음. 본 task 는 그 instruction 을 실제로 따라가 보면서 누락 / 불명확 항목을 catch.

**dry-run 만** — 실제 새 프로젝트를 main 에 만들지 않음. dummy 디렉토리 생성 → 검증 → 즉시 삭제 (또는 별도 worktree 안에서만 작업).

---

# Scope

## In Scope

### 1. TEMPLATE.md 의 신규 프로젝트 부트스트랩 step 1~N 따라가기

- dummy 프로젝트 이름 (예: `dummy-domain-X`) 가정
- TEMPLATE.md instruction 그대로 실행:
  - PROJECT.md frontmatter 작성
  - 디렉토리 구조 생성
  - root settings.gradle include
  - root package.json 단축 스크립트
  - docker-compose.yml Traefik labels
  - .env.example
  - tasks/INDEX.md
  - GAP IdP 통합 (V00XX 시드 + tenant 등록 + gateway OAuth2 RS)
  - sync-portfolio.sh PROJECT_REMOTES 추가

### 2. 누락 / 불명확 catch

각 step 에서:
- "이거 어디 적힌 대로 해야 하는지 모호" — TEMPLATE.md 보강 필요
- "이 step 은 빠져 있음" — TEMPLATE.md 추가 필요
- "이 step 의 예시 코드 가 stale" — TEMPLATE.md 갱신 필요

### 3. 검증 결과 매트릭스

step 별 ✅ / ⚠️ / ❌ 매트릭스 PR body 에 attach.

### 4. catch 된 항목 → TEMPLATE.md fix

본 PR 안에서 즉시 fix.

### 5. dummy artifact 정리

- worktree 안에서만 작업 → worktree remove 시 자동 정리
- 또는 dummy 디렉토리 명시적 삭제 commit (실수로 dummy 가 main 머지되지 않게)

## Out of Scope

- 실제 새 도메인 프로젝트 생성 — 별도 task (사용자 도메인 의도 발화 시)
- TEMPLATE.md 의 narrative 톤 / 가독성 개선 — 본 task 는 정확성만

---

# Acceptance Criteria

- [ ] TEMPLATE.md instruction 의 모든 step 을 dummy 프로젝트로 dry-run.
- [ ] step 별 ✅/⚠️/❌ 매트릭스 PR body.
- [ ] catch 된 항목 본 PR fix.
- [ ] dummy artifact main 에 들어가지 않음 (worktree 내 작업 또는 명시적 cleanup).

---

# Related Specs

- `TEMPLATE.md` (검증 대상)
- `tasks/done/TASK-MONO-033-template-md-consistency-audit.md`
- `CLAUDE.md` § Local Network Convention (cross-ref)

---

# Edge Cases

- dummy 가 다른 프로젝트 빌드에 영향 가능 (root settings.gradle 추가 시) — worktree 내 작업으로 격리.
- GAP IdP 통합 step 은 실제 GAP DB 변경 (V00XX 시드) 까지 가지 않음 — instruction 의 SQL skeleton 만 검증.

---

# Failure Scenarios

- dummy artifact 가 실수로 main 에 commit → CI 에서 detect 또는 chore PR 으로 cleanup.
- dry-run 결과 instruction 의 critical 누락 발견 시 본 PR 안에서 fix → 본 PR 분량 큼.

---

# Test Requirements

- dry-run 자체가 검증.
- TEMPLATE.md fix 후 sample build (변경된 step 영향 받는 영역) 확인.

---

# Definition of Done

- [ ] 부트스트랩 instruction 매트릭스 PR body.
- [ ] catch 된 항목 fix.
- [ ] dummy artifact 정리.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-033 완료
