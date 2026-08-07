# Task ID

TASK-SCM-BE-060

# Title

발주가 `DRAFT` 밖으로 나갈 수 없다 — 상신이 **어느 compose 에도 없는 서비스**를 호출한다

# Status

ready

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

# Acceptance Criteria

- [ ] **AC-0 (결정)** — 위 A/B/C 중 하나를 **근거와 함께** 고른다. B 를 고르면
      `docs/adr/` 에 ADR 을 쓰고 **ACCEPTED 될 때까지 착수하지 않는다**
      (`platform/architecture-decision-rule.md`)
- [ ] **AC-1 (완주)** — 데모 스택에서 `DRAFT → SUBMITTED → CONFIRMED` 가 성립하고,
      **DB 의 `purchase_orders.status`** 로 확인한다. 🔴 2xx 로 판정하지 말 것 —
      `seed-scm.sh` 가 처음에 `409|422 → "이미 그 상태"` 로 세어 **진짜 거절을 초록으로**
      만들었다(DRAFT 는 confirm 할 수 없다). 지금은 상태를 **다시 읽어** 판정한다
- [ ] **AC-2 (시드 자동 회복)** — `seed-scm.sh` 를 **고치지 않고** 재실행하면
      `⛔ 관측` 줄이 사라지고 상신·확정이 성립한다. 🔵 그렇지 않다면 지문 기반 분류가
      깨진 것이므로 그 자체가 결함이다
- [ ] **AC-3 (화면)** — 콘솔 `/scm/procurement` 에 `DRAFT` 아닌 상태의 발주가 보인다
      (판정은 **BFF 원소 수**로 — 이 화면들은 클라이언트 렌더라 SSR HTML grep 은 0건을 낸다)
- [ ] **AC-4 (메모리)** — A 를 골랐다면 추가 컨테이너의 실측 메모리를 기록한다
      (`TASK-MONO-399` AC-2 의 입력)

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

- [ ] AC-0~AC-4 전부
- [ ] Ready for review
