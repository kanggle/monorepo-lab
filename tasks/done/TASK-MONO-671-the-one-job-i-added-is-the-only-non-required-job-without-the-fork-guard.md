# Task ID

TASK-MONO-671

# Status

done

# Title

🔴 **내가 더한 잡 하나가 비-required 중 유일하게 사본 가드를 안 달고 있었다** — 그리고 그걸 재면서 `TASK-MONO-664` 의 중심 수치가 낡은 것이 드러났다

# Owner

monorepo

# Task Tags

- ci
- chore

---

# Goal

`ci.yml` 의 `flyway-version-collision` 잡에 사본 가드(`github.repository == 'kanggle/monorepo-lab'`)를
단다. 🔴 `TASK-MONO-669` 가 그 잡을 더하면서 **이것만 빠뜨렸다** — 이 티켓은 그 뒷정리다.

🔵 그리고 그것을 재는 과정에서 나온 **`TASK-MONO-664` 의 수치 정정**을 그 티켓에 되돌려 준다.

---

# 🔴 실측 (2026-09-11 UTC)

가드가 **없는** 잡은 다섯이었다:

| 잡 | required 인가 |
|---|---|
| `changes` | 🟢 **예** |
| `INDEX queue drift (…)` | 🟢 **예** |
| `Task ID collision (…)` | 🟢 **예** |
| `Walkthrough limitation ledger drift (…)` | 🟢 **예** |
| **`flyway-version-collision`** | 🔴 **아니오** ← 내가 더한 것 |

🔵 **앞 넷이 가드가 없는 것은 의도적이다.** `scripts/required-check-names.txt` 의 넷과 **정확히
일치**하고, required context 가 fork 에서 `skipped` 가 되면 그 context 는 **영구 pending** 이
되어 `main` 이 모든 PR 에서 BLOCKED 된다.

⇒ 🔴 **비-required 중 가드가 없던 것은 내 잡 하나뿐이었다.** 그것이 이 티켓이다.

## 🔴🔴 그리고 `TASK-MONO-664` 의 중심 수치가 낡았다

664 는 자기 술어를 **적어 두었다**(그 티켓 210–211행):

```
ci.yml 잡 수  = grep -cE '^  [a-z0-9_-]+:$'      → 62
가드 수       = grep -c 'github.repository =='   → 14
```

🔵 **그 술어를 그대로** 오늘 돌렸다: 가드 수 = **56**(이 티켓의 수정 후 **57**), 잡 수 = **61**.

⇒ 🔴 664 의 전제(*"62개 잡 중 **14곳에만** 붙어 있다"*)는 더는 참이 아니다. 지금은 **거의 다
붙어 있고**, 안 붙은 것의 대부분이 **일부러** 그렇다. **그 티켓이 무엇을 하는 티켓인지가 바뀐다.**

🔴 **여기서 664 를 닫지 않는다** — 그 티켓의 AC-1 이 *"어디까지 붙일지 **소유자에게 묻고**"* 로
시작하고, 그 질문은 수치가 바뀌어도 남는다(required 넷을 어떻게 다룰지). 이 티켓이 하는 것은
**그 티켓에 재측정을 되돌려 주는 것**뿐이다.

---

# Scope

## 포함

- `ci.yml` 의 `flyway-version-collision` 에 사본 가드 한 줄(이웃 잡의 문법 그대로).
- `TASK-MONO-664` 에 재측정 기록(그 티켓의 술어로, 결과와 해석).
- `TASK-MONO-667` 의 게이트 정정 — 아래 § 곁가지.

## 제외

- 🔴 **required 넷에 가드를 다는 것** — 그러면 `main` 이 영구 BLOCKED 된다.
- 🔴 **664 를 닫거나 그 갈래를 고르는 것** — 소유자 질문이 남아 있다.

---

# Acceptance Criteria

- [x] `flyway-version-collision` 에 사본 가드. 🔵 문법은 이웃 잡과 **같게**(`if: >-` 2줄).
- [x] 🔴 **수정 후 가드 없는 잡이 정확히 required 넷인지** 확인한다 —
      `scripts/required-check-names.txt` 와 대조하라. 다섯째가 남아 있으면 이 티켓은 안 끝났다.
- [x] YAML 이 파싱되고 잡 수가 **안 변하는지**(61) 확인한다.
- [x] 🔴 `TASK-MONO-664` 에 재측정을 적는다 — **그 티켓이 쓴 술어로**, 그리고 «전제가 바뀌었다»
      까지. 🔴 **닫지는 마라.**
