# Task ID

TASK-MONO-711

# Title

🔴🔴 묶음 여섯이 전부 `ready` 인데 콘솔의 **랜딩 화면(운영자 통합 개요)이 저하**다 — 그리고 촬영 사전점검이 그 저하를 통과시킨다

# Status

ready

# Owner

monorepo

# Task Tags

- console
- bff
- demo
- capture

---

# Goal

2026-09-18 데모 창에서 콘솔 전량을 **두 번**(테넌트 `ecommerce` 04:08–04:14 · `demo-corp`
04:12–04:18) 찍었다. 두 실행 모두에서 콘솔의 **첫 화면**이 저하로 나왔다.

| 경로 | `degradedBy` | 두 실행 |
|---|---|---|
| `/` · `/dashboards/overview` · `/onboarding` | `operator-overview-bff-unavailable` | **둘 다** |
| `/dashboards/health` | `domain-health-bff-unavailable` | **둘 다** |
| `/wms/operations` | `wms-operations-settings-degraded` | **둘 다** |

화면 본문(2026-09-18 04:16, `demo-corp`):

> 통합 개요를 **일시적으로 불러올 수 없습니다.**
> 콘솔 자체는 정상 동작합니다. 각 도메인 화면으로 직접 이동하거나 잠시 후 다시 시도하세요.

🔴 **그 시점에 묶음 여섯이 전부 `ready` 였다**: `console` · `console-ecommerce` · `console-erp` ·
`console-finance` · `console-scm` · `console-wms`(제어 API `/bundles` 로 확인). 같은 창에서
`/ecommerce/products`(24건) · `/ledger`(시산표 대차 일치) · `/scm/procurement`(PO 3건) 은 **정상**이었다
⇒ 개별 도메인은 살아 있는데 **집계하는 층만** 못 부른다.

🔵 두 번의 실행이 **6분 간격**이고 둘 다 같으므로 「일시적」이라는 문구는 최소한 이 창에서는
사실이 아니었다. 🔴 다만 «항상 이렇다» 도 아직 아니다 — AC-0 이 그것부터 잰다.

## 곁문제 — 사전점검이 이 화면을 통과시킨다

`scripts/capture-portfolio.mjs` 는 찍기 전에 `/dashboards/overview` 한 장을 열어 «운영자 화면이
맞는지» 본다(`probe`). 2026-09-18 실행 로그:

```
[portfolio] ✔ 사전 확인 /dashboards/overview (본문 220자)
```

**그 220자가 위 저하 문구 자체다.** 사전점검은 거부·빈값만 보고 **저하를 안 본다** ⇒ 「운영자
권한으로 들어왔다」는 확인은 맞지만, 「이 창이 찍을 만한 상태인가」는 통과시킨다.
🔵 `TASK-MONO-707` 이 저하 술어를 고쳤고 그 함수는 이미 있다(`judgeDegraded`) — **사전점검이
그것을 안 부를 뿐**이다.

---

# Scope

## 포함

- `operator-overview-bff-unavailable` 의 원인 지목: 집계 BFF 가 **어떤 상류**를 부르고 그중
  무엇이 실패하는가. 🔴 후보를 미리 못 박지 않는다 — 안 띄운 묶음(`fan` · `store` ·
  `store-fulfillment`)을 기다리다 fail-closed 하는 것인지, 상류 하나가 죽은 것인지, 타임아웃인지.
- `domain-health-bff-unavailable` 이 **같은 원인인지 다른 원인인지** 가른다(같이 고쳐질 수도,
  아닐 수도 있다 — 두 마커가 다르다는 것이 단서다).
- 사전점검이 저하를 보게 한다(`capture-portfolio.mjs`).

## 제외

- `/wms/operations` 의 `wms-operations-settings-degraded` — 같은 창에서 같이 저하였지만 **wms
  도메인의 설정 화면**이고 기전이 다를 가능성이 크다. 🔴 AC-0 에서 같은 원인으로 밝혀지면
  그때 편입하고, 아니면 자기 티켓을 준다(«컨테이너 블로커 ≠ 내용물 블로커»).
- 저하 술어 자체(`707` 에서 끝났다 — 이 티켓은 그 술어가 **옳게** 문 결과를 다룬다).

---

# Acceptance Criteria

