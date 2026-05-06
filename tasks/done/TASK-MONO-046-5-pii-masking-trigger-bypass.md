# Task ID

TASK-MONO-046-5

# Title

GAP security-service login_history 트리거가 PiiMaskingService UPDATE 차단 (TASK-MONO-046-3 Phase 8 분리)

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

[TASK-MONO-046-3](../done/TASK-MONO-046-3-...md) Phase 7 진단 결과 PiiMaskingIntegrationTest 5건 의 root cause = `V0002__create_login_history_triggers.sql` 의 `trg_login_history_no_update` 가 PII masking 의 UPDATE 차단:

```
[KafkaListenerEndpointContainer#0-0-C-1] ERROR o.h.e.jdbc.spi.SqlExceptionHelper
  - SQL Error: 1644, SQLState: 45000
  - UPDATE not allowed on login_history (append-only)
```

`PiiMaskingService.maskPii` 가 `UPDATE login_history SET ip_masked='0.0.0.0', user_agent_family='REDACTED', device_fingerprint=SHA2(...) WHERE tenant_id=? AND account_id=?` 실행 — GDPR-driven masking 의도. 그러나 trigger 가 모든 UPDATE 차단 (audit append-only invariant).

두 invariant 충돌:
- (i) login_history 는 append-only audit log (trigger 가 보장)
- (ii) PII 는 GDPR 으로 masking 가능해야 함

본 task 가 trigger bypass 메커니즘 도입 + 5 PiiMasking 테스트 통과.

---

# Scope

## In Scope

### Schema 변경 — V0010__pii_masking_trigger_bypass.sql (신규)

옵션 (택 1):

- **(A)** Trigger 가 session variable bypass 로 PII masking 만 허용:
  ```sql
  DROP TRIGGER trg_login_history_no_update;

  CREATE TRIGGER trg_login_history_no_update
  BEFORE UPDATE ON login_history FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UPDATE not allowed on login_history (append-only)'
    -- @pii_masking_bypass=1 인 connection 만 통과 (PiiMaskingService 전용)
    WHEN @pii_masking_bypass IS NULL OR @pii_masking_bypass != 1;
  ```
  PiiMaskingService 가 `SET @pii_masking_bypass=1` → UPDATE → `SET @pii_masking_bypass=0` 실행.

- **(B)** Trigger 가 특정 컬럼 변경만 허용 (ip_masked, user_agent_family, device_fingerprint):
  ```sql
  CREATE TRIGGER trg_login_history_no_update
  BEFORE UPDATE ON login_history FOR EACH ROW
  WHEN OLD.event_id != NEW.event_id OR OLD.account_id != NEW.account_id OR ...
  SIGNAL SQLSTATE '45000' ...;
  ```
  단점: 컬럼 추가 시 trigger 재작성 필요.

- **(C)** Stored procedure 사용 — masking 만 stored procedure 통해 수행, trigger 가 stored procedure context 검사. MySQL 에서 복잡.

권장: **(A)** — 가장 명시적 + PiiMaskingService 변경 최소.

### Production 변경 — PiiMaskingService 또는 PiiMaskingLogJpaRepository

옵션 (A) 적용 시:

```java
// PiiMaskingService 또는 Custom @Repository 메서드
@Transactional
public boolean maskPii(...) {
    jdbcTemplate.execute("SET @pii_masking_bypass = 1");
    try {
        int lhRows = piiMaskingLogRepository.maskLoginHistory(...);
        ...
    } finally {
        jdbcTemplate.execute("SET @pii_masking_bypass = 0");
    }
    ...
}
```

session variable 은 connection-scoped → @Transactional 의 single connection 보장.

### 테스트 활성화

- `PiiMaskingIntegrationTest` `@Disabled("TASK-MONO-046-5: ...")` 제거 + 6/6 통과 검증
- 기존 `LoginHistoryImmutabilityIntegrationTest` 회귀 0 보장 (외부 UPDATE 는 여전히 차단)

### 검증

- `:security-service:integrationTest` 6 추가 PASS (PiiMasking)
- main CI `Integration (GAP)` Job 100% (046-4 + 046-5 머지 시)

## Out of Scope

- TASK-MONO-046-4 의 DLQ producer ClassCastException (별 cluster)
- account_lock_history / suspicious_events 의 trigger (별 trigger 없음 — UPDATE 허용)

---

# Acceptance Criteria

## 부팅 + 통과

1. PiiMasking 6/6 PASS (`@Disabled` 제거 후)
2. LoginHistoryImmutability 2/2 회귀 0 — 외부 UPDATE 시도 여전히 SQLSTATE 45000
3. main CI `Integration (GAP)` Job 다음 run SUCCESS

