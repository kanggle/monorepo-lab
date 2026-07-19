# Task ID

TASK-MONO-432

# Title

federation-hardening-e2e nightly 워크플로에 inbound-expected live leg 배선 (ADR-MONO-050 Phase 2 잔여 — shared `.github/workflows/`)

# Status

ready

# Owner

monorepo / devops

# Task Tags

- ci
- e2e
- monorepo

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) SCM-INT-004 가 남긴 **의도적 핸드오프**를 종결한다. inbound-expected federation live-leg 아티팩트(overlay compose · seed SQL · Playwright spec)는 이미 작성됐으나, 그것들을 **실행**시키는 nightly 워크플로 배선(`.github/workflows/federation-hardening-e2e.yml`)은 **미완**이다. 이유: shared-path + nightly 로만 검증 가능 + blind 편집 시 critical nightly 를 조용히 깨는 클래스(메모리 `project_nightly_e2e_service_addition_drift` · `env_ci_zero_runs_means_merge_conflict`). root task 로 분리해 신중히 배선한다.

**monorepo-level**(shared `.github/workflows/`) → root `tasks/`.

---

# Scope

## In Scope

1. `.github/workflows/federation-hardening-e2e.yml` 에 inbound-expected leg 배선:
   - `wms-inbound-service` jar-artifact 플러밍(빌드·아티팩트).
   - `-f docker-compose.federation-e2e.inbound-expected.yml` 추가(약 20개 compose invocation 전수 — 누락 시 drift).
   - health gate(wms-inbound-service).
   - `fixtures/seed-wms-inbound.sql` psql seed step.
   - `E2E_WMS_INBOUND_BASE_URL` env.
2. SCM-INT-004 이 남긴 2 open risk 해소: (a) `AsnController` GET `hasRole('INBOUND_READ')` 가 이 스택의 SUPER_ADMIN 와일드카드 토큰에 매핑되는지, (b) 직접-SQL 마스터 seed 타이밍 vs Flyway.

## Out of Scope

- 아티팩트 자체 변경 0 (overlay/seed/spec 은 SCM-INT-004 에서 작성됨).
- PR-gated Testcontainers 가드 변경 0 (그게 권위, 이건 nightly 보조).

---

# Acceptance Criteria

1. nightly federation 워크플로가 inbound-expected overlay 를 부팅·시드·검증(실 scm→wms InboundExpectation).
2. compose invocation 전수 갱신(drift 0) + health gate + seed step + env.
3. 2 open risk(INBOUND_READ 매핑, seed 타이밍) 확인·해소.
4. nightly 실행으로만 검증 가능 — 배선 후 nightly 관찰.

---

# Related Specs

- [ADR-MONO-050](../../docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md) §3 Phase 2 / §5 (nightly=silent regress, PR-gated 가 권위)
- [TASK-SCM-INT-004](../../projects/scm-platform/tasks/ready/TASK-SCM-INT-004-inbound-expected-cross-service-e2e.md) — 아티팩트 작성(선행)
- ADR-MONO-027 replenishment federation leg — 동형 선례

---

# Related Contracts

- 없음 (CI 배선).

---

# Target Service / Component

- `.github/workflows/federation-hardening-e2e.yml`
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.inbound-expected.yml` (참조)

---

# Edge Cases

1. compose invocation 일부만 갱신 → drift(초록 머지 후 nightly RED, 메모리 `project_nightly_e2e_service_addition_drift`). 전수 갱신 필수.
2. paths-filter 에 새 잡 추가 시 negation 금지(pure-positive, 메모리 `project_ci_path_filter_074_075_quirk`).

---

# Failure Scenarios

## A. blind 편집으로 nightly 손상

→ shared critical 워크플로. 신중히·전수·nightly 관찰. 로컬 검증 불가이므로 배선 후 첫 nightly 확인 필수.

---

# Test Requirements

- 배선 후 nightly federation 실행에서 inbound-expected leg GREEN 관찰.

---

# Definition of Done

- [ ] 워크플로 배선(jar·compose 전수·health·seed·env)
- [ ] 2 open risk 해소
- [ ] nightly 관찰 확인

---

# Notes

- **Recommended impl model**: Opus(shared critical 워크플로, drift 리스크). 선행=ADR-050 통합 랜딩. `.github/workflows/` 는 분류기 비차단(shared-path, root task).
