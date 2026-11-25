# Task ID

TASK-MONO-841

# Title

셋째 방향 가드 — 스펙 metric · span 표의 이름은 «계획» 표시가 없으면 코드가 실제로 내보내야 한다(스펙 → 코드). 판정은 알림 가드의 술어를 그대로 불러 쓴다(두 벌 두지 않는다)

# Status

review

# Owner

monorepo

# Task Tags

- guard
- observability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 (모집단 정의 · 술어 재사용 · self-test — 가드 설계)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-837` AC-5 «D2 = (b) 뒤 셋째 방향 가드는 «계획» 이 아닌 행만 문다» → 소유자 «권장안대로 진행».

# Dependency Markers

- 선행: `TASK-MONO-840`(«계획» 표시 형식 — 이 가드의 면제 표지) · `TASK-BE-668` · `TASK-BE-669`(D1 구현 — 표시 없는 행이 전부 구현된 상태).
- 같은 계열: `check-metric-name-registry.sh`(833, 코드 → 스펙) · `check-alert-rule-metrics.sh`(834 · 839, 알림 · 대시보드 → 코드). 이 티켓이 남은 방향을 닫는다.

# Goal

837 이 한 번 잰 것(스펙 → 코드)을 가드로 만든다. 840 뒤의 불변식: **스펙 metric · span 표의 행은 (a) 코드가 내보내거나 (b) «계획» 표시가 있거나 (c) «파생» 이다.** 지금 이것을 지키는 것이 없다 — 스펙에 새 행을 적고 구현을 잊으면, 837 이전처럼 «필수» 라고 적힌 것이 조용히 없다.

# Scope

## In Scope

1. **판정 재사용**: `scripts/check-alert-rule-metrics.sh` 에 `--unexported <project>` 모드 — stdin 의 이름(한 줄에 하나)을 받아, 그 프로젝트에서 내보내지지 **않는** 이름만 출력. 기존 `exported()` 술어(정확 이름 = 아무 리터럴 · 접미사 뗀 이름 = meter 등록 이름 · 프레임워크 · `-` 정규화)를 그대로. 술어 코드를 옮기거나 복사하지 않는다.
2. **새 가드** `scripts/check-spec-metrics-implemented.mjs`: 모집단 = 837 과 같은 규칙(가장 안쪽 분류 제목 · 로그 표 제외 · 첫 칸의 백틱 이름, `projects/*/specs/**/*.md`, `git ls-files`). 면제: 행에 `🔵 *계획 · 미구현` 표시 · `파생` 표기 · 폐기 서비스 스펙 디렉터리(`*-deprecated`). span 표(트레이스) 행은 metric 술어로 판정할 수 없으므로 **«계획» 표시가 있어야만** 통과(수동 span 생성 코드가 생기면 그때 술어를 넓힌다 — 지금 0).
3. 실패 메시지: 프로젝트 · 파일:줄 · 이름 + 처방(«구현하거나, 아직이면 행에 계획 표시»).
4. `--self-test`: 표시 없는 미구현 행 bite · 표시 있으면 통과 · 구현된 이름 통과 · 파생 통과 · deprecated 디렉터리 통과 · 로그 표 무시 · «Contract» 제목을 span 으로 안 잡음(837 오탐) · 표시 없는 span 행 bite · 빈 모집단 fail-closed.
5. CI 잡(paths: `projects/*/specs/**` · `projects/*/apps/*/src/main/**/*.java` · `libs/*/src/main/**/*.java` · 두 스크립트). 가드 수 산문 +1/+1.

## Out of Scope

- 표가 아닌 목록(`- \`name\` — …`)으로 적힌 metric — 837 모집단 밖.
- 라벨 일치.

# Acceptance Criteria

- [x] AC-1: 실제 트리에서 rc=0. 위반을 하나 넣으면(표시 하나 지우기) rc=1 — 파일:줄을 정확히.
- [x] AC-2: `--self-test` 전부 PASS · 알림 가드 `--self-test` 도 PASS(새 모드가 기존 판정을 안 바꿈).
- [ ] AC-3: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · `check-ls-files-guard-count.sh` · PR CI 에서 새 잡이 **실행되어** success.

