# Event Contract: tenant lifecycle (admin-service)

admin-service 가 발행하는 테넌트 lifecycle 이벤트. account-service 가 SUSPEND 시 신규 로그인·가입 게이트를 잠그고, 외부 SIEM·관측성 시스템이 운영 신호로 소비한다.

**발행 방식**: Outbox 패턴 (T3). admin-service 가 `admin_actions` 행 INSERT 와 같은 DB 트랜잭션에서 outbox row 작성 → relay 가 Kafka 발행.
**파티션 키**: `tenantId` (같은 테넌트의 lifecycle 이벤트 순서 보장)
**Source**: `admin-service`
**도입 태스크**: TASK-BE-256 (계약 정의), TASK-BE-250 (실제 발행 구현)

---

## Event Envelope

[auth-events.md](auth-events.md) 와 동일한 표준 envelope (`eventId`, `eventType`, `source`, `occurredAt`, `schemaVersion`, `partitionKey`, `payload`).

---

## tenant.created

신규 테넌트가 등록될 때 발행. `POST /api/admin/tenants` 의 audit row INSERT 와 같은 트랜잭션에서 outbox 작성.

**Topic**: `tenant.created`

**Schema version**: 1

**Payload**:
```json
{
  "tenantId": "wms",
  "displayName": "Warehouse Management System",
  "tenantType": "B2B_ENTERPRISE",
  "status": "ACTIVE",
  "actor": {
    "type": "operator",
    "id": "string (operator_id, SUPER_ADMIN)"
  },
  "createdAt": "2026-05-02T09:00:00Z"
}
```

**필드 노트**:
- `tenantId`: slug, 정규식 `^[a-z][a-z0-9-]{1,31}$`. 한 번 발급되면 변경 불가.
- `tenantType`: `B2C_CONSUMER` | `B2B_ENTERPRISE`. 격리 정책·기본 역할셋 결정에 사용.
- `status` (**TASK-MONO-773**): `POST /api/admin/tenants` 로 만든 테넌트는 **`PENDING`**(첫 관리자 대기 — `ACTIVE` 는 첫 관리자 초대 수락 때 아래 `tenant.activated`). 값이 하나 늘었을 뿐 칸은 그대로다 — `schemaVersion` 1 유지(아래 Consumer Rules «schema tolerance» · additive). 🔴 이 값을 `ACTIVE` 로 가정하는 소비자는 없다(서비스 소비자 0 — 아래 Consumers).
- `actor.type` 은 항상 `operator`. 🔵 셀프 온보딩(ADR-MONO-044 · [onboarding-api.md](../http/onboarding-api.md))으로 만든 테넌트는 이 이벤트를 내지 않는다(관리 행위자 · 감사 행이 없는 경로 — 773 AC-0 § 1, 773 이 바꾸지 않는다). ~~테넌트는 self-service 등록 불가~~ — ADR-044 이후 낡은 문장.

**Consumers**:
- account-service: 내부 tenant 캐시 갱신 (선택 — account-service 가 source of truth 이므로 일반적으로 무시 가능)
- 외부 SIEM: 신규 테넌트 등록 알림
- 관측성 시스템: 테넌트 등록 카운터 (`tenant.created.count` 메트릭)

---

## tenant.suspended

테넌트가 ACTIVE → SUSPENDED 로 전이될 때 발행. **TASK-MONO-773**: `PENDING → SUSPENDED`(첫 관리자 대기 테넌트의 «정리» — admin-api § PATCH)도 이 이벤트다 — `previousStatus = "PENDING"`, `reason` = 운영자 사유. 생성 보상(`POST /api/admin/tenants` 의 초대 쓰기 실패 → 정지)은 감사 트랜잭션이 롤백된 경로라 이 이벤트를 **내지 않는다**(outbox 는 감사 행과 같은 트랜잭션에서만 쓴다). account-service 가 이 이벤트를 받아 해당 테넌트의 신규 로그인·가입을 차단한다.

**Topic**: `tenant.suspended`

**Schema version**: 1

**Payload**:
```json
{
  "tenantId": "wms",
  "previousStatus": "ACTIVE",
  "currentStatus": "SUSPENDED",
  "reason": "string (운영자 사유, max 500자, PII 금지)",
  "actor": {
    "type": "operator",
    "id": "string (operator_id, SUPER_ADMIN)"
  },
  "suspendedAt": "2026-05-02T09:00:00Z"
}
```

**필드 노트**:
- `reason`: 운영자가 `X-Operator-Reason` 헤더에 입력한 값. PII (이메일·전화·이름) 포함 금지 (admin 입력 validation 의 책임).
- 동일 status 로의 PATCH 는 이벤트 미발행 (no-op).

**Consumers**:
- account-service: 신규 로그인 게이트 SUSPENDED 처리. 기존 발급된 access token 은 TTL 만료까지 유효 (별도 token revocation 필요 시 후속 task).
- 외부 SIEM: 테넌트 운영 중단 알림
- 관측성 시스템: 테넌트 SUSPEND 카운터·알림

---

## tenant.reactivated

