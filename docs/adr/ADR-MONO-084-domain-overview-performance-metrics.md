# ADR-MONO-084 — 도메인 개요에 **실적 지표**를 싣는다: 한 응답 모양 · 한국 시간 기간 · 백엔드 집계 (Fan 은 뺀다)

**Status:** PROPOSED
**Date:** 2026-10-23
**주관 티켓:** `TASK-MONO-805`
**Decision driver:** 소유자 요구(2026-10-23 대화) — «개요가 현재 상태만 보여 준다. 이번 달 · 이번 주 매출, 판매 수량 같은 인사이트가 없다» → «이커머스만이 아니라 모든 도메인에». 화면 배치(메뉴를 새로 만들지 않고 각 도메인 «개요» 맨 위에 둔다) · 시간대(한국 시간 통일) · Fan 제외 · 매출 기준(결제 확정 주문)은 같은 대화에서 소유자가 **추천대로 진행**으로 방향을 정했다. 🔴 그 문장은 이 ADR 의 ACCEPT 가 아니다(§ 수락 형식).
**Supersedes:** 없음.
**Related:** [`ADR-MONO-015`](ADR-MONO-015-platform-console-dashboards-model.md) (콘솔 «대시보드» = 업무 데이터 합성, Grafana 아님 — 이 ADR 은 그 안에서 **기간 축**을 더한다) · [`ADR-MONO-017`](ADR-MONO-017-platform-console-bff-architecture.md) (D3.B 도메인 안 개요 = console-web 직접 fan-out · D5 실패 격리) · [`ADR-MONO-081`](ADR-MONO-081-console-composition-in-the-console-server.md) (교차 도메인 합성 = 콘솔 서버) · [`ADR-MONO-079`](ADR-MONO-079-agencies-sellers-and-console-fan-management.md) D4-A · [`ADR-MONO-059`](ADR-MONO-059-fan-authoring-identity-plane.md) (팬 운영자 = 디렉터리 관리만) · `TASK-MONO-322`(이커머스 `/summary` 공용화) · `TASK-BE-648`(`CONFIRMED` = 사가만)

> 🟡 **PROPOSED** — 소유자 ACCEPT 는 정확형 **`ADR-MONO-084 ACCEPTED`** 로만 넘어간다. 이 ADR 은 갈래를 내놓지 않으므로 갈래 글자가 없다. «진행» · «좋아» · «추천대로» 는 수락이 아니다(`platform/architecture-decision-rule.md` § The ACCEPTED Gate).
> 이 문서는 구현을 인가하지 않는다. 단계 티켓은 ACCEPT PR 안에서 기안한다(§ 단계).

---

## Context

### 무엇을 정하나

콘솔의 도메인 «개요» 6곳(`console-nav-config.ts:172` WMS · `:219` SCM · `:263` Finance · `:282` ERP · `:314` E-Commerce · `:417` IAM)과 전체 홈(`:140`)은 거의 전부 **지금 이 순간의 상태**(건수 · 상태 분포 · 최근 목록)를 보여 준다. 소유자가 원하는 것은 **기간의 실적** — 이번 주/이번 달에 얼마나 팔렸나, 직전 기간보다 늘었나, 날마다 어떻게 움직였나 — 이다. 정할 것은 넷이다:

1. 실적 지표를 **어디에** 보여 주나(새 메뉴 vs 기존 개요)
2. 도메인마다 **어떤 모양**으로 내려주나
3. «이번 주» · «이번 달» 의 **경계**를 무엇으로 자르나
4. 무엇을 **실적으로 세나**(특히 돈)

### 지금 어떻게 생겼나 (실측 — 코드 · 계약, 2026-10-23 UTC, origin/main `bb4f73185`)

경로 약어: `CW` = `projects/platform-console/apps/console-web/src`.

#### ① 기간 지표가 있는 도메인은 둘뿐이고, 둘의 규칙이 다르다

