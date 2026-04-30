# Task ID

TASK-BE-073

# Title

infra(test): integration test context/infra 재진단 — 069+070+072 머지 후에도 남은 실패 원인 규명

# Status

superseded

> **종결 (2026-04-21)**: P0 (`OutboxPollingScheduler @PreDestroy + running guard`)
> 는 별도 PR 로 머지 예정 — shutdown noise 해소는 benign improvement. 그러나
> P2 (3 테스트 @Disabled 제거) CI 실측 결과 9건 동일 실패 재현 (OAuth 5 /
> DetectionE2E 1 / DlqRouting 3). RCA 가 부정확 — 실제 원인은 scheduler
> shutdown 이 아니라 **test body 내부 assertion 실패** (`stubXxx` 호출부 또는
> MockMvc andExpect 연쇄). 심층 진단은 TASK-BE-074 로 승계. 3 테스트 `@Disabled`
> 유지.

# Owner

backend

# Task Tags

- test
- infra

# depends_on

- TASK-BE-069 (merged)
- TASK-BE-070 (merged)
- TASK-BE-072 (merged)
- TASK-BE-071 (superseded)

---

# Goal

TASK-BE-069 (OAuth provider HTTP 분리) + TASK-BE-070 (Testcontainers 타임아웃/wait) + TASK-BE-072 (account-service HTTP 분리) 를 모두 머지한 후에도 PR #32 CI 에서 TASK-BE-062 residual 3건이 여전히 실패함:

- `OAuthLoginIntegrationTest` 5건 (Google/Microsoft/Kakao happy path + existing email + Microsoft fallback)
- `DetectionE2EIntegrationTest` 1건 (velocity rule auto-lock)
- `DlqRoutingIntegrationTest` 3건 (malformed JSON / invalid UTF-8 / missing eventId)

CI 로그의 핵심 증상:
- `HikariPool-1 - Shutdown initiated` 가 테스트 중간에 관측됨
- `SQLState 08S01` (communications link failure) — MySQL 세션이 끊김
- `HikariPool-2 - Connection is not available, request timed out after 3000ms (total=0, active=0, idle=0, waiting=0)` — pool 이 **완전 비어있음** (HikariPool-1 shutdown 후 HikariPool-2 로 전환된 것으로 추정)
- `OutboxPublisher$$SpringCGLIB$$0.publishPendingEvents` 가 Spring context 종료 중에도 계속 실행됨

즉 connection pinning 보다 **test context lifecycle + scheduler 정리 실패** 가 더 직접적 원인으로 판단됨.

---

# Scope

## In Scope

1. **Spring context 수명 추적**:
   - 어떤 시점에 `HikariPool-1` 이 shutdown 되는지 (어느 테스트의 `@AfterAll`/`@DirtiesContext`?)
   - Spring test context caching 이 활성 상태에서 왜 HikariPool-2 가 새로 생성되는지 (context 재로딩 신호)
   - `OutboxPublisher` 의 `@Scheduled` 메서드가 context shutdown 이후에도 실행되는 이유
2. **OutboxPublisher scheduler 정리**:
   - `@PreDestroy` 또는 `SmartLifecycle` 로 graceful shutdown 구현
   - 테스트 프로파일에서 scheduler disable (`@ConditionalOnProperty` 또는 test-only config)
3. **XML test reports 분석**:
   - CI artifact `build/test-results/test/*.xml` 다운로드하여 각 AssertionError 의 `<failure>` 메시지 파악
   - 특히 `OAuthLoginIntegrationTest.googleHappyPath` line 166 assertion 의 expected vs actual — stub 불일치인지 DB 상태인지 판별
4. **WireMock stub 매칭 재검증**:
   - TASK-BE-069/072 의 orchestration 변경으로 호출 경로/순서가 바뀌었을 수 있음 (예: `accountServicePort.socialSignup` 호출이 이제 UseCase 에서 직접 나가는데 기존 stub 이 HTTP path/method 가정을 공유하는지)
   - stub hit miss 를 `wireMock.getAllServeEvents()` 로 dump 하여 검증
5. **결과 반영**:
   - 근본 원인 fix → 필요 시 TASK-BE-069/070/072 에 hotfix commit 추가 (별도 task 가 아니라 본 task 의 커밋으로 포함 가능)
   - 3 테스트 `@Disabled` 제거
   - CI 3회 연속 green 확인 (TASK-BE-071 의 AC 이 본 task 에 이전됨)

## Out of Scope

- 새 통합 테스트 추가
- OAuth provider 추가
- Testcontainers 외 실행기로 교체

---

# Acceptance Criteria

- [ ] `OutboxPublisher` scheduler 가 Spring context shutdown 시 clean 하게 정지
- [ ] test profile 에서 `HikariPool-1 Shutdown initiated` 로그가 테스트 method 실행 중간에 나타나지 않음
- [ ] `OAuthLoginIntegrationTest`, `DetectionE2EIntegrationTest`, `DlqRoutingIntegrationTest` 의 XML test reports 에서 나온 원인 분석 결과가 task 완료 시 task file 또는 commit 메시지에 기록됨
- [ ] 위 3 테스트의 `@Disabled` 제거 + CI 3회 연속 green
- [ ] 기존 green 테스트 회귀 없음

---

# Related Specs

- `platform/testing-strategy.md` (TASK-BE-070 convention 섹션)
- `specs/services/auth-service/architecture.md` (TASK-BE-069/072 트랜잭션 경계 섹션)
- `platform/event-driven-policy.md` (OutboxPublisher 정책)

---

# Related Contracts

없음 (test infra)

---

# Target Service

- `apps/auth-service` (OAuthLoginIntegrationTest + OutboxPublisher)
- `apps/security-service` (DetectionE2E, DlqRouting)
- 공용 messaging lib (OutboxPublisher 가 libs/ 에 있을 경우)

---

# Architecture

test infrastructure + application-layer scheduler 정리. 레이어 영향 없음.

---

# Edge Cases

- `@DirtiesContext` 를 붙이면 context 재생성 비용이 크지만 격리 보장됨 — trade-off 기록
- 테스트 프로파일에서 scheduler disable 은 대부분 권장되지만, scheduler 자체의 동작이 테스트 대상인 경우 (예: OutboxPollingScheduler 테스트) 조건부 처리 필요
- CI 에서 HikariPool-1 / HikariPool-2 전환은 Spring test context caching 이 실패했다는 신호일 가능성 — `application-test.yml` 의 property override 가 context key 에 영향주는지 확인

---

# Failure Scenarios

- 근본 원인이 여전히 규명 안 되면 3 테스트 각각 독립적으로 @Disabled 처리 유지 + fix-task 세분화

---

# Test Requirements

- 본 task 해결 후 `./gradlew :apps:auth-service:test :apps:security-service:test` 가 로컬(Docker 가용) 에서 pass
- CI 3회 연속 green (71 AC 승계)

---

# Definition of Done

- [ ] 근본 원인 분석 결과가 commit/task 에 기록
- [ ] 3 테스트 @Disabled 제거 + CI 3회 green
- [ ] Ready for review
