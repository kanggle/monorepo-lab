# Task ID

TASK-SCM-BE-002d

# Title

procurement-service Testcontainers integration tests (TASK-SCM-BE-002b Phase 5)

# Status

ready

# Owner

backend / qa

# Task Tags

- test
- code

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

[TASK-SCM-BE-002b](../done/TASK-SCM-BE-002b-procurement-test-pyramid.md) 의 Phase 5 — Testcontainers integration tests. procurement-service 의 multi-tenant isolation / outbox relay / supplier circuit breaker / asn overreceipt / state machine atomicity / audit log 를 실 MySQL + Kafka + Redis + WireMock 환경으로 검증.

**선결 조건**: Docker Desktop 4.36+ socket 회귀 (memory `project_testcontainers_docker_desktop_blocker.md`) 해결. 본 task 는 Docker 사용 가능 환경에서 실행. CI Linux runner 는 정상 동작하므로 PR CI 만으로도 진행 가능 (로컬 검증 NO).

---

# Scope

## In Scope (≥ 7 IT tests)

`@SpringBootTest` + `@Tag("integration")` + `AbstractIntegrationTest` 상속 (MySQL + Kafka 공유). 각 IT class 는 service-specific Redis + WireMock 추가.

1. **Multi-tenant isolation IT**: tenant A의 PO가 tenant B의 actor JWT 로 조회 시 404 (PoNotFoundException — Edge Case #5)
2. **Outbox relay IT**: PO submit → outbox_events row insert → polling scheduler → Kafka topic 발행 검증 (Kafka consumer 로 수신)
3. **Supplier circuit breaker IT**: WireMock 5xx stub × 3 → Resilience4j circuit OPEN → SupplierUnavailableException → 503 응답
4. **Supplier idempotency IT**: 동일 idempotency key 재전송 → 200 + supplierReceiptRef 동일
5. **State machine atomicity IT**: submit 중 outbox writer 실패 시 rollback → PO 상태 + history + outbox 모두 일관 (DRAFT 유지)
6. **ASN overreceipt IT**: PO line quantity=10 + ASN line quantity=15 → AsnOverreceiptException → 422
7. **Audit log IT**: draft → submit → cancel 흐름에서 audit_log row 3개 (DRAFT / SUBMIT / CANCEL) 모두 기록

## Out of Scope

- production code 변경 (002b 정책 동일 — small fix 만 별 commit)
- supplier 실 EDI / SFTP integration (v2)

---

# Acceptance Criteria

1. ≥ 7 IT tests `@Tag("integration")` 마킹 + PASS
2. local `:integrationTest` PASS (Docker 환경)
3. CI `Integration` Job PASS (다음 run)
4. `:procurement-service:check` 에 `@Tag("integration")` exclude 유지 (Docker-less default)

---

# Related Specs

- [TASK-SCM-BE-002b](../done/TASK-SCM-BE-002b-procurement-test-pyramid.md) — 직접 선행
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `rules/traits/transactional.md` § T1-T4 (idempotency / outbox / state machine atomicity)
- `rules/traits/integration-heavy.md` § (supplier circuit breaker)

---

# Implementation Notes

- **선결**: Docker Desktop 4.36+ socket 회귀 해결 — Rancher Desktop / downgrade / WSL Integration toggle 중 하나
- 답습 reference: `projects/global-account-platform/apps/security-service/src/test/java/.../integration/SecurityServiceIntegrationTest.java` (AbstractIntegrationTest 상속, `@DirtiesContext(AFTER_CLASS)`, Redis container 추가, WireMock for supplier)
- Outbox relay IT 의 timing — `await().atMost(15s).untilAsserted(...)` 패턴
- Circuit breaker IT — Resilience4j config 의 `slidingWindowSize` + `failureRateThreshold` 명시적 인지

---

# Edge Cases

1. **Cross-test offset accumulation** (TASK-MONO-046-3 선례) — `@DirtiesContext` + per-class consumer group 명시적 설정 필요
2. **Supplier WireMock URL race** — `@DynamicPropertySource` + lazy URL resolution (TASK-MONO-046-1 패턴)
3. **Outbox polling scheduler 와 IT race** — test profile 에서 scheduler 비활성화 또는 명시적 trigger
4. **AES credential encryptor master key** — test profile 에서 fixed key 주입 (production key leak 방지)

---

# Failure Scenarios

## A. Docker fix 안 됨

WSL Integration toggle / downgrade / Rancher 중 어느 것도 효과 없으면 CI iteration 만 사용. local 검증 포기, PR description 에 명시.

## B. cross-class offset replay (TASK-MONO-046-3 패턴)

per-class consumer group + `auto-offset-reset=latest` + `waitForAssignment` 답습 — 검증된 패턴이라 즉시 적용.

## C. CI 환경 자원 부족

Testcontainers startup memory 부족 시 sequential execution 강제 (`@ResourceLock` 또는 `forkEvery` 조정).

---

# Test Requirements

- ≥ 7 IT tests + 모두 `@Tag("integration")` 마킹
- 각 IT class 가 `AbstractIntegrationTest` 상속
- `:procurement-service:integrationTest` PASS

---

# Definition of Done

- [ ] 7 IT tests 작성 + PASS (local + CI)
- [ ] CI `Integration` Job PASS
- [ ] knowledge/incidents 또는 별 보고서에 IT 추가 결과 단락 (선택)
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — IT 패턴 deterministic, GAP / fan-platform reference 답습 가능.
- **분량**: medium-large — 7 IT class + Testcontainers 환경 setup (각 service-specific Redis / WireMock).
- **dependency**:
  - `선행`: TASK-SCM-BE-002b (이미 done). Docker Desktop 4.36+ socket 회귀 해결 (memory 참조).
  - `병렬 가능`: TASK-SCM-BE-002c (slice tests, Docker 무관).
- **CI gating**: 본 PR 자체 영향 = procurement IT 추가 (Testcontainers 환경 필요).
