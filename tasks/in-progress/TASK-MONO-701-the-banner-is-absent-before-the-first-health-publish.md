# Task ID

TASK-MONO-701

# Title

🔴 데모 인스턴스가 켜진 직후 **헬스가 처음 발행되기 전 약 40초** 동안 세 앱 모두 배너가 없다 — `selection_ready=null` 이 옛 동작(`running`)으로 떨어진다

# Status

in-progress (2026-09-17 UTC — AC-0~2 닫힘, AC-3 은 람다 apply + 창 대기)

# Owner

monorepo

# Task Tags

- demo
- banner
- control-plane

---

# Goal

`TASK-MONO-668` 을 판정한 창(2026-09-17 UTC, AMI `b54296645`)에서 668 의 판정 술어는 전부 통과했지만, **그 술어가 보지 않는 앞 구간**에 틈이 하나 보였다.

| 구간 (UTC) | `/status` `selection_ready` | 묶음 | store `/api/demo/backend-state` | 세 앱(store · fan · console `/login`) |
|---|---|---|---|---|
| 08:50:37 | `/bundle/start` → `starting` | — | — | — |
| **08:50:58 ~ 08:51:34** | **`null`** | 전부 `unknown`(헬스 미발행) | **`running`** | 🔴 **셋 다 배너 없음** |
| 08:51:52 ~ 09:00 | `false` | `booting` | `starting` | 🟢 셋 다 «데모 서버가 켜지는 중입니다» |
| 09:01:02 | `true` | 전부 `ready` | `running` | 🟢 배너 소거 |

⇒ 방문자가 버튼을 누른 직후 약 **36~54초** 동안 화면은 «켜졌다» 로 보인다. 668 이 없애려던 «다 됐다고 믿고 들어와 빈 화면» 과 같은 모양이다(짧을 뿐). 기록: `tasks/done/TASK-MONO-668-*` § «창 실측 — 2026-09-17».

🔴 **이것은 실수가 아니라 의도된 선택의 부작용이다.** `infra/demo/aws/terraform/lambda/handler.py` `_selection_ready()`(`:734~`)는 «헬스 stale · 선택 비었음 · running 아님» 을 전부 `None`(판정 불가)으로 내고, 해석기(`infra/demo/backend-resolver/src/index.ts:84-92`)는 `None` 을 **기존 동작(`running`)** 으로 둔다. 이유가 적혀 있다(`handler.py:745`): *stale 을 False 로 내면 발행자가 죽은 멀쩡한 인스턴스가 영원히 「켜지는 중」 으로 보인다.* 그러니 **«한 번도 발행 안 됨(방금 켬)» 과 «발행이 멈춤(stale)»** 을 가르지 않는 한 어느 쪽으로 고쳐도 다른 쪽이 깨진다.

---

# Scope

## 포함

- «방금 켜서 아직 첫 발행 전» 을 «발행이 멈췄다» 와 **구별**하는 판정(예: `STARTED_PARAM` 이 최근이고 헬스 발행 시각이 그보다 앞이면 `False`)과 그 상한(무한 «켜지는 중» 방지).
- 람다 단위 테스트 + 해석기 테스트.

## 제외

- 668 의 판정 술어와 배너 문구(끝났다).
- 헬스 발행 주기 자체를 줄이는 인스턴스 쪽 변경(AMI 재굽기가 드는 축 — AC-1 에서 고르지 않으면 안 건드린다).

---

# Acceptance Criteria