| 도메인 | 개요의 기간 지표 | 출처 | 기간 경계 |
|---|---|---|---|
| E-Commerce | 영역 7곳의 오늘/주간/월간/전체 **생성 건수** | 영역마다 `GET …/summary` → `{today, week, month, total}` (`order-api.md:271-285`) | **한국 시간** 달력 — `KstPeriodBounds`(`libs/java-common/.../time/KstPeriodBounds.java:23-37`, `Asia/Seoul` 하드코딩) |
| E-Commerce | 상위 5 순위 4개(상품 · 셀러 × 주문 수 · 매출) | `GET /api/admin/orders/insights` (`order-api.md:295-320`) | **기간 없음** — 처음부터 지금까지 누적 (`OrderJpaRepository.java:164-176`, `WHERE` 에 날짜 조건 0) |
| WMS | 일별 입고 · 출고 추세 14일 | `GET /api/v1/admin/dashboard/throughput` (`admin-service-api.md:315`) | **UTC** 날짜 — 읽기 모델이 `atZone(ZoneOffset.UTC).toLocalDate()` 로 날을 자른다 (`InboundProjectionService.java:246` · `OutboundProjectionService.java:230`; 콘솔이 그 이유를 `CW/features/wms-ops/lib/throughput.ts:11-17` 에 적었다) |
| WMS | 출고 오늘/주간/월간 | 출고 목록을 `shippedAtFrom/To` 로 잘라 콘솔이 센다 | 한국 시간 (`CW/features/wms-ops/api/kst-period.ts`) |
| IAM · SCM · ERP · Finance | **없음** — 전부 `size=1` 목록의 `totalElements` | — | — |
| Fan | **개요 화면 자체가 없다** (`/fan` → `/fan/agencies` 리다이렉트) | — | — |
| 전체 홈 | 도메인 카드 6장, 각각 현재 숫자 하나 (`CW/features/operator-overview/api/operator-overview-types.ts`) | — | — |

🔴 **같은 콘솔 안에서 «오늘» 이 두 가지다.** 00:00–09:00 KST 사이에는 WMS 추세 차트의 «오늘» 이 E-Commerce 카드의 «오늘» 보다 하루 이르다.

#### ② 응답 모양이 셋이다

- E-Commerce `/summary`: 숫자 네 개(`PeriodSummary.java:18`). **건수만**이고, 직전 기간 값과 일별 추이가 없다.
- E-Commerce `/insights`: `{id, label, value}` 순위 배열. 기간이 없다.
- WMS throughput: 일별 배열 + 합계. 직전 기간 비교가 없다.

도메인 넷(IAM · SCM · ERP · Finance)은 집계 API 가 **하나도 없다**. 날짜 범위 필터가 있는 목록 API 도 일부뿐이다(IAM 감사 `from/to` · WMS 대시보드 목록들). SCM 발주 목록은 날짜 필터 자체가 없다(`scm-platform/specs/contracts/http/procurement-api.md` § `GET /po`).

#### ③ 돈을 세는 곳은 한 곳이고 기준이 넓다

E-Commerce 순위의 매출은 `SUM(unitPrice * quantity)` 에서 `CANCELLED` 만 뺀다(`OrderJpaRepository.java:160-166`). 그래서 **결제 전 `PENDING`** 주문도 매출에 들어간다. 주문의 상태(`order-api.md:248-249`): `CONFIRMED` = 결제 · 재고 예약 사가만 만든다(BE-648) · `BACKORDERED` = 결제는 됐지만 재고를 못 잡았다. 주문 엔티티에는 `paidAt` · `refundedAt` 이 있다(`OrderJpaEntity.java:60, 63`).

#### ④ 시간대 설정은 테넌트가 아니라 계정에 있다

`timezone`(기본 `Asia/Seoul`)은 **계정 프로필** 필드다(`iam-platform/specs/contracts/http/account-api.md:32`). 테넌트에는 시간대 칸이 없다. 기간 경계를 테넌트별로 바꾸려면 새 필드가 필요하다.

