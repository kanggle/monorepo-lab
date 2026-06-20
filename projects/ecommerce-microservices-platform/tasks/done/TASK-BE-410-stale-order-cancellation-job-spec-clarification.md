---
id: TASK-BE-410
title: staleOrderCancellationJob — 차단 해소(컨트랙트·인가·경계) spec-clarification 선행
status: done
project: ecommerce-microservices-platform
service: batch-worker
type: spec
created: 2026-06-20
---

# TASK-BE-410 — staleOrderCancellationJob spec-clarification (구현 차단 해소)

> ✅ **RESOLVED (2026-06-20).** 모든 AC 해소. 잡은 **REFRAME** 됐다 — "stale order **cancellation**" 이
> 아니라 paid-but-unconfirmed 주문의 **forward-confirm** (`PENDING → CONFIRMED`). ground truth 검증
> 결과 `markPaymentCompleted()` 는 paymentId 만 세팅하고 status 는 PENDING 으로 두며, `confirm()` 은
> 별개의 PENDING→CONFIRMED 전이(saga: `product.product.stock-changed`/`ORDER_RESERVED` →
> `OrderConfirmationService.confirmOrder`)다. confirm 이벤트 유실 시 paid 주문이
> `status='PENDING' AND payment_id IS NOT NULL` 로 영구 잔류 → 고객이 결제했으므로 **취소가 아니라 이행**.
> impl 은 **TASK-BE-412**(order-service endpoint, 선행) + **TASK-BE-413**(batch-worker caller) 로 분리.
>
> > 🔁 **Orchestrator note**: status 를 done 으로 flip 했으나 파일은 아직 `tasks/ready/` 에 있다.
> > `git mv tasks/ready/TASK-BE-410-*.md tasks/done/` 후 re-stage(`git add` done-path) + `git show :<done-path>` 로 `status: done` 확인 필요 (CLAUDE.md git mv re-stage 규율).

## Resolution (decision set encoded)

- **AC-1 ✅**: `specs/services/batch-worker/dependencies.md` 에 order-service 를 **system-command** 소비자로 인가
  (read-only 문구를 widen — 명시된 단 하나의 internal command call 허용). overview/architecture 도 동기화.
- **AC-2 ✅**: 신규 order-service-owned internal contract
  `specs/contracts/http/internal/order-confirm-paid-stale.md` — `POST /api/internal/orders/confirm-paid-stale`
  (server-side predicate, batch op). admin/user 엔드포인트 재사용 **거부**.
- **AC-3 ✅**: `client_credentials` Bearer JWT (`ecommerce-internal-services-client` — iam-integration.md 에서
  "v2 DEFERRED" → **ACTIVE** 로 flip, caller 가 `IamClientCredentialsTokenProvider` 로 mint/cache),
  gateway-excluded 라우트(`/api/internal/orders/**` 는 게이트웨이 미라우팅), **fail-closed 401**.
  BE-402 / product-to-account.md 선례 mirror.
- **AC-4 ✅**: 술어 = `status='PENDING' AND payment_id IS NOT NULL AND created_at < (now - olderThanMinutes
  [default 30]) ORDER BY created_at ASC LIMIT :limit`. action = `Order.confirm()` per order, idempotent
  (re-load + status re-check; already-CONFIRMED → skip, counted not errored), 정상 confirm 경로와 **동일한**
  `OrderConfirmed` 이벤트(`order.order.confirmed`, outbox 공동커밋) 발행 → shipping-service 등 downstream
  fulfillment 동일 발화. **BE-138(`OrderStuckDetector`)와 `payment_id` 로 상호배타** (BE-138=`payment_id IS NULL`
  → STUCK 에스컬레이션, never confirm; 본 잡=`payment_id IS NOT NULL` → forward-confirm). 무중첩.
- **AC-5 ✅**: impl task 2건 작성 — `TASK-BE-412`(order-service endpoint, ready, 선행) +
  `TASK-BE-413`(batch-worker job, ready, BE-412 의존). 본 task done 이관.

## Spec/contract edits (이 task 에서 랜딩)

