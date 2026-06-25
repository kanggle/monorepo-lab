# TASK-PC-FE-133 — 한글/공백 seller_id 셀러 상세에서 "셀러 정보를 일시적으로 불러올 수 없습니다" 가 뜨는 버그 ([id] 이중 인코딩 → firewall 400)

- **Status**: ready
- **Project**: platform-console
- **Service**: console-web
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (라우트 param 디코딩 단일 지점 fix)

## Goal

콘솔 E-Commerce > 셀러(sellers) 목록에서 **공백/비-ASCII 문자를 포함한 seller_id**(예: `셀러 1`, 콘솔 셀러 등록 폼으로 생성됨)의 `상세` 를 누르면 상세 화면이 `ecommerce 셀러 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.` degrade 노트로 떨어지는 버그를 고친다. ASCII seller_id(`default`, `globex-store`, `initech-mart`)의 상세는 정상 — 퍼센트 인코딩이 필요한 id 만 깨진다.

근본 원인 (런타임 진단 2026-06-26):

- 셀러 **목록**(`GET /api/admin/sellers`)은 `200` 으로 정상 렌더된다(`ecommerce_seller_ok` 로그 확인). 깨지는 것은 **상세** 호출뿐이다.
- console-web 로그: `ecommerce_seller_request_error status:400 code:HTTP_400 path:/sellers/%25EC%2585%2580%25EB%259F%25AC%25201`. 경로를 디코딩하면 `%25`→`%` 이므로 한 번 풀면 `%EC%85%80%EB%9F%AC%201`, 한 번 더 풀면 `셀러 1` — 즉 seller_id 가 **이중 URL 인코딩**되어 백엔드로 전송되었다.
- 이중 인코딩의 원인: 상세 라우트 `(console)/ecommerce/sellers/[id]/page.tsx` 가 Next.js 의 `[id]` 동적 세그먼트를 **이미 퍼센트 인코딩된 상태**(`%EC%85%80%EB%9F%AC%201`)로 받아, 그대로 `getSellerDetailSectionState → getSeller(sellerId)` 로 넘긴다. `getSeller()`(`sellers-api.ts`)는 path 안전을 위해 `encodeURIComponent(sellerId)` 로 **한 번 더 인코딩**한다 → `%25EC..`(이중).
- 왜 404 가 아니라 400 인가: 이중 인코딩이 만든 `%25`(인코딩된 `%`)를 Spring Security `StrictHttpFirewall` 가 **인증 전에** 거부한다 → `400`. 게이트웨이 직접 호출로 실증:
  - `…/sellers/%25EC%2585%2580%25EB%259F%25AC%25201` (현재 앱이 보내는 이중) → **400**
  - `…/sellers/%EC%85%80%EB%9F%AC%201` (올바른 단일) → **401**(firewall 통과 → 토큰 있으면 200)
  - `…/sellers/default` (ASCII) → **401**(통과)
- 400 은 `mapDetailResilience`(`section-state.ts`)의 404/403/401 어느 분기에도 안 걸려 **fallback `degraded:true`** 로 매핑된다 → 페이지 waterfall 의 `state.degraded || !state.detail` 에서 degrade 노트가 렌더된다(가짜 "일시적으로 불러올 수 없습니다").

`getSeller()` 의 `encodeURIComponent` 자체는 **raw seller_id 를 받는다는 계약 하에서 올바르다**. 버그는 페이지가 raw 가 아닌 **이미 인코딩된** 세그먼트를 넘긴 데 있다. 따라서 fix 는 페이지 경계에서 세그먼트를 디코딩해 `getSeller()` 가 정확히 한 번만 인코딩하도록 만드는 것이다(consumer 측 렌더 경로 변경; producer 계약 불변).

## Scope

**In scope** (console-web only):

1. `src/app/(console)/ecommerce/sellers/[id]/page.tsx` — `[id]` 세그먼트를 `getSellerDetailSectionState` 에 넘기기 전에 `decodeSellerId()`(신규 모듈 로컬 헬퍼)로 디코딩한다. `decodeURIComponent` 가 malformed 인코딩에 throw 하지 않도록 `try/catch` → 실패 시 raw 세그먼트 fallback.
2. `tests/unit/ecommerce-seller-detail-page.test.tsx` — 신규 회귀 테스트: (a) 인코딩된 비-ASCII 세그먼트(`%EC%85%80%EB%9F%AC%201`) → `getSellerDetailSectionState(true, '셀러 1')` 로 디코딩 전달, (b) `%20` 공백 세그먼트 디코딩, (c) plain ASCII 통과(no-op), (d) malformed(`a%b`) → throw 없이 raw fallback, (e) 디코딩 후 degrade/registryDegraded waterfall 보존.

**Out of scope**:

