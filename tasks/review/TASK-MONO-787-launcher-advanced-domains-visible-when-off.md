# Task ID

TASK-MONO-787

# Status

review — 구현 완료(2026-10-10 UTC). 론처 마크업 · CSS · 표시 전용 JS 만 바뀌고 람다 · 가드는 그대로다.

# Title

론처 「고급」 영역 — 인스턴스가 꺼져 있어도 도메인 목록을 보이게 하고(잠긴 상태로), 영역을 두 구획으로 정리한다

# Owner

monorepo

# Task Tags

- demo
- launcher

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 표시 전용 변경. 단 GUARD-Z14 구간(render) 경계를 지켜야 한다.

---

# Dependency Markers

- 선례: `TASK-MONO-785` · `TASK-MONO-786`(같은 페이지 가독성 정리).
- 관련: `TASK-MONO-477`(도메인별 제어 도입) · `TASK-MONO-634`(고급 영역으로 접음) · `TASK-MONO-729` (d)(도메인 구획).

# Goal

소유자 요청(2026-10-10): 「고급 — 도메인 단위 제어 · 전체 스택 기동」에서 인스턴스가 기동 중이 아니면 도메인 목록(비즈니스 도메인 · 공용 서비스)이 안 나온다 — 꺼져 있을 때도 보이게, 그리고 고급 영역의 가독성 향상.

원인: `render()` 가 `$("domains").style.display = isRun ? "block" : "none"` 으로 running 이 아니면 목록을 통째로 숨겼다.

# Scope

## In

- `infra/demo/aws/site/index.html`
  - `render()` 의 숨김 한 줄 제거(GUARD-Z14 구간 — 다른 줄은 무변경).
  - 구간 밖에 `renderDomainsOff(state)` · `setDomainsLive(live)` 추가: running 이 아니면 각 도메인을 «⚪ 꺼짐» 으로 그리고 버튼 잠금 + 상태별 안내(stopped / pending·starting / stopping / 알 수 없음).
  - 고급 영역을 «인스턴스 전체» · «도메인별 제어» 두 구획으로. 도메인은 타일 그리드(이름 · 한 줄 설명 · 배지 · 버튼), 좁으면 한 열.
  - 「전체 시작 / 전체 종료」 → 「모든 도메인 시작 / 모든 도메인 종료」(인스턴스 전체 버튼과 혼동 방지). 동작은 그대로(`full` / `all`).

## Out

- 람다(`handler.py`) 변경 — 인스턴스가 꺼져 있을 때 `/domain/start` 가 409 를 내는 동작은 그대로 둔다(그래서 버튼을 잠근다). 바꾸려면 `terraform apply` 가 필요하다.

# Acceptance Criteria

- [x] **AC-1** — 인스턴스 stopped 에서 고급 영역을 열면 도메인 7개가 구획별로 보이고 각각 «⚪ 꺼짐» + 잠긴 「시작」, 상단에 이유 안내가 뜬다. 실측(Playwright + 모의 API): stopped → 잠긴 도메인 버튼 **7**, 콘솔 오류 0.
- [x] **AC-2** — running 에서는 기존과 같이 헬스 스냅샷으로 배지(개수 포함) · 시작/종료 버튼이 열린다. 실측: running → 잠긴 버튼 **0**, wms 17/17 · finance 꺼짐 0/0 등 스냅샷 그대로.
- [x] **AC-3** — 꺼진 동안 개수(0/0)를 붙이지 않는다 — 꺼진 인스턴스에서 잰 값이 아니다.
- [x] **AC-4** — GUARD-Z14 구간에 `querySelector*` 를 넣지 않는다(가드의 최소 DOM 대역 밖) — 새 함수는 구간 밖, 구간 안은 한 줄 제거뿐.
- [x] **AC-5** — 폭 1200px · 420px 스크린샷 확인(타일 4열 → 1열).

# Related Specs

- `infra/demo/aws/terraform/lambda/handler.py` `domains()` · `domain_start()` · `domain_stop()` — running 이 아니면 `domains: {}` · 409.

# Related Contracts

- 없음(API 무변경).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 제어 API 없음(`CONTROL_OK` false) | 목록은 보이고 잠김 · «상태를 확인할 수 없어» 안내 |
| 첫 `/status` 응답 전 | 버튼은 마크업에서 `disabled` 로 시작 — 모르면 잠근 쪽 |
| stopped → running 전환 | `setDomainsLive(true)` 후 `/domains` 스냅샷이 오면 행 버튼이 풀린다 |
| running → stopped 전환 | 다음 폴링에서 다시 «꺼짐» · 잠금 |

# Failure Scenarios

1. 꺼진 동안 버튼을 열어 둔다 → 방문자가 누르면 409 「먼저 데모를 시작하세요」 — 누를 수 있는 버튼이 실패한다(AC-1 이 잠금을 요구).
2. 행 렌더를 `render()` 안에 넣는다 → (z14) 대역의 `document` 스텁에 `querySelector` 가 없어 가드가 TypeError 로 죽는다(AC-4).
