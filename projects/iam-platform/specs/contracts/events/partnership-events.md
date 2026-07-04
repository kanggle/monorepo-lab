# Event Contract: partnership lifecycle (admin-service)

admin-service 가 발행하는 **cross-org 파트너십**(ADR-MONO-045) lifecycle 이벤트. host 테넌트 A 가 자기 테넌트에서 "partner B 의 사람들이 무엇을 하는가"를 감사(legibility)할 수 있게 하고, 종료·중단 시 cascade-revoke 를 관측 신호로 노출하며, 외부 SIEM·관측성 시스템이 소비한다.

**발행 방식**: Outbox 패턴 (T3). admin-service 가 `admin_actions` 행 INSERT 와 같은 DB 트랜잭션에서 outbox row 작성 → relay 가 Kafka 발행.
**파티션 키**: `partnershipId` (같은 파트너십의 lifecycle 이벤트 순서 보장 — invite → accept → … → terminate)
**Source**: `admin-service`
**도입 태스크**: TASK-BE-476 (계약 정의, ADR-MONO-045 §3.4 step 1), §3.4 step 2 (실제 발행 구현)

---

## Event Envelope

[auth-events.md](auth-events.md) 와 동일한 표준 envelope (`eventId`, `eventType`, `source`, `occurredAt`, `schemaVersion`, `partitionKey`, `payload`).

모든 payload 의 `delegatedScope`/`participantScope` 는 도메인·역할 키 집합(비-PII); `actor` 는 전이를 수행한 operator(항상 `operator` 타입 — 파트너십은 self-service 등록 불가, 양측 `TENANT_ADMIN` 이 당사자). PII 금지(`reason` 포함).

---

## partnership.invited

host 테넌트 A 의 `TENANT_ADMIN` 이 partner B 에게 파트너십을 invite 할 때 발행(→ `PENDING`). `POST /api/admin/partnerships` 의 audit row INSERT 와 같은 트랜잭션.

**Topic**: `partnership.invited`

**Schema version**: 1

**Payload**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "status": "PENDING",
  "delegatedScope": { "domains": ["wms", "scm"], "roles": ["WMS_OUTBOUND_OPERATOR", "SCM_PLANNER"] },
  "actor": { "type": "operator", "id": "string (operator_id, host TENANT_ADMIN)" },
  "invitedAt": "2026-07-04T10:00:00Z"
}
```

**필드 노트**:
- `delegatedScope`: host 가 위임하는 bounded `{domains}×{roles}`. admin role·플랫폼-scope 불포함(invite-time cap 검증됨). 이 시점엔 파생 접근 0(PENDING).
- `hostTenantId != partnerTenantId`, 둘 다 `'*'` 아님.

**Consumers**:
- partner B 의 콘솔/알림: "수락 대기 중인 파트너십" 배지(step 3 UI).
- 외부 SIEM: cross-org 위임 개시 감사.
- 관측성: `partnership.invited.count`.

---

## partnership.accepted

partner B 의 `TENANT_ADMIN` 이 invite 를 수락할 때 발행(`PENDING` → `ACTIVE`). 이 시점부터 B 가 배정한 participant 들이 A 를 assume-tenant 로 운영할 수 있다.

**Topic**: `partnership.accepted`

**Schema version**: 1

**Payload**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "previousStatus": "PENDING",
  "currentStatus": "ACTIVE",
  "actor": { "type": "operator", "id": "string (operator_id, partner TENANT_ADMIN)" },
  "acceptedAt": "2026-07-04T10:05:00Z"
}
```

**Consumers**:
- host A 의 감사/콘솔: 파트너십 활성 확인.
- 관측성: 활성 파트너십 카운터.

---

## partnership.suspended

파트너십이 `ACTIVE` → `SUSPENDED` 로 전이될 때 발행(either party). cascade-revoke(D6): 그 파트너십에서 파생한 **모든** participant 의 host-reach 가 다음 요청에서 0 이 된다.

**Topic**: `partnership.suspended`

**Schema version**: 1

**Payload**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "previousStatus": "ACTIVE",
  "currentStatus": "SUSPENDED",
  "reason": "string (운영자 사유, max 500자, PII 금지)",
  "actor": { "type": "operator", "id": "string (operator_id — host 또는 partner TENANT_ADMIN)" },
  "suspendedAt": "2026-07-04T11:00:00Z"
}
```

**필드 노트**:
- `actor` 는 host 또는 partner 측 — 관계 중단은 상호적(D2). 어느 쪽이 중단했는지는 `actor.id` 의 home tenant 로 식별.
- 동일 status 로의 전이는 이벤트 미발행(no-op).

**Consumers**:
- host A: partner 사람들의 접근이 일시 중단됨을 감사·관측.
- 외부 SIEM: cross-org 접근 일시 차단 알림.

---

## partnership.reactivated

파트너십이 `SUSPENDED` → `ACTIVE` 로 복귀할 때 발행(either party). participant 파생이 다음 요청부터 복구(재배정 불요 — GCP billing↔IAM parity 와 동형).

**Topic**: `partnership.reactivated`

**Schema version**: 1

**Payload**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "previousStatus": "SUSPENDED",
  "currentStatus": "ACTIVE",
  "reason": "string (운영자 사유)",
  "actor": { "type": "operator", "id": "string (operator_id — host 또는 partner TENANT_ADMIN)" },
  "reactivatedAt": "2026-07-04T12:00:00Z"
}
```

