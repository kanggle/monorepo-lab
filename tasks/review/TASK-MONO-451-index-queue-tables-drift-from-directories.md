# TASK-MONO-451 — `INDEX.md` 큐 표가 큐 디렉터리와 어긋나도 아무도 모른다: 하루 세 번 났고 매번 사람이 잡았다

**Status:** review

**Type:** TASK-MONO (shared — `scripts/` + `.github/workflows/ci.yml`)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (스크립트 + CI 잡 배선. 판정 요소가 거의 없다)

> `TASK-BE-542` close chore 중 발견. **결함 자체보다 "왜 아무도 모르는가" 가 요점**이다.

---

## Goal

각 `tasks/INDEX.md` 는 `## ready` / `## review` / `## done` 섹션에 큐 상태를 표(또는 불릿)로 적는다. 그 표는 **같은 저장소의 `tasks/ready/` · `tasks/review/` · `tasks/done/` 디렉터리와 일치해야 한다.** 일치하지 않으면 INDEX 는 거짓말을 하고, INDEX 는 **다음 작업을 고를 때 사람과 에이전트가 읽는 첫 문서**다.

**2026-07-20 하루에 같은 드리프트가 세 번 났다:**

| 티켓 | 증상 | 잡힌 경위 |
|---|---|---|
| `TASK-BE-535` | `done/` 에 있는데 `## ready` 표에도 남음 | 사람이 수동 대조 (`#2766`) |
| `TASK-BE-536` | 동일 | 사람이 수동 대조 (`#2766`) |
| `TASK-BE-539` | 행 본문은 `**DONE (#2770 …)**` 로 갱신했는데 **섹션 이동을 안 함** — `## ready` 에 DONE 행이 앉아 있었다 | 사람이 수동 대조 (`#2777`) |

**셋 다 CI 는 초록이었다.** `Task ID collision (duplicate IDs in active queues)` 잡이 이미 있지만 그 술어는 *"활성 큐에 중복 ID 가 있는가"* 이지 *"표가 디렉터리와 일치하는가"* 가 아니다. **가드는 있는데 이 축을 보지 않는다.**

### 왜 이게 반복되는가