- [ ] **AC-0 — 착수 게이트: 모집단부터.** 🔴 관측은 **한 창 · 두 실행**이다. 착수 시점에
      ⓐ 이전 창들의 촬영 매니페스트/티켓 기록에서 `/dashboards/overview` 가 **언제부터** 저하였는지
      세고(2026-09-12 · 09-17 창 기록이 있다), ⓑ 「묶음을 전부(8개) 띄우면 낫는가」를 **가른다**.
      ⓑ 가 «낫는다» 면 이 티켓은 «fail-closed 한 집계» 이야기이고 제목을 그렇게 고쳐라.
      🔴 창이 필요하면 `TASK-MONO-672` 로 보내고 저장소 쪽(코드 독해)부터 한다.
- [ ] **AC-1 — 원인을 하나로 지목한다.** 🔴 **증상이 살아남으면 원인이 아니었다.** 지목한 기전이
      «두 실행 모두 저하» 를 만드는지 산술/코드로 대고, 검증 가능한 기전을 원인이라 부르지 마라.
- [ ] **AC-2 — 사전점검이 저하를 본다.** `capture-portfolio.mjs` 의 `probe` 가 `judgeDegraded()` 를
      부르고, 저하면 **찍기 전에 경고**한다. 🔴 **막지는 마라** — 저하 상태를 일부러 찍는 경우가
      있다(`707` AC-3 이 그랬다). 경고 + 매니페스트 기록이지 중단이 아니다.
      **bite**: 저하 픽스처에서 경고가 안 나오면 `--self-test` 가 빨개진다.
- [ ] **AC-3 — 고친다(원인이 저장소 안에 있다면).** 상류가 죽은 것이라면 이 티켓은 **지목까지**
      하고 고치는 티켓을 따로 기안한다. 🔴 범위를 삼키지 마라.
- [ ] **AC-4 — 판정은 화면이다.** 다음 창에서 `/dashboards/overview` 매니페스트 항목에
      `degraded` 가 **없다**. 창이 없으면 ⚪ + `TASK-MONO-672`.

---

# Related Specs

- `projects/platform-console/apps/console-web/src/app/(console)/dashboards/` — 개요 화면
- `projects/platform-console/specs/` — 운영자 통합 개요 BFF 계약
- `scripts/capture-portfolio.mjs` § 사전 확인(`probe`) · `judgeDegraded()`
- `tasks/in-progress/TASK-MONO-707-the-capture-script-misreads-two-screen-states.md` § 창 실측
- `tasks/in-progress/TASK-MONO-648-capture-every-page-for-the-application-portfolio.md` § AC-2
  (🔵 `/dashboards/overview` 를 **대체할** 4번째 장을 소유자가 고르라는 칸이 이미 있다 — 이 티켓이
  그 칸의 **이유**를 준다)

# Related Contracts

- 집계 BFF 가 부르는 상류 계약들. 🔴 바꾸면 계약 먼저.

---

# Edge Cases

| 경우 | 다룸 |
|---|---|
| 묶음 8개를 다 띄우면 낫는다 | ⇒ 결함이 아니라 **부분 선택에서의 fail-closed** 다. 그러면 고칠 것은 «문구» 일 수 있다(«일시적» 이 아니라 «이 데모에는 X 가 안 떠 있습니다») |
| 로컬 도커에서는 정상이다 | 🔴 그 초록은 «맞다» 가 아니라 «거기선 전부 떠 있다» 다. 판정 트리를 명시해라 |
| 타임아웃이 원인 | 🔴 **딱 떨어지는 초**는 타임아웃 지문이다. 응답 시간을 재서 그 지문을 확인해라 |
| 테넌트마다 다르다 | 2026-09-18 실측은 `ecommerce` · `demo-corp` **둘 다** 저하였다 — 테넌트 축은 아니다 |

# Failure Scenarios

1. **문구만 고친다** («일시적» 삭제) → 증상은 남고 사용자는 여전히 빈 랜딩을 본다. 🔴 AC-1 을
   건너뛴 수정이다.
2. **사전점검을 «저하면 중단» 으로 만든다** → `707` AC-3 처럼 **일부러 저하를 찍는** 측정이
   불가능해진다. AC-2 가 «경고이지 중단이 아니다» 라고 못 박은 이유다.
3. **`/wms/operations` 를 같이 고치려다 둘 다 못 고친다** → 제외 절이 그것을 막는다.

---

# 분석 / 구현 권장

(분석=Opus 5 / 구현 권장=Opus — 원인 지목이 본체이고 상류가 여럿이다. AC-2 의 사전점검 수정만
따로 떼면 Sonnet 으로 충분하다)
