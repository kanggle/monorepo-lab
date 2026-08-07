# Task ID

TASK-PC-FE-273

# Title

콘솔 `/ledger` 의 fx-rates 피드만 503 TIMEOUT — 나머지 3피드는 200

# Status

ready

# Owner

platform-console

# Task Tags

- frontend
- bff
- observed-gap

---

# 배경 — `TASK-FIN-BE-068` AC-4 실측 (2026-08-07)

FIN-BE-068 이 finance 입금 경로를 열어 원장이 실제로 차게 된 뒤, 콘솔 `/ledger` 4피드를
**BFF 원소 수**로 측정했다. 3개는 정상이고 하나만 다르다.

| 피드 | HTTP | 판정 |
|---|---|---|
| `/api/ledger/trial-balance` | 200 | `accounts` **3원소** ✅ |
| `/api/ledger/periods` | 200 | `data` **빈 배열** (데이터가 없는 게 맞다) |
| `/api/ledger/reconciliation/discrepancies` | 200 | `data` **빈 배열** (없는 게 맞다) |
| `/api/ledger/fx-rates` | **503** | `{"code":"TIMEOUT","message":"ledger unavailable"}` |

🔴 **`no-array`(503) 를 `array=0` 으로 세지 말 것.** 앞의 둘은 "물어봤고 없더라" 이고
fx-rates 는 "물어보지도 못했다" 다. 계측기가 이 셋을 구분하지 않으면 이 티켓은 존재조차
드러나지 않는다.

🔵 **선행 조건 (측정하려면 반드시 필요)** — 갓 로그인한 콘솔 세션은 활성 테넌트가 `iam`
이고, 그 상태에서는 4피드 **전부** `403 TENANT_FORBIDDEN` 이다. 이것은 "화면이 비었다"
와 완전히 다른 사건이다. `POST /api/tenant {"tenant":"demo-corp"}` 로 전환한 뒤라야
위 표가 재현된다.

---

# Goal

`/ledger` 의 fx-rates 피드가 200 을 내거나, 503 이 정당하다면 **그 이유가 화면과 문서에
드러난다**(조용한 실패 금지).

# Scope

## In Scope

- 503 의 출처 판별 — 상류 ledger-service 의 fx-rate 엔드포인트인가, BFF 합성 타임아웃인가,
  외부 rate provider 도달 실패인가
- 판별 결과에 따라: 배선 수정 **또는** "이 환경에서는 비활성" 을 화면이 말하게 하기

## Out of Scope

- 실제 외부 FX rate provider 연동
- 나머지 3피드 (측정상 정상)

---

# 🔴 먼저 갈라야 하는 것

`ADR-002` 에 따르면 fx rate feed 는 **외부 HTTP fetch + 캐시 + 스케줄 폴러**다. 그러므로
503 의 후보가 최소 셋이고, **셋의 처방이 서로 다르다**:

| # | 가설 | 확인 방법 |
|---|---|---|
| 1 | 외부 provider 미도달 (로컬엔 나갈 인터넷/스텁이 없다) | ledger-service 로그의 fetch 실패 + `fx_rate_quote` 캐시 행 수 |
| 2 | BFF 합성 타임아웃 (다른 도메인에서 5s 타임아웃이 false unavailable 을 낸 전례가 있다) | BFF 로그 `Composition-level timeout after 5000ms` 유무 |
| 3 | 상류 엔드포인트 자체가 이 프로파일에서 비활성 | ledger 게이트웨이로 직접 GET |

🔵 이전에 이 저장소에서 **fx-refresh 가 200 인데 `feedEnabled:false` 인 no-op** 이었던
전례가 있다. 즉 이 피드는 **성공 코드로도 거짓말한 적이 있다** — 200 을 받아도 본문의
`feedEnabled` 와 `rates` 길이를 같이 봐야 한다.

# Acceptance Criteria

- [ ] **AC-0** — 위 세 가설을 **로그/캐시 행 수/직접 호출**로 갈라 하나를 지목한다.
      추측으로 고르지 않는다
- [ ] **AC-1** — 원인이 배선이면 고치고, 환경 제약이면 **화면이 그 사실을 말한다**
      (빈 카드 + 조용한 503 금지)
- [ ] **AC-2** — 판정은 **BFF 원소 수 + `feedEnabled`** 로 한다. 🔴 200 만으로 닫지 말 것
- [ ] **AC-3** — 재현 절차에 **테넌트 전환 선행 단계**를 적는다(없으면 다음 사람이 403 을
      "빈 화면" 으로 오독한다)

# Related Specs

- `projects/finance-platform/specs/contracts/http/ledger-api.md` § FX rates (read)
- `projects/finance-platform/specs/services/ledger-service/architecture.md` § FX rate feed
- `projects/platform-console/.../features/ledger-ops/`

# Edge Cases

- provider 도달 실패 시 캐시된 마지막 rate 로 응답해야 하는가(계약 확인)
- 캐시가 비었을 때와 stale 일 때의 응답이 같은가

# Failure Scenarios

- 503 을 그냥 빈 배열로 바꿔 화면을 초록으로 만든다 → **없는 것과 못 물어본 것을 합쳐**
  진짜 장애를 영구히 가린다

# Definition of Done

- [ ] AC-0~AC-3
- [ ] Ready for review
