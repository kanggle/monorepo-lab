# TASK-PC-FE-243 — flat-envelope gateway 호출 코어 **straggler 통합** (erp/finance/ledger)

**Status:** backlog — candidate (2026-07-18 리팩토링 스윕 발굴, 실증 검증됨)
**Area:** platform-console / console-web · `features/{erp-ops,finance-ops,ledger-ops}/api/*` + `shared/api/`
**Type:** `TASK-PC-FE` (frontend refactor, 원칙상 행동 불변 — 단 아래 §divergence 판단 1건은 관찰가능 변경)
**Confidence:** HIGH (5개 파일 + 이미 통합된 공용 코어 2개 정독, divergence 코드 실증)

## 발굴 근거 (straggler sibling parity)

"hardened HTTP 호출 스캐폴드"(토큰 resolve → 헤더 조립 → `AbortController` 타임아웃 → FLAT 에러 봉투 `{code,message,details?,timestamp}` 파싱 → `401`=세션 전체 / `403`=inline / `503`={Domain}Unavailable / timeout·network=Unavailable 사상 → `finally clearTimeout`)가 **손복사 5벌**로 존재:

- `features/erp-ops/api/erp-client.ts` — `parseErpError`/`callErp`
- `features/erp-ops/api/approval-call.ts` — `parseApprovalError`/`callApproval`
- `features/erp-ops/api/delegation-api.ts` — `parseDelegationError`/`callDelegation`
- `features/finance-ops/api/finance-api.ts` — `parseFinanceError`/`callFinance`
- `features/ledger-ops/api/ledger-client.ts` — `parseLedgerError`/`callLedger`

**같은 결함 클래스가 형제에게는 이미 고쳐졌다** — `shared/api/wms-gateway.ts`(TASK-PC-FE-192, 2벌→1) · `shared/api/scm-gateway.ts`(TASK-PC-FE-189, 3벌→1)는 명시적 dedup 추출본이고, 각 doc-comment 가 *"previously each carried a near-verbatim copy of the SAME hardened call scaffold. This is that scaffold, extracted ONCE."* 라고 적는다. `iam-gateway.ts`(PC-FE-208), `ecommerce-gateway.ts` 도 동일 패턴 코어. **erp(3)·finance(1)·ledger(1)만 미이관 straggler.**

## 🔴 이미 발생한 divergence (검증 완료 — 이 티켓이 phantom 이 아닌 증거)

`shared/api/wms-gateway.ts` L161–168 은 non-GET 요청에 `idempotencyKey` 가 없으면 **client-side 로 즉시** `throw new ApiError(400, 'VALIDATION_ERROR', ...)` 한다(fail-fast 가드). erp 3벌(`erp-client.ts:235`·`approval-call.ts`·`delegation-api.ts`)은 이 가드가 **없다** — `if (opts.idempotencyKey !== undefined)` 로 **있을 때만** 헤더를 실어, mutation 에 키가 누락돼도 조용히 전송하고 producer 의 서버측 `400/409` 에 의존한다. **한 가족만 가드를 받고 셋은 못 받았다 — 어느 task 가 코어를 추출했느냐의 우연.**

## Proposed direction (backlog→ready 승격 시 확정)

`scm-gateway.ts` 의 `parseScmError`/`callScmGateway`(FLAT 봉투 + 401/403/503/timeout/network 택소노미를 이미 구현, scm 전용 429 분기만 제외)를 `shared/api/flat-envelope-gateway.ts` 로 일반화하고 `Profile`(log prefix, unavailable-error factory, messages)로 매개변수화 — `WmsGatewayProfile`/`ScmGatewayProfile` 가 이미 두 번 입증한 패턴. 5개 파일은 profile 을 공급하는 thin wrapper 로 축소(`wms-client.ts`/`outbound-client.ts`/`scm-client.ts` 가 오늘 하는 방식 그대로).

## ⚠️ 착수 전 분리 판단 (관찰가능 행동 변경)

idempotency-key fail-fast 가드를 erp/finance 에도 **부여할지는 별개 결정**이다 — 부여하면 client 조기 400(관찰가능)이 되어 순수 추출이 아니게 된다. **순수 dedup(행동 불변) 을 코어로 하고, 가드 부여 여부는 도메인 소유자 판단으로 분리**할 것. finance/ledger 봉투가 정확히 FLAT 인지(429 등 도메인 분기 유무) 는 승격 시 각 producer 계약으로 재확인.

## backlog → ready 게이트 (INDEX 규칙)

- [ ] 5개 파일의 봉투 shape·상태코드 택소노미가 scm 코어와 100% 일치하는지 파일별 재확인(429·도메인 특수 분기 목록화).
- [ ] idempotency 가드 부여/미부여 결정(관찰가능 변경 → 별도 AC 또는 out-of-scope).
- [ ] Related Specs/Contracts: 각 도메인 gateway 계약 + `console-integration-contract.md` 의 에러 택소노미 절.
- [ ] AC: 통합 후 vitest 회귀 0(현 테스트 무수정 목표), 행동 불변 단언.

## Reference

- 선례: TASK-PC-FE-189 (scm 3→1), TASK-PC-FE-192 (wms 2→1), TASK-PC-FE-208 (iam 5→1).
- 발굴: 2026-07-18 콘솔 리팩토링 발굴 스윕(중복 스캔).
