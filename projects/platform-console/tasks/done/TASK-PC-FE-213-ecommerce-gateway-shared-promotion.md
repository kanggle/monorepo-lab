# TASK-PC-FE-213 — 이커머스 게이트웨이 call-core를 shared/api로 승격 (scm/wms 정렬)

**Status:** done
**Done:** 2026-07-07 · impl PR #2300 squash `e387e9f90` (3-dim verified) — ecommerce-gateway.ts(281) 승격, ecommerce-client.ts 242→101 shim, 8 slice 무접촉, tsc 0·lint 0·ecommerce 152/152.
**Area:** platform-console / console-web · **Refactor:** behavior-preserving client dedup (location + abstraction 정렬)
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (behavior-preservation 정밀 — 8-slice 계약 보존, resilience taxonomy byte-동일)

---

## Goal

SCM(PC-FE-189)·WMS(PC-FE-192)·IAM(PC-FE-208)은 각 도메인의 서버사이드 call-core를 `src/shared/api/<domain>-gateway.ts`로 **승격**하고 feature client는 `Profile`을 주입하는 얇은 wrapper가 됐다. **이커머스만 이 마지막 단계를 안 밟았다** — call-core `callEcommerce`가 아직 `features/ecommerce-ops/api/ecommerce-client.ts`에 feature-내부로 남아 있고, 추상화 형태도 `EcommerceCallLabel`(문자열/label만 주입, `EcommerceUnavailableError`는 코어에 하드코딩)로 SCM/WMS의 `*GatewayProfile`(`makeUnavailable`/`isUnavailable` 팩토리 주입) 패턴과 다르다.

이 core는 `scm-gateway.ts`와 **near-identical**이다(둘 다 `getDomainFacingToken()`·FLAT 엔벨로프 `{code,message,timestamp}`·**NO** `X-Tenant-Id`(JWT tenant claim)·**NO** `Idempotency-Key`·AbortController 타임아웃·401/403→`ApiError`·503/timeout/network→section-degrade). 3 도메인 완전 정렬을 위해 이커머스 core를 shared로 승격하고 profile 패턴에 맞춘다.

## Scope

**신규 shared 프리미티브** `shared/api/ecommerce-gateway.ts`:
- `parseEcommerceError(res, failLabel)`(FLAT 엔벨로프) + `callEcommerceGateway<T>(req, parse, profile): Promise<T>`. `scm-gateway.ts`의 `ScmGatewayProfile` 형태를 미러링한 `EcommerceGatewayProfile`로 파라미터화:
  - `logPrefix` — 로그 이벤트 prefix. **이커머스의 event-infix 뉘앙스 보존**: products 슬라이스는 EMPTY infix(`ecommerce_ok`), 나머지는 `ecommerce_<event>_ok`. profile `logPrefix`가 이 규칙(빈 문자열 처리)을 재현해야 함.
  - `requestFailedLabel`(synthetic default 라벨), `messages{degraded,timeout,network}` — 슬라이스별 문자열 verbatim 보존.
  - `makeUnavailable(reason,code,message)` + `isUnavailable(err)` — 코어에 하드코딩된 `EcommerceUnavailableError`를 profile 팩토리로 주입(SCM/WMS와 동형). 단일 도메인이라 팩토리 구현은 하나지만, **추상화 형태를 SCM/WMS와 일치**시키는 것이 목적.
- `EcommerceGatewayRequest`: `method`·`base`(admin/public subtree, **caller가 해석 — 유지**)·`path`·`body`. 이커머스는 per-call `base`가 SCM(env 단일 base)과 다른 유일 지점 → request에 유지.
- **429 backoff / 404-sentinel 없음**: 이커머스 core는 둘 다 없음 → 추가하지 말 것(SCM 고유 기능; over-engineering 금지).

**8개 slice를 shared core 소비로 재작선**(공개 함수 시그니처·반환·throw·헤더·fetch 호출 불변): `products/orders/users/promotions/shippings/notifications/sellers/images-api.ts`. 각 slice가 이미 `callEcommerce(opts, parse, label)`를 호출 → import 경로를 `@/shared/api/ecommerce-gateway`로 바꾸고 `label`→`profile` 형태 매핑. 슬라이스별 label 문자열(event infix·message)은 profile로 그대로 이관.

