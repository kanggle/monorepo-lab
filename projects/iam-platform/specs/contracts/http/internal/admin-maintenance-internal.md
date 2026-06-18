# Internal HTTP Contract: admin-service maintenance endpoints

admin-service 내부 유지보수(one-time backfill 등) 엔드포인트. 운영자 명령 surface(`/api/admin/**`)가 아니라 기계 호출용 `/internal/admin/**` 이다.

**호출 방향**: 운영/배포 도구 (client) → admin-service (server)
**노출 경로**: `/internal/admin/*`
**인증**: `@Order(0)` `/internal/**` resource-server chain — GAP `client_credentials` Bearer JWT (fail-closed). dev/test/standalone 프로파일에서는 `InternalApiFilter` bypass. 공개 게이트웨이로 노출되지 않음(S2).

---

## POST /internal/admin/operator-oidc-subject-backfill

**TASK-MONO-298 (ADR-MONO-040 Phase 3 part A)** — `admin_operators.oidc_subject` 를 운영자 로그인 **email**(Phase-2 시드 값)에서 **account_id**(Phase-3 end-state 키)로 마이그레이션하는 **idempotent** backfill. email→account_id 매핑은 `auth_db.credentials`(admin_db 와 물리적으로 분리된 DB)에 있으므로 Flyway 단계가 아니다 — 각 account_id 는 실행 시 auth-service 내부 엔드포인트(`POST /internal/auth/credentials/account-id-by-email`)로 해석한다.

**Request**: 본문 없음.

**Response 200**:
```json
{
  "scanned": 5,
  "updated": 4,
  "skippedAlreadyUuid": 0,
  "skippedNull": 0,
  "unresolved": 1
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `scanned` | int | `oidc_subject` 가 non-null 인(provisioned) 운영자 행 수 |
| `updated` | int | email-shaped → account_id 로 마이그레이션된 행 수 |
| `skippedAlreadyUuid` | int | 이미 마이그레이션된(UUID-shaped) 행 수 — 재실행 시 no-op |
| `skippedNull` | int | blank `oidc_subject` 행 수(방어적; finder 가 true null 은 제외) |
| `unresolved` | int | auth-service 가 account_id 를 해석하지 못한 email-shaped 행 수(변경 없이 유지, fail-soft) |

**동작**:

- **email-shape 판정**: non-null + `@` 포함 + UUID-parse 불가 → email-shaped(처리 대상). UUID-parse 가능 → 이미 마이그레이션됨(skip). null → skip.
- **tenant scoping (CRITICAL)**: 각 운영자의 `tenant_id` 를 auth-service 조회에 전달한다(`credentials.email` 은 tenant 별 unique). 잘못된 account_id 는 운영자를 mis-authorize 한다.
- **fail-soft**: account_id 미해석(credential 부재 / email 모호 / auth-service 장애) → `oidc_subject` 변경 없이 `unresolved` 로 카운트. RETAINED email fallback 으로 계속 해석 가능(어떤 운영자도 회귀하지 않음). 한 건 실패가 배치 전체를 중단시키지 않는다.
- **idempotent**: email-shaped 행만 처리하므로 부분 backfill 후 재실행은 already-UUID 행에 대해 no-op.

**Side Effects**:

- email-shaped 운영자 행의 `admin_operators.oidc_subject` = 해석된 account_id, `updated_at` bump.
- 각 update 는 PII-안전 audit 로그(operator_id + tenant + `email-shaped → account_id` 의 **key-shape 전이**만; email **값**은 절대 로깅하지 않음 — `confidential`). admin-service `admin_actions` 감사 서브시스템은 운영자-컨텍스트·ActionCode 바인딩(운영자 개시 명령용)이므로, 운영자 컨텍스트가 없는 이 기계 호출 배치는 구조화 애플리케이션 로그로 감사한다(ADR-014 token-exchange "admin_actions 행 아님" 선례와 일치).

> **Phase 3 part B (MONO-299, gated)** — 이 backfill 이 merge·검증된 후 email fallback / transitional `account_id` claim / 4개 게이트웨이의 legacy-email fallback 을 제거한다. 이 task(part A)에서는 dual-key fallback 을 **유지**한다.
