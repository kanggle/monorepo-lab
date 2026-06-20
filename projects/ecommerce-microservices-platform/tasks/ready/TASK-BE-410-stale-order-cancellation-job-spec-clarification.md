---
id: TASK-BE-410
title: staleOrderCancellationJob — 차단 해소(컨트랙트·인가·경계) spec-clarification 선행
status: ready
project: ecommerce-microservices-platform
service: batch-worker
type: spec
created: 2026-06-20
---

# TASK-BE-410 — staleOrderCancellationJob spec-clarification (구현 차단 해소)

> ⛔ **구현 차단 task** — 아래 AC(spec/contract 결정)가 **모두 해소되기 전에는 잡 구현 금지**. 본 task 는 결정/문서화만 다루며, 구현은 별도 후속 impl task 로 분리한다. (2026-06-20 BE-409 스코핑에서 HARDSTOP-08/09 로 식별.)

## Goal

batch-worker `staleOrderCancellationJob`(`@Scheduled(cron="0 */10 * * * *")`, PENDING 30분 초과 주문 자동취소)은 명세는 있으나 구현 가능한 컨트랙트가 없다. 구현 전 해소해야 할 결정·spec/contract 갱신을 확정한다.

## Background (차단 사유)

- `overview.md` 는 이 잡이 order-service 를 HTTP 로 호출한다고 하나, batch-worker `dependencies.md` 는 HTTP 소비자로 product/search-service 만 인가(order-service 미인가).
- order-api.md 에 batch 전용 내부 취소 컨트랙트 없음. user-facing `POST /api/orders/{id}/cancel`(소유권 체크)·admin `POST /api/admin/orders/{id}/status`(ADMIN Bearer)만 존재 → batch 가 어떤 인증으로 호출하는지 미정.
- BE-138(order-service saga stuck-detector)와 경계 미정: 이 잡이 `STUCK_RECOVERY_FAILED` 를 건너뛰는지, `payment_id IS NULL` 만 취소하는지, 모든 30분+ PENDING 을 취소하는지.

## Acceptance Criteria (전부 해소 전 impl 금지)

- [ ] **AC-1**: `specs/services/batch-worker/dependencies.md` 에 order-service 를 HTTP 소비자로 인가(또는 인가하지 않기로 결정 시 대안 데이터경로 명시).
- [ ] **AC-2**: batch→order 취소 컨트랙트 확정 — (a) 신규 internal 컨트랙트 `specs/contracts/http/order-internal-batch.md`(`POST /api/internal/orders/cancel-pending-stale`, system-triggered, 소유권/ADMIN Bearer 불요) 발행, 또는 (b) 기존 admin status 엔드포인트를 system credential 로 호출하기로 명시.
- [ ] **AC-3**: batch-worker 의 order-service 호출 인증 방식 확정(client_credentials? internal-only 포트? 무인증 internal endpoint?).
- [ ] **AC-4**: BE-138 과 경계 확정 — 취소 대상 PENDING 부분집합 정의(STUCK_RECOVERY_FAILED·payment_id 유무 처리). idempotency(이미 CANCELLED→422 no-op) 규칙 포함.
- [ ] **AC-5**: 위 해소 후 구현 impl task(신규 BE-id)를 ready/ 에 작성하고 본 task 는 done/ 로 이관.

## Related Specs / Contracts

- `specs/services/batch-worker/{overview,architecture,dependencies}.md`
- `specs/contracts/http/order-api.md` (+ 신규 internal 컨트랙트 가능)
- order-service BE-138(saga stuck-detector, done) — 경계 참조

## Failure Scenarios

- AC 미해소 상태로 구현 강행 → 미인가 cross-service 호출 / 인증부재 / BE-138 중복취소. 반드시 결정 선행.
