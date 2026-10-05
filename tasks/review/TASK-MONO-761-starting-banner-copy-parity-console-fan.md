# Task ID

TASK-MONO-761

# Title

console-web · fan-platform-web 의 «데모 서버가 켜지는 중입니다» 문구를 web-store(`TASK-FE-104`)와 같은 **«선택한 데모 화면 중 일부가 아직 켜지는 중»** 으로 맞춘다 — 세 앱이 같은 판정(`selection_ready === false`)에 다른 문장을 말하는 갈라짐을 닫는다

# Status

review

# Owner

monorepo

# Task Tags

- demo
- frontend
- copy
- cross-project

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 문구 세 곳 + 시험 단언 + 주석. 판정·해석기·Lambda 는 건드리지 않는다.
>
> 🔵 소유자 결정(2026-10-04 UTC): 21차 재굽기 동안 이 후속을 진행한다(`TASK-FE-104` 의 «콘솔·팬 적용은 각 프로젝트 티켓이 정한다» 를 받아서). Vercel 앱이라 재굽기와 무관하다.

---

# 왜 루트 티켓인가

두 프로젝트(`projects/platform-console` · `projects/fan-platform`)의 같은 문구를 한 PR 로 맞춘다(CLAUDE.md § Cross-Project Changes — 한쪽만 바뀐 main 을 남기지 않는다). web-store 주석의 «console·fan 은 다르다» 문장도 같은 PR 에서 고친다.

# Dependency Markers

