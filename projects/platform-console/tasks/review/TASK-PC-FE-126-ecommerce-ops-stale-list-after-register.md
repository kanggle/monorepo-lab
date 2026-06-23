# TASK-PC-FE-126 — ecommerce-ops: registered/created entity missing from list until hard reload (stale client cache) + promotion producer error-code mapping

Status: review
Project: platform-console
Service: console-web
Type: fix
Related ADR: ADR-MONO-031 § 2.4.10 (ecommerce console absorption)

> 재번호 이력: 원래 병행 세션이 `TASK-PC-FE-124`로 작성했으나 124는 이미 다른 fix(promotion 날짜 Instant, #1905 머지)로 점유된 ID였다 → 충돌 해소를 위해 **PC-FE-126**으로 재번호. 동시에 아래 ②(promotion 에러코드 매핑)를 같은 PR로 번들.

## Goal

두 개의 연관된 ecommerce-ops 콘솔 UX 버그를 한 번에 고친다.

① **목록 stale 캐시** — 콘솔의 ecommerce 운영 화면에서 **셀러를 등록(또는 상품/프로모션/알림템플릿을 생성)해도 목록에 즉시 나타나지 않고, 브라우저 하드 리로드(F5) 후에야 보이는** 버그.

② **promotion producer 에러코드 미매핑** — 프로모션 생성/수정/쿠폰발급 실패 시 producer가 돌려주는 `INVALID_PROMOTION_REQUEST`(기간 역전·퍼센트>100·날짜형식) 및 422 상태가드 코드가 `shared/api/errors.ts` 메시지 맵에 없어, 사용자가 **무엇이 잘못됐는지 모르는 일반 폴백 "저장하지 못했습니다."** 만 보던 버그(보고된 "프로모션 저장 실패" 혼란의 직접 원인). ①과 동일 화면군의 자매 결함이라 함께 처리.

## Background / Root cause

데이터는 백엔드~프론트 프록시까지 끝까지 정상으로 흐른다(등록 `201`, 등록 직후 목록 재조회 `200`, 백엔드가 새 엔티티 반환까지 확인). 버그는 **console-web의 React Query 캐시 처리**에 있다.

`use-ecommerce-sellers.ts`(및 products/promotions/notifications)의 seeded page-0 목록 쿼리는:

```ts
staleTime: seeded ? 30_000 : 0,
refetchOnMount: seeded ? false : true,
```

여기에 `READ_QUERY_REFETCH`(`refetchOnWindowFocus: false`, `refetchInterval: false`)가 겹친다. 의도는 "SSR 시드를 source of truth로 쓰고 background refetch는 하지 않는다"이다.

문제 시퀀스(등록 흐름):

1. 목록(/ecommerce/sellers) 방문 → React Query 캐시에 page-0 목록(A,B,C) 적재.
2. "셀러 등록"(별도 `/new` 라우트) → 제출 → `POST 201`.
3. 등록 성공 시 mutation `onSuccess`가 `invalidateQueries({ queryKey: [SELLERS_KEY, 'list'] })` 호출. **그러나 이 시점에 목록은 mount돼 있지 않다(active observer 없음)** → `invalidateQueries`는 stale 표시만 하고 refetch하지 않는다.
4. 폼이 `router.push('/ecommerce/sellers')` → 목록 페이지는 `force-dynamic`이라 **새 셀러 포함 신선한 SSR 시드(A,B,C,D)** 를 내려준다.
5. 그러나 SellersScreen이 다시 mount될 때 `useSellers`가 받은 신선한 시드(`initialData`)는 **무시된다** — React Query는 해당 key에 이미 캐시(1단계의 stale A,B,C)가 있으면 `initialData`를 쓰지 않는다. 게다가 `refetchOnMount: false`라 mount 시 refetch도 안 한다. window-focus/interval refetch도 꺼져 있어 **자동 복구 경로가 없다.**

결과: 등록 후 목록은 등록 전 stale 캐시(A,B,C)를 그대로 표시하고, 새 엔티티는 **하드 리로드(캐시 비움 → 신선한 SSR 시드가 `initialData`로 채워짐)** 전까지 보이지 않는다.

동일 버그 클래스는 "별도 페이지에서 변경 후 목록으로 redirect"하는 모든 ecommerce-ops 흐름에 존재한다:

- **sellers** — register (`/new` → `/ecommerce/sellers`) — 보고된 케이스
- **products** — create (`/new`), update/delete (`/[id]`) → `/ecommerce/products`
- **promotions** — create (`/new`), update/delete (`/[id]`) → `/ecommerce/promotions`
- **notifications/templates** — create/update (`/new`, `/[id]`) → `/ecommerce/notifications/templates`

**orders/shippings는 제외**: 상태 변경이 목록 화면 위 다이얼로그(인라인, 목록이 active observer)에서 일어나므로 `invalidateQueries`가 즉시 refetch한다 → 정상. 이들을 `removeQueries`로 바꾸면 오히려 seamless background refetch가 깨지므로 건드리지 않는다.

## Scope

`projects/platform-console/apps/console-web/src/features/ecommerce-ops/hooks/` 내:

- `use-ecommerce-sellers.ts` — list 무효화를 `removeQueries`로
- `use-ecommerce-products.ts` — `invalidate()` 헬퍼의 **list** 라인만 `removeQueries`로 (detail은 `invalidateQueries` 유지)
- `use-ecommerce-promotions.ts` — 동일
- `use-ecommerce-notifications.ts` — 동일

각 경우 **list 키는 `removeQueries`**(캐시를 비워 목적지 force-dynamic 페이지의 신선한 SSR 시드가 `initialData`로 다시 채워지게 함), **detail 키는 `invalidateQueries` 유지**(detail 페이지는 변경 시 active라 seamless refetch가 맞음).

out of scope: orders/shippings 훅, 백엔드, seeded 쿼리의 `refetchOnMount`/`staleTime` 정책(설계 의도 유지).

② **promotion 에러코드 매핑** — `projects/platform-console/apps/console-web/src/shared/api/errors.ts` `MESSAGES` 맵에 promotion-service producer 코드 추가:

- `INVALID_PROMOTION_REQUEST`(400 — endDate≤startDate / PERCENTAGE>100 / Instant 파싱실패 cross-field 가드) → 기간·할인값 둘 다 짚어주는 actionable 메시지
- `PROMOTION_NOT_FOUND`(404), `PROMOTION_ALREADY_ENDED`·`PROMOTION_HAS_ISSUED_COUPONS`·`PROMOTION_NOT_ACTIVE`·`COUPON_LIMIT_EXCEEDED`(422 상태가드)

매핑 코드는 **promotion-service `GlobalExceptionHandler`가 실제 방출하는 것만**(계약서 추정 아님). `VALIDATION_ERROR`/`ACCESS_DENIED`는 이미 매핑됨. 쿠폰 사용/복원 코드(`COUPON_ALREADY_USED`/`COUPON_NOT_OWNED`/`COUPON_EXPIRED`/`COUPON_RESTORE_NOT_ALLOWED`/`COUPON_NOT_FOUND`)는 web-store(고객) 경로라 콘솔 미도달 → 제외. `PromotionForm`은 이미 `messageForCode(code, '저장하지 못했습니다.')`를 쓰므로 컴포넌트 변경 불필요.

## Acceptance Criteria

- AC-1: 셀러 등록 성공 후 `/ecommerce/sellers`로 돌아오면 **하드 리로드 없이** 새 셀러가 목록에 보인다.
- AC-2: products/promotions/notification-templates의 생성/수정/삭제 후 목록 복귀 시 동일하게 즉시 반영된다.
- AC-3: orders/shippings의 인라인 상태 변경은 회귀 없이 기존대로 즉시 반영된다(다이얼로그 닫힘 후 목록 갱신).
- AC-4: 회귀 테스트 — 4개 mutation 훅에 대해 "성공 시 list 캐시가 비워진다(removeQueries)"를 단언하는 vitest 단위 테스트 추가. detail 무효화는 보존됨을 확인.
- AC-6: promotion producer 코드(`INVALID_PROMOTION_REQUEST` + 422 상태가드 5종)가 `messageForCode`에서 일반 폴백("저장하지 못했습니다.")이 아닌 actionable 메시지로 매핑됨을 단언하는 vitest 테스트 추가(`error-messages-promotion.test.ts`). 미매핑 코드는 폴백 유지 회귀 확인.
- AC-5: `pnpm lint` + `tsc` + `vitest` 3종 GREEN (console-web). (web-store 무관)

## Related Specs

- ADR-MONO-031 § 2.4.10 (ecommerce 운영 표면 흡수)
- `specs/contracts/console-integration-contract.md` § 2.4.10.x

## Related Contracts

없음(클라이언트 캐시 동작만 변경, 와이어/계약 무변).

## Edge Cases

- 다중 페이지 캐시(page 0/1/2)도 `removeQueries([KEY,'list'])` prefix로 모두 제거 → 등록 후 어느 페이지로 가도 신선.
- detail 페이지에서 update 시: list 제거(inactive, 무해) + detail invalidate(active → refetch). 복귀 시 시드 신선.

## Failure Scenarios

- removeQueries를 detail에도 적용하면 detail 페이지가 update 직후 잠깐 loading flash → detail은 `invalidateQueries` 유지로 회피.
- orders/shippings까지 바꾸면 인라인 변경 시 목록 loading flash 회귀 → 범위에서 제외.
