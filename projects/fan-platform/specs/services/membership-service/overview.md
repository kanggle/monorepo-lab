# membership-service — Overview

> 1-pager: responsibilities, public API surface, key invariants.
>
> Spec authored by **TASK-FAN-BE-008**. Implementation = **TASK-FAN-BE-009**;
> community-service adapter swap = **TASK-FAN-BE-010**.

## Service identity

| Field | Value |
|---|---|
| Service name | `membership-service` |
| Project | `fan-platform` |
| Service Type | `rest-api` |
| Architecture Style | **Layered + 명시적 상태 기계** |
| Stack | Java 21, Spring Boot 3.4, Postgres 16, Kafka 3.7 |
| Deployable unit | `apps/membership-service/` |
| Bounded Context | `membership` (subscription / tiered access / PG mock) |
| Persistent stores | Postgres (`fanplatform_membership` DB) — no Redis in v1 |
| Event publication | `fan.membership.activated.v1`, `fan.membership.canceled.v1` (`fan.membership.expired.v1` forward-declared, NOT emitted) |

## Responsibilities

- **구독 라이프사이클** — `subscribe → ACTIVE → cancel → CANCELED` 상태 기계 + windowed `[validFrom, validTo]`. 만료는 read-time 계산 (저장 전이 아님 — delegation `isActiveAt` 선례).
- **티어 위계** — `MEMBERS_ONLY` ⊂ `PREMIUM`. PREMIUM 구독은 MEMBERS_ONLY 접근도 부여. `AccessPolicy.tierGrants` 가 단일 진실.
- **PG mock 결제** — `PaymentGatewayPort` + 결정적 mock 어댑터. subscribe = `Idempotency-Key` 필수 + mock authorize → 성공 시 ACTIVE 생성 / 거절 시 미생성 + 422. 외부 실 PG 미연동.
- **internal access-check** — `GET /internal/membership/access` 가 community-service `MembershipChecker.hasAccess(accountId, tier, tenantId) → boolean` 의 원격 짝. fail-closed.
- **이벤트 발행** — outbox 패턴 (libs:java-messaging), activated/canceled 2개 토픽. 소비자 = notification-service v2 (forward).
- **테넌트 격리** — 모든 row `tenant_id`, 모든 쿼리 `WHERE tenant_id = ?`. 서비스 레벨에서 fail-closed 재검증.

## Public API surface (요약)

자세한 스펙은 `specs/contracts/http/membership-api.md` 참조.

| Method | Path | 설명 | Auth |
|---|---|---|---|
| POST | `/api/fan/memberships` | 구독 (subscribe, `Idempotency-Key` 필수, PG mock) | bearer (end-user) |
| POST | `/api/fan/memberships/{id}/cancel` | 구독 취소 (ACTIVE→CANCELED, 멱등) | bearer (end-user) |
| GET | `/api/fan/memberships` | 내 멤버십 목록 (accountId = sub) | bearer (end-user) |
| GET | `/api/fan/memberships/{id}` | 멤버십 단건 (cross-account/cross-tenant → 404) | bearer (end-user) |
| GET | `/internal/membership/access` | access-check (community 호출, **workload identity**) | client_credentials JWT (ADR-MONO-005) |

`/actuator/health`, `/actuator/info`, `/actuator/prometheus` 는 인증 없이 접근.
`/internal/**` 는 게이트웨이 미노출 — 내부 docker 망에서 workload-identity 로만 접근.

## Key invariants

1. **Tenant isolation (multi-tenant.md M2)** — 모든 row 의 `tenant_id` 가 가드되며, 서비스 토큰의 `tenant_id` 와 일치해야 한다. SUPER_ADMIN 의 `*` wildcard 만 예외. cross-tenant access-check → `allowed=false`.
2. **State machine (transactional.md T4)** — `MembershipStateMachine` 통과 없이는 어떤 status 전이도 일어나지 않는다. `CANCELED` 는 terminal. re-cancel = 멱등 no-op. 만료 = read-time, 저장 전이 없음.
3. **Tier hierarchy** — `tierGrants(PREMIUM, *) = true`; `tierGrants(MEMBERS_ONLY, PREMIUM) = false`. 단일 진실 = `AccessPolicy`.
4. **Idempotent subscribe (transactional.md T1)** — `Idempotency-Key` 재사용 시 동일 결과, 중복 row 미생성. 충돌 payload → 409.
5. **Fail-closed access-check** — 인프라 오류 시 access-check 는 항상 DENY (`allowed=false`) — community `MembershipChecker` 포트 계약과 동형.
6. **Outbox at-least-once** — 비즈니스 트랜잭션과 outbox 적재가 한 트랜잭션. 컨슈머는 `event_id` 기반 멱등 처리. expired 는 v1 미발행 (read-time).
7. **PG mock 경계** — 결제는 결정적 mock. 실 PG 미연동. 거절 시 멤버십 미생성.
8. **Cross-tenant/cross-account non-disclosure** — 타 테넌트/타 계정 membership id 조회 → 404 (403 아님 — 존재 누설 방지).

## Out of scope (this increment)

- **production code** — FAN-BE-009 (skeleton + domain + PG mock + endpoints + outbox + infra).
- **community-service 어댑터 교체** (`HttpMembershipChecker` 가 `AlwaysAllowMembershipChecker` stub 대체) — FAN-BE-010 (workload-identity provider 포함).
- **frontend 멤버십 게이트 / 구독 UI** — FAN-FE 후속.
- **notification-service** (membership 이벤트 소비) — PROJECT.md § v2 별건.
- **실 PG 연동** — mock 경계만. admin-service 무관.
- **`fan.membership.expired.v1` 발행** — read-time 만료 → scheduler 미존재. forward-declared only.
- **Redis 캐시** — v1 access-check 는 단일 indexed point read, 캐시 케이스 없음.