- 선행: `TASK-FE-104`(review, #4155 `678b6d003`) — web-store 문구를 바꾸면서 세 앱의 첫 문장이 갈라졌다.
- 배경 결정: 09-15 소유자 결정 ⓑ(Lambda `_selection_ready` docstring — «전부» 는 선택된 묶음 전부) · 10-04 소유자 결정 (나)(`TASK-FE-104` AC-0 ③ — 기준 유지, 문구를 판정이 아는 것으로).
- 후속: 없음.

# Goal

세 앱의 «켜지는 중» 문구가 같은 판정에 같은 사실을 말한다: «선택한 데모 화면 중 일부가 아직 켜지는 중이고, 이 앱은 이미 준비됐을 수 있다». fan 위젯 주석이 경고한 «해석기가 같은 값을 주는데 화면이 다른 말을 하면 `ADR-MONO-068 § D6` 이 막으려던 갈라짐이 문구 층에서 생긴다» 를 닫는다.

# Scope

## In Scope

- `projects/platform-console/apps/console-web/src/widgets/demo-notice/DemoBackendNotice.tsx` — `starting` 배너 문구 + 주석.
- `projects/fan-platform/web/fan-platform-web/src/widgets/demo-notice/DemoBackendNotice.tsx` — `starting` 배너 문구 + 주석.
- `projects/fan-platform/web/fan-platform-web/src/app/(auth)/login/page.tsx` — `starting` 로그인 문구(같은 판정, 같은 부정확함).
- 각 앱의 시험 단언(옛 첫 문장 → 새 문장) + bite.
- `projects/ecommerce-microservices-platform/apps/web-store/src/widgets/demo-notice/DemoBackendNoticeClient.tsx` — «console·fan 과 다르다» 주석만 고친다(코드·문구 무변경).

## Out of Scope

- 판정(`selection_ready`)·해석기(`infra/demo/backend-resolver`)·Lambda — 그대로.
- «꺼져 있어»(`unavailable`) 배너 · 콘솔 로그인 화면(`starting` 문구를 따로 내지 않는다 — `login/page.tsx:49` 주석).

# Acceptance Criteria

- [x] **AC-1** — console-web · fan-platform-web 의 `starting` 배너가 «선택한 데모 화면 중 일부가 아직 켜지는 중입니다» 로 시작하고, 옛 첫 문장 «데모 서버가 켜지는 중입니다» 를 **포함하지 않는다**(렌더된 DOM 단언). fan 로그인 화면의 `starting` 문구도 같다.
- [x] **AC-2 (대조군)** — 각 앱의 기존 칸(`unavailable` 배너 · `running`/`not-demo` 무배너 · 콘솔 «도메인 이름을 주장하지 않는다» · fan «샘플을 주장하지 않는다» · fan 로그인 «관리자에게 문의를 붙이지 않는다»)이 그대로 초록.
- [x] **AC-3** — bite: 각 앱에서 문구를 옛것으로 되돌리면 그 앱의 AC-1 칸만 빨강.
- [ ] **AC-4 (라이브, ⚪)** — 다음 데모 창에서 묶음 하나가 booting 인 동안 세 앱의 배너가 같은 첫 문장을 낸다.

# Related Specs

- `docs/adr/ADR-MONO-068-*.md` § D6 (해석기는 하나 — 화면이 같은 값에 다른 말을 하지 않는다)
- `docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md` § D5.1 (`selection_ready`)
- `projects/ecommerce-microservices-platform/tasks/review/TASK-FE-104-store-starting-banner-follows-whole-demo-selection.md`

# Related Contracts

- 없음(문구·주석만. 외부 API 변경 없음).

# Edge Cases

- 콘솔 위젯 규칙 «도메인 이름을 주장하지 않는다»(`/iam|wms|scm|finance|erp|ecommerce/i` 단언) — 새 문구도 도메인 이름을 쓰지 않는다.
- fan 로그인 문구는 «로그인이 실패할 수 있다» 가 핵심이다 — 새 문구도 그 처방(몇 분 뒤 다시 시도)을 지운다면 안 된다.

# Failure Scenarios

1. **한 앱만 바꾼다** — 갈라짐이 두 앱 대 한 앱으로 바뀔 뿐이다(AC-1 이 두 앱을 같이 요구한다).
2. **배너만 바꾸고 fan 로그인 문구를 남긴다** — 같은 판정의 같은 문장이 한 앱 안에서 갈라진다.
3. **문구에 도메인 이름을 넣는다** — 콘솔 위젯 규칙을 깬다(AC-2).

---

# 구현 (2026-10-05 UTC)

> 분석=Opus 5.5 / 구현=Opus 5.5. 21차 굽기(`678b6d003`) 동안 진행했다. 🔴 이 티켓은 `ready/` 에 기안한 직후 같은 PR 안에서 착수했다(main 에 `ready` 로 머문 적이 없다 — 소유자가 굽기 동안의 후속으로 정한 작은 문구 작업이라 기안 PR 을 따로 두지 않았다).

| 곳 | 옛 | 새 |
|---|---|---|
| console-web 배너 | «데모 서버가 켜지는 중입니다. 준비가 끝나기 전에는 로그인과 운영 데이터가 일부만 동작할 수 있습니다. …» | «선택한 데모 화면 중 일부가 아직 켜지는 중입니다. 이 콘솔은 이미 준비됐을 수 있지만, 그 전에는 로그인과 운영 데이터가 일부만 동작할 수 있습니다. 몇 분 뒤 다시 열어 주세요.» |
| fan 배너 | «데모 서버가 켜지는 중입니다. 준비가 끝나기 전에는 로그인·멤버십 같은 실시간 기능이 동작하지 않을 수 있습니다. …» | «선택한 데모 화면 중 일부가 아직 켜지는 중입니다. 이 팬 페이지는 이미 준비됐을 수 있지만, 그 전에는 로그인·멤버십이 실패할 수 있습니다. 실패하면 몇 분 뒤 다시 시도해 주세요.» |
| fan 로그인 `DEMO_STARTING_MESSAGE` | «데모 서버가 켜지는 중입니다. 준비가 끝나기 전에는 로그인이 실패할 수 있습니다. …» | «선택한 데모 화면 중 일부가 아직 켜지는 중입니다. 준비가 끝나기 전에는 로그인이 실패할 수 있습니다. 몇 분 뒤 다시 시도해주세요.» |

- 각 파일의 주석에 이유를 적었다(fan 위젯 주석의 «세 앱이 같은 첫 문장» 을 새 문장으로). web-store 주석의 «console·fan 과 다르다» 를 «맞췄다» 로 고쳤다(코드 무변경).
- 콘솔 위젯 규칙 «도메인 이름을 주장하지 않는다» — 새 문구에 도메인 이름 없음(기존 단언 초록).

## 시험 · bite

- 각 파일에 TASK-MONO-761 칸을 하나씩 더했다: 콘솔 `tests/unit/demo-backend-notice.test.tsx` · fan `widgets/demo-notice/__tests__/DemoBackendNotice.test.tsx` · fan `__tests__/login-page.test.tsx`. 새 첫 문장 · 옛 첫 문장 **없음** (+ 배너는 «이 … 은 이미 준비됐을 수 있지만», 로그인은 «다시 시도» 처방 유지). 기존 «켜지는 중» 칸은 두 문구 공통인 «켜지는 중입니다» 만 단언하도록 좁혔다(bite 가 새 칸에만 떨어지게).
- **bite (AC-3)** — 문구를 고치기 **전에** 새 시험을 돌렸다:
  - 콘솔: `1 failed | 5 passed` — 실패 = 새 칸 하나.
  - fan: 첫 실행은 `4 failed | 21 passed` 였는데, 그중 둘은 새 칸이 아닌 «꺼짐» 칸의 **5000 ms 타임아웃**(각 파일 첫 칸 · 두 파일 동시 실행 콜드스타트)이었다. `--maxWorkers=1` 순차 재실행 → `2 failed | 23 passed`, 실패 = 새 칸 둘(배너 · 로그인)뿐.
- 수정 뒤(로컬):
  - console-web: `tsc` rc=0 · `pnpm lint` rc=0 · 전체 vitest `5 failed | 3800 passed` — 실패 3 파일(`OperatorsScreen` · `ProductForm` · `AccountSelfService`)은 이 변경과 **무관**한 파일이고 증상이 5000 ms 타임아웃 · 입력 중복(`expected 'ee' to be ''`)이다. 그 셋 + 이 티켓의 시험 파일을 `--maxWorkers=1` 로 단독 재실행 → `4 files / 31 passed`. 같은 세션의 PC-FE-307 에서 같은 스위트가 3804/3804 였다. ⇒ 부하 flake 로 판정, 권위는 CI(아래).
  - fan-platform-web: `tsc` rc=0 · `pnpm lint` rc=0 · 전체 vitest(`--maxWorkers=2`) `40 files / 359 passed`.
- AC 판정: **AC-1** ✅(두 앱 배너 + fan 로그인, DOM 단언) · **AC-2** ✅(각 파일 기존 칸 초록) · **AC-3** ✅(위 bite) · ⚪ **AC-4** 라이브 — 다음 데모 창. CI 결과는 PR 체크로 확인한다.
