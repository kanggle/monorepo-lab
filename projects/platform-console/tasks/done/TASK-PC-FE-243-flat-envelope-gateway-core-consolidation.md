# TASK-PC-FE-243 — flat-envelope gateway 호출 코어 **straggler 통합** (erp/finance/ledger)

**Status:** done
**Area:** platform-console / console-web · `features/{erp-ops,finance-ops,ledger-ops}/api/*` + `shared/api/`
**Type:** `TASK-PC-FE` (frontend refactor — **행동 불변**, 기존 테스트 무수정 통과)
**Lifecycle:** backlog(2026-07-18 발굴) → 인라인 spec + 구현 1-pass → review

---

# Goal

"hardened FLAT-envelope HTTP 호출 스캐폴드"(토큰 resolve → 헤더 조립 → `AbortController` 타임아웃 → FLAT 봉투 `{code,message,details?,timestamp}` 파싱 → 401=세션 / 403=inline / 503=section-degrade / !ok=inline / timeout·network=degrade 사상)를 **공용 코어 1개**로 통합. wms(PC-FE-192)·scm(PC-FE-189)이 이미 각 가족에 한 dedup 을 미이관 straggler(erp ×3 · finance · ledger)로 확장한다. **행동 불변** — 각 클라이언트의 헤더·env·에러 택소노미·로그 이벤트명·429/404 정책·반환 shape 를 그대로 보존.

# Scope

## In (구현 완료)

- **신규** `shared/api/flat-envelope-gateway.ts` — `callFlatEnvelopeGateway<T>(req, parse, profile)` + `FlatEnvelopeGatewayProfile`. scm-gateway 의 로직을 일반화.
- `shared/api/scm-gateway.ts` → 공용 코어 위 thin shim(`parseScmError`/`callScmGateway`/`ScmGatewayProfile`/`MAX_SCM_RETRY_AFTER_SECONDS` 재수출 — scm 소비자 import 경로 무변경).
- `features/erp-ops/api/{erp-client,approval-call,delegation-api}.ts` → `ERP_PROFILE`/`APPROVAL_PROFILE`/`DELEGATION_PROFILE` thin wrapper(approval 의 query helper·`parseApprovalRequest` 보존).
- `features/finance-ops/api/finance-api.ts` → `FINANCE_PROFILE`(exports `getAccount`/`getBalances`/`listTransactions` 무변경).
- `features/ledger-ops/api/ledger-client.ts` → `LEDGER_PROFILE`.

## Out (의도적 보존 — 절대 변경 금지)

- `shared/api/wms-gateway.ts` + wms 클라이언트 — **NESTED 봉투** `{error:{code}}` 라 FLAT 가족 아님. 무접촉.
- 모든 `*.test.ts` — 무수정(행동보존 증거).
- **idempotency fail-fast 가드 부여** — 관찰가능 변경이라 별개 판단. 코어에 `requireIdempotencyKeyOnMutation` 플래그로만 존재, **erp/finance/ledger 기본 OFF**(현 "있을 때만 헤더" 행동 그대로).

# `FlatEnvelopeGatewayProfile` 설계

per-domain 변이만 담는다: `logPrefix`(로그 이벤트명 보존) · `requestFailedLabel` · `resolveDefaults(env)→{baseUrl,timeoutMs}`(도메인별 env 선택자) · `makeUnavailable`/`isUnavailable` · `messages{degraded,timeout,network}` · `rateLimit?`(429 정책 — scm 만 공급, erp/finance/ledger 는 없어서 429 가 plain `ApiError` 로 falls through = 현행) · `notFoundSentinel?`(scm-config 404-as-empty) · `requireIdempotencyKeyOnMutation?`(default OFF). request 에 `logPath?`(민감 route 의 `{id}` placeholder 로그 보존) 추가.

# Acceptance Criteria

- [x] 5개 straggler + scm-gateway 가 공용 코어로 통합, ~1045줄 중복 제거(442 ins / 1487 del + 395줄 코어).
- [x] 각 클라이언트 행동 불변(헤더·env·택소노미·로그명·429/404·반환 shape 보존).
- [x] idempotency 가드 default OFF (erp/finance/ledger 현행 보존).
- [x] wms-gateway·wms 클라이언트·모든 `*.test.ts` 무접촉.
- [x] 검증: `tsc --noEmit` 0 · `next lint` clean · `vitest run` **270 files / 2804 tests GREEN, 테스트 파일 0개 수정**.

# Related Specs / Contracts

- `console-integration-contract.md` § 2.4.x (per-domain credential) · § 2.5 (resilience 택소노미) — 봉투·상태코드 사상 보존.
- 선례: TASK-PC-FE-189 (scm 3→1) · TASK-PC-FE-192 (wms 2→1) · TASK-PC-FE-208 (iam 5→1).

# Edge Cases

- scm 소비자 3개(`scm-client`·demand-planning 2개)는 `ScmGatewayProfile`(작은 subset)을 선언 — `callScmGateway` 가 내부에서 scm `resolveDefaults`+429 `rateLimit` 을 병합해 코어에 위임. 세 파일 무변경 유지(코어 profile 전체 타입으로 넓히지 않음).
- `parseScmError` shim 반환에 `details` 키가 추가되나 어떤 호출 경로도 `details` 를 읽지 않고 shape 를 단언하는 테스트 0 → 행동 불변.

# Failure Scenarios (검증됨)

- 과추상화로 도메인 택소노미 어긋남 → 각 클라이언트 unit test 가 정확한 사상을 pin → 2804 GREEN 으로 배제.
- wms NESTED 봉투를 FLAT 코어로 잘못 흡수 → wms 무접촉으로 원천 차단.

# Review Notes

- 발굴: 2026-07-18 콘솔 리팩토링 스윕. divergence 실증(wms 가드 vs erp 미강제)이 phantom 아님을 입증.
- 구현: frontend-engineer(Opus) 위임 → 오케스트레이터 diff 검증(파일 범위·테스트 무수정·가드 default-off) + vitest 독립 재실행.
- **후속 후보**: idempotency 가드 parity(erp/finance/ledger 에 fail-fast 부여) = 관찰가능 변경, 도메인 소유자 판단 시 별도 task.
