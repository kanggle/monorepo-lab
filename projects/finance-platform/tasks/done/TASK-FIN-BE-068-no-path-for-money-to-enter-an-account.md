# Task ID

TASK-FIN-BE-068

# Title

계좌에 돈이 들어갈 길이 없다 — 이체·홀드·캡처와 원장 전 화면이 그 하나에 막혀 있다

# Status

done

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

## ✅ AC-0 결정 — **A (`topUp` 을 운영자 전용 엔드포인트로 승격)**

### 테스트 6개를 읽은 결과 — 기대와 반대였다

**6개 중 topUp 이 주어인 것은 0개다.** 전부 `openActiveFullKyc(...)` → `topUp(...)` →
그리고 **다른 것**(hold · capture · release · transfer · 멱등 · 감사)을 단언한다.
`AccountLifecycleIntegrationTest:48` 의 주석이 그 성격을 자백한다 —
*"Seed ledger via the v1 internal/stub funding source."*

⇒ 티켓의 기대(*"그 테스트가 이미 답을 갖고 있을 수 있다"*)는 **빗나갔다**. 갖고 있지
않았다. topUp 의 계약(누가 부를 수 있나 · 무엇이 거절되나 · 통화가 다르면)에 대한
커버리지는 **0** 이었다. 그래서 "stub 을 그대로 노출" 이 작은 변경일 수 없었다 —
**공개하기 전에 행위를 먼저 못박아야** 했다(이번에 6건 추가).

### B 와 C 는 선언된 v1 범위와 충돌한다 (추론 아님, 스펙 인용)

| 안 | 판정 | 근거 (열어서 확인) |
|---|---|---|
| B (Kafka 컨슈머) | ❌ | `architecture.md` §Service Type — *"`PROJECT.md` 의 `event-consumer` 는 **v2 `notification-service`** 를 위해 예약"*, v1 account-service 는 인바운드 이벤트를 소비하지 않는다. 리스너 추가 = Service Type 재분류 ⇒ HARDSTOP-09/10 |
| C (결제 도메인 계약) | ❌ | §Responsibilities MUST-NOT — *"v1 has no real external adapter"*. 외부 유입은 v2 |
| **A** | ✅ | 아래 |

### "v1 stub" 이 미룬 것은 **출처**이지 연산이 아니다

티켓은 *"stub = 설계가 미완이라는 신고"* 라고 적었다. **그 읽기가 틀렸다.**
Javadoc 을 끝까지 읽으면 *"There is no real external bank adapter in v1 (that is v2)"*
— 미뤄진 것은 **돈을 대는 어댑터**다. 연산 자체는 이미 프로덕션 품질이다:

```
gateOrFail(TOPUP)          F4 게이트를 그대로 통과 (KYC 상한 + AML 스크리닝)
balance.credit             단일 잔액 writer
audit(AGG_BALANCE,TOPUP)   감사 행
publishSettledAndCompleted 아웃박스 이벤트
```

그리고 **하류는 이미 전부 지어져 있었다** — `PostingPolicy:52` 의 `case TOPUP ->`
(DR CASH_CLEARING / CR wallet), 그리고 ledger IT **7개**가 TOPUP 이벤트를 **손으로
빚어서** 각자의 진짜 주제를 세팅한다(`LedgerEndToEnd`·`GlFeed`·`ManualPosting`·
`FxRevaluation`·`FxSettlement`·`FxRateFeed`·`FxRateConsumption`).

🔵 **하류가 손으로 만들고 있는 이벤트가 있는데 상류에 그걸 낼 입구가 없다면, 그것이
누락의 지문이다.** 파이프는 완성돼 있었고 초인종만 없었다.

### 🔴 승격이 새로 만드는 위험과 그 처리

내부 메서드를 HTTP 로 열면 **인증된 누구나 자기 잔액을 찍어낼 수 있다**. 이건 stub
문제가 아니라 인가 문제다 ⇒ **운영자 전용**, 게이트는 **애플리케이션 계층**에 둔다
(`SecurityConfig` 는 POST 에 `finance.write` 스코프면 통과시키므로 컨트롤러 검사만으로는
스코프만 가진 홀더 토큰을 못 막는다). `upgradeKyc` 와 같은 자리.

