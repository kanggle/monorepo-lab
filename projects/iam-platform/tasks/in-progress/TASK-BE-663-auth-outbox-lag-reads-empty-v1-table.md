# Task ID

TASK-BE-663

# Title

auth-service `auth_outbox_lag_seconds` 가 v2 이전 뒤 빈 v1 `outbox` 테이블을 읽어 **늘 0** — `AuthOutboxLagHigh` 가 울릴 수 없다

# Status

in-progress

# Owner

backend

# Task Tags

- observability
- code
- fix

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (본보기 그대로 — `TASK-BE-662` 의 account `OutboxLagMetric`)
> 기안 2026-11-24 UTC. 출처: `TASK-BE-662` 구현 중 발견(증거 절 «후속 거리») → 소유자 «진행해».

# Dependency Markers

- 선행: `TASK-BE-662`(done) — account-service 에 같은 metric 을 v2 테이블로 구현한 본보기.

# Goal

`apps/auth-service/.../infrastructure/messaging/OutboxLagMetric` 의 쿼리는 `FROM outbox WHERE status = 'PENDING'` 이다. `V0027__auth_outbox_v2.sql` 이후 relay 는 `auth_outbox`(`published_at IS NULL` = 미발행)를 쓰고, v1 `outbox` 는 일부러 남겨 둔 **빈 테이블**이다 — `TASK-BE-450` 이 *«reports 0. Left as-is (compiles, harmless)»* 로 기록했다. 그 «harmless» 가 틀렸다: `projects/iam-platform/infra/prometheus/alert-rules.yml` 의 `AuthOutboxLagHigh`(`auth_outbox_lag_seconds > 60`)와 스펙(`auth-service/observability.md:20` · :74)이 이 값에 기대고 있어서, relay 가 멈춰도 알림이 **울릴 수 없다**. 이름은 내보내지므로 `TASK-MONO-834` 의 이름 가드는 이것을 못 본다 — **값** 의 결함.

부차: 쿼리가 `NOW()`(세션 시간대)를 쓰는데 `created_at` 은 UTC 로 저장된다(JDBC `serverTimezone=UTC`) → 세션 시간대가 UTC 가 아니면 시간 단위로 틀어진다.

# Scope

## In Scope

- 쿼리를 `SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), UTC_TIMESTAMP(6)), 0) FROM auth_outbox WHERE published_at IS NULL` 로(컬럼 = `V0027` 의 `created_at DATETIME(6) NOT NULL` · `published_at DATETIME(6)`). account 본보기와 같은 모양 · 같은 -1 실패 표식 · v1 을 읽지 않는 이유 javadoc.
- 단위 테스트 — account 의 `OutboxLagMetricTest` 와 같은 5건(0 · 나이 · null · -1 · SQL 이 v2 를 읽고 v1 을 안 읽는지).

## Out of Scope

- metric 이름 · 알림 규칙 · 스펙 변경(이름과 의미는 그대로, 값만 고친다).
- v1 `outbox` 테이블 삭제(별개 마이그레이션 결정).

# Acceptance Criteria

- [ ] AC-1: 쿼리가 `auth_outbox` 의 `published_at IS NULL` 행을 `UTC_TIMESTAMP(6)` 기준으로 잰다.
- [ ] AC-2: 단위 테스트 5건 초록 · `:projects:iam-platform:apps:auth-service:test` rc=0.
- [ ] AC-3: PR CI 의 iam Build & Test · Integration (iam A/B) 가 **실행되어** success.

# Related Specs

- `projects/iam-platform/specs/services/auth-service/observability.md` :20 · :74

# Related Contracts

- 없음.

# Edge Cases

- 미발행 행 없음 → 0 · 조회 실패 → -1(account 와 같은 표식, «fresh 아님»).

# Failure Scenarios

- v1 쿼리를 남긴 채 테이블 이름만 바꿈(`status` 조건 유지) → `auth_outbox` 에는 `status` 가 없어 SQL 오류 → 영원히 -1. AC-2 의 SQL 단언이 잡는다.

# Test Requirements

- 단위 테스트 · auth-service `test` · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
