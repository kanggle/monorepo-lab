# Task ID

TASK-MONO-740

# Status

in-progress (2026-09-29 UTC)

# Title

에이전트 메모리에만 있던 저장소 규칙 넷을 `CLAUDE.md` · `platform/testing-strategy.md` 로 올린다 (`/audit-memory` 2026-09-29 § 공통 규칙 후보)

# Owner

monorepo

# Task Tags

- docs
- rules

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 공유 문서 두 곳에 문단 추가, 코드 변경 없음.

---

# Goal

2026-09-29 `/audit-memory`(인덱스 토픽 186개 전수)가 «레포 전체에 적용돼야 하는데 에이전트 메모리에만 있는 규칙»을
골랐다. 메모리는 이 호스트의 에이전트 세션만 읽는다 — 다른 개발자·다른 호스트의 세션은 못 본다. 넷을 정본 문서로 올린다.
소유자가 «전부 진행»으로 승인했다(2026-09-29).

| # | 규칙 | 출처 메모리(요지) | 올릴 곳 |
|---|---|---|---|
| R1 | 결함을 고치기 **전에** 같은 역할의 형제 모듈(특히 테스트 서포트)을 증상 문구로 grep 하라 — 이미 진단·수정된 판이 주석에 있으면 복사하고, 고쳐진 형제 명단이 곧 낙오 명단이다 | `feedback_grep_siblings_before_fixing_yourself` | `CLAUDE.md` § Required Workflow 10단계 |
| R2 | 각각 옳은 두 제외(빌드의 태그 제외 · CI 잡의 모듈 나열)가 겹치면 아무도 안 도는 스위트가 생기고, 안 도는 스위트는 썩는다 — 테스트를 쓰기 전에 러너를 확인하고 «선언한 모듈 ↔ 나열된 모듈»을 대조하라 | `feedback_two_correct_exclusions_compose_hole` | `platform/testing-strategy.md` § CI Guards (새 G 규칙) |
| R3 | 검증·bite 하네스는 대상 트리를 절대경로로 고정하고 시작 시 브랜치를 단언하라 — 셸 cwd 는 말없이 되돌아가고, 중지한 하네스는 계속 쓴다 | `feedback_harness_must_pin_which_tree_measures` | `platform/testing-strategy.md` § G3 |
| R4 | 기준 출처(SSOT)를 바꾸기 전에 두 출처가 실제로 일치하는지 필드별로 재라 — 픽스처로 고정한 테스트는 **옛 출처의 답**을 고정하므로 스왑이 결과를 바꿔도 전부 초록이다 | `feedback_migration_data_question_before_code_question` | `platform/testing-strategy.md` § Test Types (픽스처 한계 계열 절) |

# Scope

## In
- `CLAUDE.md` — Required Workflow 10단계 문장 확장(R1).
- `platform/testing-strategy.md` — R2 새 G 규칙, R3 는 G3 에 문단 추가, R4 새 절.
- 영향 받는 `projects/<name>/`: **없음**(규칙 문서만).

## Out
- 에이전트 메모리 파일 수정(메모리 쪽 «승격» 표시는 머지 뒤 에이전트가 따로 한다 — 저장소 밖).
- 기존 규칙 문구의 재작성.

# Acceptance Criteria

- [ ] **AC-1** — R1~R4 가 위 표의 위치에 들어가 있다.
- [ ] **AC-2** — 넣은 문장은 **프로젝트 무관**이다(HARDSTOP-03): 서비스명·API 경로·도메인 엔티티·프로젝트 이름 없음. 사례는 일반화한 모양으로만.
- [ ] **AC-3** — 스크립트 가드 **전부** rc=0(스테이지 후 실행). `CLAUDE.md` 는 여러 가드의 입력이다.

# Related Specs

- `platform/testing-strategy.md` § CI Guards / Drift Detectors — Authoring Rules (G1·G3)
- `platform/hardstop-rules.md` § HARDSTOP-03
- `tasks/INDEX.md` § (writing a new task) → ready — `CLAUDE.md` 를 바꾸는 태스크는 경로를 나열한다(위 Scope)

# Related Contracts

- 없음.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 기존 G 규칙과 뜻이 겹침(G1 «못 도는 가드는 가드가 아니다») | R2 는 **테스트 스위트**의 도달성이고 G1 은 **가드 트리거**다 — 새 규칙에서 G1 을 참조해 관계를 적는다 |
| `CLAUDE.md` 줄 길이·요약 성격 | 한 문장 확장에 그친다. 상세는 `platform/` 에 둔다 |

# Failure Scenarios

1. 메모리의 실사고 서술(프로젝트명·티켓 번호)을 그대로 옮김 → 공유 경로에 프로젝트 고유 내용이 들어가 HARDSTOP-03. AC-2 로 막는다.
2. `CLAUDE.md` 수정 뒤 관련 가드만 돌림 → 이 저장소는 가드가 서로의 입력이다. AC-3 은 **전부**다.