- 백엔드(product-service `AdminSellerController`) — `GET /api/admin/sellers/{id}` 동작·`StrictHttpFirewall` 정책 불변. 비-ASCII seller_id 자체의 백엔드 유효성(register 시 허용 여부)은 별개 정책 사안.
- 형제 상세 라우트(products/orders/users/promotions/shippings/notifications `[id]`) — 동일하게 `params.id` 를 `encodeURIComponent` 로 재인코딩하지만, 해당 id 는 UUID/ASCII slug 라 퍼센트 인코딩이 발생하지 않아 **이중 인코딩이 실현되지 않는다**. 본 task 는 실제 신고/관측된 셀러 상세에 한정한다. 방어적 일관성 후속은 별도 task 후보.
- 셀러 목록·등록 폼·`SellersScreen`/`SellerDetail` 컴포넌트 — 변경 불필요(목록 링크 `/ecommerce/sellers/${sellerId}` 는 Next `<Link>` 가 올바르게 단일 인코딩한다; 문제는 그 세그먼트를 **다시 읽을 때** 발생).

## Acceptance Criteria

- **AC-1 — 디코딩 후 단일 인코딩.** 인코딩된 `[id]` 세그먼트가 상세 페이지에 도착하면, 페이지는 raw seller_id 로 디코딩한 뒤 `getSellerDetailSectionState` 에 넘긴다 → 하류 `getSeller()` 가 정확히 한 번 인코딩 → 게이트웨이 경로는 단일 인코딩(`%EC..`)이 되어 firewall 400 이 발생하지 않는다.
- **AC-2 — ASCII 보존.** plain ASCII seller_id(`default` 등)는 디코딩이 no-op 이라 기존 동작 그대로 유지된다.
- **AC-3 — malformed 무크래시.** 유효하지 않은 퍼센트 인코딩(예: 단독 `%`)이 와도 페이지는 throw 하지 않고 raw 세그먼트로 폴백한다.
- **AC-4 — waterfall 보존.** registryDegraded / notEligible / forbidden / notFound / degraded 분기 동작은 디코딩 도입 후에도 불변(404→notFound, 비-404→degrade 등).
- **AC-5 — 게이트.** console-web `pnpm lint` + `pnpm tsc --noEmit` + `pnpm vitest run` GREEN(신규 회귀 테스트 포함).

## Related Specs

- `console-integration-contract.md` § 2.4.10 — ecommerce 셀러 operator surface(`AdminSellerController` `/api/admin/sellers`); 상세 read.
- TASK-PC-FE-090 (ADR-MONO-031 § 2.4.10 7th area) — 셀러 운영 표면 흡수(이 상세 라우트의 출처).
- `PROJECT.md` § Out of Scope — "콘솔은 멱등 호출 + 결과 표시만"(consumer 측 렌더 보정).

## Related Contracts

- ecommerce `product-service` `GET /api/admin/sellers/{sellerId}` — 성공 200(SellerDetail), 대상 없음 → 404 `SELLER_NOT_FOUND`(FLAT 봉투). 경로 변수는 단일 퍼센트 인코딩을 기대; `%25`(인코딩된 `%`)는 Spring `StrictHttpFirewall` 가 400 으로 거부.
- console-web `callEcommerce`(`ecommerce-client.ts`) — 비-2xx 4xx 는 `ApiError(status, code, …)` 로 표면화; 400 은 `mapDetailResilience` 의 fallback `degraded:true` 로 매핑(404 만 notFound, 403 만 forbidden).

## Edge Cases

- **공백 포함 ASCII id**(`shop a` → `shop%20a`) — 디코딩 후 `shop a` 로 정상 처리(테스트 b).
- **malformed 인코딩**(`a%b`) — `decodeURIComponent` throw → raw `a%b` 폴백, 크래시 없음(테스트 d). 그 경우 하류 `getSeller` 가 `a%25b` 로 인코딩하나, 이는 존재하지 않는 id 일 뿐 firewall 거부 대상 아님(원래 단일 `%` → `%25` 은 정상 인코딩).
- **registry degraded / not eligible** — 디코딩 이전/이후 모두 상세 read 전에 short-circuit(테스트 e). 디코딩이 이 분기에 영향 없음.
- **형제 상세 라우트** — 본 task 미변경; UUID/ASCII id 라 이중 인코딩 미실현(범위 한정).

## Failure Scenarios

- 디코딩을 `getSeller()` 안에서 `encodeURIComponent(decodeURIComponent(id))` 로 처리하면 raw `%` 를 가진 다른 호출자를 깨뜨릴 수 있음 → 디코딩은 **페이지 경계**(인코딩된 세그먼트가 들어오는 유일 지점)에서만 수행하고 `getSeller()` 의 raw-id 계약은 불변으로 둔다.
- `decodeURIComponent` 의 throw 를 처리하지 않으면 malformed id 에서 페이지가 500 으로 크래시 → `try/catch` + raw fallback 로 핀(AC-3).
- 형제 상세 라우트에 같은 함정이 잠재(현재 UUID 라 미발현) — 비-UUID id 를 쓰는 표면이 추가되면 동일 패턴(경계 디코딩)을 적용하는 후속 task 로 처리.
