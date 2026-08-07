# Task ID

TASK-SCM-BE-060

# Title

발주가 `DRAFT` 밖으로 나갈 수 없다 — 상신이 **어느 compose 에도 없는 서비스**를 호출한다

# Status

done

# Owner

scm-platform

# Task Tags

- backend
- infra
- demo-gap

---

# 배경 — `TASK-MONO-510` 6회차 실측

시드가 발주 3건을 만들고 상신을 시도했다:

```
[seed:scm] 관측  SCM-PO-0002 → SUBMITTED 불가 — supplier-mock 이 데모 스택에 없다 (status=DRAFT 유지)
[seed:scm] 생략  SCM-PO-0003 → CONFIRMED — 선행 SUBMITTED 가 없다
```

## 실측

| 확인 | 결과 |
|---|---|
| `submit` 이 부르는 곳 | `http://supplier-mock:9090` — **실제 HTTP 호출** |
| 저장소 전체에서 `supplier-mock` 서비스 정의 | **0건** (`application.yml` 의 URL 로만 존재) |
| federation 스펙 주석 | 같은 말을 적어 두었다 |

⇒ **DRAFT 가 이 도메인의 종착 상태다.** 콘솔 `/scm/procurement` 은 DRAFT 만 보여준다.

🔵 시드는 이것을 **우회하지 않았다.** 우회하면 화면은 차지만 *제품이 하지 못하는 일을
시드가 대신한 것*이 된다. 그래서 `⛔ 관측` 으로 남기고 그 지문(`503` + 본문에
`supplier-mock`)이 맞을 때만 그렇게 분류한다 — 지문이 어긋나면 그대로 실패로 센다.
⇒ **이 티켓이 닫히면 시드는 한 줄도 안 고쳐도 저절로 초록이 된다.**

---

# Goal

발주가 `DRAFT → SUBMITTED → CONFIRMED` 를 로컬/데모에서 완주한다.

# Scope

## In Scope

- 상신 경로의 외부 의존을 **로컬에서 성립하게** 만든다
- `specs/` 에 그 결정을 남긴다

## Out of Scope

- 실제 공급사 EDI/API 연동
- `TASK-SCM-BE-059`(공급사 등록 API 부재) — 선행이지만 별개

---

# 🔴 먼저 정해야 하는 것 — 이것은 구현 이전에 **결정**이다

세 갈래가 있고 **셋이 서로 다른 것을 주장한다**. 착수자가 임의로 고르지 말 것:

| 안 | 내용 | 대가 |
|---|---|---|
| A | `supplier-mock` 컨테이너를 compose 에 **추가** | 데모 스택에 컨테이너 1개 추가(메모리 예산 — `TASK-MONO-399`). 상신이 "실제 HTTP" 라는 성질은 보존된다 |
| B | 상신을 **비동기/아웃박스**로 바꿔 외부 응답을 기다리지 않게 한다 | 아키텍처 변경 ⇒ **ADR 필요**. 상태 기계의 의미가 바뀐다 |
| C | 어댑터에 **폴백 프로파일**(외부 부재 시 낙관 전이) | 🔴 가장 위험 — 제품이 못 하는 일을 설정이 대신하게 된다. 데모에서 초록인데 운영에서 다른 코드경로 |

⇒ **AC-0 이 이것을 가른다.** 판단 근거는 "어느 것이 쉬운가" 가 아니라 **"상신이
외부 확인을 요구한다는 것이 도메인 규칙인가, 구현 사정인가"** 다. 그 답이 A(규칙이다)
면 mock 을 세우는 것이고, B(구현 사정이다)면 ADR 이다.

---

# 🟢 착수 (2026-08-07) — **A 채택, 완주.** 그리고 A 는 ADR 이 필요 없었다

## ⓪ AC-0 — 사용자가 **A** 를 선택. 그런데 착수 시 실측이 그 선택을 더 강하게 만들었다

```
application.yml:129   base-url: ${SUPPLIER_MOCK_BASE_URL:http://supplier-mock:9090}
저장소 전체에서 그 mock 의 정의   0건 (compose · wiremock · mockserver 어디에도 없음)
```

⇒ **이음매가 이미 외부화돼 있고 아무도 채우지 않았다.** 아키텍처 변경이 아니라
**배선 누락**이다 — 그래서 A 는 이 티켓의 ADR 게이트에 걸리지 않는다(게이트는 B 전용).
🔵 "상신이 외부 확인을 요구하는 것이 도메인 규칙인가" 라는 AC-0 의 질문에 대해,
`application.yml` 이 URL 을 **설정 가능한 값으로** 들고 있다는 사실 자체가 답이었다.

## ① 🔴🔴 A 의 범위가 예상보다 컸다 — **ack 콜백 없이는 아무것도 안 끝난다**

