# Task ID

TASK-MONO-041

# Title

ADR-MONO-002 — Phase 4 (Template 레포 추출) 진입 결정 + scm catalyst 명시

# Status

ready

# Owner

architect

# Task Tags

- docs
- adr
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

공통규칙 정리 시리즈 029~039 (13 task / 26 PR) 종결로 monorepo 가 Phase 4 진입 ready 상태. 새 도메인 (scm/erp/mes) 부트스트랩 의향 발화로 의사결정 필요 시점.

본 task 는 결정 자체를 ADR-MONO-002 로 명시 + 미래 reference (자기 / 다른 개발자 / AI 세션) 확보. 코드/빌드 영향 0, 순수 docs.

---

# Scope

## In Scope

### 1. `docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md` 신설

ADR-MONO-001 패턴 따름 (Status / Date / Decision driver / Supersedes / Related / Accepted Decisions / Migration window / Context / Decision / Consequences / Alternatives).

핵심 결정:
- **D1**: Phase 4 진입 trigger = 1 도메인 추가 (5 프로젝트 동거) — 3 도메인 동시 추가 안 함
- **D2**: 첫 도메인 = scm — wms 시너지 + 구현 난이도 중 + 첫 도메인 churn 최소화
- **D3**: Template 레포 실제 추출 시점 = scm 머지 + 라이브러리 churn 안정 평가 후 별도 ADR (ADR-MONO-003 candidate)
- **D4**: erp / mes 순서 = scm 종결 후 결정 (별도 ADR 또는 task 의향 시점)

Alternatives 명시:
- (a) Phase 3 유지 (4 프로젝트) — Rule of Three 충족하나 Template 추출 catalyst 없음
- (b) 1 도메인 추가 (5 프로젝트) ✅ — 라이브러리 churn 단계적
- (c) 3 도메인 동시 추가 (7 프로젝트) — root 공유 파일 conflict + GAP V seed race + 사용자 인풋 bottleneck

### 2. (선택) ADR 목록 갱신

`docs/adr/` 에 INDEX 가 있으면 ADR-MONO-002 한 줄 추가. 없으면 skip.

## Out of Scope

- scm 부트스트랩 자체 — 별도 task (TASK-MONO-040 추정, 새 세션 진행 중)
- erp / mes 부트스트랩 결정 — 별도 ADR 또는 task
- Template 레포 실제 신설 — 별도 ADR (ADR-MONO-003 candidate)
- CLAUDE.md / TEMPLATE.md 의 ADR-MONO-002 reference 추가 — 별도 작은 follow-up (또는 본 PR 마지막 commit 으로 추가)

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md` 신설.
- [ ] ADR-MONO-001 패턴 (Status / Date / Decision driver / Supersedes / Related / Accepted Decisions / Migration / Context / Decision / Consequences / Alternatives) 모두 포함.
- [ ] D1-D4 명확히 결정 명시.
- [ ] Status: ACCEPTED (사용자 의향 발화 받음 — 2026-05-04).

---

# Related Specs

- `docs/adr/ADR-MONO-001-port-prefix-scaling.md` (패턴 reference)
- `tasks/done/TASK-MONO-022-traefik-hostname-routing-migration.md` (precedent — ADR + task 짝)
- 메모리 [project_monorepo_template_strategy] (Phase 4 정의)
- 메모리 [project_cleanup_series_029_033] (cleanup 종결 → Phase 4 ready)

---

# Edge Cases

- **새 세션이 동시에 TASK-MONO-040 (scm 부트스트랩) 진행** — 본 task 는 docs/adr/ + tasks/INDEX.md 변경. INDEX 갱신 시 새 세션의 INDEX 변경과 conflict 가능. 새 세션 일시 중지 상태 (2026-05-04) 라 위험 낮음.
- **ADR-MONO-003 (Template 레포 실제 추출) 시점** — 본 task scope 외. scm 머지 후 별도 의사결정.

---

# Failure Scenarios

- **ADR 결정이 잘못 판명** — 결정 변경 시 본 ADR 을 SUPERSEDED 표시 + 새 ADR 로 대체.

---

# Test Requirements

- docs-only — 빌드 영향 없음.

---

# Definition of Done

- [ ] ADR 본문 작성 완료.
- [ ] PR 발행 + 머지.
- [ ] Ready for review.

---

# Prerequisites

- ✅ 공통규칙 정리 시리즈 029~039 종결 (Phase 4 ready 상태)
- ✅ 사용자 도메인 의향 발화 (scm/erp/mes 추천 → scm 첫 결정, 2026-05-04 세션)
