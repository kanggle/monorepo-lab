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
> 기안 2026-11-01 UTC. 소유자 요청 «토큰 만료 대비 태스크». 날짜는 UTC.

# ⏳ SCHEDULED — DO NOT START before 2026-12-01 (UTC)

**AC-0 verify-then-act 게이트** — 🔴 날짜만 보고 착수하지 않는다. 착수 전에 **재서** 확인한다:

1. 오늘(UTC, `date -u +%F`)이 **2026-12-01 이상**인가. 아니면 STOP.
   - 🔴 **2026-12-10 이후**(이미 만료)라면 «만료 전 갱신» 이 아니라 **복구**다: 먼저 드라이런 1회로 현재 상태를 기록하고(프리플라이트 실패가 예상된다 — Failure Scenarios 의 «7개 전부 조회 실패» 모양), 그 사이 소유자가 동기화를 돌려 실패한 run 이 있는지 `gh run list --workflow portfolio-sync.yml` 로 본다. 만료된 fine-grained PAT 은 Regenerate 로 되살릴 수 있다. 화면에 그 버튼이 없으면 새로 만든다(Edge Cases 의 권한 확인). 그다음 AC-1~3 은 같다.
2. 토큰이 **이미 갱신됐는가** — 🔴 프리플라이트 결과로는 **판정되지 않는다**. 만료 전에는 갱신 여부와 상관없이 success 를 낸다. 판별력 있는 측정은 둘이다:
   - 시크릿 `updated_at`: `gh api repos/kanggle/monorepo-lab/actions/secrets/PORTFOLIO_SYNC_TOKEN --jq .updated_at`. 기준값 **2026-09-11T09:35:09Z**(F4)에서 바뀌었으면 값이 교체된 것이다.
   - 소유자 화면 github.com/settings/personal-access-tokens 의 `Expires on`. 만료일은 API 로 못 읽으므로 소유자에게 묻는다.
   - 둘 다 갱신을 가리키면(조기 갱신) → 갱신 작업은 건너뛰고 AC-1 은 소유자 확인 문장으로, AC-2 는 드라이런 1회로 닫는다. 🔴 **AC-3 은 건너뛰지 않는다** — 조기 갱신으로 닫힐 때 다음 갱신 티켓을 안 남기면 다음 만료가 다시 사람 눈에만 걸린다.
   - 둘이 어긋나면(만료일은 늘었는데 `updated_at` 이 그대로) → Regenerate 뒤 시크릿 교체를 빠뜨린 상태다(Failure Scenarios 첫 항목).
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

**AC-0 사실 (기안 중 실측, 2026-11-01 UTC):**

| # | 사실 |
|---|---|
| F1 | 토큰 = fine-grained PAT `portfolio-sync` · 생성 2026-09-11 · **만료 2026-12-10** · 대상 리포 7개(소유자 화면 붙여넣기). |
| F2 | 권한 = Contents + Metadata + **Workflows: Read and write**(2026-11-01 소유자 추가, `TASK-MONO-823` F2). |
| F3 | 마지막 성공 실행 = run `41326996856`(dry-run, 프리플라이트 7/7 · Workflows 탐침 7/7 409). |
| F4 | 시크릿 `PORTFOLIO_SYNC_TOKEN` `created_at` = `updated_at` = **2026-09-11T09:35:09Z** (`gh api …/actions/secrets/PORTFOLIO_SYNC_TOKEN`, 2026-11-01 실측) — 11-01 권한 추가는 값을 안 바꿨으므로 정상. 갱신 뒤 이 값이 바뀌어야 한다(AC-0 ② · AC-2). |
| F5 | 이 호스트의 `CronCreate`/`ScheduleWakeup` 은 세션 한정이라 한 달 뒤 알림을 못 싣는다(사용자 CLAUDE.md § Scheduling) — 그래서 이 티켓이 그 알림이다. |