#### ⑤ Fan 은 운영자에게 실적 데이터가 닫혀 있다

`community-api.md:11-15` · `membership-api.md:13` — `FAN_OPERATOR` 토큰은 **어떤 경로든 403**(`ADR-MONO-079` D4-A · `ADR-MONO-059`). 운영자가 읽을 수 있는 Fan 데이터는 소속사 · 아티스트 · 그룹 디렉터리뿐이다.

---

## Decision

### D1 — 자리: 새 메뉴를 만들지 않는다. 각 도메인 «개요» 맨 위에 «실적» 줄을 둔다

- 도메인 개요 = 위에서부터 **① 실적 줄**(기간 선택 · KPI 카드 · 추세 차트 하나) → **② 현재 상태**(지금 있는 블록 그대로).
- 메뉴 이름은 «개요» 를 유지한다. 🔴 **«대시보드» 라는 새 메뉴를 만들지 않는다** — `/dashboards` 묶음(전체 홈 · 도메인 상태)이 이미 그 이름을 쓰고, 같은 이름이 두 곳이면 운영자가 어디로 갈지 헷갈린다.
- 분석 기능(임의 기간 · 쪼개 보기 · 내보내기)이 커지면 그때 도메인 메뉴 **안**에 «매출 분석» · «통계» 같은 하위 화면을 따로 만든다. 이 ADR 의 범위가 아니다.

### D2 — 모양: 모든 도메인이 같은 응답 하나를 내려준다

도메인마다 운영자 API 에 하나씩 둔다. 경로는 도메인의 기존 관례를 따른다(예: `GET /api/admin/orders/metrics` · `GET /api/v1/admin/dashboard/metrics`).

```
GET …/metrics?period=week|month
200 {
  "period": "month",
  "timezone": "Asia/Seoul",
  "current":  { "from": "2026-10-01T00:00:00+09:00", "to": "2026-10-23T14:05:00+09:00" },
  "previous": { "from": "2026-09-01T00:00:00+09:00", "to": "2026-09-23T14:05:00+09:00" },
  "metrics": [
    { "key": "revenue",  "unit": "money", "currency": "KRW", "current": 12340000, "previous": 11020000 },
    { "key": "units",    "unit": "count", "current": 812,   "previous": 790 },
    { "key": "orders",   "unit": "count", "current": 301,   "previous": 288 }
  ],
  "series": [
    { "date": "2026-10-01", "values": { "revenue": 410000, "units": 27, "orders": 10 } }
  ]
}
```

- **`previous` 는 같은 길이의 직전 구간**이다: 이번 달 1~23일 오후 2시 ↔ 지난달 1~23일 오후 2시. 지난달 **전체**와 비교하지 않는다(달의 중간에는 항상 «줄었다» 로 보인다). 지난달이 더 짧으면 그 달 말일에서 멈춘다.
- `series` 는 `current` 구간의 **날마다 한 줄**이고, 값이 없는 날도 0 으로 채워 내려준다.
- `metrics[].key` 는 도메인이 정하고 계약 문서에 적는다. 화면 문구(«매출» 등)는 콘솔이 `key` 로 붙인다.
- 돈은 `unit: "money"` + `currency` 이고, 정수 최소 단위(원)다. 🔴 **통화가 여럿이면 통화마다 한 줄**이고 환산하지 않는다(Finance — OD-3).
- 기존 E-Commerce `/summary` · `/insights` 와 WMS throughput 은 **지우지 않는다**. 지금 화면이 쓰고 있다. 새 응답이 그것들을 대신하는지는 단계 티켓이 소비자를 세어 정한다.

### D3 — 기간 경계: 한국 시간(`Asia/Seoul`) 달력, 상수는 한 곳

