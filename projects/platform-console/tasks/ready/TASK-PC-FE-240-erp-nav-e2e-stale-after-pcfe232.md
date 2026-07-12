# Task ID

TASK-PC-FE-240

# Title

**main 의 nightly 가 4일째 RED 다** — `PC-FE-232` 가 ERP 마스터를 `/erp` → `/erp/masters` 로 옮기면서 **nightly 에서만 도는 e2e 스펙**을 갱신하지 않았다. 그 PR 은 초록이었다(PR CI 는 이 스펙을 돌리지 않는다)

# Status

ready

# Owner

platform-console

# Task Tags

- test
- ci
- bugfix

---

# Dependency Markers

- **원인 커밋**: `63f1fc2bd` — *feat(platform-console): align ERP console menu to orthodox parity (PC-FE-232)* (PR #2342, **2026-07-09 03:20 KST**).
- **발견 경위**: `TASK-MONO-373` 작업 중 nightly 런을 보다가 `Platform Console E2E full-stack` 이 **연속 실패**하는 것을 관측(2026-07-13).

---

# Goal

## 실측 (2026-07-13)

`Platform Console E2E full-stack` 잡이 main 에서 **4일 연속 실패**하고 있다. flake 가 아니라 **결정론적 실패**다:

```
1) overview-consolidation.spec.ts:88
   › ERP drill parent (nav-erp toggle) → 마스터 child renders the ERP 마스터 screen

   Error: expect(locator).toHaveAttribute(expected) failed
   Locator:  getByTestId('nav-erp-masters')
   Expected: "/erp"
   Received: "/erp/masters"
```

`PC-FE-232` 가 **정석(orthodox) 파리티 정렬**을 하면서 ERP 메뉴를 바꿨다 (`ConsoleSidebarNav.tsx:262-275`):

- 구 마스터 표면 `/erp` → **`/erp/masters`** (testid `nav-erp-masters` 는 유지)
- 도메인 루트 `/erp` → **개요**(신규 `nav-erp-overview`), IAM/WMS/SCM/E-Commerce/Finance 와 동일한 형태

**컴포넌트는 바뀌었고 스펙은 안 바뀌었다.**

## 왜 아무도 못 봤나 — 이게 이 결함의 진짜 모양이다

`overview-consolidation.spec.ts` 는 **`nightly-e2e.yml` 에서만 돈다.** `ci.yml` 에는 이 잡이 없다(전 워크플로 grep 확인 — `Platform Console E2E` 는 nightly 에만 등장).

⇒ **PC-FE-232 의 PR 은 이 스펙을 한 번도 돌리지 않고 초록으로 머지됐다.** 그날 밤 main 이 빨개졌고, **아무도 알림을 받지 않았다** — `nightly-e2e.yml` 헤더가 스스로 적어둔 그대로:

> *Failure handling (v1 scope — portfolio): Failure turns the main branch badge red; visible in GitHub Actions UI. **v2 would add issue auto-create or Slack webhook (out of scope; ADR-MONO-011 § 6.1 outstanding)**.*

**나흘 동안 아무도 그 UI 를 보지 않았다.** 이 task 는 스펙을 고치지만, **감시 부재는 고치지 못한다** — § Out of Scope 참조.

---

# Scope

## In Scope

`projects/platform-console/apps/console-web/tests/e2e/overview-consolidation.spec.ts` 의 **stale 단언 3곳**:

1. **L88** — `expect(masters).toHaveAttribute('href', '/erp')` → **`'/erp/masters'`**.
2. **L103** — `await page.waitForURL('**/erp', …)` → **`'**/erp/masters'`**. **L88 만 고치면 여기서 다시 죽는다** (클릭이 `/erp/masters` 로 가는데 `**/erp` 를 기다린다). 두 번째 실패가 첫 번째 뒤에 숨어 있다.
3. **커버리지 구멍 — 그리고 세어보니 하나가 아니었다.** 스펙은 `masters`·`orgview`·`approval`·`delegation` **4개**만 단언한다. 실제 자식은 **6개**다(`ConsoleSidebarNav.tsx:271-281`): `개요`(`/erp`, `PC-FE-232` 신규) · `가이드`(`/erp/guide`) · 마스터 · 통합 조회 · 결재함 · 위임. ⇒ **`nav-erp-overview` 와 `nav-erp-guide` 둘 다** 단언에 없다. (테스트 이름의 *"4-way drill"* 은 `PC-FE-076` 시절 표현이고 지금은 6개다 — **선행 문서의 숫자는 출처가 아니라 가설이다.** 나 자신도 이 task 초안에 "자식 4개" 라 썼다가 소스를 세고 정정했다.)

`L83`(`expect(erp).not.toHaveAttribute('href', '/erp')`)은 **그대로 둔다** — `nav-erp` 는 여전히 토글 버튼이다.

## Out of Scope

- **`ConsoleSidebarNav.tsx` 수정** — 코드가 옳다. `PC-FE-232` 의 정석 파리티 정렬은 의도된 설계이고 IAM/WMS/SCM/E-Commerce/Finance 와 일관된다. **틀린 건 스펙이다.**
- **nightly 실패 알림 부재** — 진짜 뿌리이지만 이 task 의 범위가 아니다(설계 결정 필요: issue auto-create vs Slack vs 다른 것). `ADR-MONO-011 § 6.1` 이 이미 outstanding 으로 들고 있다. **별도 티켓 권고** — 이 결함이 그 부재의 비용을 실증했다(4일).
- **이 스펙을 PR CI 로 옮기기** — 구미가 당기지만 비용 판단이 필요하다(풀스택 부팅). `TASK-MONO-045` 가 ecommerce 풀스택 잡을 **정확히 반대 방향으로**(PR CI → nightly) 옮긴 전례가 있다. 함부로 되돌리지 말 것.

---

# Acceptance Criteria

- [ ] **AC-1** — `Platform Console E2E full-stack` 잡이 **GREEN**. 실제 nightly 런으로 확인(`gh workflow run nightly-e2e.yml --ref <branch>`).
- [ ] **AC-2 (헛된 초록 배제)** — 리포트에서 `overview-consolidation.spec.ts` 의 ERP 테스트가 **실행되어 통과**했음을 확인. skip 아님. (**"잡이 초록" 은 증거가 아니다** — `TASK-MONO-373` 참조.)
- [ ] **AC-3** — `nav-erp-overview`(`/erp`) + `nav-erp-guide`(`/erp/guide`) 단언이 추가되어 **자식 6개가 전부** 단언된다(개요·가이드·마스터·통합 조회·결재함·위임).
- [ ] **AC-4** — 기존 커버리지 무손실: 같은 파일의 다른 테스트(홈 overview 드릴다운, back-link)가 계속 통과.

---

# Related Specs

- `projects/platform-console/apps/console-web/tests/e2e/overview-consolidation.spec.ts` L75-107
- `projects/platform-console/apps/console-web/src/shared/ui/ConsoleSidebarNav.tsx` L260-278 — **진실의 원천**
- `.github/workflows/nightly-e2e.yml` — `platform-console-e2e-fullstack` 잡 (이 스펙의 **유일한** 실행처)
- `tasks/done/TASK-PC-FE-232-*.md` — 원인 커밋의 task

# Related Contracts

없음 — 프런트 라우팅 + e2e.

---

# Edge Cases

- **`waitForURL('**/erp')` 는 `/erp/masters` 에 매칭되지 않는다** — glob `**/erp` 는 `/erp` 로 **끝나는** 경로만 잡는다. L88 만 고치고 끝내면 L103 에서 타임아웃으로 죽는다. **두 곳을 같이 고칠 것.**
- **`nav-erp-overview` 와 `nav-erp-masters` 의 href 가 헷갈리기 쉽다** — 개요가 `/erp`(짧은 쪽), 마스터가 `/erp/masters`. 정확히 반대로 쓰면 테스트는 통과하는데 의미가 뒤집힌다.

# Failure Scenarios

- **F1 — 스펙을 고쳤는데 잡이 여전히 RED** → L103 을 놓쳤을 가능성이 가장 크다(위). 그다음은 `ERP 마스터` 헤딩이 `/erp/masters` 페이지에도 그대로 있는지(`PC-FE-232` 가 헤딩을 바꿨을 수 있다) — **가정하지 말고 페이지를 읽을 것.**
- **F2 — 잡이 초록인데 아무것도 안 돌았다** → AC-2.

---

# Test Requirements

- 실제 nightly 런에서 `Platform Console E2E full-stack` GREEN + 해당 테스트 **실행 확인**.

---

# Definition of Done

- [ ] AC-1 ~ AC-4.
- [ ] `projects/platform-console/tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-13 — `TASK-MONO-373` 작업 중 nightly 관측.

**이 결함은 `MONO-373` 과 같은 과의 병이다.** 373 은 *"스펙이 어느 잡에서도 안 돌아서 회귀가 초록으로 통과했다"* 였고, 이건 *"스펙이 PR 에서 안 돌아서 회귀가 초록으로 머지됐다"* 다. **둘 다 「검사받지 않는 표면은 드리프트한다」이고, 둘 다 실패가 조용했다.** 차이는 373 은 skip 이 초록으로 보고된 것이고 이건 **RED 가 보고되었으나 아무도 듣지 않은 것**이다 — 후자가 더 나쁘다. **알림 없는 RED 는 초록과 구별되지 않는다.**

분석=Opus 4.8 / 구현 권장=**Sonnet** (스펙 단언 3줄 + nightly 재확인. 판단은 이미 끝났다).
