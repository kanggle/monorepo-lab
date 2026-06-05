# Task ID

TASK-BE-054

# Title

GDPR/PIPA 데이터 권리 — 삭제권(PII 마스킹) + 이식권(데이터 내보내기) 구현

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Goal

GDPR/PIPA 컴플라이언스를 위한 두 가지 핵심 데이터 권리를 구현한다:
1. **Right to Erasure**: 운영자가 계정의 PII를 즉시 마스킹(이메일 해시 교체, 프로필 NULL 처리)
2. **Right to Data Portability**: 운영자가 계정의 개인 데이터를 JSON으로 내보내기

---

# Scope

## In Scope

- account-service: Flyway migration (V0006), GdprDeleteUseCase, DataExportUseCase, 내부 HTTP 엔드포인트 2개
- admin-service: AdminGdprController (public API 2개), AccountServiceClient 확장, GdprDeleteUseCase/DataExportUseCase, 감사 기록
- API 컨트랙트 업데이트 (admin-api.md, admin-to-account.md)

## Out of Scope

- 자동 데이터 보존 기간 만료 배치
- auth-service 로그인 이력 연동
- 사용자 본인의 self-service 삭제/내보내기 (현재는 운영자 전용)
- 삭제 유예 기간 (GDPR 즉시 마스킹)

---

# Acceptance Criteria

1. `POST /api/admin/accounts/{accountId}/gdpr-delete` 호출 시 계정 상태가 DELETED로 전이되고, 이메일이 SHA-256 해시로 교체되며, 프로필 PII가 NULL 처리된다
2. `GET /api/admin/accounts/{accountId}/export` 호출 시 계정+프로필 데이터가 JSON으로 반환된다
3. GDPR 삭제 시 account.deleted 이벤트가 anonymized=true로 발행된다
4. 두 엔드포인트 모두 admin_actions 감사 기록이 남는다
5. GDPR 삭제 후 email_hash 컬럼에 원본 이메일의 SHA-256 해시가 저장된다
6. 이미 DELETED 상태인 계정에 대한 GDPR 삭제 요청은 400 STATE_TRANSITION_INVALID를 반환한다
7. `./gradlew :apps:account-service:compileJava :apps:admin-service:compileJava` 성공

---

# Related Specs

- specs/features/data-rights.md
- specs/services/account-service/architecture.md
- specs/services/admin-service/architecture.md

# Related Contracts

- specs/contracts/http/admin-api.md
- specs/contracts/http/internal/admin-to-account.md

---

# Edge Cases

- 이미 DELETED 상태인 계정에 GDPR 삭제 요청 -> STATE_TRANSITION_INVALID
- 존재하지 않는 계정에 대한 요청 -> ACCOUNT_NOT_FOUND
- 프로필이 없는 계정에 대한 GDPR 삭제 -> 계정만 처리, 프로필 마스킹 스킵
- 프로필이 없는 계정에 대한 내보내기 -> profile 필드가 null인 응답 반환

# Failure Scenarios

- account-service 다운스트림 호출 실패 -> admin_actions에 FAILURE 기록 + 503 DOWNSTREAM_ERROR
- account-service circuit breaker OPEN -> admin_actions에 FAILURE 기록 + 503 CIRCUIT_OPEN
- 감사 기록 실패 -> 500 AUDIT_FAILURE (fail-closed)