🔴 그리고 **게이트 없는 `topUp(actor,id,long)` 오버로드를 남기지 않았다.** 남기면
운영자 검사가 보지 못하는 발권 경로가 그대로 유지된다 — 그게 애초에 이 결함이 생긴
모양이다. 시그니처는 하나이고 테스트 픽스처 6개도 그 하나를 통과한다.

---

# 🔴🔴 착수 후 드러난 두 번째 결함 — 이벤트 봉투에 `tenantId` 가 없었다

AC-3 이 **60초 동안 0** 이었다. 티켓의 지시대로 "지연" 과 "발행 부재" 를 브로커에서 갈랐다:

```
finance.transaction.completed.v1        offset 3   ← 발행됐다
finance.ledger.entry.posted.v1          offset 3   ← 소비되고 전기까지 됐다
finance.transaction.completed.v1.DLT    offset 0   ← 실패 없음
journal_entry 3행 / journal_line 6행               ← 장부에 있다
```

⇒ **지연도 발행 부재도 아니었다.** 투영은 완벽히 동작했고 판정만 0 이었다. 진짜 원인:

```
accounts.tenant_id     = demo-corp     (계좌의 실제 테넌트)
journal_line.tenant_id = finance       (원장에 적힌 테넌트)
```

`OutboxAccountEventPublisher.writeEvent` 가 **7필드 봉투**를 쓰는데 거기에 `tenantId`
가 없다. 그런데 `finance-account-events.md` §Envelope 와 `finance-ledger-events.md`
**둘 다 `tenantId` 를 봉투 필드로 문서화**하고 있다. 소비자
`TransactionEnvelope.effectiveTenantId()` 는 없으면 **리터럴 `"finance"`** 로 떨어진다.

⇒ **`finance` 가 아닌 모든 테넌트의 전기가 남의 테넌트로 적힌다.** 돈의 주인인 운영자는
자기 장부를 영원히 못 읽는다. 이 티켓 전에는 완료된 거래가 0건이라 전기도 0건이었고,
그래서 **오적재가 드러날 자리 자체가 없었다.**

## 🔴🔴 그 결함을 지키고 있던 것은 **테스트**였다

`OutboxAccountEventPublisherTest` 가 `containsExactly` 로 7필드를 **못박고** 있었다.
*"리팩터 전후로 wire 가 안 바뀐다"* 를 증명하려고 쓴 가드인데, **그 wire 가 구현해야 할
계약과는 한 번도 대조되지 않았다.** 그래서 처음부터 빠져 있던 필드를 불변식으로 승격시켰다.

🔵 교훈은 **비교의 방향**이다. *"지난 리팩터 이후로 안 변했다"* 는 *"계약에 맞는다"* 보다
약한 술어이고, **기준선이 현재 산출물인 검사는 처음부터 없던 것을 절대 못 잡는다.**

**범위 판단**: AC-3 이 이 티켓의 선언된 "진짜 산출물" 이고 그것 없이는 통과할 수 없다.
고칠 내용이 **이미 계약에 적혀 있던 것**(새 설계 결정 아님)이고 변경이
`account-service` 안에서 끝난다(공유 `libs/` 무관) ⇒ 이 티켓에서 처리했다. 추가만 하므로
하위 호환이고, **이미 잘못 적힌 전기는 다시 쓰지 않는다**(생산자만 고친다).

---

# Acceptance Criteria

- [x] **AC-0 (결정)** — **A** 를 골랐다. 근거는 위 § "AC-0 결정" (B/C 는 선언된 v1 범위와
      충돌 — 스펙 인용). C 가 아니므로 ADR 게이트 해당 없음
- [x] **AC-1 (계약 우선)** — `account-api.md` §`POST /{id}/topups` 를 **구현보다 먼저**
      작성. 멱등 규약 명시 + 🔴 *"멱등은 저장된 응답 재생이지 잔액 검사가 아니다 —
      상태코드로 '이번에 돈이 움직였나' 를 판정하지 말 것"* 을 계약 본문에 박았다.
      `architecture.md` §Balance Model / §Responsibilities 갱신,
      `finance-ledger-events.md` 의 **거짓 문장 정정**(아래)
