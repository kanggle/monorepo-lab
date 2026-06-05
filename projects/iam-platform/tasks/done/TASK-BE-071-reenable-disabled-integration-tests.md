# Task ID

TASK-BE-071

# Title

test(auth-service, security-service): TASK-BE-062 residual 3건 `@Disabled` 제거 + CI green 검증

# Status

superseded

> **종결 (2026-04-21)**: TASK-BE-069 + TASK-BE-070 + TASK-BE-072 머지 후 PR #32 에서
> @Disabled 제거했으나 CI 재실행 결과 9건 여전히 실패 (OAuth 5 / DetectionE2E 1 / DlqRouting 3).
> Connection pinning 외 **test context lifecycle + OutboxPublisher scheduler 정리 실패** 가
> 추가 원인으로 판별. 심층 진단은 **TASK-BE-073** 으로 승계. PR #32 close, 3 테스트
> `@Disabled` 상태 유지. 본 task 는 done/ 이동, 실제 re-enable 은 073 DoD.

# Owner

backend

# Task Tags

- test

# depends_on

- TASK-BE-069 (OAuth callback txn redesign)
- TASK-BE-070 (Testcontainers reuse + tuning)

---

# Goal

TASK-BE-069 (txn 재설계) 와 TASK-BE-070 (testcontainer 인프라 보강) 이 머지된 후, TASK-BE-062 residual 3건의 `@Disabled` 를 제거하고 CI green 을 검증한다:

- `OAuthLoginIntegrationTest` (auth-service)
- `DetectionE2EIntegrationTest` (security-service)
- `DlqRoutingIntegrationTest` (security-service)

---

# Scope

## In Scope

1. **`@Disabled` 제거** — 3개 integration 테스트 파일
2. **테스트 자체의 minor 조정** (only if needed):
   - 069/070 머지로 인해 assertion 이나 setup 이 약간 달라져야 한다면 수정
   - WireMock port, test email, container bootstrap 등 기존 convention 에 맞춤
3. **CI green 검증**:
   - `./gradlew build` CI 통과
   - **연속 CI 3회 green** 요구 (5회는 과도함 — 3회로 완화)
   - flaky 발생 시 재현/fix 사이클 수행, 필요하면 TASK-BE-069/070 에 추가 fix-task 생성
4. **task 종결**: TASK-BE-062 의 residual 상태 주석을 최종 종결 상태로 업데이트 (이미 done/ 에 있으므로 변경 없음; 본 task 가 "승계 task 의 최종 완료" 임을 명시)

## Out of Scope

- 다른 `@Disabled` 테스트 복원
- 추가 testcontainer 튜닝 (→ TASK-BE-070 scope)
- OAuth txn 구조 추가 변경 (→ TASK-BE-069 scope)
- 새 통합 테스트 추가

---

# Acceptance Criteria

- [ ] `OAuthLoginIntegrationTest` 의 `@Disabled` 제거 + CI 3회 연속 green
- [ ] `DetectionE2EIntegrationTest` 의 `@Disabled` 제거 + CI 3회 연속 green
- [ ] `DlqRoutingIntegrationTest` 의 `@Disabled` 제거 + CI 3회 연속 green
- [ ] `./gradlew :apps:auth-service:test :apps:security-service:test` 가 로컬(Docker 가용 환경) 에서 통과
- [ ] 기존 회귀 없음

---

# Related Specs

- `platform/testing-strategy.md` (TASK-BE-070 업데이트본 반영 후)
- `specs/services/auth-service/architecture.md` (TASK-BE-069 업데이트본 반영 후)

---

# Related Contracts

없음

---

# Target Service

- `apps/auth-service` (OAuthLoginIntegrationTest)
- `apps/security-service` (DetectionE2EIntegrationTest, DlqRoutingIntegrationTest)

---

# Architecture

test layer only. 구조 변경 없음.

---

# Edge Cases

- TASK-BE-069/070 이 머지됐어도 CI runner 환경에 따라 여전히 flaky 할 수 있음 — 3회 연속 green 이 조건
- 첫 run 에서 실패 시 원인이 069 미흡 / 070 미흡 / 새 flaky 요인 중 어느 것인지 구분 필요 → 실패 로그에 따라 fix-task 경로 결정

---

# Failure Scenarios

- 3회 연속 green 달성 실패 → test-by-test 로 쪼개서 개별 @Disabled 유지 + 실패한 것만 fix-task 로 넘기기 (partial 복원 허용)

---

# Test Requirements

- 3건의 통합 테스트가 CI 에서 실행 + pass
- 회귀 없음

---

# Definition of Done

- [ ] 3개 @Disabled 제거 + CI 3회 연속 green
- [ ] TASK-BE-062 residual 완전 종결
- [ ] Ready for review