- [x] 🔴 `TASK-MONO-667` 의 게이트를 정정한다(§ 곁가지).

---

# 🔵 곁가지 — `TASK-MONO-667` 의 게이트가 **부족하다**

667 의 열린 칸 첫째가 *"⏳ **659 가 닫힌 뒤** wms·scm 을 재촬영해서 싣는다"* 다.
🔴 **659 가 닫혀도 지금 찍으면 고쳐지기 전 화면이 찍힌다.** 실측(2026-09-11):

| 축 | 값 |
|---|---|
| 배포된 AMI (`deployed-ami.env`) | `6ae6145db` |
| 그 커밋의 `InventorySnapshotResponse` 에 `warehouseCode` | **0건** |
| `main` 의 같은 파일 | **1건** |

🔵 기전: 데모 AMI 는 **jar 를 구워 넣는다**(`COPY build/libs/<svc>.jar`). 새 코드는 **재굽기 →
`ami_id` 변경 → `terraform apply`** 로만 그 호스트에 도달하고, 그 apply 가 **인스턴스를 교체**한다.
⇒ 머지는 데모 호스트에 아무것도 안 보낸다.

⇒ 그 칸의 게이트는 **「659 + AMI 재굽기」** 여야 한다. 🔴 **재굽기는 소유자 승인 사항**이므로
이 티켓은 게이트를 **정정만** 하고 재굽기를 하지 않는다.

---

# Related Specs / Contracts

- `TASK-MONO-669` — 이 잡을 더한 티켓(가드를 빠뜨린 자리)
- `TASK-MONO-664` — 사본 가드 커버리지. 🔴 이 티켓이 그 수치를 되돌려 준다
- `TASK-MONO-667` — README 스크린샷. 🔴 이 티켓이 그 게이트를 정정한다
- `scripts/required-check-names.txt` — required 넷의 핀(가드 없는 넷이 이것과 일치해야 한다)
- `TASK-MONO-598` / `599` — required 집합과 그 이름이 왜 핀으로 고정돼 있는가

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| required 집합이 바뀐다 | 🔴 가드 없는 잡의 목록도 같이 바뀌어야 한다 — `required-check-names.txt` 가 권위다 |
| 사본에서 이 잡이 돌아도 통과한다 | 🔵 그럴 수 있다(파일만 읽는다). 그래도 `skipped` 가 옳다 — *"이 잡은 이 저장소의 질문이 아니다"* |
| 664 가 그 사이 닫힌다 | 재측정은 그 티켓이 `done/` 이면 `## CORRECTION` 으로 간다 |

---

# Failure Scenarios

1. 🔴🔴 **required 넷에도 가드를 단다** → fork 에서 `skipped` → context 영구 pending → `main` 전면 BLOCKED.
2. 🔴 **664 를 「수치가 바뀌었으니 닫는다」로 처리한다** → 그 티켓의 소유자 질문(required 넷을
   어떻게 다룰지)이 사라진다.
3. 🔴 **667 의 게이트를 고치면서 재굽기까지 한다** → 소유자 승인 사항이다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Haiku** — `if:` 한 줄 + 티켓 정정 둘. 🔵 다만 «required 넷에는 달면
안 된다» 는 기계적이지 않다(반대로 하면 `main` 이 막힌다).

---

# 🟢 검증 (2026-09-11 UTC)

> 🔴 이 절에는 체크박스를 두지 않는다 — 위 § Acceptance Criteria 가 유일한 체크 자리다.

| 축 | 결과 |
|---|---|
| 사본 가드 추가 | 🟢 이웃 잡과 **같은 문법**(`if: >-` 2줄) |
| 수정 후 가드 없는 잡 | 🟢 **정확히 넷** — `changes` · `INDEX queue drift` · `Task ID collision` · `Walkthrough ledger drift`. `scripts/required-check-names.txt` 의 넷과 **일치** |
| YAML | 🟢 파싱 OK · 잡 수 **61**(불변) |
| 664 술어(`grep -c 'github.repository =='`) | 14 → 56 → **57**(이 수정 후) |
| 664 | 🟢 재측정 절 추가, **닫지 않음**(소유자 질문이 남아 있다) |
| 667 | 🟢 게이트를 「659 + **AMI 재굽기**」로 정정. **재굽기는 안 했다**(승인 사항) |
