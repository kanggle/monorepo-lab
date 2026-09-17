# Task ID

TASK-MONO-701

# Title

🔴 데모 인스턴스가 켜진 직후 **헬스가 처음 발행되기 전 약 40초** 동안 세 앱 모두 배너가 없다 — `selection_ready=null` 이 옛 동작(`running`)으로 떨어진다

# Status

ready

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

⇒ 방문자가 버튼을 누른 직후 약 **36~54초** 동안 화면은 «켜졌다» 로 보인다. 668 이 없애려던 «다 됐다고 믿고 들어와 빈 화면» 과 같은 모양이다(짧을 뿐). 기록: `tasks/in-progress/TASK-MONO-668-*` § «창 실측 — 2026-09-17».

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

- [ ] **AC-0 — 재측정.** `_selection_ready()` · 해석기의 `null` 처리 · `STARTED_PARAM`/헬스 발행 시각을 **그날의 코드**에서 읽고, 위 표의 줄 번호를 정정한다.
- [ ] **AC-1 — 갈래를 고른다 (🔴 소유자 결정).** 최소한 ⓐ 람다가 «기동 후 N초 안 + 헬스 발행 시각 < 기동 시각» 이면 `False` 를 낸다(N 상한 넘으면 다시 `None`) ⓑ 해석기가 `/status` 의 다른 필드로 가른다 ⓒ 그대로 둔다(40초는 수용) 를 비교해 추천과 함께 묻는다. 🔴 추천을 결정으로 적지 마라.
- [ ] **AC-2 — 두 방향 bite.** ① 방금 켠 인스턴스(헬스 미발행) → `starting` ② 발행이 N 초 넘게 멈춘 인스턴스 → `running`(옛 동작). 둘 다 테스트가 있고, 판정을 한쪽으로 되돌리면 반대 칸이 빨개진다.
- [ ] **AC-3 — 창 판정.** 다음 창에서 668 과 같은 쌍 표본(`/status` · `/bundles` · backend-state · 세 앱 배너)으로 **첫 표본부터** `starting` 인지 본다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`). 🔴 람다 변경이면 `terraform apply` 는 소유자 몫이다.

---

# Related Specs

- `tasks/in-progress/TASK-MONO-668-the-banner-vanishes-seven-minutes-before-the-backend-can-answer.md` (§ Edge Cases «헬스 stale 도 null → running» · § 창 실측 2026-09-17)
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
