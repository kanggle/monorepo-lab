# Task ID

TASK-PC-FE-286

# Title

Finance·원장 화면이 샘플로 선다 — 개요·계좌·원장(시산표·분개·기간·대사·환율) (`ADR-MONO-074` 실행 5/8)

# Status

review

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 샘플 모드 위에서 **finance + ledger GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 직렬 머지.

화면: `/finance` · `/finance/accounts` · `/finance/guide`(정적) · `/ledger`

코어: `callFlatEnvelopeGateway` (finance account-service · ledger-service). ADR 인벤토리: GET **15**(finance 3 + ledger 12) · 쓰기 **2**.

---

# Scope

## In Scope

- 위 화면의 finance/ledger GET 픽스처 + 원장 `ready`
- 🔴 **복식부기 산술이 맞는** 합성 세계(시산표 차변 합 = 대변 합, 분개 합 = 계정 잔액 변동)

## Out of Scope

- 기간 마감·대사 확정 등 쓰기 — `SAMPLE_READ_ONLY`

---

# Acceptance Criteria

- [x] **AC-0** 282 AC-0 표·원장에서 finance/ledger `pending` GET 목록을 이 파일에 적는다. — § Implementation notes "AC-0 — 재인벤토리" (2 surface — `flat:finance`·`flat:ledger`; ADR 15 는 다른 단위).
- [x] **AC-1** 전부 `ready` + 실제 파서 통과 테스트. — `tests/unit/sample-fixtures-schema-finance-ledger.test.ts` (finance 3 GET + ledger 12 GET 전부, 실제 프로덕션 zod 스키마로 파싱).
- [x] **AC-2** «(샘플)» 규칙 테스트 초록. 🔴 금액·통화·계정 코드·기간 상태 enum 제외 — § D1 참조(이 도메인은 사람이 읽는 필드가 `reconciliation.resolution.note` 하나뿐이다).
- [x] **AC-3** 🔴 **산술 불변식 테스트**: 시산표 차변 합 = 대변 합 · 분개 라인의 계정별 합 = 시산표 해당 계정 값 — router 를 통해, 화면 사이 교차검증. bite 로 발화 증명.
- [x] **AC-4** 🔴 금액은 **합성임이 분명한 규모**로 두고, 통화·소수 자릿수는 실제 파서가 기대하는 표현(`money` 타입)을 따른다. — KRW(scale 0) + USD(scale 2) 혼합 세계로 F5 교차검증.
- [x] **AC-5** 대표 쓰기 1개 → «샘플 화면에서는 실행되지 않습니다». — 대사 차이 해소(resolve) POST + FX refresh POST 둘 다 403 `SAMPLE_READ_ONLY`.
- [x] **AC-6** `e2e-smoke` 익명 `/ledger` 렌더 1칸. — `e2e-smoke/sample-visitor-ledger.spec.ts`.
- [x] **AC-7** 🔴 **첫 화면 개요의 finance 카드가 이 픽스처와 맞는다**. `fixtures/dashboards.ts` 의 finance 카드 값을 `FinanceBalanceReadAdapter`(`GET /api/finance/accounts/{id}/balances`)와 같은 경로를 finance 픽스처에 물어 파생 — § D2 참조(카드 셰이프 불일치 발견 + 처리). `tests/unit/sample-overview-cards-match-lists.test.ts` 에 칸 추가 + bite.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `projects/platform-console/tasks/done/TASK-PC-FE-160-finance-landing-overview-snapshot.md` (🔴 finance 는 목록 GET 이 없다 — 개요가 합성 ₩ 를 만들지 않는다는 기존 결정을 픽스처가 뒤집지 않는다)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (finance · ledger 절)

# Edge Cases

- 기본 계좌 id(`getFinanceDefaultAccountId`)는 세션/env 에서 온다 — 샘플 방문자일 때의 값이 픽스처 계좌와 일치해야 한다(282 가 판정한 분기에서 확인).
- 환율 표의 기준일이 «미래» 로 보이지 않게(🔴 KST 호스트 주의 — 날짜는 UTC 로).

# Failure Scenarios