- [x] **AC-0 — 재측정.** `_selection_ready()` · 해석기의 `null` 처리 · `STARTED_PARAM`/헬스 발행 시각을 **그날의 코드**에서 읽고, 위 표의 줄 번호를 정정한다.
- [x] **AC-1 — 갈래를 고른다 (🔴 소유자 결정).** 최소한 ⓐ 람다가 «기동 후 N초 안 + 헬스 발행 시각 < 기동 시각» 이면 `False` 를 낸다(N 상한 넘으면 다시 `None`) ⓑ 해석기가 `/status` 의 다른 필드로 가른다 ⓒ 그대로 둔다(40초는 수용) 를 비교해 추천과 함께 묻는다. 🔴 추천을 결정으로 적지 마라.
- [x] **AC-2 — 두 방향 bite.** ① 방금 켠 인스턴스(헬스 미발행) → `starting` ② 발행이 N 초 넘게 멈춘 인스턴스 → `running`(옛 동작). 둘 다 테스트가 있고, 판정을 한쪽으로 되돌리면 반대 칸이 빨개진다.
- [ ] **AC-3 — 창 판정.** 다음 창에서 668 과 같은 쌍 표본(`/status` · `/bundles` · backend-state · 세 앱 배너)으로 **첫 표본부터** `starting` 인지 본다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`). 🔴 람다 변경이면 `terraform apply` 는 소유자 몫이다.

---

# Related Specs

- `tasks/done/TASK-MONO-668-the-banner-vanishes-seven-minutes-before-the-backend-can-answer.md` (§ Edge Cases «헬스 stale 도 null → running» · § 창 실측 2026-09-17)
- `infra/demo/aws/terraform/lambda/handler.py` `_selection_ready`
- `infra/demo/backend-resolver/src/index.ts` · `README.md` 상태 표

# Related Contracts

- `/status` 응답의 `selection_ready` 의미(`true`/`false`/`null`) — 바꾸면 세 앱 해석기가 같이 따라야 한다.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 발행자가 기동 직후 죽어 영영 발행 안 함 | N 초 뒤 `None` → 옛 동작. 🔴 «영원히 켜지는 중» 금지 |
| `STARTED_PARAM` 을 못 읽음(SSM 실패) | `None`(지금과 같음) — 본체 200 유지 |
| 이미 running 인 인스턴스에 묶음만 추가 | 기동 시각은 안 바뀐다 — 이 판정이 끼어들지 않아야 한다 |

# Failure Scenarios

1. **stale 을 그냥 False 로 바꾼다** → `handler.py:745` 가 경고한 «죽은 발행자 = 영원히 켜지는 중».
2. **N 을 코드에 박고 근거를 안 적는다** → 첫 발행까지 걸린 실측(이번 36~54초)과 연결하라. 🔴 단일 표본을 상수로 승격하지 마라 — 여유와 근거를 같이 적는다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet 5** (람다 판정 한 곳 + 해석기 + 테스트. 결정은 AC-1)

---

# 구현 기록 — 2026-09-17 UTC (분석=Opus 5 / 구현=Sonnet 5 에이전트 · diff·테스트·bite 는 조정자가 다시 확인)

## AC-0 — 재측정 (그날의 코드, 줄 번호 정정)

| 무엇 | 위 표가 적은 자리 | 그날의 자리(수정 전 main `a334c10d8`) |
|---|---|---|
| `_selection_ready()` | `handler.py:734~` | `:734-777` ✔ 같음 |
| «stale 을 False 로 내면 영원히 켜지는 중» 근거 | `handler.py:745` | `:745-748` ✔ |
| 해석기의 `null` 처리 | `index.ts:84-92` | 🔴 그 줄은 **주석**이다 — 판정 코드는 `infra/demo/backend-resolver/src/index.ts:224-228` `starting = status.selection_ready === false;` |
| `STARTED_PARAM` 기록 | — | `/bundle/start` stopped 분기 `:616-621` · `/start` stopped 분기 `:698-705` — **전이에서만** 찍는다(`TASK-MONO-634`) |
| 헬스 발행 시각 | — | `infra/demo/demo-status-publish.sh:137-138` `published_at=$(date -u +%s)` · 주기 `infra/demo/demo-status.timer:22-23` `OnBootSec=60` · `OnUnitActiveSec=30s` |

⇒ 위 «08:50:37 → 08:51:52» 의 약 75초는 인스턴스 부팅 + `OnBootSec=60` + 타이머 오차로 설명된다(🔴 단일 표본).

## AC-1 — 소유자 결정

2026-09-17 UTC 선택창으로 물었다(🔵 추천 표지는 **내 것**, 선택은 소유자). 소유자 선택(라벨 원문): **「ⓐ 람다가 기동 직후 False (Recommended)」**
— 선택지 설명(내가 쓴 것): *STARTED_PARAM 이 N초 안이고 헬스 published_at 이 기동 시각보다 앞서면(이번 세션에 아직 발행 전) False, N 이 지나면 다시 None. N 후보는 300초(실측 첫 발행 +75초 안팎, timer OnBootSec=60 — 여유 4배). 묶음 추가는 기동 시각이 안 바뀌고 이미 발행이 있어 끼어들지 않는다. 람다만 바뀌므로 terraform apply 필요, AMI 불필요.*
안 고른 것: **ⓑ** = `/status` 에 필드를 더 싣고 세 앱 공유 해석기가 판정 — 두 곳이 바뀌고 판정이 해석기로 가서 `ADR-MONO-068` «설정 셋» 경계에 가까워진다 · **ⓒ** = 근거가 단일 창의 표본 하나(36~54초)뿐이다.

## 구현 (`infra/demo/aws/terraform/lambda/handler.py`)

- `:63` `FIRST_PUBLISH_GRACE_SECONDS = int(os.environ.get("FIRST_PUBLISH_GRACE_SECONDS", "300"))` — `HEALTH_STALE_AFTER_SECONDS` 와 같은 env-override 관용구. 주석에 근거(2026-09-17 표본 ≈75초 · 🔴 분포가 아니라 표본 하나 · 300 은 약 4배 여유)를 적었다(Failure Scenario 2).
- `_selection_ready()` `:787-801` — `started = STARTED_PARAM`(못 읽음/0 → 이 구별을 안 한다 = 옛 동작). `started > 0` 이고 헬스가 **이 세션 것이 아니면**(`published_at is None or published_at < started`): `0 ≤ now−started < 300` → `False`, 아니면 `None`. 그 뒤는 기존 로직 그대로.
- 🔴 **덤으로 막은 틈**: stop→start 가 90초 안이면 **지난 세션의 스냅샷**이 나이만으로는 신선해 보여 옛 코드는 그 `ready` 로 `true` 를 낼 수 있었다. 이제 `published_at < started` 인 스냅샷은 판정에 안 쓴다.
- 계약의 집 `docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md` § D5.1 표의 `false`·`null` 행과 설명 한 문단을 고쳤다(668 이 그 표를 만든 자리). 해석기·세 앱 코드는 **안 바뀐다**(`false → starting` 규칙 그대로).

## AC-2 — 두 방향 bite

`python infra/demo/aws/tests/test_handler.py` — **101 tests OK, rc=0**(수정 전 95 + 새 6). 새 칸(`SelectionReadyOnStatusTest`):

| 칸 | 세계 | 기대 |
|---|---|---|
| `test_just_started_no_publish_at_all_is_false` (① 방금 켬) | 30초 전 기동 · 헬스 없음 | `False` |
| `test_previous_session_snapshot_that_still_looks_fresh_is_false_not_true` | 30초 전 기동 · 지난 세션 스냅샷(나이 40초, 전부 up) | `False` (옛 코드면 `True`) |
| `test_dead_publisher_past_grace_reverts_to_none` (② 발행 멈춤) | 400초 전 기동 · 이 세션 발행 없음 | `None` → 해석기 `running` |
| `test_old_session_fresh_publish_after_start_with_later_bundle_add_is_unaffected` | 오래 전 기동 · 기동 후 신선 발행 · 나중에 묶음 추가 | `True` → `False`(기존 로직) — Edge Case 3 |
| `test_started_param_unreadable_falls_back_to_todays_behaviour` | `STARTED_PARAM` 없음 · 헬스 없음 | `None` — Edge Case 2 |
| `test_grace_boundary_now_minus_started_equals_grace_is_none` | `now−started == 300` | `None` |

**bite**
- 유예 분기를 `None`(옛 동작)으로 되돌림 → **2 실패**: ① · 지난 세션 스냅샷 칸. (에이전트 실행 + 조정자 재실행 둘 다 같은 2칸, 마커 주입 1건 확인 → 복원 후 마커 0건 · 101 OK.)
- 상한 제거(항상 `False`) → **2 실패**: ② 발행 멈춤 칸 · 경계값 칸(에이전트 실행). ⇒ 판정을 어느 한쪽으로 되돌려도 반대 칸이 빨개진다.
- 가드: `scripts/` · `infra/demo/verify-demo-wrapper.sh` 에서 `selection_ready` · `STARTED_PARAM` · `HEALTH_STALE_AFTER_SECONDS` · 새 상수 grep **0건**(에이전트 보고, 🔴 «막는 가드가 있나» 에 grep 은 답 못 한다 — 권위는 PR CI).

## AC-3 — ⏳ 람다 apply + 창

🔴 **이 PR 만으로는 라이브가 안 바뀐다** — 람다는 `archive_file` 로 굽고 `terraform apply` 는 소유자 몫이다. AMI 재굽기는 필요 없다(인스턴스 쪽 파일 무변경). 창 술어: 668 과 같은 쌍 표본(`/status` · `/bundles` · store backend-state · 세 앱 배너)을 `/bundle/start` **직후 첫 표본부터** 읽어 `selection_ready=false` · backend-state `starting` · 세 배너 «켜지는 중» 인지 본다. 창이 없으면 ⚪ + `TASK-MONO-672`.
