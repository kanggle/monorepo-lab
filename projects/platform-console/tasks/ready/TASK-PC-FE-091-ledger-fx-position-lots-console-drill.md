# Task ID

TASK-PC-FE-091

# Title

**platform-console: 외화 포지션 lot drill-in** — 운영자가 ledger 외화 포지션의 **열린 로트**(취득시각·취득량·잔량·carrying·취득환율)를 콘솔에서 조회. FIN-BE-028(ledger lots read EP `GET /settlements/{account}/{currency}/lots`)을 소비하는 read-only 콘솔 표면. 기존 `ledger-ops` 피처(PC-FE-072~075)에 "FX 포지션 로트" 섹션 추가. ADR-001 FIFO/lot 회계를 화면으로 가시화.

# Status

ready

# Owner

frontend

# Task Tags

- frontend
- finance

---

# Dependency Markers

- **선행**: FIN-BE-028(ledger `GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots`, done — origin/main `9698d5159`; ledger-api.md §12). EP 존재·문서화 완료(별도 producer 게이트 불필요).
- **mirrors**: `ledger-ops` account-balance drill(PC-FE-074) — 프록시 route(`app/api/ledger/accounts/[code]/balance/route.ts`) + `ledger-api.ts getAccountBalance` + `use-ledger-ops` 훅 + `AccountLookup`/`AccountDetail` 컴포넌트 + `LedgerOpsScreen` 탭. 동일 구조로 lots 버전.
- **read-only / net-zero**: 신규 read 프록시 + API 클라이언트 + 훅 + 컴포넌트 + `LedgerOpsScreen` 탭만. mutation 0(`Idempotency-Key`·`X-Operator-Reason` 없음). 기존 ledger-ops 표면 byte-unchanged.
- **per-domain 자격**: ledger 는 **domain-facing IAM OIDC access token**(`getDomainFacingToken`) 사용 — `getOperatorToken` 절대 금지(#569 GAP-scoped). `X-Tenant-Id` 미전송(JWT `tenant_id` claim). 기존 ledger-api.ts 패턴 그대로.

# Goal

`/ledger` 콘솔 화면에 "FX 포지션 로트" 섹션을 추가해, 운영자가 `(ledgerAccountCode, currency)` 로 열린 로트 목록 + 요약(Σ잔량·Σcarrying·로트수)을 조회할 수 있게 한다.

# Scope

- **Proxy route**: `app/api/ledger/settlements/[ledgerAccountCode]/[currency]/lots/route.ts`(`runtime='nodejs'`, GET-only, `getPositionLots` 호출 → `mapLedgerError`). account-balance route 미러.
- **API client** `features/ledger-ops/api/ledger-api.ts`: `getPositionLots(ledgerAccountCode, currency)` — domain-facing IAM OIDC 토큰 server-side 부착, GET `${LEDGER_BASE_URL}/api/finance/ledger/settlements/{account}/{currency}/lots`(account 는 `encodeURIComponent`, colon-form code 대응), zod 파싱, sanitized logPath(no account/currency PII는 path-shape 로).
- **Types** `features/ledger-ops/api/types.ts`: `PositionLotsResponseSchema`(lots[] + `totalRemainingForeignMinor`/`totalCarryingBaseMinor`/`lotCount`) + `PositionLotSchema`(lotId, currency, acquiredAt, seq, originalForeignMinor, remainingForeignMinor, originalBaseMinor, carryingBaseMinor, sourceJournalEntryId). **money minor=문자열**(F5; UI 표시 시에만 포맷, 산술 금지).
- **Query state** `features/ledger-ops/api/ledger-state.ts`: `positionLots(account, currency)` query key.
- **Hook** `features/ledger-ops/hooks/use-ledger-ops.ts`: `usePositionLots(account, currency, enabled)` → same-origin `/api/ledger/settlements/{account}/{currency}/lots` GET(client fetch), RQ.
- **Components**: `PositionLotsLookup.tsx`(account + currency 입력 폼) + `PositionLotsTable.tsx`(열린 로트 테이블 + 요약 카드; 빈 목록=빈 상태 메시지). `LedgerOpsScreen.tsx` 에 "FX 포지션 로트" 탭/섹션 추가(기존 trial-balance/accounts/periods/reconciliation 탭 패턴).
- **Spec**: `projects/platform-console/specs/contracts/http/console-integration-contract.md` § 2.4.7.1 ledger read 목록에 lots read 추가 + `console-web/architecture.md`(있으면) 갱신.
- **NO change**: 기존 ledger-ops read/resolve 표면, finance-ops(account-service), 다른 도메인. mutation 0.

# Acceptance Criteria

- **AC-1** `/ledger` 에 "FX 포지션 로트" 섹션: account + currency 입력 → 열린 로트 테이블(취득시각·취득/잔량 foreign·carrying base·source entry) + 요약(Σ잔량·Σcarrying·로트수) 표시.
- **AC-2** 빈 포지션(로트 0) → 빈 상태 메시지(에러 아님, 404=빈상태). 미지 currency/account → inline actionable(400/404 surfaced, no crash).
- **AC-3** money minor 문자열 그대로 다룸(산술 금지, 표시 포맷만). 프록시는 GET-only(no Idempotency-Key/X-Operator-Reason/X-Tenant-Id/body).
- **AC-4** ledger 자격=`getDomainFacingToken`(domain-facing IAM OIDC), `getOperatorToken` 부재(테스트 pin). 401→whole-session re-login / 403→inline / 503·timeout→ledger 섹션만 degrade.
- **AC-5** **net-zero**: 기존 ledger-ops 표면·테스트 무변경. 신규 표면만 추가.
- **AC-6** **console-web 검증 3종 GREEN**(memory `env_console_web_local_verify_needs_lint`): `pnpm lint`(no-unused-vars 등) + `npx tsc --noEmit` + `pnpm exec vitest run`. 세 게이트 모두 통과(tsc·lint 는 vitest 가 못 잡는 CI RED 원인). 신규 vitest: api 클라이언트(getPositionLots 파싱·getOperatorToken 부재) + 프록시 route(GET·에러 매핑) + 컴포넌트/state(테이블 렌더·빈상태).

# Related Specs

- `projects/platform-console/specs/contracts/http/console-integration-contract.md` (§ 2.4.7.1 ledger read consumer — lots read 추가)
- `projects/finance-platform/specs/contracts/http/ledger-api.md` (§12 lots EP — producer 계약)

# Related Contracts

- `ledger-api.md` §12 `GET /settlements/{ledgerAccountCode}/{currency}/lots`(producer, done) — 응답 `{ data: { lots[], totalRemainingForeignMinor, totalCarryingBaseMinor, lotCount }, meta }`, money minor=문자열, 에러 flat envelope.

# Edge Cases

- base/KRW currency 조회 → 외화 로트 없음 → 빈 상태.
- colon-form ledgerAccountCode(예 `CUSTOMER_WALLET:xxx`) → `encodeURIComponent` 인코딩(account-balance route 패턴).
- money minor 문자열 → BigInt/문자열 비교로 표시, Number 산술 금지(F5).
- 403 TENANT_FORBIDDEN(cross-tenant) → inline "not scoped".

# Failure Scenarios

- `getOperatorToken` 사용 → 자격 위반(ledger=IAM OIDC). `getDomainFacingToken` 만(테스트 pin).
- money minor 를 Number 로 산술 → F5 위반. 문자열 유지.
- 프록시에 mutation 헤더(Idempotency-Key 등) 추가 → 범위 위반. GET-only.
- tsc/lint 누락한 채 vitest만 GREEN → CI 프런트 잡 RED(memory). 3종 모두 push 전 통과.
- 기존 ledger-ops 탭/테스트 변경 → net-zero 위반. 신규 섹션만.