착수 전 계획은 *"mock 이 submit 에 200 만 내면 된다"* 였다. `PoStatusMachine` 을 열자
그것이 **PO 를 SUBMITTED 에 세워 둘 뿐**임이 드러났다:

```
BUYER/OPERATOR : DRAFT → SUBMITTED
SUPPLIER       : SUBMITTED → ACKNOWLEDGED      ← ack 웹훅. 운영자가 못 한다
OPERATOR       : ACKNOWLEDGED → CONFIRMED      ← 선행이 SUBMITTED 가 아니다
```

⇒ mock 은 `POST /api/procurement/webhooks/supplier-ack` 를 **HMAC-SHA256 서명과 함께
되돌려 불러야** 한다(`timestamp + "." + rawBody`, 헤더 `X-Supplier-Signature`
· `X-Supplier-Timestamp`, freshness 300초, 서명 nonce 재사용 거절).

## ② 구현 — `projects/scm-platform/infra/supplier-mock/supplier_mock.py`

표준 라이브러리만 쓰는 단일 파일. `python:3.12-alpine` 에 **읽기 전용 마운트** —
빌드할 이미지도, 갱신할 의존성도 없다. compose 는 **프로젝트 자신의**
`docker-compose.yml` 에 넣었다(데모 오버레이가 아니라) — 매달린 기본값이 제품 설정에
있으므로 그 짝도 제품 compose 에 있어야 같은 드리프트가 재발하지 않는다.

## ③ 🔴 만들면서 실측한 함정 3건 (다음 사람 몫)

