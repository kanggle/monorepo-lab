# Task ID

TASK-PC-FE-272

# Title

프런트엔드 로컬 검증 게이트(`pnpm lint` 필수)가 저장소 어디에도 규칙으로 적혀 있지 않다 — 닫힌 task 본문과 에이전트 메모리에만 산다

# Status

ready

# Owner

frontend

# Task Tags

- docs
- onboarding

---

# Goal

`console-web`·`web-store` 를 푸시하기 전 로컬 검증은 **`pnpm lint` 를 반드시 포함해야 한다.** `tsc --noEmit` + `vitest run` 둘 다 GREEN 이어도 CI 의 프런트 잡은 RED 가 될 수 있다 — `next build` 가 컴파일 후 ESLint 를 돌려 `@typescript-eslint/no-unused-vars` 같은 위반에서 "Failed to compile" 로 빌드를 깨는데, **`tsc` 는 미사용 import 를 에러로 보지 않고 `vitest` 는 lint 를 아예 돌리지 않는다.**

**이 규칙은 지켜지고 있지만 저장소의 규칙 파일에는 없다.** 2026-08-06 audit-memory 실측:

- `platform/`, `docs/guides/`, `projects/platform-console/docs/`, 각 `PROJECT.md` — **grep 0건**.
- 유일한 in-repo 근거는 **`tasks/done/` 8곳의 AC 문구**(TASK-FE-074·FE-075·FE-076·FE-082·BE-430 등)이고, 그중 3곳은 규칙 대신 **에이전트 메모리 이름을 인용**한다(`per memory env_console_web_local_verify_needs_lint`). 나머지 근거는 저장소 밖 에이전트 메모리뿐이다.

⇒ `done/` task 는 소스 오브 트루스가 아니고(`CLAUDE.md` § Source of Truth Priority 에 등재조차 안 된다), 메모리는 저장소 밖이다. **1곳에만, 그것도 규칙이 아닌 자리에 있는 규칙은 사실상 없는 규칙이다** — 이는 `TASK-PC-FE-241` 이 UI 컨벤션 3종에 대해 정확히 같은 진단으로 닫힌 것과 동형이며, 그 task 가 만든 정경 홈이 이미 존재한다.

**이 task 는 규칙 본문을 그 정경 홈에 옮겨 적는다.** 새 게이트를 도입하는 것이 아니라, 이미 강제되고 있는 게이트를 읽을 수 있는 곳에 두는 것이다.

---

# Scope

## In Scope

- **`projects/platform-console/docs/conventions/frontend-ui.md` 에 § 5 「로컬 검증 게이트」 신설** — 3종 명령(`pnpm lint` + `tsc --noEmit` + `vitest run`), **왜 `tsc`+`vitest` 만으로는 부족한지**(lint 를 도는 주체가 `next build` 라는 사실), 그리고 이 게이트가 **막지 못하는 것**(§ 5 의 한계 — Playwright e2e-smoke URL 단언, Testcontainers IT).
- `web-store` 도 같은 게이트 아래 있음을 명시 — 이 문서의 § 4 가 이미 `web-store` 를 날짜 컨벤션의 정경 홈으로 커버하는 선례를 따른다. 단 **web-store 는 로컬 `vitest` 가 기동 불가**(vitest4×Node24)이므로 로컬 게이트는 `pnpm lint`+`tsc` 2종이고 `vitest` 권위는 CI(Node20)라는 차이를 적는다.
- `projects/platform-console/PROJECT.md` § Frontend UI Conventions 의 필독 목록에 이 절 한 줄 추가(도달성 — `PC-FE-241` 이 "아무도 안 여는 정경 문서는 정경이 아니다" 로 얻은 교훈).
- 문서 § Provenance 표에 이 task 행 추가.

## Out of Scope

- **lint 규칙 신설·CI 워크플로 변경 0건.** 게이트는 이미 CI 에 존재한다(`Frontend unit tests … vitest` 의 lint 선행 스텝 + `Frontend E2E smoke … Playwright` 의 `next build`). 이 task 는 그 게이트를 **문서화**할 뿐 강제 장치를 추가하지 않는다.
- **코드 0줄.** 두 앱 모두 `src/` 미변경.
- **`platform/` 승격 안 함.** 이 게이트는 pnpm/Next.js 프런트 앱에 한정된 규약이고 `platform/` 은 project-agnostic 을 유지해야 한다(HARDSTOP-03 인접). 대신 이미 `platform/testing-strategy.md` 에 승격돼 있는 형제 규칙(Playwright URL 단언 / Gradle `--rerun-tasks`)으로의 **교차 포인터**만 둔다.
- **에이전트 메모리 삭제 안 함.** `env_console_web_local_verify_needs_lint` 는 승격 후에도 worked-detail(PC-FE-076 실제 사건, junction 정리 함정, OperatorsScreen Windows flake)을 보존한다 — `project_shared_file_task_series_single_worktree_serialize`·`feedback_gradle_rerun_tasks_after_mockito_dep_change` 가 승격 후 취한 것과 같은 처리.

---

# Acceptance Criteria

