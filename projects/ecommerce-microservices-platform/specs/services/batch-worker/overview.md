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

- ~~**Expired session cleanup** — `auth-service` 세션 inactivity timeout 경과 row 정리 (직접 DB 접근 금지, API 또는 event 경유).~~ **REMOVED (TASK-BE-132 GAP 위임) — 인증 세션은 GAP 가 소유하며, ecommerce batch-worker 는 더 이상 세션을 정리하지 않는다.**
- **Stale order cancellation** — `order-service` 의 `PENDING` 상태 30 min 초과 주문 자동 취소 (TASK-BE-138 saga stuck-detector 와 별개 의 longer-horizon cleanup).
- **Daily sales aggregation** — order / payment 합산 일일 요약 (downstream 분석 dashboard 용).
- **Elasticsearch index consistency check** — `product-service` 와 `search-service` HTTP read-only 조회 후 drift 검출.
- **Job execution history tracking** — 모든 job 실행 결과 `batch_job_execution_history` 에 기록.

## Public surface

batch-worker 는 **HTTP API 0**. 다음 채널만 보유:

| Channel | Endpoint / Topic / Job | Auth | Purpose |
|---|---|---|---|
| Spring Scheduler | `expiredSessionCleanupJob` (`@Scheduled(cron = "0 0 * * * *")`) | — | hourly session GC |
| Spring Scheduler | `staleOrderCancellationJob` (`@Scheduled(cron = "0 */10 * * * *")`) | — | every 10 min PENDING sweep |
| Spring Scheduler | `dailySalesAggregationJob` (`@Scheduled(cron = "0 0 1 * * *")`) | — | daily 01:00 summary |
| Spring Scheduler | `searchIndexConsistencyCheckJob` (`@Scheduled(cron = "0 0 3 * * *")`) | — | daily 03:00 index drift check |
| Kafka publish | TBD (per future contracts) | — | batch 완료 통지 |
| HTTP outbound | `product-service`, `search-service` (published contracts read-only) | — | index consistency check |

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

## Out of scope (v1)

- Real-time request handling — no HTTP APIs.
- Business domain ownership — 다른 service 의 데이터를 published contract 로만 read.
- User-facing functionality — admin / customer UI 부재.
- Cross-service DB sweep — service-boundaries 위반, HTTP / event 경유.
- Manual job trigger UI — v2 (admin-dashboard 에 추가 시).