- 차대 불일치 ⇒ AC-3.
- 기본 계좌 id 불일치로 개요 칸 degrade ⇒ Edge Case 1.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `TZ=UTC pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [x] 원장의 finance/ledger `pending` 0 — `coverage.ts` `SURFACE_COVERAGE`(`flat:finance`·`flat:ledger`)/`SCREEN_COVERAGE`(`/finance`·`/finance/accounts`·`/ledger`) 전부 `ready`.
- [x] 로그인 운영자 경로 무수정 초록 — 이 티켓이 건드린 파일은 `coverage.ts`(2행)·`label-rule.ts`(키 추가만)·`fixtures/index.ts`(배럴 추가만)·`fixtures/{finance,ledger}.ts`(신규)·`fixtures/registry.ts`(finance 카드 1필드 추가)·`fixtures/dashboards.ts`(finance 카드 파생) 뿐. finance/ledger 화면 컴포넌트·hooks·api 클라이언트는 **한 줄도 안 바뀌었다**. 기존 finance/ledger 도메인 테스트(`finance-api`·`finance-proxy`·`finance-state`·`finance-overview-state`·`ledger-api`·`ledger-account-api`·`ledger-*-api/proxy`·`ledger-state`·`ledger-statement-*`)는 무수정 초록 — 전부 이미 282 의 D8 로 "빈 쿠키 병=세션 없음"이 반쪽 세션으로 바뀌어 있었거나(파일 헤더 `ADR-MONO-074` 인용 확인), 애초에 쿠키 없이는 절대 empty jar 로 도달하지 않는 구조(예: `ledger-account-api.test.ts` — 인용 없음, 모든 셀이 최소 한 쿠키를 세팅해 확인).

분석=Opus 5 / 구현 권장=Sonnet 5.

---

# Implementation notes (구현 에이전트, 2026-09-17 UTC)

## AC-0 — 재인벤토리

`TASK-PC-FE-282`~`285` 는 원장 granularity 를 엔드포인트가 아니라 **surface**(게이트웨이 프로필
`logPrefix`)로 잡았다(282 D1) — 이 티켓도 그대로 따른다. `coverage.ts` 의 finance/ledger `pending` 2
surface 를 그대로 인용한다:

| surface | 화면 | GET (코드 읽기 스윕, `shared/api/finance-accounts-read.ts`·`shared/api/ledger-*`·`features/{finance-ops,ledger-ops}/api/*` 전수) |
|---|---|---:|
| `finance` | `/finance`(개요 계좌 leg) · `/finance/accounts` | account(1) + balances(1) + transactions(1) = **3** |
| `ledger` | `/finance`(개요 ledger leg) · `/ledger` | trial-balance(1) + periods list(1) + periods detail(1) + entries detail(1) + accounts/{code}/balance(1) + accounts/{code}/entries(1) + discrepancies list(1) + discrepancies detail(1) + statements detail(1) + settlements lots(1) + fx-rates(1) + fx-rates history(1) = **12** |

합계 GET **15** — 🔴 ADR 본문의 **15**(finance 3 + ledger 12)와 **우연히 일치**하지만, 단위는 여전히
다르다(ADR 은 "호출 함수 이름" 코드 읽기 스윕, 위 표는 "이 티켓이 실제로 만든 핸들러 분기" 세기 —
282~285 가 이미 기록한 "단위가 다르다" 원칙을 그대로 따른다. 우연의 일치를 근거로 쓰지 않는다). 이
티켓의 **실제 범위는 원장의 finance/ledger `pending` 2 surface**이고, `SCREEN_COVERAGE` 의
finance/ledger `pending` 화면은 3개(`/finance` · `/finance/accounts` · `/ledger` — `/finance/guide`
는 이미 282 소유의 `static`), 작업지시서 나열과 일치.

쓰기 2 — `resolveDiscrepancy`(POST `.../discrepancies/{id}/resolve`) + `refreshFxRates`(POST
`.../fx-rates/refresh`). finance 자체는 쓰기 0(account-service 는 순수 읽기 전용).

## 스크린 ↔ 서페이스 매핑 (구현 중 실측)

- `/finance/accounts` → `finance`(account-id-driven, `?accountId=` 쿼리로 seed).
- `/ledger` → `ledger`(trial-balance/periods/discrepancies 서버사이드 seed + `?entryId=`/`?accountCode=`/
  `?statementId=` id-driven 추가 seed; position lots·fx-rates·fx-history 는 클라이언트 hook 이 같은
  `ledger` 표면을 프록시로 부른다).
- `/finance` → `finance-overview` 기능이 `finance`(계좌 leg: `getFinanceDefaultAccountId()` → 있으면
  `getAccount`+`getBalances`) + `ledger`(browsable leg: trial-balance/periods/discrepancies/fx-rates)
  를 **둘 다** 독립 degrade 로 fan-out — 새 표면이 아니라 위 두 표면의 **조합**으로 이미 `ready`.
- `/finance/guide` → 이미 282 소유(static, 변경 없음).

## 픽스처 세계 (하나로 엮은 이중 도메인)

finance(account-service)와 ledger(ledger-service)는 **서로 다른 프로듀서**(finance 는 고객 지갑형
계좌, ledger 는 복식부기 총계정원장)라 서로 참조하지 않는다 — 각자 독립된 세계:

- **finance**: 계좌 1개(`sample-account-0001`, KRW, `ledger=available+held`=100,000=80,000+20,000) +
  거래 3건(COMPLETED credit 80,000 · ACTIVE hold 20,000 · FAILED transfer 15,000 — FAILED 는 잔액에
  반영되지 않아 위 등식이 그대로 성립).
- **ledger**: 계정 4개(`CASH`·`AR`·`REVENUE`·`FX_USD_HOLDING`) + 분개 3건 —
  `entry-sample-0001`(DEBIT CASH 500,000 / CREDIT REVENUE 500,000) ·
  `entry-sample-0002`(DEBIT AR 50,000 / CREDIT REVENUE 50,000) ·
  `entry-sample-0003`(DEBIT FX_USD_HOLDING $100.00(USD, `exchangeRate` 1300, `baseAmount` 130,000 KRW)
  / CREDIT CASH 130,000 KRW) — Σ기준통화차변 = Σ기준통화대변 = 680,000. 기간 2개(8월 CLOSED 스냅샷
  550,000/550,000 · 9월 OPEN 스냅샷 없음). 대사 차이 2건(OPEN — entry-3 참조 · RESOLVED — WRITTEN_OFF).
  대사 statement 1건(entry-1 매치 1건 + discrepancy-1 재사용). FX position lot 1건(entry-3 와 동일
  취득 — `sourceJournalEntryId` 로 연결). FX rate 2건(USD 신선 · JPY stale) + history 2쌍.

## 편차 / 구현자 선택

- **D1 — 이 두 표면은 사람이 읽는 필드가 (거의) 없다.** 작업지시서 AC-2 는 "계정명·적요 끝"을 언급하지만,
  `AccountSchema`/`BalanceSchema`/`TransactionSchema`(finance) 와 모든 ledger-types 스키마를 읽어 확인한
  결과 name/title/description 류 필드가 **전혀 없다**(283 의 IAM `email` 같은 유일한 예외 필드조차
  없다) — 283 의 "그 필드가 없으면 그 축은 안 따른다" 선례와 같은 종류. 유일한 예외는 대사 해소의
  `resolution.note`(해소 담당자가 입력하는 자유 서술) — `label-rule.ts` 의 **기존(282 부터 있던) 전역
  규칙**이 `note` 를 이미 HUMAN_READABLE 로 분류해 두었으므로(285 가 `reason` 을 MACHINE 으로 재확인한
  것과 반대 결론), 그 규칙을 그대로 따라 «(샘플)» 접미를 붙였다(최초 구현 시 MACHINE 으로 잘못 분류했다가
  `pnpm test` 1회에서 실제로 빨개져 잡았다 — 아래 § 측정 참고, bite B4).
- **D2 — AC-7 finance 카드의 실제 배선 확인, 그리고 셰이프 불일치 발견.** `FinanceBalanceReadAdapter`(콘솔-bff)
  코드를 읽으면 `GET /api/finance/accounts/{operatorDefaultAccountId}/balances`(`readBalances`)를 부르고,
  `OperatorOverviewCompositionUseCase.callFinance` 는 그 원시 응답(`{data:[Balance],meta}`)을 **그대로**
  leg 의 `data` 로 통과시킨다. 그런데 console-web 자신의 `FinanceDataSchema`(`operator-overview-types.ts`)
  는 `{balance:{amount,currency}, accountId}` 라는 **다른** 모양을 기대한다 — 이것은 콘솔-bff 컴포지션과
  프런트 카드 스키마 사이의 **기존 셰이프 불일치**(이 티켓 범위 밖의 실제 프로덕션 이슈로 보인다, 아래
  ⚪ 참고). 이 픽스처를 원시 셰이프로 만들면 카드가 매번 **빈 값**으로 파싱돼(스키마가 `passthrough` +
  전부 optional 이라 throw 는 안 하지만 `balance`/`accountId` 가 항상 undefined) 모든 샘플 방문자에게
  깨진 카드를 보여준다 — ADR-MONO-074 의 목적("실제로 동작하는 실제 화면")에 정면으로 반한다. 그래서
  기존 `{balance,accountId}` 셰이프(카드가 실제로 파싱하는 계약)는 유지하고, **값**만 finance 픽스처의
  같은 경로 조회로 파생했다(AC-7 의 문언 요구 — "같은 경로를 물어 카드 값을 파생" — 를 그대로 만족하며,
  진짜로 존재하지 않는 셰이프 버그를 재현하지 않는 쪽을 택했다). `fixtures/dashboards.ts` 에 근거 기록.
- **D3 — `registry.ts → finance.ts` import edge 는 순환 초기화 크래시를 낸다(발견 + 되돌림).**
  Edge Case 1 을 만족시키려 처음엔 `registry.ts` 가 `finance.ts` 의 `SAMPLE_FINANCE_DEFAULT_ACCOUNT_ID`
  를 **import** 했다. `pnpm test` 전체 스위트 1회에서 `tests/unit/sample-fixtures-schema.test.ts` 가
  `TypeError: Cannot read properties of undefined (reading 'flat:finance')`(`dashboards.ts:73`)로
  실제로 빨개져 잡았다 — 그 테스트가 `registry.ts` 를 `dashboards.ts` **보다 먼저** import 하는 순서였고,
  `finance.ts` 는 `../router` 를 import 하며 `router.ts` 는 `./fixtures`(이 디렉터리의 배럴)를
  import 하고, 배럴은 `dashboards.ts` 를 import 하며, `dashboards.ts` 는 모듈 로드 시점에 **즉시**(지연
  클로저가 아니라) `FINANCE_FIXTURE_HANDLERS` 를 읽어 카드 값을 계산한다 — `registry.ts` 를 시작점으로
  이 순환을 밟으면 `finance.ts` 가 **아직 자기 자신의 export 문에 도달하지 못한 채로** `dashboards.ts`
  에 되읽힌다. iam/erp/ecommerce 는 이 문제가 없다 — `registry.ts` 가 그들을 import 하지 않기 때문에
  이 순환의 시작점이 될 일이 없다(`dashboards.ts` 가 항상 그들을 **먼저** 여는 쪽이라 안전하다). 해결:
  `registry.ts` 는 리터럴 문자열을 갖고, TASK-PC-FE-285 의 알림벨 `sourceId` CORRECTION 과 같은 모양
  ("소유 파일을 import 하지 말고 리터럴을 복제 + router 교차검증 테스트")으로 드리프트를 막는다
  (`tests/unit/sample-fixtures-schema-finance-ledger.test.ts` "Edge Case 1").
- **D4 — 시산표 grand 필드 해석.** `TrialBalanceSchema` 는 원본통화 grand 쌍(`grandDebitTotal`/
  `grandCreditTotal`)과 기준통화(KRW) grand 쌍을 **둘 다** 갖는다(8차 증분 다통화 통합). 계정마다
  원본통화가 다르면(CASH=KRW, FX_USD_HOLDING=USD) 원본통화 grand 합은 원칙적으로 의미가 없어, 기준통화
  쌍과 동일하게 채웠다(둘 다 KRW, 680,000) — `inBalance` 는 기준통화 쌍으로 판정.
- **D5 — 원장 계정 코드는 콜론-형이 아니라 단순 코드(`CASH`/`AR`/`REVENUE`/`FX_USD_HOLDING`).** 문서
  예시(`CUSTOMER_WALLET:acc-1`)를 따르지 않은 의도적 단순화 — `encodeURIComponent` 인코딩 경로는 콜론
  유무와 무관하게 동일하게 동작하고(erp 의 department code 도 콜론 없는 단순 코드다), 이 세계의 핵심은
  코드 형태가 아니라 복식부기 산술이라 판단했다.

## 기존 테스트 파일 무영향 확인

`finance-api.test.ts`/`ledger-api.test.ts` 는 이미 282 가 "빈 쿠키 병 = 세션 없음" 셀을 반쪽 세션(운영자
쿠키만)으로 바꿔 뒀다(파일 헤더 `ADR-MONO-074` 인용 확인) — finance/ledger 를 `ready` 로 뒤집어도
무영향. `ledger-*-api.test.ts`/`ledger-*-proxy.test.ts`/`finance-*.test.ts` 계열(모듈 분할
TASK-PC-FE-102/106/233 의 자식 파일들) 은 파일별로 `ADR-MONO-074` 인용 여부를 grep 했고, 인용이 **없는**
파일(`ledger-account-api.test.ts` 등)은 스팟체크로 모든 `it(` 셀이 최소 한 개의 쿠키를 세팅해 애초에
빈 병(샘플 방문자) 경로에 도달하지 않음을 확인했다 — 282 가 고칠 것이 없었던 파일이다.

## ⚪ 측정하지 못한 것 / 넘길 의무

- **console-bff `FinanceBalanceReadAdapter` 컴포지션의 원시 셰이프(`{data:[Balance],meta}`)와 console-web
  `FinanceDataSchema`(`{balance,accountId}`)의 불일치**(§ D2) — 실제 프로덕션 코드의 문제로 보이지만
  이 티켓의 범위(샘플 픽스처)를 벗어난다. console-bff 또는 `operator-overview-types.ts` 를 건드리는
  다음 티켓이 확인해야 한다 — 지금은 샘플 픽스처가 카드 스키마가 실제로 기대하는 모양을 유지해 화면이
  깨지지 않게 했을 뿐, 실제 배포 환경에서 이 카드가 진짜 값을 보여주는지는 이 워크트리에서 검증할 수
  없다(console-bff 는 별도 Java 서비스).
- **실제 Vercel 배포에서 샘플 방문자** — 로컬 production build + smoke 까지만 쟀다(283/284/285 와 동일 한계).
- 넘길 의무 **0건** — 이 티켓이 임시로 들고 있던 남의 미해결 작업 없음.

## 측정 (이 워크트리 · Windows 호스트 · 각 게이트 독립 실행 + 명시 rc)

BEFORE 는 이 워크트리의 분기점(`origin/main` = `447131325`, TASK-PC-FE-285 의 close chore 커밋 —
`tasks/done/` 이동만, console-web 코드는 285 의 머지 커밋과 동일)에서, 어떤 편집도 하기 전에 쟀다.

| 게이트 | 트리 | 결과 |
|---|---|---|
| `TZ=UTC pnpm test` | BEFORE(워크트리, 편집 전) | rc=0 · **311 files / 3402 tests passed**, 실패 0 |
| `pnpm lint` | AFTER(최종) | rc=0 · «No ESLint warnings or errors» |
| `npx tsc --noEmit` | AFTER(최종) | rc=0 |
| `pnpm test` (1차 — 구현 직후) | AFTER | rc=1 · 2 files 실패 — 아래 § "구현 중 잡힌 회귀" |
| `TZ=UTC pnpm test` (최종) | AFTER | rc=1 · **312 files 중 3 failed(개별 셀 5) / 3439 tests 중 5 failed** — 전부 아래 § "기지 Windows 타이밍 flake" |
| `pnpm build` | AFTER(최종) | rc=0 |
| `pnpm e2e:smoke` | AFTER(최종, 프로덕션 빌드, 백엔드 전부 loopback 127.0.0.1:1) | rc=0 · **23 passed** — 기존 22개 + 신규 `sample-visitor-ledger.spec.ts` 1개(AC-6) |

### 구현 중 잡힌 회귀 (수정 전 `pnpm test` 1회, 2 파일 실패 — 커밋에는 없음, 전부 § 편차에서 고쳤다)

| # | 파일 | 원인 | 고침 |
|---|---|---|---|
| 1 | `tests/unit/sample-fixtures-schema.test.ts` | `registry.ts → finance.ts` import 순환 초기화 크래시 | D3 — 리터럴 + 교차검증 테스트로 전환 |
| 2 | `tests/unit/sample-label-rule.test.ts` › `flat:ledger` | `note` 를 MACHINE 으로 잘못 분류(실제로는 기존 전역 규칙상 HUMAN_READABLE) | D1 — «(샘플)» 접미 추가, 잘못된 MACHINE_KEYS 항목 제거 |

### 기지 Windows 타이밍 flake (최종 `pnpm test` 3 files/5 cells — 이 변경과 무관, 개별 재실행으로 확인)

| 파일 | 이 변경과의 관계 | 개별 재실행 |
|---|---|---|
| `LedgerOpsScreen.test.tsx` | memory 에 기록된 기존 flake(정확히 이 파일명). 이 티켓은 이 파일을 건드리지 않았다 | rc=0 · 53/53 passed |
| `OperatorsScreen.test.tsx` | memory 에 기록된 기존 flake(정확히 이 파일명). IAM 화면, finance/ledger 와 무관 | rc=0(아래 조합 재실행에 포함) |
| `features/erp-ops/DelegationScreen.test.tsx` | 같은 부류의 새 사례(erp 위임 화면, finance/ledger 와 무관) — `Test timed out in 5000ms` | rc=0(아래 조합 재실행에 포함) |

`OperatorsScreen.test.tsx` + `DelegationScreen.test.tsx` 조합 재실행: rc=0 · 28/28 passed. 세 파일 모두
finance/ledger 표면·픽스처·화면 코드와 겹치지 않는다(git diff 로 이 티켓이 건드린 파일 목록과 대조 확인).

### Bites (금지/결함 코드를 넣어 빨강 → 되돌려 복원 트리에서 초록)

| # | 가드 | 주입 | 빨강 | 복원 후 |
|---|---|---|---|---|
| B1 | trial balance `inBalance`(AC-3, 차대 불일치) | `entry-sample-0003` 의 CASH CREDIT 라인 금액을 999,999 로 | rc=1 · `expected false to be true`(inBalance) | rc=0 · 34/34 |
| B2 | account balance = 계정별 분개 합(AC-3) | `accountBalanceFixture` 에서 CASH 의 `debitTotal` 을 하드코딩 999,999 로 | rc=1 · `expected 999999n to be 500000n` | rc=0 · 34/34 |
| B3 | 개요 finance 카드 = 목록 계좌(AC-7) | `dashboards.ts` finance 카드를 옛 하드코딩 값(`1250000000`)으로 되돌림 | rc=1 · `expected '1250000000' to be '100000'` | rc=0 · 4/4 |
| B4 | `note` «(샘플)» 접미(AC-2) | 접미 제거 | rc=1 · `missing-suffix` (`discrepancies[1].resolution.note`) | rc=0 · 35/35 |
| B5 | 존재하지 않는 분개 id → 404(AC-1/AC-3) | `fixtureNotFound(...)` 대신 `undefined` 반환 | rc=1 · `expected 503 to be 404` | rc=0 · 34/34 |

B1–B5 모두 단독 주입 → 실행 → 복원 순으로 개별 확인했다. `git diff` 로 복원 후 파일이 스테이지된
버전과 바이트 단위로 동일함을 확인(잔여 변경 0). `grep -rn "BITE-" src tests e2e-smoke` = 0건.