**Consumers**:
- host A / partner B: 관계 재개 감사.

---

## partnership.terminated

파트너십이 `TERMINATED` 로 전이될 때 발행(either party, 종단). **one-shot cascade 이벤트** — 그 파트너십에서 파생한 participant 가 N 명이어도 이벤트는 **1건**이다(D6: 파생 자체가 사라지므로 per-operator sweep·이벤트 불필요). 다음 요청부터 모든 participant 의 host-reach 가 0.

**Topic**: `partnership.terminated`

**Schema version**: 1

**Payload**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "previousStatus": "ACTIVE",
  "currentStatus": "TERMINATED",
  "reason": "string (운영자 사유)",
  "participantCountAtTermination": 3,
  "actor": { "type": "operator", "id": "string (operator_id — host 또는 partner TENANT_ADMIN)" },
  "terminatedAt": "2026-07-04T18:00:00Z"
}
```

**필드 노트**:
- `previousStatus`: `PENDING`(invite 철회/거절) 또는 `ACTIVE`/`SUSPENDED`(운영 중 종료) 가능.
- `participantCountAtTermination`: 종료 시점 배정된 participant 수(감사 legibility — "몇 명의 접근이 한 번에 소멸했나"). cascade 는 이벤트가 아닌 **파생 소멸**로 발효되므로 이 카운트는 관측용 메타일 뿐 per-operator 이벤트를 대체하지 않는다(애초에 없음).

**Consumers**:
- host A: "partner 관계 종료 — 관련 접근 전량 소멸" 감사·관측(가장 중요한 소비자).
- 외부 SIEM: cross-org 위임 종료 알림.
- 관측성: `partnership.terminated.count`, cascade 규모(`participantCountAtTermination`) 히스토그램.

---

## partnership.participant_added

partner B 가 자기 operator 를 participant 로 배정할 때 발행(D4). 그 operator 의 assume-tenant reach 에 host 가 추가(다음 발급부터).

**Topic**: `partnership.participant_added`

**Schema version**: 1

**Payload**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "operatorId": "string (operator_id, B 소유)",
  "participantScope": { "domains": ["wms"], "roles": ["WMS_OUTBOUND_OPERATOR"] },
  "actor": { "type": "operator", "id": "string (operator_id, partner TENANT_ADMIN)" },
  "assignedAt": "2026-07-04T13:00:00Z"
}
```

**필드 노트**:
- `participantScope`: `null` 이면 키 omit(⟺ `delegatedScope` 전체). 비-`null` 은 `⊆ delegatedScope`.
- `operatorId` 의 home tenant 는 항상 `partnerTenantId`(B 는 자기 사람만 배정).

**Consumers**:
- host A: "partner 사람 X 가 내 테넌트 운영에 합류" 감사.
- 관측성: participant 카운터.

---

## partnership.participant_removed

participant 해제 시 발행(B 가 자기 직원 offboard 또는 참여 종료, D6 individual offboarding). 해제 즉시 그 operator 의 host-reach 파생 소멸(A-측 조치 불요).

**Topic**: `partnership.participant_removed`

**Schema version**: 1

**Payload**:
```json
{
  "partnershipId": "00000000-0000-7000-8000-00000000p001",
  "hostTenantId": "acme-corp",
  "partnerTenantId": "globex-corp",
  "operatorId": "string (operator_id, B 소유)",
  "actor": { "type": "operator", "id": "string (operator_id, partner TENANT_ADMIN)" },
  "removedAt": "2026-07-04T17:00:00Z"
}
```

**Consumers**:
- host A: "partner 사람 X 의 접근 소멸" 감사.
- 관측성: participant 제거 카운터.

---

## Consumer Rules

- **멱등 처리 필수**: `eventId` (UUID v7) 기반 dedupe. 같은 `partnershipId` 의 같은 transition 이 중복 발행될 수 있음 (Kafka at-least-once).
- **순서 보장**: 같은 `partnershipId` 의 이벤트는 같은 파티션에 도착(invite → accept → terminate 순서 보장). 다른 파트너십 사이 순서 보장은 없음.
- **cascade 는 이벤트가 아닌 파생 소멸로 발효**: `partnership.terminated`/`suspended` 는 **one-shot** — 소비자는 이를 "이 파트너십의 모든 participant 접근이 무효화됨"으로 해석해야 하며, per-operator revocation 이벤트를 기대하지 않는다(발행되지 않음). 실제 접근 차단은 assume-tenant 발급기의 request-time 재평가([rbac.md](../../services/admin-service/rbac.md#cross-org-partner-delegation-confinement-adr-mono-045-d3d5))가 보장한다.
- **schema tolerance**: 알 수 없는 필드는 무시 (forward-compatible). `schemaVersion` 이 지원 범위 밖이면 DLQ 로 이관.
- **DLQ**: `<topic>.dlq`. 3회 지수 백오프 재시도 후 이관.
- **PII 금지**: `reason` 필드에 이메일·전화·이름 등 PII 포함 금지 (admin 입력 validation 의 책임). `delegatedScope`/`participantScope` 는 도메인/역할 키(비-PII).
- **schema bump 정책**: 새 필드는 additive 로 추가하고 기존 schemaVersion 유지. breaking change 시 schemaVersion +1 + 마이그레이션 가이드.
