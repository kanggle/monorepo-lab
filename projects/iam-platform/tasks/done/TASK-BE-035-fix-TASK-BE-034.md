# Task ID

TASK-BE-035

# Title

Fix TASK-BE-034 — data-model.md `refresh_tokens` schema drift from device-session.md D5; stale IP masking in sibling contracts

# Status

ready

# Owner

backend

# Task Tags

- spec
- fix

# depends_on

- TASK-BE-034

---

# Goal

TASK-BE-034가 `device_sessions` 분류는 추가했지만 `refresh_tokens` 테이블 자체의 D5 변경(`device_id` 추가, `device_fingerprint` deprecated 표시)을 `data-model.md`에 반영하지 않았다. `data-model.md`는 canonical schema 선언이므로 여기가 device-session.md와 불일치하면 TASK-BE-023 구현이 막힌다. 그리고 Fix 2에서 `auth-api.md`·`auth-events.md`는 정리했지만 동일 포맷을 공유하는 sibling contract(`security-query-api.md`, `admin-api.md`) 및 `gateway-service/observability.md`는 여전히 구 포맷을 사용한다.

---

# Scope

## In Scope

### Critical — `data-model.md` refresh_tokens D5 반영
- `specs/services/auth-service/data-model.md`의 `refresh_tokens` 테이블 정의에:
  - `device_id VARCHAR(36) NULL, INDEX` 컬럼 추가 (device-session.md D5 일치)
  - 기존 `device_fingerprint` 컬럼을 `deprecated` 표시 (주석 또는 비고 열)
- Data Classification Summary에 `refresh_tokens.device_id`를 **internal**로 명시 추가 (catch-all에 의존하지 않음)

### Warning — IP 마스킹 포맷 sibling contract 정렬
- `specs/contracts/http/security-query-api.md`, `specs/contracts/http/admin-api.md`의 `ipMasked` 예시 값을 `"192.168.*.*"`로 교체 (기존 `"192.168.1.***"` 제거)
- `specs/services/gateway-service/observability.md`의 IP 마스킹 설명("마지막 옥텟 `***`")을 두 옥텟 규칙으로 수정, `specs/services/auth-service/device-session.md`의 IP Masking Format 섹션을 canonical 참조로 표기
- Grep 검증: 저장소 전체에서 `192.168.1.\*\*\*` 문자열이 남아있지 않아야 함

## Out of Scope

- `session-events.md`의 `TOKEN_REUSE_DETECTED` vs `TOKEN_REUSE` 네이밍 정렬 (별도 cross-contract 정리 태스크)
- device-session.md의 reciprocal 분류 참조 노트 (Suggestion 레벨, 별도)

---

# Acceptance Criteria

- [ ] `data-model.md`의 `refresh_tokens` 테이블에 `device_id VARCHAR(36) NULL, INDEX` 컬럼 존재
- [ ] `device_fingerprint` deprecated 표시
- [ ] Data Classification Summary에 `refresh_tokens.device_id` = internal 명시
- [ ] 저장소 전체 `rg '192\.168\.1\.\*\*\*'` 결과 0건
- [ ] `gateway-service/observability.md`가 두 옥텟 규칙 또는 device-session.md 참조로 통일

---

# Related Specs

- `specs/services/auth-service/data-model.md`
- `specs/services/auth-service/device-session.md`
- `specs/services/gateway-service/observability.md`

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/security-query-api.md`
- `specs/contracts/http/admin-api.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/auth-service` (중심), sibling 계약은 읽기 전용 정렬

---

# Edge Cases

- `refresh_tokens.device_id`가 NULL 가능 (기존 row 백필 전 상태) — NULL 허용 명시
- 저장소 전체 grep 시 docs/ 및 README에도 `192.168.1.***`가 존재할 수 있음 — PROJECT.md Source of Truth 우선순위상 docs/는 out of scope이나 발견 시 보고

---

# Failure Scenarios

- `data-model.md`·`device-session.md` 간 지속적 drift — canonical 선언 위치를 `data-model.md`로 단일화하고 `device-session.md`는 "재선언 없이 참조" 원칙 기재

---

# Test Requirements

- 스펙 리뷰만

---

# Definition of Done

- [ ] 두 섹션(Critical, Warning) 모두 반영
- [ ] grep 검증 통과
- [ ] TASK-BE-023 unblock 확인
