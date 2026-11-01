# Task ID

TASK-MONO-823

# Title

포트폴리오 동기화 프리플라이트가 **Workflows 권한**을 안 물어 추출을 다 한 뒤 push 에서 죽는다 — 그리고 `docs/portfolio.md` 사본 표가 2026-11-01 동기화 뒤 거짓이 됐다

# Status

done (2026-11-02 UTC — 4-dim verified · 기안 PR #4615 · impl PR #4616 `6228e3803`, 실패 0)

# Owner

monorepo

# Task Tags

- ci
- portfolio
- docs

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (워크플로 한 스텝 + 문서 표 한 개)
> 기안 2026-11-02 UTC. 소유자 요청 «둘 다 진행». 사실은 origin/main `c391d1bbe` 와 GitHub API 에서 읽었다.

# Dependency Markers

- 출처: `TASK-MONO-822` 직후 소유자 승인 동기화(7개 전부).
- 관련: `TASK-MONO-663`(워크플로·프리플라이트 도입) · `TASK-MONO-657`(사본 낡음 발견).

# Goal

1. 토큰에 Workflows 쓰기 권한이 없으면 **프리플라이트가** 사유를 대며 멈춘다(추출 뒤 push 거절이 아니라).
2. `docs/portfolio.md` 사본 표가 지금 사실을 말한다.

# Scope

## In Scope

**AC-0 사실 (기안 중 실측):**

| # | 사실 |
|---|---|
| F1 | run `41324172383`(2026-11-01 16:02Z): 프리플라이트 **success** → `wms-platform` 첫 push 에서 `refusing to allow a Personal Access Token to create or update workflow .github/workflows/_integration.yml without workflow scope` · 7개 전부 미변경. 프리플라이트 술어는 `.permissions.push` 하나다(`portfolio-sync.yml`). |
| F2 | 소유자가 fine-grained PAT 에 **Workflows: Read and write** 추가 → run `41325411416` success · 7개 `pushed_at` 전부 2026-11-01T16:16–16:18Z. |
| F3 | 프리플라이트 문구 *"권한은 Contents: Read and write 하나면 됩니다"* 는 F1 으로 **거짓**. |
| F4 | 쓰기 없는 탐침: `PUT repos/kanggle/<r>/contents/.github/workflows/<기존 .yml>` 에 `sha=0×40` → workflow 권한 있는 토큰으로 **409 Conflict**(`does not match`), 맨 위 커밋 불변(`wms-platform` `3f0c02bc5`) — 양성 쪽 실측. 7개 사본 전부 `.yml` 7개 보유. |
| F5 | `docs/portfolio.md` § 저장소 사본 표는 8월·9월 날짜 + *"사본은 전부 이전 상태"* — F2 뒤 거짓. |

- 프리플라이트에 리포별 Workflows 쓰기 탐침(F4) 스텝 추가. 응답 분기: 409 ⇒ ✔ · 403 ⇒ ✗(Workflows 권한 없음 안내) · 그 외 ⇒ ✗(알 수 없음, 응답 출력) · 사본에 `.yml` 0개 ⇒ ⚠ 경고 후 통과(첫 동기화는 탐침 대상이 없다).
- 시크릿 부재 안내 문구를 «Contents + Workflows: Read and write» 로.
- `docs/portfolio.md` 사본 표를 7개 = 2026-11-01 로, 문구 정리.

## Out of Scope

- 토큰 만료(2026-12-10) 알림.
- 동기화 자동 트리거(소유자 결정 «workflow_dispatch 만», TASK-MONO-663).

# Acceptance Criteria

- **AC-1** 프리플라이트가 7개 리포 각각에 F4 탐침을 돌리고, 409 만 통과로 센다. 탐침이 **아무것도 쓰지 않음**을 dry-run 실행 1회에서 확인(실행 전후 7개 `commits/main` sha 동일).
- **AC-2** 실제 토큰으로 `workflow_dispatch dry_run=true` 1회 → 프리플라이트 7/7 ✔.
- **AC-3** 음성 쪽(권한 없는 토큰 → 403): ⚪ 허용 — 소유자 토큰 권한을 일부러 빼야만 잴 수 있다. 재지 못하면 «못 잼 + 이유 + 남는 위험» 을 기록한다. 🔵 남는 위험의 방향: 탐침이 오판으로 통과해도 결과는 **지금과 같은**(push 에서 죽는) 상태다 — 더 나빠지지 않는다.
- **AC-4** `docs/portfolio.md` 사본 표가 F2 와 일치. `node scripts/check-readme-screenshots.mjs` rc=0.

# Related Specs

- `.github/workflows/portfolio-sync.yml` 헤더(TASK-MONO-663 결정)

# Related Contracts

없음.

# Edge Cases

- 탐침이 `sha` 를 맞히는 경우 — 0×40 은 어떤 blob sha 와도 같지 않다.
- 사본이 아직 없는(빈) 리포 — `.github/workflows` 404 ⇒ ⚠ 경고 후 통과.
- GitHub 가 sha 검증을 권한 검사보다 **먼저** 하면 권한 없어도 409 → 오판 통과(AC-3 의 남는 위험).

# Failure Scenarios

- 탐침이 실제로 파일을 쓴다 → AC-1 의 전후 sha 대조가 잡는다. 🔴 탐침 응답이 200/201 이면 즉시 ✗ 로 멈추고 «사본에 쓰기가 일어났다» 를 출력한다.
- 탐침 실패를 경고로 흘림 → 403·기타는 반드시 exit 1.

# Implementation — 2026-11-02 UTC

- `portfolio-sync.yml` 프리플라이트에 스텝 «7개 리포에 Workflows 쓰기가 되는가 — 쓰기 없는 탐침» 추가(분기 409 ✔ · 403 ✗ · 200/201 즉시 정지 · 기타 ✗ · `.yml` 없음 ⚠ 통과). 시크릿 부재 문구를 «Contents + Workflows» 로.
- `docs/portfolio.md` 사본 표 → 7개 = 2026-11-01 한 줄 + «사본은 사람이 돌린다» 한 문장.
- 🔴 **첫 실측이 결함을 잡았다**: run `41326859025` 프리플라이트 **failure**, 탐침 스텝이 출력 0줄로 exit 1. 원인 = Actions 기본 셸 `bash -e` + 409 에 `gh` rc=1 + `pipefail` → 대입문 실패로 즉사. 로컬 첫 시험은 `-e` 없이 돌려 초록이었다(평가 맥락 차이). 수선 = 스텝 머리에 `set +e`. 로컬 `bash -e` 재현: 수정본 rc=0 · 수정 전 rc=1(bite). 🔵 결함은 **막는 쪽**으로 실패했다(동기화 미시작, 7개 sha 불변).

# Verification

- **AC-1** ✅ run `41326996856`(브랜치 `ci/mono-823-impl`, dry_run): 탐침 7/7 «409, 무쓰기». 실행 전후 7개 `commits/main` sha `diff` 동일.
- **AC-2** ✅ 같은 run: Preflight success · Sync(dry-run) success.
- **AC-3** ⚪ **못 잼** — 음성(권한 없는 토큰 → 403)은 소유자 토큰 권한을 일부러 빼야 하고, 로컬 `gh` 토큰은 `workflow` scope 를 가져 대조군이 될 수 없다. 남는 위험: GitHub 가 sha 를 권한보다 먼저 검사하면 권한 없이도 409 → 오판 통과 ⇒ 결과는 이 스텝 이전과 같다(push 에서 거절).
- **AC-4** ✅ 사본 표 = `pushed_at` 2026-11-01T16:16–16:18Z(7개). `check-readme-screenshots.mjs` rc=0.

---

## CORRECTION — 2026-11-01 UTC (TASK-MONO-824 기안 중 발견)

위 본문의 «2026-11-02 UTC» 는 **한국 시간 날짜**였다. 이 작업이 끝난 시각의 UTC 날짜는 **2026-11-01** 이다(`date -u +%F` = `2026-11-01`, 커밋·PR 머지 시각 기준). KST 00:00–09:00 에 쓴 날짜를 UTC 라 표기한 사용자 CLAUDE.md § Date Stamps 함정이다. 위 기록은 지우지 않는다 — 측정·판정 내용은 그대로 유효하고 날짜 표기만 틀렸다.
