# admin-service — Data Retention Policy

## Purpose

admin-service가 저장하는 데이터의 **보존 기간**과 **파기/익명화 경로**를 선언한다. [rules/traits/regulated.md](../../../rules/traits/regulated.md) R6 (보존 기간 명시)과 R8 (데이터 이식성), [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3 (감사 불변성) · A4 (감사 장기 보존) 준수를 위한 canonical 문서. 테이블 DDL은 [data-model.md](./data-model.md), 행동 규칙은 [rbac.md](./rbac.md) 참조.

구현(스케줄러·파기 잡)은 본 태스크 범위 외이며 TASK-BE-028 이후 스프린트에서 수행한다. 본 문서는 **정책 선언**만 담는다.

---

## Summary

| 테이블 | 보존 기간 | 파기/익명화 경로 | 근거 |
|---|---|---|---|
| `admin_operators` | `status=DISABLED` 전환 후 **365일** 유지 → 마스킹 → archive | 1단계 즉시 제로화 + 2단계 365일 후 PII 익명화 + 3단계 archive | R6, R7, PIPA 파기 원칙 |
| `admin_actions` | **영구 보관** (최소 5년 강제, 이후 cold storage 이관 가능성 open) | UPDATE/DELETE 금지. 정정은 correction row append | A3, A4, regulated R5 |
| `admin_roles` / `admin_role_permissions` / `admin_operator_roles` | 참조 무결성 유지 기간 동안 | 연결된 operator가 모두 archive된 후에만 삭제 가능 (일반 경로 아님) | 참조 무결성 + A3 (감사 역추적) |
| `outbox` | 기존 공통 정책에 위임 ([libs/java-messaging](../../../libs/java-messaging)) | 본 서비스에서 재정의 없음 | — |

---

## `admin_operators`

### Retention

- 활성(`status=ACTIVE`) 동안: 무기한 보유
- 비활성(`status=DISABLED`) 전환 시점부터 **365일** 유지 → 마스킹(아래 2단계) → archive(3단계)

### Destruction / Anonymization Path

1. **즉시 제로화 (status=DISABLED 전환 트랜잭션 내, T+0)**
   - `password_hash` → NULL (로그인 경로 차단 + R2 시크릿 평문/해시 잔존 최소화)
   - `totp_secret_encrypted` → NULL (R2)
   - 관련 operator session/refresh token은 auth-service 내부 호출로 revoke
2. **PII 익명화 (T+365일)**
   - `email` → SHA256 tokenize (`anon:<hash16>`) — 감사 로그의 operator FK 역추적을 위해 row 자체는 유지
   - `display_name` → `"(removed)"` 상수
   - `last_login_at` → NULL
3. **Archive (T+365일 이후)**
   - row를 `admin_operators_archive`(cold, append-only) 테이블로 이관. 원본 row는 삭제 대신 `status=ARCHIVED`로 마킹 + PII 컬럼 비운 상태 유지 (admin_actions.operator_id FK 보호)
   - archive 테이블은 감사 조회에서 `display_name="(archived operator)"`로 표기

### 근거

- regulated R6 (보존 기간 명시), R7 (삭제 경로는 유예+익명화), R1 (PII 분류)
- PIPA 제21조 파기 원칙: 목적 달성 시 지체 없이 파기. 365일은 법적 분쟁 대응 기간으로 근거화.
- audit-heavy A3 (감사 불변): operator PK 자체는 소멸시키지 않고 PII만 익명화하여 admin_actions 역추적을 보존

---

## `admin_actions`

### Retention

- **영구 보관**이 원칙. 최소 **5년**은 online storage에 유지 (audit-heavy A4, regulated R5 — restricted 데이터 접근 감사 증거)
- 5년 경과 이후: cold storage(S3 Object Lock 등 write-once) 이관 가능성 open. 본 스코프에서는 **이관/삭제 없음** — 정책은 선언만, 스케줄러는 구현하지 않음.

### Destruction / Anonymization Path

- **UPDATE/DELETE 금지** (audit-heavy A3). DB 레벨 권한 제거 또는 트리거로 차단 ([data-model.md](./data-model.md) `admin_actions` Immutability).
- 수정이 필요한 경우 **correction row**를 새 INSERT로 append. 원본 row는 유지.
- operator가 archive되어 `admin_operators` PII가 익명화되어도 `admin_actions.operator_id` FK는 유지 — archive row의 anonymized email/display_name이 감사 응답에 표시된다.

### 근거

- audit-heavy A3 (불변성), A4 (보존 기간은 일반 로그보다 길다. 최소 1년, 규제 5~7년)
- regulated R5 (restricted 데이터 접근은 감사)

---

## `admin_roles` / `admin_role_permissions` / `admin_operator_roles`

### Retention

- 참조 무결성이 유지되어야 하는 한 보존. 일반적으로 삭제 경로가 존재하지 않는다.
- role 이름 변경 금지 ([rbac.md](./rbac.md) Seeding Strategy). role의 **논리적 폐기**는 새 role을 도입하고 모든 operator 바인딩을 제거하는 방식.

### Destruction Path

- `admin_operator_roles`: operator가 archive된 후 **CASCADE**로 정리 (FK `ON DELETE CASCADE`)
- `admin_role_permissions`: role이 폐기되고 어떤 operator도 해당 role을 보유하지 않을 때만 삭제 가능
- `admin_roles`: 위 두 조건 동시 성립 시에만 삭제. 삭제는 별도 Flyway migration을 통해서만 수행 (운영 런타임 경로에 삭제 API 없음)

### 근거

- audit-heavy A3 (감사 이벤트의 `action.permission` 역추적을 위해 role/permission 이름은 가능한 한 보존)
- 참조 무결성

---

## `outbox`

- admin-service는 자체 outbox 보존 정책을 **재정의하지 않는다**.
- 보존·cleanup은 [libs/java-messaging](../../../libs/java-messaging)의 공통 정책에 위임 (published row는 공통 정책 TTL에 따라 정리).
- payload에는 마스킹된 `displayHint`만 포함되며 원문 PII 없음 — 따라서 outbox row의 분류는 `internal` ([data-model.md](./data-model.md) Data Classification Summary 참조, R4 교차).

---

## Out of Scope

- 실제 파기 스케줄러 구현
- cold storage 이관 파이프라인
- 사용자(operator) 데이터 이식성(R8) 엔드포인트 — 현 시점 operator는 내부 운영자이므로 R8은 외부 사용자 대상일 때 활성화 (account-service 소유). admin-service에서는 설계만 인지.