| # | 함정 | 어떻게 드러났나 |
|---|---|---|
| 1 | **Spring `RestClient` 는 이 제출을 chunked 로 보낸다** | `Content-Length` 만 읽던 첫 판이 본문을 **0바이트**로 보고 전건 400. 게다가 그 거절 경로에 **로그가 없어** "mock 이 호출조차 안 됐다" 처럼 보였다 — 계측기가 조용히 실패했다. 지금은 두 프레이밍을 다 읽고, **거절할 때 무엇을 봤는지 찍는다** |
| 2 | **ack 가 submit 트랜잭션과 경합한다** | `submit()` 은 공급사 호출을 **먼저** 하고(Edge Case #7) 그 뒤에 커밋한다 ⇒ 즉시 ack 하면 아직 `DRAFT` 인 PO 를 보고 거절당한다. **고정 지연이 아니라 유계 재시도**로 처리했다 — 고정 지연은 남의 커밋 지연에 대한 추측이라 빠른 호스트에서 통과하고 느린 호스트에서 썩는다 |
| 3 | **재시도는 타임스탬프를 다시 찍어야 한다** | 검증기가 **이미 본 서명을 재생으로 거절**한다. 같은 timestamp 로 재시도하면 서명이 같아져 **구조적으로 성공할 수 없는** 재시도가 된다 |

## ④ 🔴 남은 비대칭 (우회하지 않고 기록) — ack 는 제출이 알려주지 않는 필드를 요구한다

`SupplierAckWebhookRequest` 는 `tenantId` 를 **필수**로 요구하는데
`RestSupplierAdapter.toSupplierPayload` 는 poId/poNumber/supplierId/currency/
totalAmount/lines 만 보낸다. **실제 공급사라도 그 값을 알 길이 없다** — 구매자 쪽
파티션 키다. 그래서 mock 에 `ACK_TENANT_ID` 로 대역 밖 주입한다(데모는 `demo-corp`).

🔵 이것을 "mock 의 한계" 로만 적지 않는다 — **제품 outbound 계약의 비대칭**이다.
제출 페이로드가 테넌트를 싣게 되면 그 env 를 지우고 요청에서 읽으면 된다.

## ⑤ 🔴 시드의 술어가 틀려 있었다 (2건) — 배선이 성립하자 드러났다

배선 직후 시드가 여전히 `✗` 를 냈다. 시드 탓이었다:

- **`= "$want"` 정확 일치.** ack 가 비동기로 도착해 submit 직후 상태가 이미
  `ACKNOWLEDGED` 다. 앞으로 나아간 것을 실패로 셌다 ⇒ `po_rank()` 로 **`>=` 판정**.
- **주석이 `"confirm 은 SUBMITTED 에서만 유효"` 라고 적혀 있었다.** 상태 기계는
  `ACKNOWLEDGED → CONFIRMED` 다. 상태 기계를 안 열어 보고 쓴 문장이었다
  ⇒ `po_await_rank()` 로 ack 를 기다린 뒤 confirm.
- 🔴 그리고 **"이 실행이 전이시켰나" 를 상태코드로 판정할 수 없다** — 고정
  `Idempotency-Key` 라 재실행의 confirm 도 서버가 **저장된 2xx 를 재생**한다(BE-445).
  판정을 **`po_status_history` 행 수 전후**로 바꾸자 재실행의 유령 전이가 0이 됐다.
  (이 시드가 PO *생성* 에서 이미 쓰던 해법을 전이에 적용하지 않았던 것이다.)

---

# Acceptance Criteria

- [x] **AC-0 (결정)** — **A 채택**(사용자 지시, 2026-08-07). 🔵 착수 시 실측이 A 를
      "ADR 불요" 로 확정했다 — 이음매(`SUPPLIER_MOCK_BASE_URL`)가 이미 외부화돼 있고
      정의만 0건이므로 배선 누락이다. B(비동기화)를 골랐다면 ADR 이 필요했다
- [x] **AC-1 (완주)** — **깨끗한 볼륨**에서 기동 → 시드 1회차 `생성 11 · 기존 0 · 실패 0`,
      rc=0. DB 실측:
      ```
      purchase_orders   PO-4F985BB9 DRAFT · PO-14202AD4 ACKNOWLEDGED · PO-860E2489 CONFIRMED
      po_status_history DRAFT→SUBMITTED OPERATOR ×2
                        SUBMITTED→ACKNOWLEDGED **SUPPLIER** ×2   ← 서명 검증을 통과한 웹훅만 만들 수 있는 행
                        ACKNOWLEDGED→CONFIRMED OPERATOR ×1
      ```
      🔵 **2xx 로 판정하지 않았다.** 그리고 이력의 `actor_type=SUPPLIER` 가 ack 가
      실제로 HMAC 필터를 통과했다는 증거다 — 응답 코드보다 강한 증거다
- [~] **AC-2 (시드 자동 회복)** — 🔴 **이 AC 의 전제가 틀렸다.** 지문 기반 `⛔ 관측`
      분류는 의도대로 사라졌지만(그 부분은 충족), 시드가 **그것과 무관한 이유로**
      계속 빨갰다 — 위 ⑤의 술어 결함 3건. 배선이 성립해야 비로소 드러나는 것들이라
      "고치지 않고" 가 성립할 수 없었다. 시드를 고쳤고 **무엇을 왜 고쳤는지 ⑤에 적었다**
- [x] **AC-3 (화면)** — 콘솔 `/scm/procurement` BFF 실측: 원소 **3**,
      상태 **`["CONFIRMED","ACKNOWLEDGED","DRAFT"]`** ⇒ DRAFT 밖으로 나갔다.
      🔵 원소 수만으로는 부족하다 — 이 티켓의 주장은 "DRAFT 를 벗어난다" 이므로
      **상태값**이 판정이다(전에도 3건이었고 전부 DRAFT 였다)
- [x] **AC-4 (메모리)** — `supplier-mock` **16.81 MiB**. scm 슬라이스 9컨 ≈ **2,537 MiB**
      (procurement 538 · inventory-visibility 477 · demand-planning 451 · logistics 357 ·
      kafka 334 · gateway 276 · postgres 84 · redis 3.5 · **supplier-mock 17**).
      ⇒ 추가분은 슬라이스의 **0.4%** — `TASK-MONO-399` 의 예산에 유의미한 영향 없음

# Related Specs

- `specs/services/procurement-service/architecture.md`
- `specs/contracts/` — 발주 상태 전이

# Edge Cases

- 상신 중 외부가 5xx — 재시도인가 실패 확정인가. 상태 기계가 그 답을 이미 갖고 있는가
- 상신했으나 확정 전에 취소

# Failure Scenarios

- **C 를 조용히 고른다** → 데모는 초록인데 운영 코드경로가 달라진다. 가장 흔하고 가장 늦게 드러난다
- **AC-1 을 2xx 로 판정** → 위 실측이 보여준 그대로, 거절이 초록이 된다

# Definition of Done

- [x] **AC-0 · AC-1 · AC-3 · AC-4 충족.** AC-2 는 `[~]` — 그 AC 의 전제("시드를 고치지
      않고")가 성립할 수 없었고, 무엇을 왜 고쳤는지 ⑤에 적었다(조용한 누락 없음)
- [x] **깨끗한 볼륨에서 검증** — `down -v` 후 재기동, 시드 1회차 `생성 11 · 기존 0 · 실패 0`
- [x] **멱등** — 재실행 `생성 4 · 기존 7 · 실패 0`, `purchase_orders` **3행 불변**.
      🔵 판정은 로그 라벨이 아니라 **행 수**로 했다(고정 Idempotency-Key 라 2xx 가 재생된다)
- [x] 가이드 한계 표 갱신 — "DRAFT 밖으로 못 나간다" 행 **회수**, ④의 테넌트 비대칭 추가
- [ ] Ready for review
