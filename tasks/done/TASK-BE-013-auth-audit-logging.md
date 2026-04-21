# Task ID

TASK-BE-013

# Title

auth-service 감사 로그 구현 — 로그인 시도, 토큰 갱신, 로그아웃 이력 기록

# Status

review

# Owner

backend

# Task Tags

- code
- api

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

인증 관련 주요 활동(로그인 성공/실패, 토큰 갱신, 로그아웃)에 대한 감사 로그를 DB에 기록한다.
보안 모니터링, 이상 접근 탐지, 컴플라이언스 대응을 위한 기반 데이터를 확보한다.

이 태스크 완료 후: 모든 인증 이벤트가 `auth_audit_log` 테이블에 기록되고, IP/User-Agent 정보가 포함된다.

---

# Scope

## In Scope

- `auth_audit_log` 테이블 생성 (Flyway 마이그레이션)
- `AuditLog` 도메인 엔티티 및 `AuditLogRepository` 인터페이스 생성
- `AuditLogService` 애플리케이션 서비스 생성
- `LoginService`, `LogoutService`, `RefreshTokenService` 에서 감사 로그 기록 호출
- 로그인 실패 시에도 기록 (IP, 시도한 이메일)
- `SignupService` 회원가입 성공 시 기록

## Out of Scope

- 감사 로그 조회 API (별도 태스크)
- 감사 로그 보관/삭제 정책 (운영 태스크)
- 외부 로그 수집 시스템 연동
- Rate limit 초과 이벤트 기록 (필터 레벨이라 별도 판단 필요)

---

# Acceptance Criteria

- [ ] `auth_audit_log` 테이블이 Flyway 마이그레이션으로 생성된다
- [ ] 테이블 컬럼: `id (UUID PK)`, `user_id (UUID, nullable)`, `email`, `event_type`, `ip_address`, `user_agent`, `result (SUCCESS/FAILURE)`, `failure_reason (nullable)`, `created_at (TIMESTAMPTZ)`
- [ ] 로그인 성공 시 `LOGIN_SUCCESS` 이벤트가 기록된다
- [ ] 로그인 실패 시 `LOGIN_FAILURE` 이벤트가 기록된다 (user_id는 null 가능)
- [ ] 토큰 갱신 시 `TOKEN_REFRESH` 이벤트가 기록된다
- [ ] 로그아웃 시 `LOGOUT` 이벤트가 기록된다
- [ ] 회원가입 성공 시 `SIGNUP` 이벤트가 기록된다
- [ ] IP 주소는 `X-Forwarded-For` 헤더를 우선 사용한다
- [ ] 감사 로그 기록 실패가 인증 플로우를 차단하지 않는다 (비동기 또는 try-catch)
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/platform/testing-strategy.md`
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md`

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

계층 배치:
- Domain: `AuditLog` 엔티티, `AuditLogRepository` 인터페이스
- Application: `AuditLogService` (이벤트 타입별 기록 메서드)
- Infrastructure: `AuditLogJpaRepository` (Spring Data JPA)
- Presentation: 변경 없음 (IP/User-Agent는 서비스 레이어에서 수신)

---

# Implementation Notes

### 감사 로그 스키마

```sql
CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY,
    user_id UUID,
    email VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    result VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_log_user_id ON auth_audit_log (user_id);
CREATE INDEX idx_audit_log_event_type ON auth_audit_log (event_type);
CREATE INDEX idx_audit_log_created_at ON auth_audit_log (created_at);
```

### 비차단 기록 전략

감사 로그 기록 실패가 인증 플로우를 방해해서는 안 된다. `@Async` 또는 try-catch로 감사 로그 저장 실패를 격리한다.

### IP 리졸빙

`X-Forwarded-For` 헤더의 첫 번째 IP를 사용하고, 없으면 `HttpServletRequest.getRemoteAddr()`를 사용한다.

---

# Edge Cases

- 로그인 실패 시 user_id가 없을 수 있음 (존재하지 않는 이메일) → user_id nullable
- X-Forwarded-For 헤더에 복수 IP가 있는 경우 → 마지막 IP 사용 (리버스 프록시가 추가한 신뢰할 수 있는 IP; 첫 번째 항목은 클라이언트가 위조 가능하여 레이트리밋 우회에 악용될 수 있음)
- User-Agent가 매우 긴 경우 → VARCHAR(500) 초과 시 truncate
- 동시 다발 로그인 시도 시 감사 로그 insert 성능 → 인덱스 최적화

---

# Failure Scenarios

- DB 커넥션 부족으로 감사 로그 저장 실패 → 인증 플로우는 정상 진행, 에러 로깅
- 트랜잭션 내에서 감사 로그 저장 시 인증 트랜잭션 롤백 위험 → 별도 트랜잭션 또는 비동기 처리
- Flyway 마이그레이션 실패 → 서비스 시작 차단 (Flyway 기본 동작)

---

# Test Requirements

- 단위 테스트: `AuditLogServiceTest` — 각 이벤트 타입별 기록 호출 검증
- 단위 테스트: `AuditLogTest` — 엔티티 생성 및 유효성 검증
- 통합 테스트: 로그인/로그아웃/갱신 후 `auth_audit_log` 테이블에 레코드 존재 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