# Related Specs

- `platform/observability.md` § Change Rule(문서 먼저)

# Related Contracts

- 없음.

# Edge Cases

- 한 행에 이름 둘 — 하나라도 미구현이고 표시가 없으면 실패.
- 같은 이름이 두 프로젝트에 — 프로젝트별 판정(다른 프로젝트가 내보내도 통과 안 함).

# Failure Scenarios

- 술어를 JS 로 다시 써서 두 가드가 다르게 판정 → 같은 이름이 한쪽에선 «있음» 다른 쪽에선 «없음». Scope 1 이 막는다.

# Test Requirements

- self-test 두 개 · bite · 가드 전수 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **판정 재사용** — `check-alert-rule-metrics.sh`: `exported()` 를 `run_check` 안에서 최상위 함수로 꺼냈다(본문 그대로, 호출자의 `defined` · `registered` · `nl` 을 읽는다). `--unexported <project>` 모드 = stdin 이름 중 그 프로젝트가 내보내지 **않는** 것만 출력(`.`/`-` 정규화). 스펙 행은 Micrometer **meter 이름**(`http.server.requests`)을 쓸 수 있으므로 이 모드에서만 «이름 + `_` 가 프레임워크 접두사» 도 통과 — 자기 시험이 이 구멍을 잡았다(실제 트리에선 fan 코드에 그 리터럴이 우연히 있어 통과하고 있었다). 기존 모드의 판정은 바뀌지 않았다: `run_check` 출력 동일(«55 … 239 … except 6 listed as pending») · `--self-test` 25/25.
- **새 가드** `scripts/check-spec-metrics-implemented.mjs` — 모집단 · 면제 · span 규칙은 티켓 Scope 2 그대로. 프로젝트당 `--unexported` 1회(이름을 모아 stdin). 실제 트리: rc=0 — «**274** metric/span row(s) … **92** exported, **182** marked planned / derived / deprecated»(274 = 837 의 중복 제거 전 행 수와 같다 · 182 = 계획 표시 171 + 파생식 4 + deprecated 7). 로컬 20초.
- **AC-1 bite** — auth 스펙에서 «계획» 표시 하나를 지우고 스테이지 → rc=1, 정확히 `projects/iam-platform/specs/services/auth-service/observability.md:16  auth_login_duration_seconds — projects/iam-platform does not export it` → 복원 rc=0.
- **AC-2** — 새 `--self-test` **13/13**(표시 없는 미구현 bite · 표시 통과 · 구현된 이름(점 표기 · `_total`) 통과 · 파생 통과 · `-deprecated` 면제 · 로그 표 무시 · «Contract» 제목을 span 으로 안 잡음 · 표시 없는 span bite · 표시 있는 span 통과 · 프레임워크 meter 이름 통과 · 한 행 두 이름 중 하나 미구현 bite · 다른 프로젝트가 내보내도 bite · 빈 모집단 fail-closed). 🔴 첫 실행 10/13: 둘은 **고정물** 이 틀렸다(모집단 밖 행만 넣어 빈 모집단 → fail-closed — 유효한 행을 하나씩 추가), 하나는 위의 **진짜 구멍**(meter 이름). 알림 가드 `--self-test` 25/25.
- **CI** — 잡 `spec-metrics-implemented`(paths: `projects/*/specs/**` · 앱 · `libs` 의 `src/main/**/*.java` · 두 스크립트, timeout 10). 가드 수 산문 `CLAUDE.md` · `platform/git-workflow-policy.md` 31/72 → **32/73**(`check-ls-files-guard-count.sh` «2 homes agree with the measured 32/73»).
- **AC-3** — 스테이지 뒤 `check-*.sh` 43: 41 rc=0, 비0 둘 = 환경 전제(erp 래칫 · prerendered verdict). `check-*.mjs` **8/8**(새 가드 포함). `check-ls-files-guard-count.sh` rc=0. PR CI: 대기.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
