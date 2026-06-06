# Task ID

TASK-BE-011

# Title

security-service 비정상 로그인 탐지 규칙 — VelocityRule, GeoAnomalyRule, DeviceChangeRule, TokenReuseRule

# Status

backlog

# Owner

backend

# Task Tags

- code
- event

# depends_on

- TASK-BE-008
- TASK-BE-009

---

# Goal

security-service에 4개 탐지 규칙(Strategy Pattern)을 구현하고, risk score 집계 → 임계치 기반 자동 잠금 HTTP 호출 → suspicious_events 기록 → 이벤트 발행의 전체 파이프라인을 완성한다.

---

# Scope

## In Scope

- `SuspiciousActivityRule` 전략 인터페이스 + 4개 구현: VelocityRule, GeoAnomalyRule, DeviceChangeRule, TokenReuseRule
- Risk score 집계 (max)
- Action threshold: NONE(0-49) / ALERT(50-79) / AUTO_LOCK(80-100)
- `suspicious_events` 테이블 + Flyway 마이그레이션
- 자동 잠금: `POST /internal/accounts/{id}/lock` 호출 (Idempotency-Key = suspicious_event_id)
- Outbox: `security.suspicious.detected`, `security.auto.lock.triggered`
- Redis 키: `security:velocity:*`, `security:geo:last:*`, `security:device:seen:*`
- 탐지 파라미터 `@ConfigurationProperties`로 외부 주입 (코드 하드코딩 금지)
- MaxMind GeoLite2 로컬 DB (GeoAnomalyRule)

## Out of Scope

- 외부 IP 평판 API 연동 (선택적, 미래)
- 실시간 알림 발송 (이벤트 발행으로 대체)
- admin 대시보드에서의 규칙 파라미터 동적 변경 UI

---

# Acceptance Criteria

- [ ] `auth.login.failed` 10회/시간 초과 → VelocityRule riskScore ≥ 80 → AUTO_LOCK
- [ ] 한국 → 30분 후 미국 로그인 → GeoAnomalyRule riskScore ≥ 85 → AUTO_LOCK
- [ ] 미등록 디바이스 단독 → DeviceChangeRule riskScore=50 → ALERT (잠금 안 함)
- [ ] `auth.token.reuse.detected` 수신 → TokenReuseRule riskScore=100 → 즉시 AUTO_LOCK
- [ ] AUTO_LOCK 시 `POST /internal/accounts/{id}/lock` 호출 + 멱등 키 사용
- [ ] `suspicious_events` 테이블에 ruleCode, riskScore, evidence, actionTaken 기록
- [ ] `security.suspicious.detected` + `security.auto.lock.triggered` 이벤트 발행
- [ ] account-service 호출 3회 실패 → outbox에 pending 이벤트 + 운영자 알림 메트릭
- [ ] 탐지 임계치가 환경 변수로 조정 가능

---

# Related Specs

- `specs/features/abnormal-login-detection.md`
- `specs/use-cases/abnormal-login-detection.md` (UC-8, 9, 10)
- `specs/services/security-service/architecture.md` — domain/detection/
- `specs/services/security-service/redis-keys.md`

# Related Contracts

- `specs/contracts/events/auth-events.md` (소비)
- `specs/contracts/events/security-events.md` (발행)
- `specs/contracts/http/internal/security-to-account.md` (auto-lock)

---

# Target Service

- `apps/security-service`

---

# Edge Cases

- 동일 계정에 VelocityRule + GeoAnomalyRule 동시 발동 → finalScore = max(둘 중 높은 것)
- AUTO_LOCK 후 같은 계정의 후속 이벤트 → 이미 LOCKED → lock 호출 멱등 200
- GeoIP DB에 IP가 없는 경우 → geoCountry=UNKNOWN, GeoAnomalyRule 스킵

---

# Failure Scenarios

- MaxMind DB 파일 없음 → GeoAnomalyRule 비활성화 (다른 규칙은 정상 동작)
- account-service 장애 → 3회 재시도 → pending → 운영자 수동 처리
- Redis 장애 → velocity/geo/device 캐시 miss → 탐지 "판단 보류" (false positive 방지)

---

# Test Requirements

- Unit: 각 Rule 개별 테스트 (경계값, 임계치 직전/직후)
- Integration: Testcontainers (Kafka + MySQL + Redis) + WireMock (account-service) — 이벤트 소비 → 탐지 → auto-lock → DB 기록 E2E
- Config: 임계치 변경 → 동작 변화 확인

---

# Definition of Done

- [ ] 4개 규칙 구현 + 테스트
- [ ] Auto-lock 파이프라인 E2E 확인
- [ ] Contracts match
- [ ] Ready for review
