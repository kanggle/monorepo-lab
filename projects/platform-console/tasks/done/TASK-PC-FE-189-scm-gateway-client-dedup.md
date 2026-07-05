# TASK-PC-FE-189 — SCM 게이트웨이 client fetch scaffold 공통화 (개요·보충·설정 dedup)

**Status:** done
**Area:** platform-console / console-web · **Refactor:** behavior-preserving client dedup
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (behavior-preservation 정밀 요구 — 3-client 계약 보존)

---

## Goal

SCM 콘솔 3화면(개요=`scm-ops` · 보충=`scm-replenishment` · 설정=`scm-config`)의 서버사이드 client가 각자 복붙한 **scm 게이트웨이 fetch scaffold**를 shared 프리미티브 하나로 통합한다. 현재 3곳에 거의 verbatim 중복:

- `scm-ops/api/scm-client.ts` → `callScm<T>` + `parseScmError` + `parseRetryAfter` (GET-only, `{raw,res}` 반환, `ScmUnavailableError`, `scm_*` 로그)
- `scm-replenishment/api/demand-planning-api.ts` → `callDemandPlanning<T>` (method+body, `ScmReplenishmentUnavailableError`, `scm_replenishment_*` 로그)
- `scm-config/api/demand-planning-seed-api.ts` → `callSeed<T>` (method+body, 404→`NOT_FOUND` sentinel, `scm_config_*` 로그)

셋 다 동일한 뼈대: 도메인-facing 토큰(`getDomainFacingToken`, **never** `getOperatorToken`) + `X-Request-Id`, **no** `X-Tenant-Id`, `${SCM_GATEWAY_BASE_URL}${path}` fetch(AbortController `SCM_TIMEOUT_MS` 타임아웃, `cache:'no-store'`), 429 `Retry-After` 1회 bounded backoff(cap 5s), 401→`ApiError`, 403→`ApiError`, 503→Unavailable, `!ok`→`ApiError`, catch→timeout/network Unavailable, scm FLAT 에러 엔벨로프 파싱.

## Scope

**신규 shared 프리미티브** `shared/api/scm-gateway.ts`:
- `MAX_SCM_RETRY_AFTER_SECONDS` + `parseScmRetryAfter(res)` + `parseScmError(res, failMessage)`
- `callScmGateway<T>(req, parse, profile): Promise<{ raw: T; res: Response }>` — 공통 흐름. `profile`로 (a) 로그 이벤트 prefix, (b) `ScmUnavailableError` vs `ScmReplenishmentUnavailableError` 팩토리, (c) degrade/timeout/network 메시지, (d) 선택적 404-sentinel hook(config seed lookup 전용)을 파라미터화. **method 기본 GET**, `body` 있으면 `Content-Type` 부착.

**3개 client를 얇은 wrapper로 재작성**(공개 엔드포인트 함수 시그니처·반환·throw·헤더·fetch 호출 전부 불변):
- `scm-client.ts`: `callScm` = `callScmGateway` (profile: `scm` prefix, `ScmUnavailableError`, GET-only). `readCacheHeader`·`pageParams` 유지. `{raw,res}` 반환 유지(X-Cache).
- `demand-planning-api.ts`: `callDemandPlanning` = `(await callScmGateway(...)).raw` (profile: `scm_replenishment`).
- `demand-planning-seed-api.ts`: `callSeed` = `callScmGateway` + 404 hook(`notFoundIsEmpty`→`NOT_FOUND`) (profile: `scm_config`).

**Out of scope:** 엔드포인트 로직·producer·contract·proxy 라우트·UI 무변경. 컴포넌트 분할은 후속 TASK-PC-FE-190. 로그 이벤트명은 profile prefix로 **동일하게 보존**(scm_ok / scm_replenishment_ok / scm_config_ok 등).

## Acceptance Criteria
- **AC-1** 3개 client의 공개 엔드포인트 함수(scm-api 6개 · demand-planning 4개 · seed 4개)의 fetch 호출(URL·method·headers·body·cache·signal)·반환·throw가 리팩토링 전과 **byte-동일**.
- **AC-2** 크리덴셜 규칙 보존: `getDomainFacingToken` 사용, `getOperatorToken` 절대 미호출, `X-Tenant-Id` 미전송, read-only 화면(scm-ops)은 `Idempotency-Key`/`X-Operator-Reason`/`Content-Type` 미부착.
- **AC-3** 저항성 보존: 429 1회 bounded backoff(fetch 정확히 2회)·no storm, 401/403→ApiError, 503/timeout/network→해당 Unavailable, scm FLAT 엔벨로프 파싱, tolerant 파싱.
- **AC-4** config 404-as-empty-state(`POLICY_NOT_FOUND`/`MAPPING_NOT_FOUND`→`{found:false}`) 보존, PUT은 sentinel 미노출.
- **AC-5** `tsc --noEmit` + `next lint` + `vitest run`(scm-api·demand-planning-api·demand-planning-seed-api·각 proxy·state 테스트 전부) green. **신규 테스트 불필요**(기존 테스트가 behavior-preservation 계약).

## Edge Cases / Failure Scenarios
- 3 client의 미묘한 차이(반환 shape `{raw,res}` vs `T` vs `T|NOT_FOUND` · error class · 로그 prefix · degrade/timeout 메시지)를 profile로 정확히 분기 — 하나라도 어긋나면 기존 테스트 RED.
- scm-ops는 GET-only·read-only(mutation artifact 부재 테스트) → profile이 body/Content-Type을 강제 부착하지 않아야 함(method 기본 GET, body 미전달).
- shared 프리미티브는 **project-internal**(console-web `shared/`)이지 monorepo `libs/` 아님 — HARDSTOP-03 무관.

## Related
- SoT(불변 계약): `specs/contracts/console-integration-contract.md` § 2.4.5/2.4.6/2.4.6.1/2.4.6.2 (per-domain credential·tenant·read-only·404-empty).
- 기존 테스트(behavior-preservation 계약): `tests/unit/{scm-api,demand-planning-api,demand-planning-seed-api,scm-proxy,demand-planning-proxy,demand-planning-seed-proxy}.test.ts`.
- 후속: TASK-PC-FE-190 (god-file 컴포넌트 분할).
