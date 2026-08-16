# Task ID

TASK-MONO-537

# Title

Finance 시드가 **9일 전에 반증된 사실**을 매 실행 인쇄한다 — 그리고 콜드 스택에서는 도메인 전체가 6/6 실패한다

# Status

in-progress

# Owner

monorepo

# Task Tags

- infra
- demo
- seed
- finance

---

# Goal

`seed-finance.sh` 에 서로 다른 결함이 **둘** 있다. 둘 다 같은 파일이라 한 PR 로 간다.

---

## 🔴🔴 결함 1 — 시드가 *"입금 API 가 존재하지 않는다"* 고 말한다. **존재한다.**

오늘(2026-08-16, finance 7/7 healthy) 시드 출력:

```
[seed:finance] 관측  이체 A→B 불가 — 잔액 0, 그리고 입금 API 가 존재하지 않는다
[seed:finance]       topUp() 은 있으나 프로덕션 호출자 0건(테스트 전용), Kafka 리스너 0건
[seed:finance]       ⇒ 자금 이동이 불가능하므로 /ledger 는 이 스택에서 채울 수 없다
```

**같은 스택·같은 운영자 토큰으로 그 사슬을 끝까지 태워 반증했다:**

| 단계 | 결과 |
|---|---|
| `POST /api/finance/accounts/{A}/topups` | **200** · `TOPUP` / `COMPLETED` / 500,000 |
| 잔액 재조회 | `ledger 0 → 500000` (`available 500000`) |
| `POST /{A}/transfers` → B | **200** · `TRANSFER` / `COMPLETED` / 100,000 |
| `GET /ledger/trial-balance` | **찼다** — `CASH_CLEARING` 차변 1,000,000 · `CUSTOMER_WALLET:{A}` 차변 100,000 / 대변 500,000 |

입금 경로는 `TASK-FIN-BE-068`(2026-08-07, **DONE**)이 열었다. 워크스루 §6 은 이미
`✅ Finance 계좌에 돈이 들어간다` 로 고쳐 적혀 있다. **시드만 옛 세계에 남아 있다.**

### 왜 아직 그런가 — 추적되지 않는 인계 하나

BE-068 세션에서 `seed-finance.sh` **편집이 분류기에 하드 차단**됐고, 저장소 규칙대로 셸
우회 대신 패치를 인계했다. 그 패치는 **지금도 그 자리에 있다**:
`infra/demo/seed/TASK-FIN-BE-068-seed-finance.patch.md`.

그리고 그 티켓은 **DONE 인데 그 AC 는 열려 있다** — 본문에 문자 그대로:

```
- [ ] **AC-5 (시드)** — ⚠️ 미완: `infra/demo/seed/seed-finance.sh` 편집이 이 세션에서 …
```

🔴 **이 인계를 이어받는 티켓이 어느 큐에도 없다.** 의무가 티켓이 닫히면서 같이 사라졌다.
이 티켓이 그 자리다.

🔴 **피해는 "화면이 빈다" 로 끝나지 않는다** — 시드가 읽는 사람을 **틀린 곳으로 보낸다**
(*"제품에 갭이 있다"* 고 말하지만 그 갭은 9일 전에 닫혔다). `TASK-MONO-535` 가 고친 결함
(*경고 문구가 정상 코드를 감사하게 만든다*)의 **다른 사본**이다.

---

## 🔴 결함 2 — 준비성 술어가 **게이트웨이만** 본다 ⇒ 콜드 스택에서 6/6 실패

`demo-up.sh finance` 직후 자동 시드:

```
✗ 계좌 A — HTTP 500   ✗ 계좌 B — HTTP 500   ✗ KYC ×2   ✗ 이체 생략   ✗ 시산표 — HTTP 500
요약 — 생성 0 · 기존 0 · 실패 6
```

그 시각 `finance-platform-account` · `-ledger` 는 `health: starting` 이었다. healthy 확인 후
**같은 명령을 재실행하니 `생성 4 · 기존 0 · 실패 0`** ⇒ 코드 결함이 아니라 **너무 일찍 물었다.**

원인은 술어다 — `seed-finance.sh` 는 `wait_http "$FIN/api/finance/accounts/probe"` **하나**만
기다린다. `lib.sh` 가 그 함수 바로 위에 경고까지 적어 두었다:

> 🔴 `wait_http` 는 **엣지 준비성**만 잰다. 뒤의 백엔드에 대해서는 아무것도 증명하지
> 않는다 — 아래 `wait_backend` 를 반드시 함께 쓸 것.