- «오늘» = 한국 시간 0시부터, «이번 주» = 월요일 0시부터, «이번 달» = 1일 0시부터, 모두 **지금까지**. 지금 `KstPeriodBounds` 가 쓰는 규칙 그대로다.
- 백엔드는 `KstPeriodBounds`(`libs/java-common`)만 쓴다. 이전 구간(`previous`)과 일별 경계 계산을 그 클래스에 더한다. 콘솔은 `kst-period.ts` 하나만 쓴다. 🔴 **서비스 안에 `ZoneId.of("Asia/Seoul")` 이나 `ZoneOffset.UTC` 날짜 자르기를 새로 쓰지 않는다.**
- **WMS 일별 추세를 한국 시간으로 옮긴다.** 읽기 모델의 날짜 열이 UTC 로 잘려 저장돼 있으므로(Context ①), 이것은 **데이터 문제**다. 투영을 KST 로 바꾸고 읽기 모델을 다시 쌓는 절차가 단계 티켓의 본체다. 화면만 바꿔서는 안 된다. 다시 쌓기가 끝나기 전까지 화면은 지금처럼 «UTC 기준» 이라고 말한다.
- 테넌트별 시간대는 **하지 않는다**. 테넌트에 그 칸이 없다(Context ④). 응답의 `timezone` 필드가 그 자리를 미리 잡아 둔다. 나중에 테넌트 시간대가 생기면 이 필드 값만 바뀐다.

### D4 — 계산 위치: 각 도메인 백엔드가 집계한다

- 콘솔이 목록 API 를 날짜로 잘라 세는 방식(WMS 출고 건수의 지금 방식)은 **건수만** 낼 수 있다. 금액 · 수량 합계를 못 내고, 기간마다 목록 호출이 늘어난다. 그래서 새 지표에는 쓰지 않는다.
- 도메인 안 개요는 지금처럼 console-web 이 그 도메인 API 를 **직접** 부른다(ADR-017 D3). 실패 격리도 지금과 같다: 실적 줄이 실패하면 **그 줄만** «불러올 수 없음» 을 띄우고 현재 상태 블록은 선다. 401 이면 다시 로그인한다.
- 집계는 그 도메인의 테넌트 범위 안에서만 한다. 판매자 범위 같은 데이터 범위 규칙이 있는 도메인은 그 규칙을 집계에도 똑같이 적용한다.

### D5 — 실적으로 세는 것: 도메인 계약 문서에 «무엇을 세나» 를 한 줄로 적는다

도메인마다 지표 하나당 **포함 상태 · 귀속 시각 · 제외** 를 계약에 적는다. 이 ADR 이 정하는 원칙은 하나다: **돈이 실제로 확정된 것만 돈으로 센다.**

| 도메인 | 지표(첫 판) | 세는 기준 |
|---|---|---|
| E-Commerce | 매출 · 판매 수량 · 주문 수 | 상태 ∈ {`CONFIRMED`, `SHIPPED`, `DELIVERED`}, **`paidAt`** 이 구간 안. `PENDING` · `CANCELLED` 제외 · 환불(`refundedAt` 있음) 제외. `BACKORDERED` = OD-1. 🔴 기존 `/insights` 순위도 같은 기준으로 바꾼다(지금은 `PENDING` 을 센다 — Context ③) |
| WMS | 입고 수량 · 출고 수량 · 출고 건수 | 지금 throughput 의 정의 그대로(`putawayCount/qtyReceived` · `shipmentCount/qtyShipped`), 경계만 KST |
| SCM | 발주 건수 · 발주 금액 · 입고 완료 건수 | 발주는 제출(`SUBMITTED` 이후) 시각 기준, `DRAFT` · `CANCELED` 제외 |
| ERP | 결재 상신 · 승인 · 반려 건수 | 각 전이가 일어난 시각 기준 |
| IAM | 로그인 성공 · 실패 · 계정 잠금 · 신규 계정 | 이벤트 발생 시각 기준 |
| Finance | 유형별 거래 건수 · 금액 | 확정된 거래만 — 보류(hold)는 **캡처(capture)된 금액만**, 해제(release)된 보류는 0. 통화마다 따로(OD-3) |