- 소유자: 토큰 **Regenerate**(만료 기간 재설정) → 리포 시크릿 `PORTFOLIO_SYNC_TOKEN` 값 교체. 🔴 Regenerate 는 새 값을 발급한다 — 시크릿 교체가 빠지면 Actions 는 옛 값을 계속 쓴다. 옛 값이 Regenerate 즉시 무효가 되는지는 **기안 중 확인하지 못했다**(GitHub 문서 원문 미확인). 그래서 이 티켓은 어느 쪽이어도 맞게 썼다: 즉시 무효면 드라이런이 바로 실패하고, 아니면 12-10 까지 통과한다. 어느 경우든 교체 확인은 `updated_at` 으로 한다.
- 에이전트: `gh workflow run portfolio-sync.yml --ref main -f dry_run=true` 1회 → 프리플라이트 확인.

## Out of Scope

- 실제 동기화(강제 푸시) — 별도 소유자 승인 축.
- 만료 자동 감시 워크플로(`schedule:` 트리거 금지 결정, `TASK-MONO-663`).

# Acceptance Criteria

- **AC-1** 소유자 화면의 새 `Expires on` 이 2026-12-10 **이후**다(소유자 확인 문장을 티켓에 인용).
- **AC-2** 시크릿 교체가 **측정으로** 확인되고, 그 뒤 드라이런 1회가 통과한다 — 둘 다 있어야 닫힌다:
  - (a) 시크릿 `updated_at` 이 기준값 `2026-09-11T09:35:09Z`(F4)와 **다르고**, 갱신 날짜(UTC)다. 측정값을 그대로 기록한다.
  - (b) (a) 이후에 시작한 드라이런: Preflight success — push 7/7 · Workflows 탐침 7/7(409). run id 기록.
  - 🔴 (b) 만으로는 닫히지 않는다. 옛 값이 아직 살아 있으면 교체하지 않아도 success 가 나온다.
- **AC-3** 새 만료일이 정해지면, 그 9일 전 날짜로 같은 모양의 다음 갱신 티켓을 `tasks/ready/` 에 남긴다(아니면 같은 일이 다시 사람 눈에만 걸린다). 🔴 AC-0 ② 의 조기 갱신 경로로 닫힐 때도 적용된다.

# Related Specs

- `.github/workflows/portfolio-sync.yml` 헤더 · 프리플라이트

# Related Contracts

없음.

# Edge Cases

- 소유자가 새 토큰을 **처음부터 다시** 만든 경우 — 대상 리포 7개 + Contents · Workflows **둘 다** Read and write 인지 확인(Workflows 를 빼면 `TASK-MONO-823` 탐침이 403 으로 막는다).
- 만료일을 «No expiration» 으로 고른 경우 — AC-3 은 ⚪(다음 갱신 티켓 불필요) 로 기록.

# Failure Scenarios

- Regenerate 만 하고 시크릿을 안 바꿈 → 증상은 옛 값의 생존 여부에 따라 둘 중 하나다(위 In Scope 의 ⚪). 둘 다 `updated_at` 이 기준값 그대로인 것으로 잡힌다(AC-2 (a)):
  - 옛 값이 즉시 무효 → 드라이런 프리플라이트가 **7개 전부 «조회 실패 (리포명 오타 또는 토큰이 이 리포를 못 봅니다)»** 로 죽는다(`portfolio-sync.yml` 의 `gh api` 실패 분기). 🔴 그 문구는 리포명·스코프를 가리키지만 **7/7 전부**가 같은 실패면 토큰 값 자체를 의심한다. 권한(F2)을 다시 만지기 전에 `updated_at` 부터 본다.
  - 옛 값이 만료일까지 유효 → 드라이런이 **통과**한다(거짓 양성). 그래서 AC-2 는 (b) 만으로 닫히지 않는다.
- 시크릿 이름 오타로 **새 시크릿**을 만듦 → `PORTFOLIO_SYNC_TOKEN` 의 `updated_at` 은 그대로다. `gh api repos/kanggle/monorepo-lab/actions/secrets --jq '.secrets[].name'` 로 목록을 본다.
- 날짜를 KST 로 읽음 → 게이트는 UTC(`date -u`).
