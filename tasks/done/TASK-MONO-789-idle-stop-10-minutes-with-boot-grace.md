# Task ID

TASK-MONO-789

# Status

done — 구현 완료(2026-10-11 UTC). AC-1~4 ✅. ⏳ `done/` 전 남은 것 = **AC-5**(소유자 `terraform apply`).

# Title

데모 자동 종료(유휴) 20분 → 10분 — 켠 뒤 20분은 유휴로 끄지 않는 부팅 보호와 함께

# Owner

monorepo

# Task Tags

- demo
- lambda
- cost

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 값 변경 + 조건 한 줄. 단 부팅 보호가 없으면 웜업을 자르는 회귀가 된다.

---

# Dependency Markers

- 관련: `TASK-MONO-389`(웜업 중 정지가 kafka KRaft 로그 디렉터리를 망가뜨려 이후 모든 부팅 실패 — 이 티켓의 부팅 보호가 막는 결함) · `TASK-MONO-788`(꺼진 상태에서 「모든 도메인 시작」 — 전체 기동을 한 클릭으로 시작하는 길).

# Goal

소유자 요청(2026-10-11): 서버 자동 종료(유휴)를 20분 → 10분.

🔴 그대로 줄이면 생기는 위험(소유자에게 고지 후 «10분 + 부팅 보호» 선택): 유휴 판정 틱이 5분이라 실제 정지는 10~15분 뒤이고, 전체 기동은 실측 약 10~13분이다(`demo-stack.service` 주석: 부팅 #3 = down 184s + up 480s + 시드~요약 108s = 777s; 람다 문구의 «약 10분» = MONO-389 저널 9분 32초). 「모든 도메인 시작」 직후 탭을 닫은 방문자의 부팅이 웜업 한복판에서 잘릴 수 있다. 20분 시절엔 부팅이 언제나 먼저 끝나서 이 보호가 필요 없었다.

# Scope

## In

- `infra/demo/aws/terraform/lambda/handler.py` — `IDLE_MINUTES` 기본 10 · `BOOT_GRACE_MINUTES`(기본 20) · `idle_check` 의 유휴 갈래에 `run_sec >= BOOT_GRACE_MINUTES*60`. 예산 · max-runtime 갈래는 무변경(창과 무관하게 문다).
- `infra/demo/aws/terraform/variables.tf` — `idle_minutes` 20 → 10 · 새 `idle_boot_grace_minutes`(20). `main.tf` 람다 env `BOOT_GRACE_MINUTES`.
- `infra/demo/aws/tests/test_handler.py` — env 를 운영값과 같게(10 · 20) + `IdleBootGraceTest` 4칸.
- 표시 문구: `infra/demo/aws/site/index.html`(두 곳) · console-web `global-guide/data.ts` `SHUTDOWN_RULES`(제목 · 본문 · 인용 줄 범위) · `infra/demo/aws/README.md` · `terraform.tfvars.example`.

## Out

- AMI 재굽기 — 불필요(람다 · 런처 · 콘솔만).
- `terraform apply` — 소유자 실행.
- 켜진 인스턴스에 묶음을 **추가**하는 기동(SSM)의 보호 — 세션 시작 기준 창이라 덮지 않는다. 20분 시절과 같은 위험 수준이고 이 티켓이 바꾸지 않는다.
- 하트비트 주기 — 런처 30초 · 스토어/팬/콘솔 60초로 10분 창에 충분하다(측정: `DemoHeartbeat.tsx` 세 곳 `60_000`, 런처 `30000`).

## projects/ 영향

`projects/platform-console` — 가이드 데이터 문구 한 항목(동작 무변경).

# Acceptance Criteria

- [x] **AC-1** — 켠 지 20분이 지났고 마지막 하트비트가 10분을 넘으면 `idle_check` 가 정지한다(`idle 10m 초과`). 9분이면 안 끈다. 실측: `test_after_the_grace_window_ten_idle_minutes_stop_it` · `test_nine_idle_minutes_still_keep_it_running`.
- [x] **AC-2** — 켠 지 20분 안이면 하트비트가 없어도 유휴로 끄지 않는다. 실측: `test_a_booting_instance_is_not_stopped_for_idle`(켠 지 14분 · 유휴 14분 → 정지 0) + **bite**: 복사본에서 `and run_sec >= BOOT_GRACE_MINUTES * 60` 를 지우면 **정확히 그 칸 하나**가 빨강(같은 배치 대조군의 위치 산물 3칸을 `comm` 으로 제외).
- [x] **AC-3** — 예산 소진은 부팅 보호 창 안에서도 정지한다. 실측: `test_budget_still_stops_inside_the_grace_window`(켠 지 5분 · 예산 소진 → 정지). max-runtime 은 180분 > 20분 창이라 겹치지 않고, 기존 `test_max_runtime_guard_still_bites_while_beating` 통과.
- [x] **AC-4** — 런처(두 곳) · 콘솔 가이드 `SHUTDOWN_RULES` · README · tfvars 예시의 «20분» → «10분(켠 뒤 20분 부팅 보호)». ⚪ 콘솔 가이드 단위시험(`GlobalGuideScreen.test.tsx`, 인용 경로 실재)은 worktree 에 node_modules 가 없어 로컬에서 안 돌렸다 — 인용 경로(`variables.tf:133-148`)는 그대로 실재하고 CI 가 잰다.
- [ ] **AC-5** — ⏳ 소유자 `terraform apply` 뒤: plan 이 람다 1건 in-place(코드 + env `IDLE_MINUTES=10` · `BOOT_GRACE_MINUTES=20`)이고 `terraform output idle_stop_minutes` = 10.

# Related Specs

- `infra/demo/aws/README.md` § 비용(세 겹 가드).

# Related Contracts

- 없음(제어 API 무변경).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 「모든 도메인 시작」 뒤 바로 탭 닫음 | 20분까지 유휴로 안 꺼짐 → 그 뒤 첫 틱(최대 +5분)에 정지 |
| 켠 뒤 계속 사용 | 마지막 하트비트 10~15분 뒤 정지 |
| 부팅 보호 창 안에서 예산 소진 | 즉시 정지(창 무시) |
| terraform 센티널 `started=0` | `max(started, LaunchTime)` 하한이 그대로 작동 — 기존 `FreshlyLaunchedInstanceTest` 통과 |

# Failure Scenarios

1. 값만 20 → 10 으로 바꾼다 → 전체 기동 중 정지 → kafka 로그 손상(TASK-MONO-389) — 이후 모든 부팅 실패.
2. 부팅 보호를 유휴 외 갈래에도 건다 → 예산 상한이 20분 늦게 물어 지출 상한이 깨진다(AC-3 이 막는다).
3. 문구 한 곳만 바꾼다 → 런처 · 콘솔이 서로 다른 시간을 말한다.

# Implementation Notes

- 람다 단위시험 122 → **126 통과**.
- `terraform fmt -check` (variables.tf · main.tf) rc=0.
- `verify-demo-wrapper.sh` 정적 **PASS** (rc=0, FAIL 0). ⚪ (h) 이미지 실재 검증은 이 호스트에서 `docker manifest inspect` 가 매달려 docker 심으로 0/18(skip) — CI 가 잰다.
- `terraform.tfvars`(gitignored, 로컬 운영값)는 `idle_minutes` 를 **주석으로만** 적고 있어 변수 기본값(10)이 그대로 쓰인다 — 손댈 필요 없음(확인함).

---

## CORRECTION

(2026-10-11 UTC, close chore) 위 Status 와 AC-5 의 «⏳ 소유자 `terraform apply`» 는 **더 이상 남은 일이 아니다** — 아래가 지금의 사실이다. 위의 기록은 그대로 둔다.

- **4차원 검증**: (a) #4310 `MERGED` · 머지 커밋 `031c12d30` (b) `origin/main` tip 일치 (c) 머지 시점 실패 check **0**(14 pass · 필수 4종 · Demo wrapper smoke · Frontend unit tests 포함 — 로컬 ⚪ 둘을 CI 가 쟀다) (d) AC-1~4 는 review 시점에 닫혔고 AC-5 는 아래로 닫힘.
- **AC-5 실측**:
  - apply 전 plan(읽기 전용): `aws_lambda_function.control` in-place 1건 — `source_code_hash` · env `+ BOOT_GRACE_MINUTES = "20"` · `IDLE_MINUTES "20" -> "10"`, `Plan: 0 to add, 1 to change, 0 to destroy`, 출력 `idle_stop_minutes 20 -> 10`.
  - 소유자 apply: `Apply complete! Resources: 0 added, 1 changed, 0 destroyed.` · `idle_stop_minutes = 10`. 🔵 첫 시도는 PowerShell 5.1 이 `-out=mono-789.tfplan`(`-` 로 시작 + `.` 포함)을 두 인자로 쪼개 plan 이 실패했고 apply 도 파일이 없어 실패했다 — 아무것도 반영되지 않은 채였다. 인자를 따옴표로 감싸 재실행.
  - apply 후 재-plan: `-detailed-exitcode` **0**, «No changes».
  - 실물 람다 env(`aws lambda get-function-configuration`): `IDLE_MINUTES=10` · `BOOT_GRACE_MINUTES=20` · `MAX_RUNTIME_MINUTES=180`.
- ⚪ 안 잰 것: 실제 유휴 정지 시각(10~15분)과 부팅 보호의 라이브 발화 — 인스턴스를 켜고 방치해야 재는 값이라 이 티켓만을 위해 예산을 쓰지 않았다. 판정 로직은 단위시험 4칸 + bite 가 덮는다.
