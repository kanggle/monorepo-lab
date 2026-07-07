# TASK-PC-FE-208 — IAM 게이트웨이 client fetch scaffold 공통화 (accounts·audit·operators·partnerships·subscriptions dedup)

**Status:** done
**Area:** platform-console / console-web · **Refactor:** behavior-preserving client dedup
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (behavior-preservation 정밀 — 5-client 계약 보존, per-endpoint 헤더 매트릭스 위험)

---

## Goal

SCM(PC-FE-189)·WMS(PC-FE-192)와 동일한 접근으로, IAM(운영자/신원 평면) 콘솔의 서버사이드 client들이 각자 복붙한 **IAM `/api/admin/**` 게이트웨이 fetch scaffold**를 shared 프리미티브 하나로 통합한다. 현재 5곳(+accounts 내부 1곳)에 near-verbatim 중복:

- `accounts/api/accounts-api.ts` → `callGapAdmin<T>` (+ `exportAccount`의 **파일 내 2번째** raw-fetch 복사, 비-JSON blob export)
- `audit/api/audit-api.ts` → 감사 로그 read scaffold
- `operators/api/operators-client.ts` → `callGapOperators<T>` + `OPERATORS_PREFIX`(이미 operators-{crud,self,assignments}-api가 소비하는 feature-내부 코어)
- `partnerships/api/partnerships-client.ts` → `callPartnerships<T>`
- `subscriptions/api/subscriptions-client.ts` → 구독 mutation scaffold

5곳 모두 동일 뼈대: **EXCHANGED operator 토큰**(`getOperatorToken()`, **never** `getAccessToken()`)·`getActiveTenant()`→`X-Tenant-Id`(미선택 시 **400 NO_ACTIVE_TENANT** — 빈 헤더 금지)·per-endpoint `X-Operator-Reason`(= `encodeURIComponent(reason)`, mutation·빈 reason은 fetch 전 fail-safe)·**선택적** `Idempotency-Key`(엔드포인트별)·AbortController 타임아웃·`401`/`403`/`!ok`→`ApiError`·`503`/timeout/network→해당 `*UnavailableError`·GAP **FLAT** 엔벨로프(`{code,message,timestamp}`) 파싱·구조적 서버-only 로깅(토큰/PII 미로깅).

## Scope

**신규 shared 프리미티브** `shared/api/iam-gateway.ts`(또는 `admin-gateway.ts`):
- `parseAdminError(res, failLabel)`(FLAT 엔벨로프) + `callAdminGateway<T>(req, parse, profile): Promise<T>`. `profile`(`AdminGatewayProfile`)로 (a) 로그 prefix, (b) prefix/base(전부 `IAM_ADMIN_API_BASE` + 서로 다른 `/api/admin/*` prefix), (c) `*Unavailable` 팩토리+instanceof(Accounts/Audit/Operators/Partnerships/Subscriptions 5종), (d) timeout env, (e) **401/403 처리 뉘앙스**(accounts=`401||403` 병합 분기 / operators·partnerships·subscriptions·audit=`401` 별도[whole-session re-login] + `403` 별도[inline])를 분기. per-endpoint reason/idempotencyKey는 공통 `CallOptions`(이미 각 client 동형)로 수용.

**5개 client를 얇은 wrapper로 재작성**(공개 엔드포인트 함수 시그니처·반환·throw·헤더·per-endpoint 매트릭스·fetch 호출 불변):
- `accounts-api` `callGapAdmin` = `callAdminGateway(..., ACCOUNTS_PROFILE)`; `exportAccount`의 비-JSON blob 경로도 gateway의 raw/blob variant(또는 문서화된 예외)로 흡수해 2번째 복사 제거.
- `operators-client` `callGapOperators` = wrapper(operators-{crud,self,assignments}-api 소비 경로 불변, barrel 미노출 유지). `audit-api`·`partnerships-client`·`subscriptions-client` 동일.

**Out of scope:** 엔드포인트 로직·producer·contract·proxy 라우트·UI·컴포넌트·상태 매퍼 무변경. `dashboards/overview-api`·`operator-overview/operator-overview-api`는 **fan-out 소비자 / console-bff BFF 경로**(각 client의 `*UnavailableError`를 재사용하거나 `X-Operator-Token` proxy)라 scaffold 중복 아님 → dedup 대상 아님(ecommerce overview·wms overview와 동일). 로그 이벤트명은 profile prefix로 동일 보존.

## Acceptance Criteria
- **AC-1** 5개 client 공개 엔드포인트 함수의 fetch 호출(URL·method·headers·body·cache·signal)·반환·throw가 리팩토링 전과 byte-동일.
- **AC-2** 크리덴셜/테넌트 규칙 보존: `getOperatorToken` 사용·`getAccessToken` 절대 미호출·`X-Tenant-Id`=activeTenant(미선택→400 NO_ACTIVE_TENANT)·per-endpoint `X-Operator-Reason`(encodeURIComponent, 빈 reason fail-safe)·`Idempotency-Key`는 producer 매트릭스대로(예: operators create만, roles/status는 금지).
- **AC-3** 저항성 보존: 401/403→ApiError(각 client의 병합 vs 분리 뉘앙스 그대로)·503/timeout/network→해당 `*UnavailableError`·FLAT 엔벨로프 파싱.
- **AC-4** `accounts` export의 비-JSON blob 경로 동작(파일 스트림/헤더) 보존.
- **AC-5** `tsc --noEmit` + `next lint` + `vitest`(accounts-*/audit-*/operators-*/partnerships-*/subscriptions-* client·proxy·state·화면) green. 신규 테스트 불필요(기존 테스트=behavior-preservation 계약).

## Edge Cases / Failure Scenarios
- **per-endpoint 헤더 매트릭스가 최대 위험**(operators-client 주석 § 2.4.3): create=reason+key / roles·status=reason-only / me/password=token-only / read=none — 하나라도 어긋나면 privilege-escalation 표면 회귀. profile이 아니라 **공통 CallOptions per-call**로 정확 전달.
- accounts의 `401||403` 병합 vs 나머지의 `401`/`403` 분리를 profile 플래그로 정확 분기 — 병합/분리 뒤바뀌면 재로그인 루프 or inline 오분류.
- shared 프리미티브는 project-internal(console-web `shared/`) — HARDSTOP-03 무관.

## Related
- 미러: TASK-PC-FE-189 (scm-gateway), TASK-PC-FE-192 (wms-gateway).
- 파일 disjoint 후속(병렬 가능): PC-FE-209~212 (IAM god-file 분할, components vs api).
- 기존 테스트(계약): `tests/unit/{accounts-*,audit-*,operators-*,partnerships-*,subscriptions-*}.test.ts(x)`.