**형제 전수 — 낙오가 하나다:**

| 시드 | 백엔드별 `wait_backend` |
|---|---|
| `seed-scm.sh` | 3 (procurement · demand-planning · inventory-visibility) |
| `seed-erp.sh` | 3 (masterdata · approval · read-model) |
| `seed-wms.sh` | 있음 |
| **`seed-finance.sh`** | **0** ← |

🔵 `TASK-MONO-535` 덕에 이제 요약 마지막 줄이 `실패 6` 이라 **조용히 지나가지는 않는다.**
그러나 *일어나지 않게* 하는 것은 별개다.

---

# Scope

## In Scope

- **입금 → 이체 → 원장** 경로를 시드에 넣는다. 인계 패치
  (`infra/demo/seed/TASK-FIN-BE-068-seed-finance.patch.md`)가 **출발점**이다 — 그대로 적용하지
  말고 **오늘 코드에 대해 다시 검증**한 뒤 반영한다(패치는 2026-08-07 작성이다).
- 🔴 패치가 이미 짚은 술어를 유지할 것: **판정은 응답 코드가 아니라 잔액 재조회**다.
  고정 `Idempotency-Key` 를 쓰면 2회차는 서버가 **저장된 2xx 를 재생**하므로 200 으로는
  *"이번 실행이 돈을 넣었나"* 를 알 수 없다. 잔액이 목표 이상인가로 가른다.
- 옛 서술(`입금 API 가 존재하지 않는다` 3줄)을 **삭제**한다. 남겨 두면 다음 사람이 또
  존재하는 API 를 찾아 헤맨다.
- `wait_backend` 를 **account-service · ledger-service** 에 대해 추가.
- 패치 파일(`TASK-FIN-BE-068-seed-finance.patch.md`)은 반영 후 **제거**한다 — 반영됐는데
  남아 있으면 그 자체가 다음 사람에게 미완 신호로 읽힌다.
- 워크스루 §6 의 Finance 시드 행 갱신(`계좌 2건` → 입금·이체·원장까지) + §7 에
  *"finance 화면이 전부 비고 시드가 실패 6 을 보고한다"* 증상 행.

## Out of Scope

- `account-service` / `ledger-service` **코드 변경** — 경로는 이미 있고 동작한다(위 실측).
- `/finance/accounts` **목록 라우트 신설** — §6 이 🟡 로 유지 결정을 이미 기록했다
  (실측 재확인: `GET /api/finance/accounts` → **405 METHOD_NOT_ALLOWED**).
- 다른 도메인 시드의 준비성 술어 — 형제 3개는 이미 `wait_backend` 를 쓴다(위 표).

---

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act)** — 착수 시 두 결함을 **다시 잰다**: ① `topups` 가 아직
      200 인가(잔액 전후 대조) ② 콜드 스택에서 여전히 6/6 실패인가. 이미 고쳐졌으면
      phantom 으로 기록한다.
- [ ] **AC-1 (입금 경로)** — 시드가 입금→이체를 완주하고, `trial-balance` 에 **계정이 생긴다.**
      🔴 판정 필드는 **`ledgerAccountCode`** 다(`accountCode` 가 아니다 — 발굴 중 이 오타로
      "원장이 안 찬다" 는 정반대 결론을 낼 뻔했다). 차변합 = 대변합 임을 함께 단언한다.
- [ ] **AC-2 (멱등)** — 연속 2회 실행이 같은 상태로 수렴하고, 2회차가 **잔액을 두 배로
      만들지 않는다**(고정 Idempotency-Key). 판정은 잔액이지 로그 문구가 아니다.
- [ ] **AC-3 (준비성)** — `wait_backend` 추가 후 **콜드 스택에서** `demo-up.sh finance` 를
      돌려 `실패 0` 을 보인다. 🔴 **웜에서 재면 아무것도 증명하지 못한다** — 웜은 수정 전에도
      초록이다(`TASK-MONO-532` 가 같은 함정을 기록했다).
- [ ] **AC-4 (문서)** — §6 갱신 + §7 증상 행 + 패치 파일 제거.
      `check-walkthrough-ledger-drift` OK.
- [ ] **AC-5** — `check-index-queue-drift` · `verify-demo-wrapper.sh` OK.

---

# Related Specs

