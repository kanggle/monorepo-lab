# Task ID

TASK-MONO-598

# Title

🔴🔴 **머지 게이트의 required check 집합이 비어 있다** — CLAUDE.md 의 3-dim (c) 「0 failing
**required** checks」가 **어떤 상태에서도 참**이다. 물지 않는 가드이고, 오늘 실제로
그 공허한 통과에 기대어 티켓 하나를 닫았다.

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- guard
- process

---

# Goal

CLAUDE.md § Task Rules 의 **객관적 머지 검증**은 세 축을 요구하고, 그 (c)는 이렇게 쓰여 있다:

> (c) the impl PR's pre-merge `gh pr checks <n>` snapshot had **0 failing required checks**

이 저장소에는 **required check 가 하나도 설정돼 있지 않다.** 그러므로 (c)는 ci.yml 이
통째로 빨강인 PR 에서도 **참**이다. 술어가 참조하는 집합이 공집합이면 그 술어는 아무것도
재지 않는다 — 이 저장소가 반복해서 잡아 온 결함 클래스 그대로다.
[[feedback_why_a_guard_does_not_bite]]

---

# Context — 실측 (2026-08-28 UTC, `origin/main` = `f10ae43a6`)

## ① 게이트가 **아예 없다** — 그리고 플랜 문제가 아니다

```
gh api repos/kanggle/monorepo-lab/branches/main/protection
  → 404  {"message":"Branch not protected"}

gh api repos/kanggle/monorepo-lab/rulesets            → 0
gh api repos/kanggle/monorepo-lab/rules/branches/main → 0

gh api repos/kanggle/monorepo-lab --jq .private       → false
```

🔵 **저장소가 public 이라 branch protection 에 요금제 장벽이 없다.** 「Pro 가 아니라서 못
켠다」가 아니다 — 그냥 안 켜져 있다. classic protection 과 ruleset **둘 다 0** 이므로
`gh pr merge` 는 CI 상태와 **무관하게** 성공한다.

## ② 🔴 그 공허한 통과에 오늘 실제로 기댔다

`TASK-MONO-594` 를 `review → done` 으로 닫으면서 3-dim 을 쟀다:

| 축 | 결과 |
|---|---|
| (a) PR 상태 | `MERGED`, sha `37e234616` |
| (b) main 조상 | rc=0 |
| (c) 머지 시점 rollup | 5 SUCCESS · 44 SKIPPED · **2 FAILURE** |

그 2건은 Vercel 계정 빌드 레이트리밋(`description=null` + `?upgradeToPro=build-rate-limit`)
이었고 코드 실패가 아니었다. **닫아도 되는 진짜 근거는 «main push 런이 success» 였다** —
그런데 CLAUDE.md 의 문구를 그대로 적용하면 (c)는 **required 집합이 비었으므로 자동 통과**다.

🔴 **즉 그날 판정이 옳았던 것과, 규칙이 그 판정을 보장한 것은 별개다.** 규칙은 아무것도
보장하지 않았다. 다음번에 진짜 RED 가 왔을 때도 (c)는 똑같이 통과할 것이다.

## ③ 🔴 (c)가 막으려던 해악은 실재한다 — 같은 날 관측됐다

`TASK-MONO-592` 의 impl 머지(`1a7f7b54f`)는 PR 런이 초록이었는데 **main push 런이
failure** 였다(`Integration (inventory …)`; 재실행은 통과 — `TASK-MONO-597`). 즉
**«PR 초록» 과 «main 초록» 은 다른 명제**이고, (c)는 전자만 본다. required 집합이 비어
있으면 전자마저 안 본다.

## ④ 🔵 무엇을 required 로 걸지는 **자명하지 않다**

`ci.yml` 의 잡은 대부분 path-filter 로 `skipping` 이다. GitHub 의 required check 는
**«그 이름의 체크가 성공으로 보고될 것»** 을 요구하므로, skip 되는 잡을 그대로 required 로
걸면 **모든 PR 이 영원히 pending** 이 된다. 이것이 이 티켓이 «켜라» 로 끝나지 않는 이유다.

🔴 그리고 **Vercel 체크는 required 후보가 아니다** — ②가 보여주듯 계정 한도로 FAILURE 가
되며, 그 상태는 어떤 커밋으로도 못 고친다. required 로 걸면 한도에 걸린 24시간 동안
**저장소 전체가 머지 불가**가 된다. [[feedback_a_verifiable_mechanism_is_not_the_cause]]

---

# Acceptance Criteria

## AC-0 — 🔴 착수 시 **다시 잰다**. ① 이 그 사이 바뀌었을 수 있다