`tasks/INDEX.md` § PR Separation Rule 은 close chore 가 **3단계를 한 커밋에** 묶으라고 명시한다 — ① `git mv` ② 파일 안 `Status` 갱신 ③ INDEX 섹션 이동. 그리고 *"Skipping step 2 or 3 produces silent drift"* 라고 **이미 경고까지 적어 뒀다**(PR #375 선례 인용). 즉 **규칙은 존재하고, 위반 사실도 알려져 있고, 그런데도 반복된다.** 문서로 막는 데 실패한 것이다 — 남은 방법은 기계 검사다.

---

## Scope

### In Scope

1. 저장소 전체(`tasks/` + 모든 `projects/*/tasks/`)에 대해 **INDEX 큐 표 ↔ 큐 디렉터리** 를 대조하는 스크립트.
2. `ci.yml` 에 잡으로 배선(문서 변경에도 돌아야 한다 — path filter 주의).
3. 현재 남아 있는 드리프트 전수 수리.

### Out of Scope

- INDEX 행 **내용**의 정확성(본문이 사실인지)은 검사 대상이 아니다. 이 검사의 술어는 **집합 일치**뿐이다.
- 라이프사이클 규칙 자체의 변경.
- `backlog` 섹션 — 파일이 없는 항목이 정상이므로 대조 대상에서 제외한다(그 근거를 스크립트 주석에 적을 것).

---

## Acceptance Criteria

- **AC-0 (gate — 현재 드리프트 전수 측정)** — 스크립트를 쓰기 전에 **지금 몇 건이 어긋나 있는지 전 프로젝트에 대해 센다.** 위 표의 3건은 이미 수리됐으므로 **0 이 나올 수도 있고, 아닐 수도 있다** — 다른 프로젝트는 아무도 대조한 적이 없다. 0 이 아니면 그 목록이 이 티켓의 가장 큰 수확이다.
- **AC-1 (스크립트)** — `scripts/` 에 대조 스크립트. 각 INDEX 의 `ready`/`review`/`done` 섹션에서 task ID 를 추출해 대응 디렉터리의 파일명 ID 와 **집합 비교**한다. 표-only / 디스크-only 를 **양방향** 모두 보고한다.
- **AC-2 (guard — 물 기회 확인)** — 스크립트를 **실제 드리프트를 심은 상태에서 먼저 돌려 RED 를 확인**한다. 세 가지 모양 전부: ① 디스크에 없는 행 ② 표에 없는 파일 ③ **`BE-539` 모양 — 행 본문은 DONE 인데 섹션이 `ready`**. ③ 이 가장 중요하다. 본문만 보는 검사는 이걸 놓친다.
- **AC-3 (배선)** — CI 잡으로 등록. **`dorny/paths-filter` 부정 패턴 금지**(`platform/git-workflow-policy.md` § CI Path-Filter Constraint), 그리고 **문서만 바뀐 PR 에서도 반드시 돌아야 한다** — 드리프트는 정확히 그런 PR 에서 생긴다. 코드 변경 필터에 묶으면 가드가 **도달하지 못한다.**
- **AC-4 (형식 내성)** — INDEX 마다 형식이 다르다: 루트는 **불릿**(`- \`file\` — text`), ecommerce 등은 **표**(`| ID | Title | … |`), 일부 섹션은 `(empty)` / `_(없음)_`. 전 프로젝트 INDEX 를 실제로 열어 **파서가 전부를 처리하는지** 확인한다. 파싱 실패를 **조용히 0건으로 처리하지 말 것** — 파싱 못 한 섹션은 에러로 보고한다.
- **AC-5** — AC-0 에서 발견된 드리프트를 전부 수리한다.

---

## Related Specs

- `tasks/INDEX.md` § PR Separation Rule — 3단계 규칙 + *"Skipping step 2 or 3 produces silent drift"* 경고(PR #375 선례)
- `CLAUDE.md` § Task Rules — `git mv` 재스테이징 규칙(같은 클래스의 다른 함정)
- `platform/git-workflow-policy.md` § CI Path-Filter Constraint — AC-3
- `.github/workflows/ci.yml` — `Task ID collision` 잡이 참조 구현이자 **이 축을 보지 않는 이웃 가드**

## Related Contracts

- 없음 — 저장소 위생 검사다.

---

## Edge Cases

1. **🔴 `BE-539` 모양이 가장 잡기 어렵다** — 행 텍스트가 `**DONE …**` 라 사람이 훑으면 "처리됐네" 로 읽힌다. **섹션 위치가 진실이고 행 텍스트는 주장이다.** 검사는 위치로 판정해야 한다.
2. **`backlog` 섹션** — 파일 없는 항목이 정상. 제외하되 근거를 남길 것. 무심코 포함하면 전 프로젝트가 영구 RED 가 된다.
3. **동시 세션** — 두 세션이 각각 INDEX 를 고치면 머지 후 한쪽 삭제가 유실될 수 있다(`BE-539` 행이 정확히 그렇게 살아남았을 가능성). **검사는 머지 후 main 에서도 돌아야** 이 경로를 잡는다.
4. **파일명 ↔ ID 추출** — `TASK-BE-542-long-slug.md` 에서 `TASK-BE-542` 를 뽑아야 하고, `TASK-FIN-BE-059` 처럼 **세그먼트가 셋인 ID** 도 있다. 정규식이 이걸 다 먹는지 known-positive 로 자기검증할 것.

---

## Failure Scenarios

- **F1 — 코드 변경 path filter 아래에 잡을 매단다.** 드리프트는 문서 전용 PR 에서 생기므로 가드가 **영원히 안 돈다.** 무는가보다 **물 기회가 있는가**가 먼저다.
- **F2 — 행 텍스트에서 "DONE" 을 찾는 방식으로 구현.** `BE-539` 모양은 잡지만 정상 done 섹션 행까지 오탐한다. **술어는 집합 일치이지 문자열 검색이 아니다.**
- **F3 — 파싱 실패를 0건으로 처리.** 형식이 다른 INDEX 를 파서가 못 읽고 조용히 통과하면, 초록은 "드리프트 없음" 이 아니라 "안 봤음" 을 뜻한다.
- **F4 — AC-2 를 건너뛴다.** 심은 드리프트로 RED 를 못 보면 이 스크립트가 무는지 알 수 없다.

---

## Test Requirements

- **픽스처 기반**: 세 가지 드리프트 모양을 심은 임시 트리에서 스크립트가 각각 RED 를 내는지 + 정상 트리에서 GREEN 인지.
- **라이브 프로브**: 실제 저장소에 대해 돌려 AC-0 결과와 일치하는지.

---

## Definition of Done

- [ ] AC-0 전 프로젝트 현재 드리프트 실측 (0 이어도 0 이라고 기록)
- [ ] AC-1 대조 스크립트 (양방향 보고)
- [ ] AC-2 세 모양 전부 수정 전 RED 확인
- [ ] AC-3 CI 배선 (문서 전용 PR 에서도 실행됨을 실측)
- [ ] AC-4 전 INDEX 형식 파싱 확인, 파싱 실패는 에러
- [ ] AC-5 발견 드리프트 수리

---

## Notes

- **분량**: small~medium. 스크립트는 짧고 **배선과 도달성이 실질**이다.
- **dependency**: 없음. 독립.
- **출처**: `TASK-BE-542` close chore(`#2777`) — `BE-539` 의 stale ready 행을 수리하며 같은 드리프트가 하루 세 번째임을 확인.
- **이 task 가 방어하는 실패 모드**: **규칙이 존재하고, 위반이 문서화돼 있고, 그런데도 반복된다면 문서로 막는 데 실패한 것이다.** `INDEX.md` 는 이미 *"Skipping step 2 or 3 produces silent drift"* 라고 경고하며 선례까지 인용한다. 경고를 한 번 더 굵게 쓰는 것으로는 네 번째를 막지 못한다. **세 번 반복된 실수는 사람 문제가 아니라 가드 부재다.** [[project_guard_reachability_not_just_bite]] [[feedback_guard_predicate_wrong_verify_the_artifact]] [[env_empty_detector_output_is_not_absence]]