**기존 `features/ecommerce-ops/api/ecommerce-client.ts`**: core 제거 후 (a) 완전 삭제하고 slice가 shared를 직접 import, 또는 (b) shared re-export 얇은 shim으로 축소(SCM `scm-client.ts`가 `callScm`+`SCM_PROFILE` wrapper를 두는 것과 정렬 — 이커머스도 `ECOMMERCE_PROFILE` 정의처 하나 두는 편이 slice 중복 줄임). 구현자가 SCM 선례(`scm-ops/api/scm-client.ts`)에 맞춰 판단.

**Out of scope:** 엔드포인트 로직·producer·contract·proxy 라우트·UI·컴포넌트·상태 매퍼 무변경. `overview-api`(fan-out 소비자)는 dedup 대상 아님(SCM/WMS/IAM overview와 동일). `EcommerceUnavailableError` 클래스 자체(`shared/api/errors.ts`)는 이동/개명 금지.

## Acceptance Criteria
- **AC-1** 8개 slice 공개 함수의 fetch 호출(URL=`base+path`·method·headers·body·cache·signal)·반환·throw가 리팩토링 전과 byte-동일.
- **AC-2** 크리덴셜/테넌트 규칙 보존: `getDomainFacingToken()` 사용·`getOperatorToken`/`getAccessToken` 절대 미호출·`X-Tenant-Id` 미부착·`Idempotency-Key` 미부착·`Content-Type`은 body 있을 때만.
- **AC-3** 저항성 보존: 401/403→`ApiError`(각 별도 분기)·503→`EcommerceUnavailableError`(circuit_open|downstream)·`!ok`→`ApiError`·timeout→`EcommerceUnavailableError('timeout')`·network→`EcommerceUnavailableError('downstream')`·FLAT 엔벨로프 파싱.
- **AC-4** 로그 이벤트명 byte-동일: products=`ecommerce_ok`/`ecommerce_unauthorized`/… (bare), 나머지=`ecommerce_<event>_*`.
- **AC-5** 신규 core 파일 위치 = `src/shared/api/ecommerce-gateway.ts`(SCM/WMS와 동일 디렉터리·명명 규칙). profile 형태가 `ScmGatewayProfile`와 구조적으로 일치(`makeUnavailable`/`isUnavailable` 포함).
- **AC-6** `tsc --noEmit` + `pnpm lint` + `vitest`(products-*/orders-*/users-*/promotions-*/shippings-*/notifications-*/sellers-*/images-* slice·proxy·state·화면) green. 신규 테스트 불필요(기존 테스트=behavior-preservation 계약).

## Edge Cases / Failure Scenarios
- **event-infix EMPTY 처리**가 최대 함정: products 슬라이스만 bare `ecommerce_ok`. profile logPrefix 구현이 빈 infix를 `ecommerce_${suffix}`로, 비어있지 않으면 `ecommerce_${event}_${suffix}`로 분기해야 함(현 `eventName()` 헬퍼 로직 보존).
- per-call `base`(admin vs public subtree)는 caller가 해석 — SCM처럼 env 단일 base로 하드코딩하면 회귀. request 필드로 유지.
- shared 프리미티브는 project-internal(console-web `shared/`) — HARDSTOP-03 무관.

## Related
- 미러: TASK-PC-FE-189 (scm-gateway), TASK-PC-FE-192 (wms-gateway), TASK-PC-FE-208 (iam-gateway).
- 원본 dedup: TASK-PC-FE-094 (ecommerce feature-내부 call-core 추출 — 이 task가 그 core를 shared로 승격).
- 기존 테스트(계약): `tests/unit/{products,orders,users,promotions,shippings,notifications,sellers,images}-api.test.ts`.
- 파일 disjoint 후속(병렬 가능하나 ecommerce/api 접촉 순서 주의): PC-FE-216(PromotionForm 분할, components), PC-FE-217(네이밍/레이아웃 정규화 — ecommerce api barrel·hooks 이동).
