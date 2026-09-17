# Task ID

TASK-PC-FE-295

# Title

운영자 개요의 finance 카드가 console-bff 가 보내지 않는 모양을 읽는다 — 잔액이 있어도 «잔액 정보 없음»

# Status

ready

# Owner

platform-console

# Task Tags

- code
- test
- contract

---

# Goal

`TASK-PC-FE-286` 조정자 리뷰에서 **코드 판독으로** 발견(샘플 모드와 무관한, 로그인 운영자 경로의 결함 후보).

- console-bff `OperatorOverviewCompositionUseCase.callFinance` → `FinanceBalanceReadAdapter.readBalances` 는
  `GET /api/finance/accounts/{id}/balances` 응답 **본문을 그대로** finance 레그의 `data` 로 싣는다.
  그 응답 모양은 `projects/finance-platform/specs/contracts/http/account-api.md` § `GET /api/finance/accounts/{id}/balances`:
  `{ "data": [ { "currency", "ledger", "available", "held" } ], ... }`.
- console-web `features/operator-overview/api/operator-overview-types.ts` `FinanceDataSchema` 는
  `{ balance?: { amount?, currency? }, accountId? }` 를 기대하고(전 필드 optional + passthrough ⇒ **파싱은 통과**),
  `DomainCardSummaries.tsx` 는 `parsed.data.balance !== undefined` 일 때만 «잔액 조회 가능» 을 그린다.
- ⇒ 기본 계좌가 설정된 운영자에게 백엔드가 잔액을 정상으로 돌려줘도 **첫 화면 finance 카드는 «잔액 정보 없음»** 일 것이다.

🔴 **계약의 공백이 원인이다**: `console-integration-contract.md` § 2.4.9.1 은 finance 레그가 무엇을 부르는지는 적지만 카드 `data`
의 **모양은 정의하지 않는다** — producer(bff)와 consumer(web)가 각자 다른 가정을 했고 둘을 함께 재는 테스트가 없다
(bff 슬라이스 테스트는 `Map.of("balance", 0)` 을, web 테스트는 `{ balance: {...} }` 를 각자 심는다).

⚪ **라이브 미확인** — 코드 판독이다. AC-0 이 먼저 실측한다.

---

# Scope

## In Scope

- AC-0 실측(결함이 실제로 나타나는가)
- 계약 § 2.4.9.1 에 finance 레그 `data` 모양을 **먼저** 적는다(계약 → 구현 순서)
- 적은 모양에 맞춰 한쪽을 고친다 + 양쪽을 한 모양으로 묶는 테스트

## Out of Scope

- 샘플 모드(`ADR-MONO-074`) — `TASK-PC-FE-286` 의 샘플 finance 카드는 **지금의** `FinanceDataSchema` 모양을 따른다.
  🔴 이 티켓이 모양을 바꾸면 `shared/sample/fixtures/dashboards.ts` 의 finance 카드와
  `tests/unit/sample-overview-cards-match-lists.test.ts` 의 finance 칸도 **같은 PR 에서** 새 모양으로 옮긴다.
- 카드에 금액 숫자를 새로 그리는 것 — F5 money discipline(«잔액 조회 가능» + 통화만) 은 그대로.

---

# Acceptance Criteria

- [ ] **AC-0 실측 먼저** — ① red-first 단위 테스트: 계약 모양(`{ data: [ { currency: 'KRW', ledger: '1000', available: '1000', held: '0' } ], meta: {} }`)을
      finance 레그 `data` 로 `DomainCardSummaries` 에 넣으면 지금 «잔액 정보 없음» 이 그려진다(rc=1 로 실패하는 칸). ② 가능하면 로컬/라이브
      `GET /api/console/dashboards/operator-overview` 의 finance 카드 `data` 원문을 한 번 기록한다(못 재면 ⚪ + 이유).
      🔴 ①이 초록이면(= 결함 없음) **구현하지 않고** 이 파일에 근거를 적고 닫는다.
- [ ] **AC-1 계약 먼저** — `console-integration-contract.md` § 2.4.9.1 에 finance 레그 `data` 모양을 적는다. 갈래와 추천:
      ⓐ **(추천)** bff 는 producer 본문을 그대로 싣고(다른 레그와 같은 원칙), web 스키마가 `data[]` 에서 기본 계좌 통화의 행을 읽는다 — bff 변경 0.
      ⓑ bff 가 `{ balance: { amount, currency }, accountId }` 로 가공해 싣는다 — web 변경 0, 대신 bff 가 finance 모양을 안다.
      갈래 선택은 이 파일에 이유와 함께 적는다(소유자가 한 줄로 뒤집을 수 있게).
- [ ] **AC-2 고침** — 적은 모양대로 한쪽만 고친다. AC-0 ① 칸이 초록이 된다.
- [ ] **AC-3 양쪽을 묶는 테스트** — producer 계약 모양 한 벌을 **양쪽 테스트가 같이 쓰는** 형태로 둔다(bff 슬라이스/IT 가 싣는 것 = web 이 파싱하는 것).
      지금처럼 양쪽이 각자 다른 가짜 모양을 심으면 이 결함이 다시 초록으로 숨는다.
- [ ] **AC-4 샘플 동반 이동** — 모양이 바뀌면 샘플 finance 카드 + `sample-overview-cards-match-lists.test.ts` finance 칸을 같은 PR 에서 옮기고 초록.
- [ ] **AC-5 대조군** — 기본 계좌 **없는** 운영자(레그 short-circuit) 경로는 무변화.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (operator overview) · § 2.4.7 (finance)
- `projects/finance-platform/specs/contracts/http/account-api.md` § `GET /api/finance/accounts/{id}/balances`
- `projects/platform-console/specs/services/console-web/architecture.md` · `projects/platform-console/specs/services/console-bff/architecture.md`
- `projects/platform-console/tasks/done/TASK-PC-FE-014-*` (finance Option (a) 활성화 — 이 레그를 켠 티켓)

# Related Contracts

- `console-integration-contract.md` § 2.4.9.1 — 🔴 이 티켓이 finance 레그 `data` 모양을 **추가**한다
- `account-api.md` balances 응답(불변)

# Edge Cases

- 계좌가 여러 통화의 잔액을 가진다 — ⓐ 에서 어느 행을 카드의 통화로 보일지 정한다(계좌 통화 = 기본).
- 잔액 배열이 비어 있다 — «잔액 정보 없음» 이 **정직한** 값이 되는 유일한 경우.
- finance 레그가 `forbidden`/`degraded` — 카드 상태 분기는 기존 그대로.

# Failure Scenarios

- AC-0 없이 고친다 → 결함이 없던 경우 멀쩡한 경로를 바꾼다.
- 계약을 안 적고 고친다 → 다음 변경에서 다시 한쪽만 움직인다(이번 결함의 원인 그대로).
- 샘플 픽스처를 안 옮긴다 → `main` 의 샘플 개요 카드가 degrade 로 떨어진다(`sample-overview-cards-match-lists` 가 빨강).

# Test Requirements

- console-web: `pnpm lint` · `npx tsc --noEmit` · `pnpm test` (각각 독립 + `rc=$?`)
- console-bff(ⓑ 선택 시): `./gradlew :projects:platform-console:apps:console-bff:test`

# Definition of Done

- [ ] 계약에 finance 레그 `data` 모양이 있다
- [ ] 계약 모양으로 넣은 red-first 칸이 초록
- [ ] 샘플 개요 카드 가드 초록

분석=Opus 5 / 구현 권장=Sonnet 5 (갈래 ⓐ 기준 — web 스키마·렌더 한 곳 + 테스트).
