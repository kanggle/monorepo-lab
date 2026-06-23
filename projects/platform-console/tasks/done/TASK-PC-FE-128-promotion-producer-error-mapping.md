# TASK-PC-FE-128 — ecommerce promotion producer error-code mapping (errors.ts)

Status: done
Project: platform-console
Service: console-web
Type: fix
Related ADR: ADR-MONO-031 § 2.4.10 (ecommerce console absorption)

## Goal

콘솔의 프로모션 생성/수정/쿠폰발급 실패 시, promotion-service 가 돌려주는 producer 에러코드가 `shared/api/errors.ts` 메시지 맵에 없어 **사유 불명의 일반 폴백 "저장하지 못했습니다." 만** 표시되던 버그를 고친다. (실제 발단: 프로모션 등록 실패 시 `400 INVALID_PROMOTION_REQUEST` — 종료일≤시작일 / 퍼센트>100 / 날짜형식 — 가 미매핑이라 운영자가 무엇이 잘못됐는지 알 수 없었음.)

> 자매 버그였던 "ecommerce-ops 목록 stale 캐시(등록 후 하드리로드 전까지 안 보임)"는 별 트랙(#1909 / TASK-PC-FE-126)으로 main 에 이미 머지됨. 본 task 는 그와 함께 묶으려다 동시-세션 id 충돌(124→126→already-merged)로 분리된, **errors.ts 매핑 단독** 잔여분이다.

## Background / Root cause

`PromotionForm` 은 mutation 실패 시 `messageForCode(code, '저장하지 못했습니다.')` 로 producer 코드를 한국어 메시지로 변환한다. 그러나 promotion-service `GlobalExceptionHandler` 가 방출하는 코드 중 다수가 `MESSAGES` 맵에 없어 폴백으로 떨어졌다. 특히 `INVALID_PROMOTION_REQUEST` 는 `VALIDATION_ERROR`(이미 매핑됨)와 다른 코드라 "입력값이 올바르지 않습니다" 도 못 보여줬다.

## Scope

`projects/platform-console/apps/console-web/src/shared/api/errors.ts` `MESSAGES` 맵에 promotion-service producer 코드 추가(producer 가 **실제 방출하는 것만** — `GlobalExceptionHandler` 확인, 계약서 추정 아님):

- `INVALID_PROMOTION_REQUEST` (400 — endDate≤startDate / PERCENTAGE discountValue>100 / Instant 파싱실패 cross-field 가드) → 기간·할인값 둘 다 짚어주는 actionable 메시지
- `PROMOTION_NOT_FOUND` (404)
- `PROMOTION_ALREADY_ENDED` · `PROMOTION_HAS_ISSUED_COUPONS` · `PROMOTION_NOT_ACTIVE` · `COUPON_LIMIT_EXCEEDED` (422 상태가드)

out of scope: `VALIDATION_ERROR`/`ACCESS_DENIED`(이미 매핑됨); 쿠폰 사용/복원 코드(`COUPON_ALREADY_USED`/`COUPON_NOT_OWNED`/`COUPON_EXPIRED`/`COUPON_RESTORE_NOT_ALLOWED`/`COUPON_NOT_FOUND` — web-store 고객 경로라 콘솔 미도달); 백엔드; `PromotionForm` 컴포넌트(이미 `messageForCode` 사용 → 변경 불필요); 와이어/계약(무변).

## Acceptance Criteria

- AC-1: promotion 생성/수정 시 `400 INVALID_PROMOTION_REQUEST` 가 폴백이 아닌 "프로모션 정보가 올바르지 않습니다 …" 로 표시된다.
- AC-2: 422 상태가드 4종 + `COUPON_LIMIT_EXCEEDED` 가 각각 actionable 메시지로 표시된다.
- AC-3: 매핑되지 않은 임의 코드는 기존대로 제공된 폴백을 반환한다(회귀 없음).
- AC-4: `error-messages-promotion.test.ts` vitest 단위 테스트로 위를 단언.
- AC-5: `pnpm lint` + `tsc --noEmit` + `vitest` GREEN (console-web). (web-store 무관)

## Related Specs

- ADR-MONO-031 § 2.4.10 (ecommerce 운영 표면 흡수)
- `specs/contracts/console-integration-contract.md` § 2.4.10.2 (promotions operator CRUD 에러코드)

## Related Contracts

없음(클라이언트 메시지 맵만 변경, 와이어/계약 무변).

## Edge Cases

- `INVALID_PROMOTION_REQUEST` 는 producer 에서 3가지 원인(날짜형식·기간역전·퍼센트>100)을 같은 코드로 방출 → 단일 메시지로 기간·할인값 양쪽을 짚어줌(날짜형식은 PromotionForm 의 dayToInstant 로 항상 유효 Instant 전송되어 UI 경로에선 사실상 비발생).

## Failure Scenarios

- 계약서에만 있고 producer 가 안 쓰는 코드를 매핑하면 dead entry → producer `GlobalExceptionHandler` 의 `ErrorResponse.of("…")` 실측 코드만 매핑.
