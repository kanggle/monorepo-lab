# Task ID

TASK-FIN-BE-068

# Title

계좌에 돈이 들어갈 길이 없다 — 이체·홀드·캡처와 원장 전 화면이 그 하나에 막혀 있다

# Status

ready

# Owner

finance-platform

# Task Tags

- backend
- api
- demo-gap

---

# 배경 — `TASK-MONO-510` 5·6회차 실측

시드가 계좌 2개를 실제 API 로 열고 KYC 를 `ACTIVE/FULL` 로 승급까지 시켰다. 그 다음이 없다.

```
POST /api/finance/accounts/{A}/transfers  →  INSUFFICIENT_AVAILABLE_BALANCE
```

잔액을 만들 방법을 찾다 막혔다.

## 실측 (추론 아님)

| 확인 | 결과 |
|---|---|
| deposit / topup / credit **HTTP 매핑** | **0건** |
| `topUp()` 메서드 | 존재하나 주석이 *"internal funding (v1 stub)"*, **프로덕션 호출자 0건**(테스트 6회만) |
| `@KafkaListener` (외부 입금 이벤트 수신) | **0건** |
| 콘솔의 finance 쓰기 라우트 | 계좌 생성 **없음**(단건 조회뿐) · 환율 refresh 는 **200 인데 no-op** · 대사 해소는 대상 0건 |

⇒ **자금이 계에 들어오는 입구가 없다.** 그래서:

- 이체 · 홀드 · 캡처가 **전부 도달 불가**
- 전기(posting)가 일어나지 않으니 `/ledger` 의 **시산표 · 기간 · 환율 · 불일치 네 피드가 전부 0**
- 콘솔에서 finance 도메인은 **쓰기가 하나도 성립하지 않는다**(4도메인 중 유일)

🔵 시드는 원장을 **일부러 손으로 넣지 않았다.** 이벤트 기반 전기가 `ensureAccountExists`
로 계정을 자동 생성하므로, 이체가 성립했다면 `/ledger` 데이터는 *투영이 동작한다*는
증거가 됐을 것이다. 손으로 넣으면 *시드가 넣었다*는 증거밖에 안 된다.

🔴 그리고 `/finance/accounts` 에 **목록 라우트가 없는 것**(id 조회뿐)도 같은 방향의
미완성이다 — 화면은 뜨지만 운영자가 무엇을 볼지 알 수 없다.

---

# Goal

계좌에 자금이 들어오는 **지원되는 경로**가 하나 있고, 그것을 쓰면 이체가 성립하고
원장 화면이 찬다.

# Scope

## In Scope

- 입금(또는 그에 상응하는 자금 유입) 경로 하나
- `specs/contracts/` 갱신 — **구현보다 먼저**
- `infra/demo/seed/seed-finance.sh` 가 그 경로로 이체까지 완주

## Out of Scope

- 실제 PG / 은행 연동
- 콘솔 화면 추가 — 별도 프런트 티켓(단, AC-4 가 상류 존재를 확인한다)

---

# 🔴 먼저 정해야 하는 것

`topUp()` 이 *"v1 stub"* 이라고 스스로 적어 둔 것은 **설계가 미완이라는 신고**다.
그것을 그대로 노출할지, 다른 모델을 택할지가 이 티켓의 첫 결정이다:

| 안 | 내용 | 물어야 할 것 |
|---|---|---|
| A | `topUp` 을 **정식 엔드포인트로 승격** | v1 stub 이 무엇을 미룬 것인가. 원장 전기를 실제로 만드는가, 잔액만 올리는가 |
| B | **외부 입금 이벤트 컨슈머**를 만든다 | 이 도메인의 이벤트 평면이 지금 살아 있는가(`@KafkaListener` 0건이 우연인가 설계인가) |
| C | 결제 도메인과의 **계약**으로 유입 | 크로스 도메인 ⇒ **ADR** |

🔵 **`topUp` 의 테스트 6개를 먼저 읽을 것** — 그 테스트가 "무엇이 참이어야 하는가" 에
대해 이미 답을 갖고 있을 수 있다. 이 저장소에서 *"스펙에 적혀 있다"* 는 추측이
인용으로 굳은 사례가 반복됐다; 인용 전에 열어 볼 것.

---

# Acceptance Criteria

- [ ] **AC-0 (결정)** — A/B/C 중 하나를 근거와 함께 고른다. C 면 ADR 을 쓰고
      **ACCEPTED 될 때까지 착수하지 않는다**
- [ ] **AC-1 (계약 우선)** — `specs/contracts/` 가 그 경로를 정의한다(멱등 규약 포함 —
      이 도메인은 이미 `Idempotency-Key` 를 강제하고 **실패 응답까지 재생**한다)
- [ ] **AC-2 (이체 성립)** — 시드 계좌 A→B 이체가 `INSUFFICIENT_AVAILABLE_BALANCE`
      없이 통과하고, **양쪽 잔액을 다시 읽어** 확인한다. 🔴 2xx 로 판정하지 말 것 —
      `seed-finance.sh` 는 처음에 이체 409 를 "이미 존재" 로 세어 첫 실행을 초록으로
      만들었다. 진짜 원인은 `ACCOUNT_NOT_ACTIVE` 였고, 그것을 갈라 본 덕에
      **빠져 있던 KYC 승급 단계**가 드러났다
- [ ] **AC-3 (원장 투영)** — 이체 후 `/api/finance/ledger/trial-balance` 의 원소 수가
      **0 이 아니다**. 🔵 이것이 이 티켓의 진짜 산출물이다 — 전기가 자동으로 따라오는지가
      확인된다. 🔴 프로젝션이 0 일 때 "지연" 과 "발행 부재" 를 **브로커 offset 으로**
      가를 것(이 저장소가 erp 에서 같은 함정을 밟았다)
- [ ] **AC-4 (콘솔)** — 콘솔 `/ledger` 4피드 중 최소 하나가 0 이 아니다.
      판정은 **BFF 원소 수**로 한다. 🔴 계측기가 껍데기를 1 로 세지 않게 할 것 —
      `{"accounts":[]}` 와 `{"feedEnabled":false,"rates":[]}` 를 각각 1 로 세어
      **빈 것을 있음으로 오독한** 전례가 있다
- [ ] **AC-5 (시드)** — `seed-finance.sh` 가 입금 → 이체까지 완주하고, 연속 2회 실행이
      **행 수로** 수렴한다
- [ ] **AC-6 (목록 라우트)** — `/finance/accounts` 가 목록을 줄 수 있는지 판단하고,
      범위 밖이면 **"안 한다" 를 숫자와 함께** 적는다(조용한 누락 금지)

# Related Specs

- `specs/services/account-service/architecture.md`
- `specs/services/ledger-service/architecture.md`
- `specs/contracts/`

# Edge Cases

- 입금 멱등 — 같은 키 재전송이 **잔액을 두 번 올리지 않는다**(이 도메인은 저장된 응답을 재생한다)
- 통화 불일치 입금
- KYC 미승급 계좌로 입금 — `ACCOUNT_NOT_ACTIVE` 가 맞는가

# Failure Scenarios

- **잔액만 올리고 전기를 안 만든다** → AC-3 이 0 으로 남는다. 화면은 계좌만 차고 원장은 빈다
- **stub 을 그대로 노출** → *"v1 stub"* 이 미룬 것이 무엇인지 모른 채 계약이 굳는다

# Definition of Done

- [ ] AC-0~AC-6 전부
- [ ] Ready for review