- [x] **AC-2 (이체 성립)** — 실측, 양쪽 잔액 재독:
      `topup A 0→500000` · `topup B 0→500000` · `transfer A 500000→400000, B 500000→600000`.
      2xx 로 판정하지 않았다
- [x] **AC-3 (원장 투영)** — `trial-balance` **3계정**, `t=5s` 에 도달.
      `CASH_CLEARING` DR 1,000,000 / `CUSTOMER_WALLET:A` DR 100,000·CR 500,000 /
      `CUSTOMER_WALLET:B` CR 600,000, **DR 합 = CR 합 = 1,100,000 (in balance)**.
      🔵 지시대로 브로커 offset 으로 갈랐고, 그 덕에 `tenantId` 결함이 드러났다(위 §)
- [x] **AC-4 (콘솔)** — BFF 원소 수로 판정, **4피드 중 1개 비어있지 않음**:
      `trial-balance array=3` · `periods array=0` · `discrepancies array=0` ·
      `fx-rates **no-array**(HTTP 503 TIMEOUT)`.
      🔵 계측기가 셋을 구분한다 — **원소 있음 / 진짜 빈 배열 / 배열이 아예 없음**.
      마지막을 0 으로 세지 않는 것이 이 AC 의 요구였다
      🔴 **선행 함정**: 갓 로그인한 콘솔 세션은 테넌트가 `iam` 이라 4피드 전부
      `403 TENANT_FORBIDDEN` 이다. 이건 "화면이 비었다" 와 **글자만 다르고 결론이 정반대**다
      — 물어보지도 못한 것이다. `POST /api/tenant {"tenant":"demo-corp"}` 로 전환한 뒤라야
      측정이 성립한다
- [ ] **AC-5 (시드)** — ⚠️ **미완: `infra/demo/seed/seed-finance.sh` 편집이 이 세션에서
      분류기에 하드 차단됐다**(3줄짜리 주석 수정도 동일하게 차단 — 내용 크기가 아니라
      파일 단위). 우회하지 않고 **패치를 사용자에게 인계**한다. 패치가 하려는 판정은
      시드가 아닌 프로브로 **전부 실측 완료**했다: 입금→이체 완주 + 동일 키 재실행 시
      `ledger 400000 → 400000` **불변**(이중 입금 없음)
- [x] **AC-6 (목록 라우트)** — **안 한다**, 숫자와 함께 `account-api.md` 에 기록:
      영향 화면 **1개** · 데모에서 id 가 필요한 계좌 **2개**(시드가 출력) ·
      목록을 받쳐줄 repository 메서드 **0개**. 진짜 장벽은 배선이 아니라 `owner_ref`
      **암호화 저장**(F7)이라 운영자가 실제로 원하는 "고객으로 찾기" 는 blind index /
      searchable encryption **설계 결정**을 요구한다 — 누락이 아니라 별개 문제

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

- [x] AC-0~AC-4, AC-6 (AC-5 는 분류기 차단으로 패치 인계 — 위 참조)
- [x] Ready for review

---

# 후속으로 넘긴 것 (조용히 두지 않는다)

| 항목 | 어디로 |
|---|---|
| `seed-finance.sh` 입금→이체 패치 | `infra/demo/seed/TASK-FIN-BE-068-seed-finance.patch.md` (분류기 차단 → 사용자 적용) |
| 콘솔 `/ledger` fx-rates 피드 **503** | `TASK-PC-FE-273` (나머지 3피드는 정상) |
| 이미 `finance` 로 잘못 적힌 기존 전기 | **다시 쓰지 않는다.** 생산자만 고쳤다. 데모 스택은 볼륨을 비우면 사라지고, 운영 데이터가 생기기 전이라 마이그레이션 대상이 없다 — 생기면 그때가 별도 결정이다 |
| `withdraw` 엔드포인트 | 만들지 않았다. 근거를 `architecture.md` § Balance Model 에 적었다(출금의 상대는 같은 v2 은행 어댑터라 v1 엔 목적지가 없다) |