- (NEW) `specs/contracts/http/internal/order-confirm-paid-stale.md`
- `specs/services/batch-worker/{overview,architecture,dependencies,observability}.md`
  (잡 reframe + order-service/client_credentials 인가 + architecture.md cross-service-DB 모순 라인 HTTP-only 수정
  + `batch_stale_orders_cancelled_total` → `batch_paid_orders_confirmed_total`/`_confirm_skipped_total`)
- `specs/services/order-service/{overview,dependencies}.md` (inbound internal system call 예외 + `/api/internal/orders/**` 게시면)
- `specs/contracts/http/order-api.md` (§ Internal Endpoints 교차참조 + system PENDING→CONFIRMED recovery + BE-138 경계표)
- `specs/integration/iam-integration.md` (`ecommerce-internal-services-client` → ACTIVE, BE-410 trigger)

## (원본) Goal

batch-worker `staleOrderCancellationJob`(`@Scheduled(cron="0 */10 * * * *")`)은 명세는 있으나 구현 가능한
컨트랙트가 없었다. 위 Resolution 이 결정·spec/contract 갱신을 확정한다 (REFRAME: cancellation → forward-confirm).

## Goal

batch-worker `staleOrderCancellationJob`(`@Scheduled(cron="0 */10 * * * *")`, PENDING 30분 초과 주문 자동취소)은 명세는 있으나 구현 가능한 컨트랙트가 없다. 구현 전 해소해야 할 결정·spec/contract 갱신을 확정한다.

## Background (차단 사유)

- `overview.md` 는 이 잡이 order-service 를 HTTP 로 호출한다고 하나, batch-worker `dependencies.md` 는 HTTP 소비자로 product/search-service 만 인가(order-service 미인가).
- order-api.md 에 batch 전용 내부 취소 컨트랙트 없음. user-facing `POST /api/orders/{id}/cancel`(소유권 체크)·admin `POST /api/admin/orders/{id}/status`(ADMIN Bearer)만 존재 → batch 가 어떤 인증으로 호출하는지 미정.
- BE-138(order-service saga stuck-detector)와 경계 미정: 이 잡이 `STUCK_RECOVERY_FAILED` 를 건너뛰는지, `payment_id IS NULL` 만 취소하는지, 모든 30분+ PENDING 을 취소하는지.

## Acceptance Criteria (전부 해소 전 impl 금지)

> 참고: AC-2 의 컨트랙트는 `cancel-pending-stale` 가 아니라 **forward-confirm** 으로 REFRAME 됐고
> (cancel → confirm), 파일명은 `specs/contracts/http/internal/order-confirm-paid-stale.md` 로 확정됐다.
> AC-4 의 idempotency 도 "이미 CANCELLED→422" 가 아니라 "이미 CONFIRMED→skip(no-op, counted)" 로 REFRAME.

- [x] **AC-1**: order-service 를 batch-worker dependencies.md 에 **system-command** 소비자로 인가 (read-only widen).
- [x] **AC-2**: (a) 신규 internal 컨트랙트 `order-confirm-paid-stale.md` 발행 (admin/user 재사용 거부). REFRAME: cancel → forward-confirm.
- [x] **AC-3**: `client_credentials` Bearer (`ecommerce-internal-services-client` ACTIVE), gateway-excluded, fail-closed 401.
- [x] **AC-4**: BE-138 경계 확정 — `payment_id` 로 상호배타(BE-138=NULL→STUCK, 본 잡=NOT NULL→confirm). idempotency: 이미 CONFIRMED→skip.
- [x] **AC-5**: impl task TASK-BE-412(선행)+TASK-BE-413 ready/ 작성. 본 task done flip (orchestrator git mv 필요).

## Related Specs / Contracts

- `specs/services/batch-worker/{overview,architecture,dependencies}.md`
- `specs/contracts/http/order-api.md` (+ 신규 internal 컨트랙트 가능)
- order-service BE-138(saga stuck-detector, done) — 경계 참조

## Failure Scenarios

- AC 미해소 상태로 구현 강행 → 미인가 cross-service 호출 / 인증부재 / BE-138 중복취소. 반드시 결정 선행.