- **AC-0 (verify-then-act 게이트)** — 착수 시 `pnpm lint` 규칙이 그 사이 저장소 규칙 파일에 들어왔는지 재측정한다: `platform/`·`docs/guides/`·`projects/*/docs/`·`projects/*/PROJECT.md` 에 대해 `pnpm lint` grep. **`tasks/done/` 히트는 근거로 세지 않는다**(done task = SoT 아님, 이 task 의 전제). 이미 규칙 파일에 있으면 STOP + 보고(이 task 는 obsolete).
- **AC-1** — `frontend-ui.md` § 5 가 3종 명령과 **각 명령이 무엇을 잡고 무엇을 못 잡는지**를 적는다. 특히 "`tsc`+`vitest` GREEN ≠ CI GREEN" 이 명시돼야 한다 — 이 문서의 다른 절들처럼 *규칙*이 아니라 *왜*가 실린 형태로.
- **AC-2** — CI 의 어느 잡이 이 게이트를 강제하는지 **실측한 잡 이름**으로 적는다(`.github/workflows/` 를 읽어 확인 — 메모리의 6주 전 잡 이름을 그대로 베끼지 말 것). 잡 이름이 바뀌었으면 현재 이름을 쓴다.
- **AC-3** — `web-store` 의 로컬 게이트 차이(vitest 로컬 미기동 → CI 권위)가 적힌다. 근거는 `projects/ecommerce-microservices-platform/apps/web-store` 의 실제 `package.json`/`vitest` 버전 재확인(메모리 인용 금지).
- **AC-4** — `PROJECT.md` § Frontend UI Conventions 에 이 절로 가는 한 줄이 추가된다.
- **AC-5** — 문서-only 확인: `git diff --stat` 이 `.md` 파일만 보여준다(두 앱 `src/` 0변경).
- **AC-6** — 이 task 자체가 게이트를 지킨다: 문서-only 이므로 프런트 3종은 무관하나, `scripts/check-index-queue-drift.sh` 를 로컬 실행해 INDEX 행 정합 GREEN 확인(ready 행 추가 → lifecycle 이동 시 review 행). 중복 `## ` 헤더 grep 도 함께.

---

# Related Specs

- `projects/platform-console/docs/conventions/frontend-ui.md` — 이 task 가 확장할 정경 홈(`TASK-PC-FE-241` 신설, `TASK-PC-FE-242` 정정).
- `projects/platform-console/PROJECT.md` § Frontend UI Conventions — 도달성 진입점.
- `platform/testing-strategy.md` § "Frontend E2E Smoke (Playwright URL assertions)" — 이 게이트가 **못 잡는** 것을 소유하는 형제 규칙(audit-memory 2026-06-18 승격). § 5 는 여기로 교차 포인터를 둔다.
- `CLAUDE.md` § Source of Truth Priority — `tasks/` 가 `docs/` 보다 위이지만 `done/` 은 lifecycle 종료 기록이지 규칙 소스가 아니라는 근거.

# Related Contracts

없음 — 문서 전용, API·이벤트 계약 무변경.

---

# Edge Cases

- **문서가 CI 를 앞지르는 경우** — § 5 가 잡 이름을 적는 순간 그 이름은 낡을 수 있다(워크플로 리네임). 완화: 잡 **이름**보다 "lint 를 도는 주체는 `next build`" 라는 **메커니즘**을 1차 서술로 두고 잡 이름은 부차 각주로. 메커니즘은 Next.js 가 바뀌지 않는 한 안 낡는다.
- **`next lint` 자체의 사망** — Next.js 16 은 `next lint` 를 deprecate 하고 ESLint 직접 호출로 옮기는 방향이다. 착수 시 `console-web` 의 `package.json` `lint` 스크립트가 실제로 무엇을 실행하는지 확인해 **스크립트 이름(`pnpm lint`)** 을 계약으로 적고 그 구현(`next lint` vs `eslint .`)은 부차로 적는다 — 구현이 바뀌어도 문서가 안 낡는 서술.
- **web-store 는 다른 프로젝트다** — 문서는 platform-console 소유인데 § 4 선례처럼 web-store 규칙 *본문*을 여기 두게 된다. 이건 의도된 중복-없음 구조(구현은 앱마다, 근거는 한 곳)이며 § 4 가 이미 그 결정을 내려 뒀다. web-store 쪽 파일은 **건드리지 않는다**(다른 프로젝트 lifecycle).
- **모노레포에 프런트 앱이 3개다** — `console-web`·`web-store`·`fan-platform-web`. 세 번째는 이 메모리·task 계열이 한 번도 측정한 바 없다. § 5 는 **측정된 두 앱만** 단언하고 `fan-platform-web` 은 "미측정" 으로 명시한다(측정 안 한 것을 GREEN 으로 적지 않는다).

# Failure Scenarios

- **규칙을 옮기다 문장이 떨어진다** — `PC-FE-242` 가 "승격은 무손실이 아니다 — 옮긴 뒤 원본과 대조하라" 로 실증한 실패. 완화: § 5 작성 후 원본 메모리 §10·§12·§18 과 문장 단위 대조하고, 의도적으로 안 옮긴 것(junction 정리 함정 = worktree 운영 detail, 문서 범위 밖)은 그 사실을 § 5 각주에 적는다.
- **잘못된 안심을 준다** — § 5 를 읽은 사람이 "3종 GREEN 이면 푸시 안전" 으로 받으면 e2e-smoke URL 단언·Testcontainers IT 에서 터진다(메모리 §22 의 실제 사건). 완화: AC-1 이 "3종 GREEN ≠ CI GREEN" 을 **필수 문장**으로 요구한다.
- **INDEX drift 가드 RED** — active 큐 task 는 INDEX 행이 필수다. 완화: AC-6 이 로컬 가드 실행을 요구. 커밋 전 실행(가드는 커밋 후가 아니라 커밋 전에 돌려야 의미 있다).
- **이 task 가 스스로 phantom 일 수 있다** — AC-0 이 그 방어. 6주 된 메모리를 근거로 "저장소에 없다" 를 단언하는 건 이 저장소가 반복적으로 틀려 온 형태(발굴 스캔의 over-state)라, 착수 시 grep 재측정이 첫 단계다.
