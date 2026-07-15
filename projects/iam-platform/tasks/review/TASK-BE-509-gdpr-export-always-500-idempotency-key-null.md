# Task ID

TASK-BE-509

# Title

GDPR 데이터 이식(export)이 항상 500 — 감사 행 `idempotency_key` NOT NULL 위반 (fail-closed)

# Status

review

# Owner

backend

# Task Tags

- code
- compliance

---

# 🔴 발견 경위 (2026-07-15 라이브 풀스택 스윕)

운영자 세션으로 `GET /api/admin/accounts/{id}/export` 를 실제 호출했더니 **Idempotency-Key 헤더 유무와 무관하게 100% `500 AUDIT_FAILURE`**. GDPR/PIPA 데이터 이식권(right to portability) 기능이 완전 불능이다. 아래는 실측 스냅샷 — **착수 시 재현부터.**

---

# Goal

`GET /api/admin/accounts/{accountId}/export`(운영자 GDPR 데이터 이식)가 정상적으로 계정 데이터를 반환하도록 고친다.

## Root Cause (실측, 재검증 대상)

`apps/admin-service/src/main/java/com/example/admin/application/GdprAdminUseCase.java` 의 성공 경로 `auditor.record(new AdminActionAuditor.AuditRecord(auditId, ActionCode.DATA_EXPORT, operator, "ACCOUNT", accountId, reason, null, null, Outcome.SUCCESS, ...))` 가 **`idempotencyKey` 인자에 하드코딩 `null`** 을 넘긴다(현재 L92~97 부근). 그런데 `admin_actions.idempotency_key` 는 **NOT NULL** 컬럼이라 insert 가 `DataIntegrityViolationException: Column 'idempotency_key' cannot be null` 으로 실패한다.

감사 정책은 **fail-closed**(`audit-trail.md` — 감사 쓰기 실패 시 명령 전체 abort)이므로, 감사 실패가 그대로 `500 AUDIT_FAILURE` 로 사용자에게 나간다.

- **Idempotency-Key 헤더를 줘도 소용없음** — export 는 GET 이고, 코드가 헤더가 아니라 리터럴 `null` 을 쓴다.
- 같은 클래스의 **실패-감사 경로** `recordSingleShotAuditFailure`(현재 L134 부근)도 동일하게 `null` 을 넘긴다 → 다운스트림 오류 시에도 진짜 원인 대신 감사 실패 500 이 나감(2차 은폐).

## 대비: 왜 lock/unlock 은 되는가

lock/unlock/bulk-lock 등 변이 명령은 클라이언트가 `Idempotency-Key` 헤더를 주고 그 값이 감사 행에 실려 통과한다. export/gdpr-export 같은 **읽기성 감사** 경로만 키 없이 기록하려다 NOT NULL 에 걸린다.

# Scope

## In Scope
- `GdprAdminUseCase` 의 export 성공/실패 감사 기록이 `idempotency_key` 제약을 만족하도록 수정. **설계 결정 필요** (택1, 착수 시 아키텍처 판단):
  - (A) 비-멱등 읽기성 액션(`DATA_EXPORT` 등)에 대해 `auditId` 파생 키(예: `auditId` 자체)를 `idempotency_key` 로 채운다 — 최소 변경, 스키마 불변.
  - (B) `admin_actions.idempotency_key` 를 **nullable** 로 완화(마이그레이션) — 읽기성 감사는 멱등키가 의미 없다는 관점. 단 기존 유니크/멱등 제약과의 상호작용을 확인.
- 동일 결함을 가진 다른 GET-감사 경로가 있는지 **전수 확인**(`AdminGdprController.export`, 그리고 감사를 남기는 다른 read 엔드포인트). 있으면 함께.

## Out of Scope
- GDPR **삭제**(gdpr-delete) 경로 — 스윕에서 정상(200) 확인됨. 건드리지 말 것.
- fail-closed 감사 정책 자체(설계대로 — 감사 실패 시 명령 abort 는 유지).

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: 운영자 토큰으로 `GET /api/admin/accounts/{id}/export` 가 **현재 500 AUDIT_FAILURE** 임을 재현. DB 에서 `admin_actions` 스키마의 `idempotency_key` nullability 를 직접 확인.
- [ ] export 가 **200** + 계정/프로필 JSON(accountId, email, status, createdAt, profile...) 반환.
- [ ] export 호출이 `admin_actions` 에 `action_code=DATA_EXPORT, outcome=SUCCESS` 감사 행을 **성공적으로 기록**(감사 추적 무결성 유지 — export 는 audit-trail.md 상 meta-audit 대상).
- [ ] 다운스트림 실패 주입 시 `recordSingleShotAuditFailure` 경로도 감사 행을 남기고 **원래 실패 원인**을 반환(감사 실패로 은폐되지 않음).
- [ ] admin-service IT 에 **export 200 + 감사 행 존재** 검증 추가(현재 이 경로를 태우는 통합 테스트가 없어 결함이 초록으로 새어나갔음 — 반드시 추가).
- [ ] `:check` GREEN.

# Related Specs

> Before reading: `platform/entrypoint.md` Step 0.

- `specs/features/data-rights.md` (Right to Data Portability)
- `specs/features/audit-trail.md` (fail-closed 감사, meta-audit)
- `specs/services/admin-service/architecture.md`

# Related Contracts

- `specs/contracts/http/admin-api.md` (`GET /api/admin/accounts/{accountId}/export`)
- `specs/contracts/http/internal/admin-to-account.md` (`GET /internal/accounts/{accountId}/export`)

# Target Service

- `admin-service`

# Edge Cases

- 옵션 (B) 선택 시: `idempotency_key` 를 nullable 로 바꾸면 기존 멱등 재요청 로직(변이 명령의 중복 방지)이 깨지지 않는지 — 변이 경로는 여전히 non-null 을 요구해야 한다.
- 옵션 (A) 선택 시: `auditId` 를 키로 쓰면 유니크 제약과 충돌하지 않는지(auditId 는 이미 고유).
- export 는 GET 이라 브라우저/프록시 재시도 가능 — 감사 행이 요청마다 중복 생성될 수 있음(멱등키 의미 재확인).

# Failure Scenarios

- 성공 경로만 고치고 `recordSingleShotAuditFailure` 를 놓치면, 다운스트림 오류 시 여전히 500 AUDIT_FAILURE 로 원인 은폐.
- 스키마를 nullable 로 바꾸면서 변이 명령의 멱등 제약을 함께 완화하면 중복 lock/unlock 방어가 약화됨.

# Definition of Done

- [ ] AC-0 재측정으로 결함·스키마 확인
- [ ] export 200 + 감사 행 정상 기록
- [ ] 실패-감사 경로 동반 수정
- [ ] export IT 추가, `:check` GREEN
- [ ] Ready for review
