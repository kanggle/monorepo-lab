# Task ID

TASK-MONO-023

# Title

main branch 의 baseline integration / E2E 회귀 청소

# Status

ready

# Owner

backend / qa

# Task Tags

- code
- test

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

PR #107 / #108 / #109 머지 직후 (2026-05-02) 발견된 사실:

- main 브랜치 자체에 GAP integration / E2E (gateway-master) / Frontend E2E full-stack 잡이 **장기간 회귀 상태로 누적**되어 있다.
- 단순 doc-only PR (#108, #109) 도 같은 잡들이 FAIL 처리됨 — 즉 PR 의 변경과 무관한 main baseline 회귀.
- 그동안 사용자가 로컬에서 해당 잡들을 실행하지 않았거나 GitHub Actions 알림을 끄고 있어 누적 발견 안 됨.

이 태스크 완료 후:

- main 의 모든 CI 잡 (`Build & Test`, `Frontend (3종)`, `Package boot (2종)`, `Integration (master-service)`, `Integration (GAP)`, `E2E (gateway-master)`, `Frontend E2E full-stack`) 이 안정적으로 PASS
- 신규 PR 가 변경 무관 잡 실패에 시달리지 않음 (이전 처럼 false-positive 로 인한 grinding 비용 없음)
- 회귀 재발 방지를 위한 알림 / 게이팅 정책 명시

---

# Scope

## In Scope

### 1. main 의 실제 실패 잡 진단 + 분류

- 가장 최근 main commit 의 CI 결과 분석 (현재는 squash-merge 직후 c1040e20 / d4ca24a0 / 512cbbd4)
- 실패 테스트들을 다음 카테고리로 분류:
  - **(a) 진짜 회귀** (코드 또는 테스트 fix 필요)
  - **(b) flaky test** (테스트 격리 또는 timing — re-run 으로 회피 가능)
  - **(c) infra 의존 실패** (Docker / 네트워크 / runner 환경 의존)
  - **(d) 의도적으로 깨진 채로 유지된 placeholder** (있다면 명시)

### 2. 카테고리별 fix

- **(a) 진짜 회귀**: 본 태스크 안에서 fix 또는 sub-task 로 분할 (개수에 따라)
  - 예시 추정 영역 (PR #107 grinding 중 관찰):
    - `AccountAnonymizationSchedulerIntegrationTest` actorType=user vs system 단언 불일치
    - `AdminAuditTenantScopeIntegrationTest` 크로스 테넌트 deny audit row 누락
    - 다양한 `Status 200 vs 401/500/503` (OIDC/auth 통합 회귀)
    - `BadSqlGrammar outbox_events` 마이그레이션 missing
    - `DataIntegrityViolationException Data too long` 잔여 (community-service 외)
- **(b) flaky test**: 테스트 격리 강화 (각 테스트 unique ID, `@DirtiesContext`, etc.) 또는 의도된 retry 정책
- **(c) infra**: `docker pull`, `:e2e` image build 등 CI step 보강 (TASK-MONO-015 패턴 참고)
- **(d) placeholder**: `@Disabled` 명시 + 사유 javadoc + 복원 조건 task 발행

### 3. CI 게이팅 정책

- 머지 차단 잡: `Build & Test`, `Frontend (lint/build/unit)`, `Package boot` (필수 그린)
- 머지 차단 권장 잡: `Integration (master/GAP)`, `E2E`, `Frontend E2E full-stack` (조직 정책 결정)
- branch protection 룰 추가 또는 GitHub Actions required checks 갱신
- 필요 시 `--admin` 머지 가이드 문서화 (사용자가 의도해서 우회할 때만 사용)

### 4. 알림 / 모니터링

- `.github/workflows/ci.yml` 의 main push 트리거에 Slack / 이메일 / 디스코드 webhook 추가 (선택)
- 또는 GitHub email 알림 활성화 가이드를 README dev-onboarding 에 명시

## Out of Scope

- TASK-MONO-022 (Traefik 마이그레이션) 의 영향에 따른 e2e CI 재구성 — 그 태스크 내부에서 처리
- 새 e2e 시나리오 추가 — 기존 잡 stabilize 만
- 운영 (production) 환경 모니터링 — 본 태스크는 CI 환경만
- master-service 의 1개 flaky test (`Status 201 expected vs 409`) — 이미 카테고리 (b) 로 별도 처리 가능

---

# Acceptance Criteria

- [ ] main 최신 commit 에 대해 모든 CI 잡이 SUCCESS (또는 의도적 SKIP) 인 상태
- [ ] 실패 카테고리별 처리 결과 문서화 (해당 PR 의 description 또는 본 task body 의 Implementation Notes 에 추가)
- [ ] (a) 진짜 회귀 → 모두 fix 됨 (개수 많을 시 sub-task 로 분할 후 추적)
- [ ] (b) flaky → 격리 또는 retry 정책 적용
- [ ] (c) infra → CI step 수정으로 안정화
- [ ] (d) placeholder → `@Disabled` + 사유 명시
- [ ] CI 게이팅 정책 결정 + 적용 (branch protection 또는 README 명시)
- [ ] (선택) 알림 채널 설정 가이드 추가

---

# Related Specs

- 본 태스크가 영향 주는 스펙 없음 (테스트·CI 청소 작업).
- 회귀 fix 가 spec 변경을 요구하면 (예: AccountAnonymization actorType 의도가 spec 과 다른 경우) 해당 spec 함께 수정.

---

# Related Contracts

해당 없음. (테스트 stabilization 작업)

---

# Target Service / Component

- `projects/global-account-platform/apps/account-service` (AccountAnonymization 회귀 가능성)
- `projects/global-account-platform/apps/admin-service` (AdminAuditTenantScope 회귀 가능성)
- `projects/global-account-platform/apps/community-service` (잔여 truncation / OIDC integration)
- `projects/global-account-platform/apps/auth-service` (OIDC / Status code 회귀)
- `projects/global-account-platform/apps/membership-service` (관련 회귀 시)
- `projects/global-account-platform/apps/security-service` (관련 회귀 시)
- `.github/workflows/ci.yml`

---

# Implementation Notes

- 시작 시 main 의 가장 최근 CI run 다운로드 → `gap-integration-test-reports`, `e2e-test-reports`, `integration-test-reports` 아티팩트의 `<failure>` 노드 수집
- 실패 테스트 클래스/메소드 별로 분류 시트 작성 (예: `tasks/in-progress/TASK-MONO-023-failure-matrix.md` 임시 작업 노트)
- 실패 개수가 ~16개 (PR #107 의 5차 CI 에서 community truncation 제외 후 잔여 추정) 라면 sub-task 분할:
  - TASK-MONO-023a: AccountAnonymization 회귀
  - TASK-MONO-023b: AdminAuditTenantScope 회귀
  - TASK-MONO-023c: OIDC / status code 회귀
  - TASK-MONO-023d: outbox_events 마이그레이션 누락
  - 등
- 진짜 회귀 fix 는 각 sub-task PR 로 분리. 본 태스크는 분류·관리·정책 결정 작업.
- TASK-MONO-022 (Traefik 마이그레이션) 와 순서 의존성: TASK-MONO-022 가 e2e 잡 구조를 바꾸면 본 태스크의 (c) infra 처리에 영향. 두 태스크 진행 순서를 사용자가 결정.

---

# Edge Cases

- 어떤 fail 이 (a) vs (b) 인지 모호한 경우 → 5회 연속 PASS 시 (b) 로 분류, 그 외 (a) 로 분류
- TASK-MONO-022 마이그레이션이 일부 e2e 잡을 obsolete 시키면 → 해당 jobs 는 본 태스크 범위에서 제외 (TASK-MONO-022 가 책임)
- 회귀 fix 가 spec 변경을 요구할 때 → spec 변경 PR 먼저, 후속 fix PR 별도 (CLAUDE.md § Source of Truth Priority)
- branch protection 룰 추가 시 사용자가 직접 GitHub UI 에서 수동 적용 필요 → CLAUDE.md/README 안내만 가능

---

# Failure Scenarios

- **분류 시 (a) 가 너무 많아 본 태스크 안에 못 담음** → 모두 sub-task 발행 후 본 태스크는 "분류·정책" 만 done 처리
- **flaky 가 영구 flaky (재현 불가)** → 일단 retry 정책 적용 + `@Disabled` 처리 + 별도 추적 task
- **CI 환경 자체 결함 (GitHub runner Docker 28 등)** → workaround 적용 + GitHub 공식 fix 대기

---

# Test Requirements

- 본 태스크 자체는 코드 변경 없이 분류 + 정책 수립이 메인 산출물
- sub-task 들에서 각각의 회귀에 대한 단위/통합 테스트 추가 (해당 sub-task 책임)

---

# Definition of Done

- [ ] main 최신 commit 의 CI 잡 결과 분석 완료 + 카테고리 분류 문서화
- [ ] 카테고리별 처리 (fix 또는 sub-task 분할) 완료
- [ ] CI 게이팅 정책 결정 + 적용 (branch protection 또는 명시적 README)
- [ ] (선택) 알림 채널 가이드 추가
- [ ] 모든 sub-task 가 발행되어 추적 가능
- [ ] Ready for review