표의 «첫 판» 은 단계 티켓의 출발점이다. 지표를 더하거나 빼는 것은 그 도메인 계약 변경이지 이 ADR 의 개정이 아니다. 🔴 단, **원칙(확정된 돈만)을 어기는 지표는 이 ADR 의 개정이다.**

### D6 — Fan 은 이번 범위에서 뺀다

운영자에게 열린 Fan 데이터로는 «등록된 아티스트 수» 정도만 만들 수 있다(Context ⑤). 실적이라 부를 만한 것(구독 · 해지 · 게시글)은 `ADR-MONO-079` D4-A 를 다시 결정해야 열린다. 그 결정은 개인정보 · 권한이 걸린 별개의 결정이다. 여기에 섞으면 다섯 도메인이 그 결정을 기다리게 된다. 그 재결정이 나오면 Fan 은 이 ADR 의 D2~D5 를 그대로 따른다.

### D7 — 전체 홈은 마지막에, 도메인마다 지표 하나

전체 홈(`/dashboards/overview`)의 도메인 카드에 그 도메인의 **대표 지표 하나**(이번 주 값 · 직전 대비 증감)를 붙인다. 합성은 콘솔 서버가 한다(ADR-081). 응답 모양 · 실패 격리 · 카드 순서는 바꾸지 않고 칸만 더한다(`console-integration-contract.md` § 2.4.9.1). 도메인 다섯 곳의 D2 응답이 다 선 다음에 한다.

---

## 단계 (ACCEPT PR 에서 티켓으로 기안 — 지금은 목록만)

| 단계 | 내용 | 프로젝트 |
|---|---|---|
| P1 | 공용 바탕: `KstPeriodBounds` 에 이전 구간 · 일별 경계 · 응답 DTO / 콘솔 공용 «실적 줄» 컴포넌트(KPI 카드 · 증감 · 추세 차트 · 기간 선택 · 칸별 실패) | 루트 `libs/` · platform-console |
| P2 | E-Commerce: `metrics` (D5 기준) + `/insights` 기준 교정 + 계약 | ecommerce · platform-console |
| P3 | WMS: 투영 KST 전환 · 읽기 모델 다시 쌓기 · `metrics` + 계약 | wms · platform-console |
| P4 | ERP | erp · platform-console |
| P5 | SCM (발주 목록 날짜 필터 포함) | scm · platform-console |
| P6 | IAM | iam · platform-console |
| P7 | Finance | finance · platform-console |
| P8 | 전체 홈 카드 (D7) | platform-console |

P2~P7 은 서로 독립이다. 다만 전부 콘솔의 같은 공용 컴포넌트를 쓰므로 **P1 이 먼저**다. 콘솔 쪽 변경은 공유 파일(네비 · 공용 컴포넌트)을 건드리므로 직렬로 머지한다.

---

## Alternatives Considered

- **«대시보드» 메뉴를 새로 만든다** — 기각. 이름이 `/dashboards` 와 겹친다. 운영자가 도메인에 들어와 처음 묻는 것은 «장사가 어떤가» 와 «처리할 게 있나» 둘 다라서, 실적을 다른 메뉴로 빼면 덜 열린다. 지금 필요한 양(KPI 몇 개 + 차트 하나)은 한 화면에 들어간다.
- **도메인마다 자유로운 모양** — 기각. 콘솔이 도메인 수만큼 화면 코드를 갖게 되고, «직전 대비» 의 뜻이 도메인마다 달라진다. 지금 E-Commerce · WMS 가 이미 서로 다른 모양이다(Context ②).
- **콘솔이 목록을 잘라 센다** — 기각(D4). 금액 · 수량을 못 낸다.
- **UTC 로 통일** — 기각. 운영자 달력과 어긋나 매일 오전 9시에 «오늘» 이 0 으로 돌아간다. 한국 시간 쪽이 이미 다수다(E-Commerce 7개 영역 · WMS 출고 건수).
- **테넌트별 시간대를 지금 만든다** — 미룸(D3). 테넌트에 칸이 없고, 지금 테넌트는 전부 한국 회사다. 응답의 `timezone` 필드가 나중의 자리를 잡아 둔다.
- **Grafana · 별도 분석 저장소** — 범위 밖. `ADR-MONO-015` D2 가 콘솔 대시보드를 업무 데이터 합성으로 정했다. 데이터 양이 집계 쿼리로 감당이 안 되는 시점이 오면 그때의 별도 결정이다.