- `branches/main/protection` · `rulesets` · `rules/branches/main` 세 엔드포인트를 다시 조회한다.
- 🔵 **양성 대조군**: 조회가 살아 있는지 증명한다 — 존재하는 다른 엔드포인트(예:
  `repos/…` 자체)가 정상 응답하는지 확인. 🔴 404 를 «보호 없음» 으로 읽는 것과 «권한이
  없어 404» 를 구별하라. [[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]

## AC-1 — 🔴 **어떤 체크를 required 로 걸 수 있는지 실측으로 고른다**

- ④ 때문에 이것이 이 티켓의 어려운 부분이다. 후보를 **이름으로 열거**하고, 각각에 대해
  **최근 N개 PR 에서 그 이름이 실제로 어떤 상태로 보고됐는지** 센다(`success` / `skipping` /
  아예 없음). 🔴 «없음» 이 한 번이라도 있으면 required 로 걸 수 없다 — 영구 pending 이 된다.
- `changes` 잡처럼 **항상 도는** 것과, `ci.yml` 이 `if:` 로 거는 것들을 구분한다.
- 🔵 GitHub 의 관용 표현(모든 잡을 aggregate 하는 단일 `all-checks-passed` 잡을 두고
  그 하나만 required 로 거는 방식)을 **검토하고, 채택하든 안 하든 이유를 적는다.**
  그 잡은 skip 된 잡을 «실패 아님» 으로 접어야 하므로 `if: always()` + 명시적 상태 집계가
  필요하고, 🔴 **그 집계가 틀리면 정확히 아무것도 안 막는 잡이 하나 더 생긴다.**

## AC-2 — **소유자 결정 지점**

🔴 branch protection / ruleset 을 켜는 것은 **무엇이 허용되는지를 바꾸는 변경**이라
에이전트가 단독으로 적용할 사안이 아니다(CLAUDE.md § 분류기 게이팅의 축과 같다).

- AC-1 의 후보 목록과 각 선택의 대가를 **표로** 제시하고 소유자 확인을 받는다.
- 🔴 «self-merge 를 막을 것인가» 는 **별개 축**이다. 이 저장소의 워크플로는 단독 작업자 +
  에이전트 self-merge 를 전제로 돌아가고 있으므로(개인 스탠딩 승인 존재), required review 를
  같이 켜면 **현재 작업 방식이 멈춘다.** 이 티켓은 **status check 축만** 다룬다 —
  approval 축을 끌어들이지 마라.

## AC-3 — 🔴 규칙 문구를 실재와 맞춘다

켜든 안 켜든, CLAUDE.md 의 (c) 문구는 지금 **거짓을 말하고 있다.**

- required 를 **켜면**: (c)는 그대로 유효해지고, 무엇이 required 인지 한 줄로 가리킨다.
- **안 켜면**: (c)를 「required checks」가 아니라 **실제로 재는 것**으로 고쳐야 한다 —
  예컨대 「머지 커밋의 `main` push 런이 success 인가」(③이 보여주듯 그것이 진짜 명제다).
  🔴 이때는 (c)가 **머지 전에는 판정 불가**해지므로, 3-dim 이 «머지 후 확인» 축을 하나
  갖게 된다는 뜻이다. 그 함의를 적어라.
- 🔵 어느 쪽이든 **한 사실이 두 곳에 있으면 한쪽만 고쳐진다** — `CLAUDE.md` 와
  `platform/git-workflow-policy.md` 가 이 규칙을 **둘 다** 담고 있는지 먼저 grep 하고,
  담고 있으면 **같은 PR 에서 둘 다** 고친다.
  [[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

## AC-4 — 🔵 «가드가 물었나» 를 증명한다

required 를 켰다면, **일부러 빨강인 PR** 을 만들어 머지 버튼이 실제로 막히는지 찍는다.
🔴 설정 화면의 스크린샷이나 API 응답은 «설정이 들어갔다» 의 증거일 뿐 «막는다» 의 증거가
아니다. [[feedback_assert_the_injection_before_reading_the_bite]]

---

# Related Specs

- `CLAUDE.md` § Task Rules — «Objective merge verification before any close chore» 의 (c)
- `platform/git-workflow-policy.md` § Merge-Verification Worked Incident — 같은 규칙의 두 번째 거처(AC-3)
- `.github/workflows/ci.yml` — required 후보의 출처(AC-1)
- `tasks/done/TASK-MONO-594-a-manual-redeploy-can-never-build.md` § 종료 검증 — ②의 원본 기록

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| skip 되는 잡을 required 로 건다 | 🔴 **모든 PR 이 영원히 pending.** AC-1 이 이름별 실측을 요구하는 이유 |
| Vercel 체크를 required 로 건다 | 🔴 계정 한도 24시간 = 저장소 전체 머지 불가. 후보에서 제외 |
| aggregate 잡 하나만 required 로 건다 | 🔵 관용 표현이지만 집계가 틀리면 **아무것도 안 막는 잡**이 하나 더 생긴다. AC-1이 이유까지 적게 한다 |
| required review 를 같이 켠다 | 🔴 **축이 다르다.** 현재 워크플로(단독 + self-merge)가 멈춘다. AC-2 가 status check 축으로 한정 |
| 안 켜기로 결정한다 | 🔵 **정당한 결론이다.** 단 AC-3 이 남는다 — 규칙 문구가 거짓인 채로 두면 안 된다 |
| 404 를 «보호 없음» 으로 읽는다 | 권한 부족도 404 다. AC-0 이 양성 대조군을 요구 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| 「protection 켜기」로 닫는다 | skip 잡이 required 가 되어 전 PR 영구 pending | AC-1 — 이름별 실측 |
| 켰다고 적고 bite 를 안 본다 | 설정은 들어갔는데 안 막는다 | AC-4 |
| 규칙 문구를 안 고친다 | (c)가 계속 공허하게 통과하고 아무도 모른다 | AC-3 |
| `CLAUDE.md` 만 고친다 | `git-workflow-policy.md` 사본이 옛 문구로 남음 | AC-3 마지막 항목 |
| approval 축까지 켠다 | 현재 작업 방식이 멈추고 규칙이 꺼진다 | AC-2 |
| 소유자 확인 없이 적용한다 | 🔴 «무엇이 허용되는지» 를 바꾸는 변경이다 | AC-2 |