- `projects/finance-platform/tasks/done/TASK-FIN-BE-068-no-path-for-money-to-enter-an-account.md`
  (§ AC-5 미완 + 인계 표)
- `infra/demo/seed/TASK-FIN-BE-068-seed-finance.patch.md` (인계된 패치)
- `projects/finance-platform/specs/contracts/http/account-api.md`
- `docs/guides/interview-demo-walkthrough.md` § 6 · § 7

# Related Skills

N/A — 셸 시드 + 문서.

# Related Contracts

`account-api.md` — `POST /accounts/{id}/topups` 는 **운영자 전용**이다. 시드는 이미 운영자
토큰으로 돌고 있으므로 새 자격증명이 필요하지 않다(계약 변경 없음).

# Target Service

N/A — `infra/demo/seed/` (공유 데모 하네스).

# Architecture

N/A — 기존 API 를 호출할 뿐이다. ADR 불필요.

---

# Implementation Notes

- 🔴 **분류기 차단 이력**: BE-068 세션에서 이 파일 편집이 하드 차단됐다(3줄짜리 로그 문구
  수정도 같은 차단). **다만 분류기는 외부이고 조용히 바뀐다** — 미리 인계하지 말고
  **한 번 시도**한 뒤 실제로 막힐 때만 패치를 넘겨라. 막히면 셸로 우회하지 말 것
  (`platform/git-workflow-policy.md`).
- 입금액은 이체(100,000) + 홀드 여유를 덮도록 잡는다(패치는 500,000 을 쓴다).
- 계좌 id 는 시드가 쓰는 것과 **같은 `Idempotency-Key`** 로 POST 하면 서버가 저장된 응답을
  재생해 돌려준다(발굴 중 이 방법으로 기존 계좌 id 를 되찾았다). 목록 라우트가 없어서
  이것이 사실상 유일한 재획득 경로다.

---

# Edge Cases

- **기존 볼륨**: 계좌가 이미 ACTIVE 이고 잔액이 이미 목표 이상일 수 있다 ⇒ 잔액 술어는
  *"목표 이상이면 도달"* 이어야 한다(정확히 같음이 아니라).
- **원장은 최종적 일관성**이다 — 이체 직후 시산표가 비어 있을 수 있다. 짧게 재시도하되,
  재시도 끝에도 비면 **실패**로 적는다(*"지연"* 으로 뭉개지 않는다).
- 🔴 시산표가 오래 0 이면 브로커부터 보라: `TASK-FIN-BE-068` 에서 발행 3 · 전기 3 · DLT 0
  인데 화면이 0 이었고, 원인은 **봉투에 `tenantId` 가 없어 원장이 리터럴 `finance` 로
  폴백**한 것이었다(주인이 못 읽는 테넌트에 전기). 같은 증상이 다시 보이면 그 자리를 먼저 열어라.

---

# Failure Scenarios

- 패치를 **검증 없이 그대로 적용** → 9일 전 코드 기준이라 필드명·엔드포인트가 어긋나면
  시드가 조용히 실패한다. AC-0 이 재측정을 요구하는 이유다.
- `wait_backend` 를 넣고도 **웜 스택에서만** 검증 → AC-3 이 공허해진다.
- 옛 3줄을 남긴 채 입금만 추가 → 로그가 *"입금 API 가 없다"* 와 *"입금 성공"* 을 **동시에**
  말한다. 지우는 것까지가 수정이다.

---

# Test Requirements

- 콜드 스택 1회(`demo-down.sh finance` → `demo-up.sh finance`) — AC-3 판정.
- 웜 스택 연속 2회 — AC-2 수렴 판정.
- `bash -n infra/demo/seed/seed-finance.sh`.

---

# Definition of Done

- [ ] AC-0~AC-5 전부 닫힘(phantom 은 그 사실을 기록).
- [ ] 인계 패치 파일 제거.
- [ ] 워크스루 §6/§7 갱신, 가드 GREEN.
- [ ] `tasks/INDEX.md` done entry(close chore 시).

---

# Provenance

2026-08-16 라이브 검증에서 발굴(사용자 요청: 나머지 4개 도메인도 iam·ecommerce·console·wms
처럼 검증). finance 는 콜드에서 6/6 실패했고, healthy 재실행에서 성공했으며, 그 성공 로그가
**저장소가 이미 반증한 문장**을 인쇄하고 있었다.

분석=Opus 5(1M) / 구현 권장=**Sonnet** (판단은 끝났다 — 패치 재검증 + 술어 추가).
