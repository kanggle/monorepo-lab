# Task ID

TASK-MONO-033

# Title

TEMPLATE.md ↔ 실제 monorepo 정합성 audit

# Status

ready

# Owner

backend

# Task Tags

- audit
- template
- chore

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

공통규칙·스펙 정리 시리즈 5개 task 중 마지막. `TEMPLATE.md` 가 현재 monorepo 의 실제 상태 + 신규 프로젝트 부트스트랩 / standalone 추출 흐름을 정확히 반영하는지 audit + 정리.

TEMPLATE.md 는 향후 Phase 4 (라이브러리 안정화 후 Template 레포 추출) 의 source of truth. 4 프로젝트 동거 + 큰 cutover 시리즈 후 TEMPLATE.md 가 stale 했을 가능성 높음 (예: GAP IdP 표준화 / Traefik hostname routing / standalone freeze 정책 / sync-portfolio.sh PROJECT_EXCLUDE_PATHS 같은 새 기능 미반영).

---

# Scope

## In Scope

### 1. Discovery → Distribution 절차 정확성

TEMPLATE.md 의 핵심 흐름 (Discovery 모노레포 → Distribution Template 레포 추출) 의 각 단계가 실제 도구 (`scripts/sync-portfolio.sh`) 와 일치하는지:

- composite-build 절차 (deprecated 됐지만 fallback 으로 보존되는지)
- direct-include 절차 (현 4 프로젝트 모두 사용)
- 신규 프로젝트 통합 시점의 trigger (`project_ecommerce_import_readiness.md` 의 트리거 ①+② 참조)

### 2. 신규 프로젝트 부트스트랩 instruction

TEMPLATE.md 의 "신규 프로젝트 추가" 절차가 다음 항목을 포함하는지:

- `PROJECT.md` frontmatter (taxonomy_version + domain + traits + service_types + scale_tier + data_sensitivity + compliance)
- `apps/`, `specs/`, `tasks/`, `knowledge/`, `docs/`, `infra/` 디렉토리 구조
- `tasks/INDEX.md` lifecycle (backlog/ ready/ in-progress/ review/ done/ archive/) — 단 root 의 INDEX.md 와 동일한 lifecycle 4단계 인지 (project 측은 backlog/archive 추가) 확인
- root `settings.gradle` include 추가
- root `package.json` 의 단축 스크립트 (`pnpm <project>:up` 류) 추가
- `docker-compose.yml` 의 Traefik hostname labels (CLAUDE.md § Local Network Convention 참조)
- `.env.example` placeholder

### 3. Local Network Convention 정합성

TEMPLATE.md § Local Network Convention 이 CLAUDE.md § Local Network Convention 과 일치하는지 (둘 다 source of truth 후보 — 어느 한쪽이 master 이고 다른 쪽이 reference 인지 명시):

- Traefik hostname pattern (`<project>.local`)
- 백엔드 서비스 expose 만 (host port 없음)
- DB 도구 접근 3가지 방법
- legacy PORT_PREFIX 폐기 명시

### 4. standalone 추출 정책

TEMPLATE.md 가 standalone v1 freeze 정책 (TASK-MONO-028 산출) 을 반영하는지:

- `PROJECT_EXCLUDE_PATHS` 의 의미 + 사용 시점 설명
- standalone 이 monorepo 의 일부 변경을 의도적으로 안 받는 케이스 (ecommerce v1 = 자체 auth-service 보존)
- standalone 의 dual-deploy 전략 (`project_portfolio_submission_strategy.md` 와의 cross-ref)

### 5. GAP IdP 표준화 반영

GAP 가 표준 OIDC IdP 가 된 후 (ADR-001) 신규 프로젝트는 자체 auth-service 가 아니라 GAP 통합으로 시작. TEMPLATE.md 가 다음 패턴을 명시하는지:

- 신규 프로젝트의 gateway-service 가 OAuth2 Resource Server + GAP JWKS validator 추가
- 새 OIDC client 등록은 GAP V00XX 시드로
- 새 tenant 등록 절차 (GAP admin API)
- consumer-integration-guide 참조

### 6. 4 프로젝트 동거 시점의 새 기능 반영

다음이 TEMPLATE.md 에 반영됐는지:

- `tasks/INDEX.md` 의 PR Separation Rule (spec / impl / chore PR 분리)
- 본 시리즈 (TASK-MONO-029~033) 가 권장하는 audit 주기 (예: N 프로젝트 동거 시점마다 또는 큰 cutover 후)
- ADR-MONO-001 (Traefik hostname routing) ACCEPTED 상태

## Out of Scope

- TEMPLATE 레포 자체 신설 (Phase 4 진입 결정 시) — 본 task 는 TEMPLATE.md 정합성만
- TEMPLATE.md 의 narrative 톤 / 가독성 개선 — 별도 refactor task
- Phase 4 진입 의사결정 (실제 Template 레포 만들지 vs 더 기다릴지) — 사용자 결정 영역

---

# Acceptance Criteria

- [ ] TEMPLATE.md ↔ 실제 monorepo 상태 매트릭스 (위 6개 영역) PR body 첨부.
- [ ] CLAUDE.md ↔ TEMPLATE.md 의 source of truth 분담 명시 (어느 쪽이 master).
- [ ] Critical drift (잘못된 instruction → 신규 프로젝트 부트스트랩 깨짐) 본 PR fix.
- [ ] Warning (stale 설명 / 누락 새 기능) fix 또는 follow-up.
- [ ] Suggestion 카탈로그.

---

# Related Specs

- `TEMPLATE.md` (audit 대상)
- `CLAUDE.md` (cross-ref source of truth)
- `scripts/sync-portfolio.sh` (실제 추출 도구)
- `tasks/done/TASK-MONO-022-traefik-hostname-routing-migration.md`
- `tasks/done/TASK-MONO-024-existing-projects-traefik-migration.md`
- `tasks/done/TASK-MONO-027-ecommerce-gap-integration.md`
- `tasks/done/TASK-MONO-028-ecommerce-standalone-v1-freeze-policy.md`
- `tasks/done/TASK-MONO-029-rules-validation-audit.md`

---

# Related Skills

- `audit-memory` (audit 패턴 reference)

---

# Target Component

- `TEMPLATE.md` (root)
- `CLAUDE.md` (cross-ref 만, 본 task 는 TEMPLATE 측만 수정)

---

# Architecture

audit + chore. TEMPLATE.md 본문 수정 가능.

---

# Implementation Notes

- TEMPLATE.md 는 narrative 문서 — diff 가 큼. 영역별 commit 분리 (예: `docs(template): GAP IdP 표준화 패턴 추가`, `docs(template): standalone freeze 정책 cross-ref 추가`).
- 4 프로젝트 (wms / ecommerce / GAP / fan-platform) 의 부트스트랩 패턴이 어떻게 다른지 비교 → 공통점만 TEMPLATE.md 에 반영, 차이는 trait/domain 결정 사항으로 명시.

---

# Edge Cases

- **TEMPLATE.md 가 future tense 로 기술된 부분**: "Phase 4 이후" 같은 future plan 은 그대로 두되, "이미 완료" 항목은 명시 갱신.
- **CLAUDE.md 와 중복**: 두 문서가 같은 내용 다르게 기술하면 source of truth 분담 결정 필요.

---

# Failure Scenarios

- **TEMPLATE.md 갱신 후 신규 프로젝트 부트스트랩 시 instruction 따라했는데 깨짐**: 본 task 머지 후 TEMPLATE.md 따라 하나의 dummy 프로젝트 부트스트랩 dry-run 권장 (별도 follow-up).
- **CLAUDE.md 와의 중복 제거 시 양쪽 정보 유실**: 어느 쪽이 master 인지 결정 후 다른 쪽은 redirect.

---

# Test Requirements

- audit 자체가 검증.
- TEMPLATE.md 변경이 빌드 / 테스트 영향 없음 확인 (docs-only).

---

# Definition of Done

- [ ] 6개 영역 매트릭스 PR body.
- [ ] source of truth 분담 명시.
- [ ] Critical fix.
- [ ] Ready for review.

---

# Prerequisites

- ✅ TASK-MONO-029 완료
- 권장: TASK-MONO-030 / -031 / -032 후 마지막에 진행 (정리된 결과를 TEMPLATE.md 에 반영하는 흐름)