테넌트가 SUSPENDED → ACTIVE 로 복귀할 때 발행.

**Topic**: `tenant.reactivated`

**Schema version**: 1

**Payload**:
```json
{
  "tenantId": "wms",
  "previousStatus": "SUSPENDED",
  "currentStatus": "ACTIVE",
  "reason": "string (운영자 사유)",
  "actor": {
    "type": "operator",
    "id": "string (operator_id, SUPER_ADMIN)"
  },
  "reactivatedAt": "2026-05-02T09:00:00Z"
}
```

**Consumers**:
- account-service: 로그인 게이트 잠금 해제
- 외부 SIEM: 운영 재개 알림

---

## tenant.activated

**TASK-MONO-773 (OD-1 · OD-5 · 구현 S4) — 새 이벤트.** 첫 관리자 대기 테넌트가 `PENDING → ACTIVE` 로 전이될 때 발행 — 첫 관리자 초대 수락(관리자 행 커밋 → account-service 전이 → 이 이벤트) 또는 그 활성화만 실패했던 경우의 `SUPER_ADMIN` 복구 PATCH. `tenant.reactivated`(SUSPENDED → ACTIVE)를 재사용하지 않는 이유: «다시» 가 아니다 — 처음 열리는 것이고, SIEM 이 «정지됐던 회사의 재개» 와 «새 회사의 개시» 를 섞지 않게.

**Topic**: `tenant.activated` — 🔴 토픽 생성 목록(`docker-compose.yml` · `docker-compose.e2e.yml` 의 topic init)과 admin-service `AdminOutboxPublisher.topicFor` 에 S4 가 함께 더한다(빠지면 outbox 릴레이가 이 행을 보낼 곳이 없다).

**Schema version**: 1

**Payload**:
```json
{
  "tenantId": "acme-corp",
  "previousStatus": "PENDING",
  "currentStatus": "ACTIVE",
  "trigger": "FIRST_ADMIN_ACCEPTED",
  "actor": {
    "type": "operator",
    "id": "string (operator_id — 첫 관리자(수락) 또는 SUPER_ADMIN(복구 PATCH))"
  },
  "activatedAt": "2026-10-16T09:00:00Z"
}
```

**필드 노트**:
- `trigger`: `FIRST_ADMIN_ACCEPTED`(수락) | `OPERATOR_COMPLETED`(수락은 끝났고 활성화만 실패했던 것을 `SUPER_ADMIN` 이 PATCH 로 마침). `reason` 칸은 없다 — 수락은 운영자 사유가 없는 자기 흐름이다(복구 PATCH 의 사유는 감사 행에 있다).
- 같은 테넌트에 한 번만 난다(`PENDING` 은 되돌아오지 않는다 — admin-api § 전이 매트릭스).

**Consumers**:
- 외부 SIEM: 새 회사 개시 알림
- 관측성 시스템: `tenant.activated.count` · «생성 → 활성» 소요 시간
- 서비스 소비자 없음(account-service 가 상태의 권위 — 이 이벤트를 소비해 게이트를 여닫지 않는다)

---

## tenant.updated

`displayName` 변경 시 발행. status 변경은 이 이벤트가 아닌 `tenant.suspended` / `tenant.reactivated` 로 발행 (status 전이는 별도 보안·감사 가치를 가지므로 이벤트 분리).

**Topic**: `tenant.updated`

**Schema version**: 1

**Payload**:
```json
{
  "tenantId": "wms",
  "changes": {
    "displayName": {
      "from": "Warehouse Management System",
      "to": "WMS Platform"
    }
  },
  "actor": {
    "type": "operator",
    "id": "string (operator_id, SUPER_ADMIN)"
  },
  "updatedAt": "2026-05-02T09:00:00Z"
}
```

**필드 노트**:
- `changes`: 변경된 필드만 포함. 향후 추가 필드 (예: `description`, `contactEmail`) 도입 시 동일 형태로 확장.
- 변경 없는 PATCH (no-op) 는 이벤트 미발행.

**Consumers**:
- 외부 SIEM: 메타데이터 변경 감사
- account-service: 캐시 invalidation (display_name 을 캐시한 경우)

---

## Consumer Rules

- **멱등 처리 필수**: `eventId` (UUID v7) 기반 dedupe. 같은 `tenantId` 의 같은 transition 이 중복 발행될 수 있음 (Kafka at-least-once).
- **순서 보장**: 같은 `tenantId` 의 이벤트는 같은 파티션에 도착. 다른 `tenantId` 사이 순서 보장은 없음.
- **schema tolerance**: 알 수 없는 필드는 무시 (forward-compatible). `schemaVersion` 이 지원 범위 밖이면 DLQ 로 이관.
- **DLQ**: `<topic>.DLT`. 3회 지수 백오프 재시도 후 이관.
- **PII 금지**: `reason` 필드에 이메일·전화·이름 등 PII 포함 금지 (admin 입력 validation 의 책임).
- **schema bump 정책**: 새 필드는 additive 로 추가하고 기존 schemaVersion 유지. breaking change 시 schemaVersion +1 + 마이그레이션 가이드.
