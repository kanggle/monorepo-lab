# TASK-PC-FE-192 — WMS 게이트웨이 client fetch scaffold 공통화 (wms-ops · wms-outbound-ops dedup)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving client dedup
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (behavior-preservation 정밀 — 2-client 계약 보존)

---

## Goal

SCM(PC-FE-189)과 동일한 접근으로, WMS 콘솔의 두 서버사이드 client가 각자 복붙한 **wms 게이트웨이 fetch scaffold**를 shared 프리미티브 하나로 통합한다. 현재 2곳에 near-verbatim 중복:

- `wms-ops/api/wms-client.ts` → `callWmsAdmin<T>` + `parseWmsError` + `readLagHeader` (admin read-model `WMS_ADMIN_BASE_URL`·`WMS_TIMEOUT_MS`, `{data,lagSeconds}` 반환[`X-Read-Model-Lag-Seconds` surface], `WmsUnavailableError`, `wms_*` 로그)
- `wms-outbound-ops/api/outbound-core-api.ts` → `callOutbound<T>` + `parseOutboundError` (outbound `WMS_OUTBOUND_BASE_URL`·`WMS_OUTBOUND_TIMEOUT_MS` + per-call baseUrl/timeout override, `T` 반환, `WmsOutboundUnavailableError`, `wms_outbound_*` 로그)

셋 다 동일 뼈대: 도메인-facing 토큰(`getDomainFacingToken`, **never** `getOperatorToken`)·**no** `X-Tenant-Id`·non-GET→`Idempotency-Key` 필수(+`Content-Type` when body)·AbortController 타임아웃·401/403/503/!ok taxonomy·wms **NESTED** 엔벨로프(`{error:{code}}`) 파싱·catch timeout/network. (scm과 달리 **429 backoff 없음**.)

## Scope

**신규 shared 프리미티브** `shared/api/wms-gateway.ts`:
- `parseWmsError(res, failLabel)`(NESTED 엔벨로프) + `readWmsLagHeader(res)` + `callWmsGateway<T>(req, parse, profile): Promise<{ data: T; lagSeconds: number|null }>`. `profile`(`WmsGatewayProfile`)로 (a) 로그 prefix, (b) `WmsUnavailable` vs `WmsOutboundUnavailable` 팩토리+instanceof, (c) degrade/timeout/network 메시지, (d) `resolveDefaults(env)→{baseUrl,timeoutMs}`(env 접근은 server-only 코어 내부에서만 — feature client는 `getServerEnv` 미접촉)를 분기. non-GET→idempotencyKey 필수는 공통 규칙(두 client 모두 mutation만 non-GET).

**2개 client를 얇은 wrapper로 재작성**(공개 엔드포인트 함수 시그니처·반환·throw·헤더·fetch 호출 불변):
- `wms-client.ts`: `callWmsAdmin` = `callWmsGateway(..., WMS_ADMIN_PROFILE)` (반환 `{data,lagSeconds}` 그대로). `pageParams`·`CallOptions`·`WmsResult` 유지.
- `outbound-core-api.ts`: `callOutbound` = `(await callWmsGateway(..., WMS_OUTBOUND_PROFILE)).data` (baseUrl/timeout override 전달). `clampSize`·`CallOptions` 유지.

**Out of scope:** 엔드포인트 로직·producer·contract·proxy 라우트·UI·컴포넌트 무변경. E-Commerce는 이미 단일 코어(`ecommerce-client.ts`)로 DRY → client dedup 불필요(별도). 로그 이벤트명은 profile prefix로 동일 보존.

## Acceptance Criteria
- **AC-1** 두 client의 공개 엔드포인트 함수의 fetch 호출(URL·method·headers·body·cache·signal)·반환·throw가 리팩토링 전과 byte-동일.
- **AC-2** 크리덴셜 규칙 보존: `getDomainFacingToken` 사용·`getOperatorToken` 절대 미호출·`X-Tenant-Id` 미전송·reads no mutation artifacts·mutation은 caller `Idempotency-Key` 필수(없으면 400 VALIDATION_ERROR).
- **AC-3** 저항성 보존: 401/403→ApiError·503/timeout/network→해당 Unavailable·wms NESTED 엔벨로프 파싱·`X-Read-Model-Lag-Seconds`가 admin 결과 `lagSeconds`로 surface.
- **AC-4** outbound의 baseUrl/timeout override(TMS-retry가 admin read-model을 `WMS_ADMIN_BASE_URL`로 조회) 경로 보존.
- **AC-5** `tsc --noEmit` + `next lint` + `vitest run`(wms-api·outbound-api·envelope·proxy·state·화면) green. 신규 테스트 불필요(기존 테스트=behavior-preservation 계약).

## Edge Cases / Failure Scenarios
- 2 client의 차이(반환 shape `{data,lagSeconds}` vs `T`·base/timeout override·error class·로그 prefix·default 메시지)를 profile로 정확히 분기 — 하나라도 어긋나면 기존 테스트 RED.
- lag 헤더 read를 shared에서 항상 수행해도 outbound 응답엔 헤더 부재 → `lagSeconds=null`, wrapper가 `.data`만 반환하므로 outbound 관측 동작 불변.
- shared 프리미티브는 project-internal(console-web `shared/`) — HARDSTOP-03 무관.

## Related
- 미러: TASK-PC-FE-189 (scm-gateway client dedup).
- 기존 테스트(계약): `tests/unit/{wms-api,outbound-api,outbound-envelope,wms-proxy,outbound-proxy,*-state}.test.ts`.
- 후속: WMS 컴포넌트 god-file 분할 · E-Commerce 화면 분할.
