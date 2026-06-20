# batch-worker — Overview

> 1-pager: responsibilities, public surface (scheduled jobs), key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `batch-worker` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `batch-job` |
| Architecture Style | **Layered** — scheduler / job / port-adapter, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL (own DB), Kafka, Spring Scheduler (no HTTP server) |
| Deployable unit | `apps/batch-worker/` |
| Bounded Context | `Platform Maintenance / Batch` |
| Persistent stores | PostgreSQL — `batch_job_execution_history` only (job name / status / started_at / finished_at / error_message) |
| Event publication | optional — batch 완료 통지 event (contracts must land in `specs/contracts/events/` before impl) |

## Responsibilities

- ~~**Expired session cleanup** — `auth-service` 세션 inactivity timeout 경과 row 정리 (직접 DB 접근 금지, API 또는 event 경유).~~ **REMOVED (TASK-BE-132 IAM 위임) — 인증 세션은 IAM 가 소유하며, ecommerce batch-worker 는 더 이상 세션을 정리하지 않는다.**
- **Stale paid-order forward-confirm** — `order-service` 의 paid-but-unconfirmed 주문(`status='PENDING' AND payment_id IS NOT NULL`, 30 min 초과)을 **FORWARD-CONFIRM**(`PENDING → CONFIRMED`). 정상 confirm 이벤트(`product.product.stock-changed`/`ORDER_RESERVED`)가 유실된 경우의 복구로, **취소가 아니라 이행**한다(고객이 결제했으므로). order-service `POST /api/internal/orders/confirm-paid-stale`(system-command, `client_credentials`)을 호출하며, 술어 평가/전이/이벤트는 order-service 가 서버측에서 수행한다(TASK-BE-412 contract). BE-138 saga stuck-detector(`payment_id IS NULL` 버킷, 취소 아닌 STUCK 에스컬레이션)와는 `payment_id` 로 **상호배타** — 무중첩. (TASK-BE-410 reframe; impl=TASK-BE-413 caller + TASK-BE-412 endpoint.)
- **Daily sales aggregation** — order / payment 합산 일일 요약 (downstream 분석 dashboard 용).
- **Elasticsearch index consistency check** — `product-service` 와 `search-service` HTTP read-only 조회 후 drift 검출.
- **Job execution history tracking** — 모든 job 실행 결과 `batch_job_execution_history` 에 기록.

## Public surface

batch-worker 는 **HTTP API 0**. 다음 채널만 보유:

| Channel | Endpoint / Topic / Job | Auth | Purpose |
|---|---|---|---|
| Spring Scheduler | `expiredSessionCleanupJob` (`@Scheduled(cron = "0 0 * * * *")`) | — | hourly session GC |
| Spring Scheduler | `stalePaidOrderConfirmationJob` (`@Scheduled(cron = "0 */10 * * * *")`) | client_credentials (outbound to order-service) | every 10 min forward-confirm of paid-but-unconfirmed `PENDING` orders (`payment_id IS NOT NULL`) via order-service internal endpoint — see [order-confirm-paid-stale.md](../../../contracts/http/internal/order-confirm-paid-stale.md). Disjoint from BE-138 (`payment_id IS NULL`). |
| Spring Scheduler | `dailySalesAggregationJob` (`@Scheduled(cron = "0 0 1 * * *")`) | — | daily 01:00 summary |
| Spring Scheduler | `searchIndexConsistencyCheckJob` (`@Scheduled(cron = "0 0 3 * * *")`) | — | daily 03:00 index drift check — **heuristic spot-check only**: search-api has no full-enumeration endpoint, so the check paginates the product-service authority catalog and queries search by name; results represent suspected drift, not confirmed inconsistency (see `SearchIndexConsistencyJob` javadoc + AC-6, TASK-BE-409). |
| Kafka publish | TBD (per future contracts) | — | batch 완료 통지 |
| HTTP outbound | `product-service`, `search-service` (published contracts read-only) | — | index consistency check |
| HTTP outbound | `order-service` (`POST /api/internal/orders/confirm-paid-stale`) | client_credentials Bearer JWT (`ecommerce-internal-services-client`) | stale paid-order forward-confirm — the ONE non-read-only internal system-command call (TASK-BE-410 decision) |

자세한 spec 은 [architecture.md § Scheduled Jobs](architecture.md) + [dependencies.md](dependencies.md) 참조 (dependencies.md 존재 시).

## Key invariants

1. **Each job MUST be idempotent** — safe to re-run without side effects (재실행 시 동일 결과 또는 no-op).
2. **Failed jobs MUST NOT block other jobs** — job 실패 시 retry/timeout 처리, scheduler 자체 정지 금지.
3. **No cross-service DB access** — `service-boundaries.md` 강제; 다른 service 의 DB 직접 read/write 금지 (HTTP / event 만 통과).
4. **Published events follow contracts** — batch 완료 event 발행 시 반드시 `specs/contracts/events/` 등록된 contract 만 사용.
5. **Scheduler ↛ business rule / persistence** — scheduler layer 는 job 등록 / 실행 trigger 만; 비즈니스 결정 / DB 직접 접근 금지 (architecture.md § Layered Rules).

## Owned Data

- `batch_job_execution_history` only (job name, status, started_at, finished_at, error_message).

## Published Interfaces

- None (no HTTP APIs).
- Potential Kafka publication per future contracts (when 정의되면 `specs/contracts/events/` 에 추가).

## Dependent Systems

- PostgreSQL — own DB (`batch_job_execution_history` only).
- Kafka — event publish (future).
- `product-service` — HTTP read-only (consistency check)
- `search-service` — HTTP read-only (consistency check)
- `order-service` — HTTP system-command (`POST /api/internal/orders/confirm-paid-stale`, `client_credentials` Bearer) for stale paid-order forward-confirm (the single non-read-only call; order-service evaluates the predicate server-side)

## Out of scope (v1)

- Real-time request handling — no HTTP APIs.
- Business domain ownership — 다른 service 의 데이터를 published contract 로만 read.
- User-facing functionality — admin / customer UI 부재.
- Cross-service DB sweep — service-boundaries 위반, HTTP / event 경유.
- Manual job trigger UI — v2 (platform-console 에 추가 시).
