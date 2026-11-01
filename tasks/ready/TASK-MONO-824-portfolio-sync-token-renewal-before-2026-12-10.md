# Task ID

TASK-MONO-824

# Title

포트폴리오 동기화 토큰 `PORTFOLIO_SYNC_TOKEN` 이 **2026-12-10 에 만료**된다 — 만료 전에 갱신하고, 갱신이 실제로 먹었는지 프리플라이트로 확인한다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- portfolio
- scheduled

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Haiku 또는 Sonnet (소유자 토큰 작업 + 드라이런 1회)
> 기안 2026-11-02 UTC. 소유자 요청 «토큰 만료 대비 태스크». 날짜는 UTC.

# ⏳ SCHEDULED — DO NOT START before 2026-12-01 (UTC)

**AC-0 verify-then-act 게이트** — 🔴 날짜만 보고 착수하지 않는다. 착수 전에 **재서** 확인한다:

1. 오늘(UTC, `date -u +%F`)이 **2026-12-01 이상**인가. 아니면 STOP.
2. 토큰이 **아직 갱신되지 않았는가**: 가장 최근 `Portfolio sync (manual)` 실행의 프리플라이트가 무엇을 냈는지, 그리고 소유자에게 만료일이 여전히 `2026-12-10` 인지 묻는다(만료일은 API 로 못 읽는다 — 소유자 화면 github.com/settings/personal-access-tokens 의 `Expires on`). 이미 늘렸으면 → 이 티켓은 «할 일 없음» 으로 AC 를 ⚪ 기록하고 닫는다.
3. 동기화 워크플로가 **아직 존재하는가**(`.github/workflows/portfolio-sync.yml`). 폐기됐으면 STOP — 이 티켓은 근거를 잃었다.

🔵 왜 날짜 2026-12-01 인가: 만료 9일 전 — 소유자 작업(1분)과 드라이런 확인(수 분)에 충분하고, 사본 동기화를 미뤄 둔 상태여도 만료 전에 한 번 돌릴 여유가 남는다.

---

# Dependency Markers

- 선행: 없음(날짜 게이트만).
- 관련: `TASK-MONO-823`(프리플라이트 Workflows 탐침 — 이 티켓의 확인 수단) · `TASK-MONO-663`(워크플로·토큰 도입).

# Goal

12월 10일 이후에도 소유자가 `Portfolio sync (manual)` 를 돌리면 **프리플라이트를 통과**한다.

# Scope

## In Scope

**AC-0 사실 (기안 중 실측, 2026-11-02 UTC):**

| # | 사실 |
|---|---|
| F1 | 토큰 = fine-grained PAT `portfolio-sync` · 생성 2026-09-11 · **만료 2026-12-10** · 대상 리포 7개(소유자 화면 붙여넣기). |
| F2 | 권한 = Contents + Metadata + **Workflows: Read and write**(2026-11-01 소유자 추가, `TASK-MONO-823` F2). |
| F3 | 마지막 성공 실행 = run `41326996856`(dry-run, 프리플라이트 7/7 · Workflows 탐침 7/7 409). |
| F5 | 시크릿 `PORTFOLIO_SYNC_TOKEN` `created_at` = `updated_at` = **2026-09-11T09:35:09Z** (`gh api …/actions/secrets/PORTFOLIO_SYNC_TOKEN`, 2026-11-02 실측) — 11-01 권한 추가는 값을 안 바꿨으므로 정상. 갱신 뒤 이 값이 바뀌어야 한다(Failure Scenarios). |
| F4 | 이 호스트의 `CronCreate`/`ScheduleWakeup` 은 세션 한정이라 한 달 뒤 알림을 못 싣는다(사용자 CLAUDE.md § Scheduling) — 그래서 이 티켓이 그 알림이다. |

- 소유자: 토큰 **Regenerate**(만료 기간 재설정) → 리포 시크릿 `PORTFOLIO_SYNC_TOKEN` 값 교체. 🔴 Regenerate 는 값을 바꾼다 — 시크릿 교체를 빼먹으면 옛 값이 만료일에 죽는다.
- 에이전트: `gh workflow run portfolio-sync.yml --ref main -f dry_run=true` 1회 → 프리플라이트 확인.

## Out of Scope

- 실제 동기화(강제 푸시) — 별도 소유자 승인 축.
- 만료 자동 감시 워크플로(`schedule:` 트리거 금지 결정, `TASK-MONO-663`).

# Acceptance Criteria

- **AC-1** 소유자 화면의 새 `Expires on` 이 2026-12-10 **이후**다(소유자 확인 문장을 티켓에 인용).
- **AC-2** 시크릿 교체 **뒤** 드라이런 1회: Preflight success — push 7/7 · Workflows 탐침 7/7(409). run id 기록.
- **AC-3** 새 만료일이 정해지면, 그 9일 전 날짜로 같은 모양의 다음 갱신 티켓을 `tasks/ready/` 에 남긴다(아니면 같은 일이 다시 사람 눈에만 걸린다).

# Related Specs

- `.github/workflows/portfolio-sync.yml` 헤더 · 프리플라이트

# Related Contracts

없음.

# Edge Cases

- 소유자가 새 토큰을 **처음부터 다시** 만든 경우 — 대상 리포 7개 + Contents · Workflows **둘 다** Read and write 인지 확인(Workflows 를 빼면 `TASK-MONO-823` 탐침이 403 으로 막는다).
- 만료일을 «No expiration» 으로 고른 경우 — AC-3 은 ⚪(다음 갱신 티켓 불필요) 로 기록.

# Failure Scenarios

- Regenerate 만 하고 시크릿을 안 바꿈 → 드라이런은 **아직 옛 값으로 통과**할 수 있다(옛 값이 12-10 전엔 살아 있다). 🔴 AC-2 는 그래서 「통과」만으로 닫히지 않는다 — 소유자에게 **시크릿 Update 를 했는지** 확인 문장을 받고, 가능하면 시크릿의 `updated_at`(`gh api repos/kanggle/monorepo-lab/actions/secrets/PORTFOLIO_SYNC_TOKEN --jq .updated_at`)이 갱신 날짜인지 본다.
- 날짜를 KST 로 읽음 → 게이트는 UTC(`date -u`).
