# Task ID

TASK-BE-037

# Title

Fix TASK-BE-027 — retention.md 신규, admin_actions outbox A2 스키마, 잔여 정리

# Status

ready

# Owner

backend

# Task Tags

- spec
- fix
- adr

# depends_on

- TASK-BE-027

---

# Goal

TASK-BE-027 리뷰에서 발견된 Critical 2건과 구조적 Warning 4건을 수정해 TASK-BE-028 구현이 막히지 않도록 한다. regulated R6와 audit-heavy A2/A4 규정 준수.

---

# Scope

## In Scope

### Critical-1 — admin-service retention.md 신규
- `specs/services/admin-service/retention.md` 생성
- 최소 포함:
  - `admin_operators`: 비활성화(`status=DISABLED`) 이후 365일 유지 → 이메일/display_name 마스킹 → 이후 archive. `password_hash`, `totp_secret_encrypted`는 비활성화 즉시 제로화. 근거: regulated R6, PIPA 파기 원칙.
  - `admin_actions`: 감사 불변 원칙(audit-heavy A3)에 따라 영구 보관. 단 5년 경과 후 cold storage 이관 가능성 명시 (현 시점 스코프에서는 삭제·이관 없음).
  - `admin_roles`, `admin_role_permissions`, `admin_operator_roles`: 참조 무결성 위해 연결된 operator가 모두 archive된 이후에만 삭제 가능 (일반적 scenario 아님).
  - Outbox: 현재 outbox 처리 정책과 일치(성공 후 X일 경과 row 삭제 — 기존 정책 참조만, 재정의 없음).
- regulated.md R6/R8, audit-heavy.md A3/A4 항목 번호를 문서 내에 각주로 인용

### Critical-2 — admin_actions 및 outbox payload에 A2 호환 audit envelope 스펙
- `specs/services/admin-service/rbac.md` 또는 `data-model.md`에 `admin.action.performed` outbox payload JSON 스키마 추가:
  ```
  {
    "eventId": uuid,
    "occurredAt": iso8601,
    "actor": { "type": "operator", "id": operator_id, "sessionId": session_id_or_jti },
    "action": { "permission": string, "endpoint": string, "method": string },
    "target": { "type": "account|operator|role", "id": string, "displayHint": masked_string_or_null },
    "outcome": "SUCCESS | DENIED | FAILURE",
    "reason": string_or_null
  }
  ```
- A2(표준 audit envelope)와 실제 DB 컬럼(`admin_actions.operator_id`/`outcome`/`reason` 등)의 매핑 표 명시
- `target.displayHint`는 R4 마스킹 후 값만 포함(email/phone 원문 불허)

### Warning-1 — `GET /api/admin/audit` 교차 권한 알고리즘 pseudocode
- `rbac.md`의 Permission Evaluation Algorithm 섹션에 해당 엔드포인트의 조건부 교집합 로직을 pseudocode로 추가. `source` 파라미터별 가드 분기와, 거부 시 `permission_used` 값 규칙("audit.read" vs "audit.read+security.event.read") 명시.

### Warning-2 — admin-api.md의 Operator Roles 요약 테이블 중복 제거
- 현재 admin-api.md에 복제된 per-role permission 테이블을 삭제하고 `rbac.md#seed-matrix` 링크로 대체

### Warning-3 — `operator_external_id` 혼동 제거
- `data-model.md`의 "기존 `operator_external_id` 문자열이 있었다면 deprecate" 문장을 실제 컬럼 존재 여부에 맞게 수정 또는 삭제 (현재 admin-service bootstrap 스키마를 확인 후 결정)

### Warning-4 — outbox.payload 분류 기술
- `data-model.md` Data Classification Summary에 `outbox.payload`가 마스킹된 confidential 필드 포함 가능성이 있음을 명시. `displayHint`는 마스킹된 값만 포함한다는 R4 제약을 교차 인용.

## Out of Scope

- Suggestion 3건 (Redis 캐시 TTL 상한 선언, 테스트 수 카운트 보정, operator/role 관리 엔드포인트) — 필요 시 TASK-BE-028 범위에서 흡수하거나 별도 스프린트
- 실제 마이그레이션/구현

---

# Acceptance Criteria

- [ ] `specs/services/admin-service/retention.md` 존재, 4개 테이블 대상 보관 정책 명시
- [ ] `admin.action.performed` outbox payload JSON 스키마가 rbac.md 또는 data-model.md에 존재
- [ ] A2 표준 envelope 필드(actor.type, actor.id, actor.sessionId 포함)와 DB 컬럼 매핑 문서화
- [ ] `rbac.md` audit 교차 권한 엔드포인트 pseudocode 추가
- [ ] admin-api.md Operator Roles 중복 테이블 제거, rbac.md 링크만 유지
- [ ] `operator_external_id` 언급 정정
- [ ] `outbox.payload` 분류 기술 추가

---

# Related Specs

- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/data-model.md`
- `specs/services/admin-service/architecture.md`
- `rules/traits/regulated.md` (R1/R4/R6/R8)
- `rules/traits/audit-heavy.md` (A1/A2/A3/A4)

# Related Contracts

- `specs/contracts/http/admin-api.md`
- `specs/contracts/events/admin-events.md` (있다면 확인; 없으면 payload는 data-model.md에 귀속)

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- `admin_actions`의 session_id 후보: operator JWT의 jti 또는 로그인 세션 추적 ID 중 하나를 택 — rbac.md에서 명시된 것을 따름

# Failure Scenarios

- `displayHint`를 마스킹 없이 로깅하는 구현 실수 — 스펙에서 마스킹 함수 인용 경로를 명시해 구현자 오해 차단

---

# Test Requirements

- 스펙 리뷰만

---

# Definition of Done

- [ ] 두 Critical 모두 반영
- [ ] 네 Warning 반영
- [ ] TASK-BE-028 구현 unblock