## 진단 + 검증

4. PR description 에 trigger bypass 메커니즘 명시 + GDPR audit 영향 (mask 변경은 허용, 다른 컬럼 변경은 차단)
5. specs/services/security-service/ 의 audit invariant 갱신 (trigger 가 PII masking 예외 허용)

## 회귀 0

6. 046 / 046-2 / 046-3 / 046-4 시리즈 + auth-service IT 회귀 0
7. `knowledge/incidents/2026-05-05-ci-regression.md` 에 본 task 결과 단락 추가

---

# Related Specs

- [TASK-MONO-046-3](../done/TASK-MONO-046-3-...md) — 직접 선행
- [TASK-MONO-046-4](TASK-MONO-046-4-dlq-producer-classcast.md) — 병렬 follow-up
- `projects/global-account-platform/specs/services/security-service/`
- TASK-BE-258 spec (PII masking 도메인 의도)

---

# Related Contracts

- 없음 — production 인터페이스 변경 없음

---

# Target Service / Component

- `projects/global-account-platform/apps/security-service/src/main/resources/db/migration/V0010__pii_masking_trigger_bypass.sql` (신규)
- `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/application/pii/PiiMaskingService.java` 또는 PiiMaskingLogJpaRepository
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/PiiMaskingIntegrationTest.java` (`@Disabled` 제거)

---

# Implementation Notes

- 옵션 (A) MySQL 8.0 SIGNAL trigger:
  ```sql
  CREATE TRIGGER trg_login_history_no_update
  BEFORE UPDATE ON login_history FOR EACH ROW
  BEGIN
    IF @pii_masking_bypass IS NULL OR @pii_masking_bypass != 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'UPDATE not allowed on login_history (append-only)';
    END IF;
  END;
  ```
  주의: BEGIN/END 블록 → Flyway 의 SQL parser 호환 검증 필요 (V0002 의 single-statement 패턴 대신).

- session variable scope: MySQL 의 user variables 는 connection-scoped. Hikari pool 이 connection reuse 하므로 `SET @pii_masking_bypass = 0` finally block 으로 명시적 reset 필수.

- LoginHistoryImmutabilityIntegrationTest 가 직접 UPDATE 시도 → trigger 작동 → 차단. 회귀 0 보장.

---

# Edge Cases

1. **Hikari pool 의 connection re-use**: `@Transactional` 종료 시 connection 반환 전 reset 보장.
2. **PII masking failure 후 retry**: bypass flag 가 leak 되지 않도록 finally + try/catch 명시.
3. **다른 서비스가 동일 connection 사용 시 영향**: session variable은 connection-scoped 이므로 다른 thread/connection 영향 0.

---

# Failure Scenarios

## A. Flyway parser 호환 안 됨

V0002 의 single-statement SIGNAL 패턴 + WHEN 절 사용 (기존 마이그레이션 패턴 보존).

## B. Hibernate 가 SET 문 실행 못함

JdbcTemplate 직접 사용. Hibernate session 우회.

## C. session variable 이 transaction 경계 넘어 leak

`@Transactional` (REQUIRES_NEW 아닌 default) 의 single connection 보장 + finally block 의 reset.

---

# Test Requirements

- PiiMasking IT 6/6 PASS
- LoginHistoryImmutability IT 2/2 회귀 0
- main CI `Integration (GAP)` Job 다음 run SUCCESS 검증
- 회귀 보고서 단락 갱신

---

# Definition of Done

- [ ] V0010 migration 추가 + trigger bypass 메커니즘 적용
- [ ] PiiMaskingService 또는 repo 에 SET @pii_masking_bypass 호출 추가
- [ ] PiiMaskingIntegrationTest `@Disabled` 제거
- [ ] security-service integrationTest 추가 6 통과
- [ ] LoginHistoryImmutability 회귀 0
- [ ] main CI `Integration (GAP)` Job SUCCESS 검증
- [ ] 회귀 보고서 단락 갱신 + spec 갱신
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — schema migration + production code + audit invariant 동시 분석. (Sonnet 도 가능 if 옵션 A 단순.)
- **분량 추정**: small-medium (1 신규 migration + 1 production fix + 1 test enable).
- **dependency**:
  - `선행`: TASK-MONO-046-3.
  - `병렬`: TASK-MONO-046-4 (DLQ producer), TASK-MONO-046-1 (auth SAS).
  - `후속`: 046-4 + 046-5 + 046-1 머지 시 main `Integration (GAP)` Job 100% milestone.