---

## Consequences

- 🟢 운영자가 도메인 개요에서 기간 실적과 직전 대비를 본다. 일곱 화면이 같은 모양이라 읽는 법을 한 번만 배운다.
- 🟢 콘솔 안의 «오늘» 이 하나가 된다(WMS 다시 쌓기 이후).
- 🔴 **E-Commerce 순위 숫자가 바뀐다** — `PENDING` 이 빠지고 기간이 생긴다. 줄어든 숫자는 버그가 아니라 기준 교정이다. 단계 티켓이 바뀌기 전후 값을 기록한다.
- 🔴 **WMS 읽기 모델 다시 쌓기**는 운영 데이터 작업이다. 데모의 구워진 시드에도 영향이 있다. 단계 티켓 AC-0 은 «데이터를 어떻게 옮기나» 를 코드보다 먼저 잰다.
- 🔴 집계 쿼리가 개요를 열 때마다 돈다(`force-dynamic`). 데모 규모에서는 문제가 없다고 **추정**한다(⚪ 측정 안 함). 단계 티켓이 쿼리 계획 · 인덱스를 확인한다. 미리 집계한 테이블은 측정이 필요하다고 말할 때 만든다.
- ⚪ Fan 은 실적 줄이 없는 채로 남는다(D6).

## Verification

- 각 단계 티켓: 계약 문서 먼저 · 경계 시험(한국 시간 0시 직전/직후, 월말, 짧은 달의 `previous`) · 포함/제외 상태 시험(D5 표의 각 칸) · 대조군(제외 상태 주문을 넣어도 매출이 안 움직임) · 콘솔 칸별 실패 시험.
- 🔴 **시간대 시험은 호스트 시간대에 기대지 않는다** — `Clock` 주입으로 시각을 고정한다. 이 저장소의 작성 호스트는 KST, CI 는 UTC 라서 «로컬 초록 · CI 빨강» 이 난다.
- P3: 다시 쌓기 전후로 같은 구간의 합계가 같아야 한다(날 경계만 옮겨지고 총량은 보존).

## Outstanding / Open Decisions (기본값 = 구현자 선택, ACCEPT 시 답하면 그 답으로)

| OD | 질문 | 기본값 |
|---|---|---|
| OD-1 | E-Commerce `BACKORDERED`(결제됨 · 재고 못 잡음)를 매출에 넣나 | **넣지 않는다** — `CONFIRMED` 가 되는 순간 그날의 `paidAt` 기준으로 들어간다. 이 지연 때문에 지난 날의 값이 나중에 바뀔 수 있다는 것을 계약에 적는다 |
| OD-2 | 매출 금액은 할인 전인가 후인가 | **할인 후 결제 금액** — 단계 티켓 AC-0 이 `totalPrice` · `discountAmount` 의 실제 의미를 재고 계약에 적는다 |
| OD-3 | Finance 다통화 | **통화마다 한 줄, 환산 없음** |
| OD-4 | 기간 선택에 «오늘» 을 넣나 | **넣지 않는다** — 주 · 월 둘. «오늘» 은 지금 카드의 오늘 숫자가 계속 보여 준다 |

## 수락 형식

- 정확형: **`ADR-MONO-084 ACCEPTED`** (갈래 없음). OD 에 답하려면 같은 문장에 `OD-1=…` 처럼 붙인다. 답이 없는 OD 는 기본값으로 간다.

## History

- 2026-10-23 UTC — PROPOSED (`TASK-MONO-805`).
