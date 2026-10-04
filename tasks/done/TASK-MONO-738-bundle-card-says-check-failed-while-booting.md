# Task ID

TASK-MONO-738

# Title

🔴 데모 서버를 켠 직후 론처 카드가 「🔴 확인 실패」를 그린다 — `/bundles` 가 «이 세션 첫 헬스 발행 전» 을 «발행이 멈췄다» 로 읽는다

# Status

done (2026-10-04 UTC — 4차원 검증 · PR #4065 squash `f0c5aa575` · AC-4 19차 창)

# Owner

monorepo

# Task Tags

- demo
- launcher
- control-plane

---

# Goal

2026-09-29 UTC 라이브: 팬 묶음을 켠 직후 론처 카드가

> 데모 서버 🔴 확인 실패 — 데모 서버의 헬스 정보가 갱신되지 않아 상태를 판정할 수 없습니다 (마지막 발행 176386초 전)

를 그렸다. 실측(같은 날 08:21:12Z): 헬스 파라미터는 **21초 전** 갱신(08:20:51Z), fan `partial 6/8` · iam `partial 14/15` — 즉 **정상 기동 중**이었다. 176386초(≈49시간)는 **지난 세션이 남긴 스냅샷**의 나이다. 인스턴스는 EC2 `running` 이 된 순간부터 `running` 이지만 발행 타이머는 `OnBootSec=60`(`infra/demo/demo-status.timer:22`)이라, 그 사이 `/bundles` 는 stale → `unknown`(🔴)을 낸다.

`TASK-MONO-701` 이 **같은 구간**을 `/status`(`selection_ready`)에서는 이미 「켜지는 중」으로 갈랐다 — 판정이 `_selection_ready()` 안에만 있어 `/bundles` 는 그 판정을 몰랐다. 소유자 요청: 켜지는 중인 경우 「🔴 확인 실패」 → 「기동 중」.

---

# Scope

## 포함

- 701 의 판정(«`STARTED_PARAM` > 0 · 헬스가 이 세션 것이 아님 · 기동 후 `FIRST_PUBLISH_GRACE_SECONDS` 안»)을 `_first_publish()` 로 빼고, `/status` 와 `/bundles` 가 **같은 함수**를 쓴다.
- 그 구간의 묶음 상태: 선택된 묶음(선택이 비었으면 전부) = `requested`(론처 🟡 기동 중… · 버튼 잠김) · 선택 밖 = `waiting`.
- `/bundles` 응답에 추가 필드 `health_first_publish_pending`.

## 제외

- 론처 HTML(`index.html`) — 표시 행렬은 이미 `requested` → 「🟡 기동 중…」이다. 무변경.
- 고급 영역 도메인 행(`/domains`)의 「🔴 응답 없음」 — 이 요청 밖.
- 발행 주기·`OnBootSec`(AMI 재굽기 축).

---

# Acceptance Criteria

- [x] **AC-1** — 기동 후 유예 안 · 이 세션 발행 전이면 선택 묶음이 `unknown` 이 아니라 `requested`. 49시간 묵은 스냅샷(라이브 그대로의 값)으로 테스트한다.
- [x] **AC-2 — 반대 방향.** 유예를 넘기면(발행자가 죽음) 여전히 `unknown`(«영원히 기동 중» 금지). `STARTED_PARAM` 이 없으면 옛 동작.
- [x] **AC-3** — `/status` 와 `/bundles` 가 이 구간에서 같은 말을 한다(판정 함수 하나).
- [x] **AC-4 — 라이브.** 🔴 람다는 `terraform apply` 가 필요하다(소유자 몫, AMI 불필요). 다음 기동에서 버튼을 누른 직후 카드가 「🟡 기동 중…」인지 본다.

---

# Related Specs

- `tasks/done/TASK-MONO-701-the-banner-is-absent-before-the-first-health-publish.md`
- `tasks/done/TASK-MONO-729-launcher-status-and-visitor-stop-control.md` (카드 표시 행렬)
- `infra/demo/aws/terraform/lambda/handler.py` `_first_publish` · `_bundle_state` · `bundles` · `_selection_ready`

# Related Contracts

- `/bundles` 응답 — 필드 **추가**만(`health_first_publish_pending`). 기존 필드 의미 무변경.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| stop→start 가 90초 안(지난 세션 스냅샷이 나이로는 신선, 전부 up) | `ready` 가 아니라 `requested` — 지난 세션의 up 을 믿지 않는다 |
| 선택이 빈 «전체 시작» | 모든 묶음 `requested` |
| 이 세션 첫 발행 이후 | 판정은 스냅샷으로 돌아간다(대조군: `ready`) |
| `now − started == 300` | 상한 쪽(`unknown`) |

# Failure Scenarios

1. **stale 을 그냥 `booting` 으로 바꾼다** → 발행자가 죽은 인스턴스가 영원히 「기동 중」(701 Failure 1 과 같은 것).
2. **판정을 `bundles()` 에 복제한다** → `/status` 와 `/bundles` 가 다시 갈라진다(이 결함의 원인이 그것이었다).

---

# 분석 / 구현 권장

분석=Opus 5.5 / 구현=Opus 5.5 (소규모 — 람다 판정 한 곳 + 테스트)

---

# 구현 기록 — 2026-09-29 UTC

- `handler.py` — `_first_publish(published_at)` → `None`/`"pending"`/`"overdue"`. `_selection_ready()` 는 그것을 부르도록 바꿨다(동작 무변경 — 기존 701 테스트 6칸 그대로 초록). `_bundle_state(..., first_publish)` 에 `pending` 갈래(stale 판정 **앞**). `bundles()` 가 running 일 때 판정을 넘기고 `health_first_publish_pending` 을 싣는다.
- 테스트 `SelectionReadyOnStatusTest` 에 8칸. `python infra/demo/aws/tests/test_handler.py` → **109 tests OK, rc=0**(이전 101).
- **bite**: `pending` 갈래를 끔 → **5 실패**(49시간 스냅샷 · 스냅샷 없음 · 신선해 보이는 지난 세션 스냅샷 · 전체 시작 · `/status`↔`/bundles` 일치) → 복원(마커 0건) → 109 OK. 반대 방향 칸(상한 넘김 · `STARTED_PARAM` 없음 · 첫 발행 이후)은 끈 상태에서도 초록 — 옛 동작을 지키는 칸이라 그것이 맞다.

---

## CORRECTION (2026-10-02 UTC) — AC-4 라이브: 카드의 **입력**은 🟢, 카드 **화면**은 ⚪ (review 유지)

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

- 이번 `terraform apply`(18차)가 Lambda 를 갱신했다. 꺼진 인스턴스(`stopped` 확인)에 `POST /bundle/start {"bundles":["fan"]}` → 직후부터 1분 넘게 `GET /bundles` 의 `fan.state = "requested"` (10:15:40 · 51 · 16:22 · 17:23Z), `health_first_publish_pending: true` — 예전의 `unknown`(«🔴 확인 실패») 이 아니다.
- ⚪ AC-4 의 동사는 «카드가 🟡 기동 중… 인지 **본다**» 다 — 론처 화면 자체를 보지 않았다(API 상태만). 다음 기동 때 론처 페이지에서 버튼을 누른 직후 카드 문구를 보고 닫는다. ⇒ `review/` 유지.

# 닫기 (2026-10-04 UTC) — 4차원 검증

| 차원 | 판정 |
|---|---|
| (a) | `gh pr view 4065` → `MERGED`, `f0c5aa575` |
| (b) | `f0c5aa575` 는 `origin/main` 의 조상 |
| (c) | #4065 `statusCheckRollup` 66 개 · FAILURE 0 |
| (d) | AC-1~3 `[x]`(구현 기록). **AC-4** — 19차 창 (2026-10-04 UTC · 인스턴스 i-08d452973ebf789be · AMI ami-00815e1f9614cda90 · 커밋 2a49dfb48): 멈춘 인스턴스에서 `POST /bundle/start` 직후 **소유자가 론처 페이지에서 카드가 «🟡 기동 중…» 인 것을 봤다**(«🔴 확인 실패» 아님). 같은 순간 API: 묶음 7개 `requested` · `health_first_publish_pending:true` · `health_age_seconds=42250`(묵은 스냅샷 — 고치기 전이면 `unknown` 이 됐을 값). Lambda 는 19차 apply 로 실림 |
