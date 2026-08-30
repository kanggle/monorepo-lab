# Task ID

TASK-MONO-607

# Title

main 축은 여전히 **커밋 1건당 배포 2~3건**을 만들고 그중 굽는 것은 소수다. Deploy Hook 으로 그 축을 끈다. 🔴 소유자 선행 하나에 막혀 있다.

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- vercel
- cost

---

# 🔎 어디서 왔나 — `TASK-MONO-590` 의 AC-1~AC-4 분리분

`TASK-MONO-590` 이 AC-5 로 효과를 실측한 뒤, § 축소 가 그 자리에 예약해 둔 결정
(*"AC-5 가 닫히면 … 별 티켓으로 분리할지 결정한다"*)에 따라 분리됐다.

## 590 이 남긴 숫자 — 🔴 **이 티켓의 전제는 이 값들이다**

창 `2026-08-29T07:29Z → 08-30T07:29Z`, 계측기 = **GitHub commit-status 행 중 context 가
`Vercel – *` 인 것**(🔴 과금 축인 `Deployments Created` 가 **아니다** — 이 저장소는 Vercel
토큰이 없어 못 읽는다).

| 축 | 08-28 | 위 창 | 커밋/PR 당 |
|---|---|---|---|
| 브랜치 | 16 PR → 32행 | 20 PR → **0행** | 2.0 → **0.0** ✅ `590` AC-6 이 닫았다 |
| **main** | 16 커밋 → 32행 | 20 커밋 → **50행** | 2.0 → **2.5** 🔴 **안 줄었다** |

그 **50행 중 실제로 구워진 것은 6** 이고, **나머지 44 는 아무것도 안 구웠다**
(생성+`Canceled by Ignored Build Step` 37 · `Deployment rate limited` 7).

🔵 **`ignoreCommand` 는 옳게 판별하고 있다** — 구운 6건은 전부 론처(`infra/demo/aws/site`)
또는 store 프로젝트가 실제로 바뀐 커밋이다. **문제는 판별이 아니라, 판별하려면 먼저
배포가 «생성»돼야 한다는 것**이다: 한도(그리고 과금)는 **생성**을 센다.
🔴 `590` § Context ⑥ 이 실측했다 — *"건너뛴 배포도 한도를 먹는다"*.

## 🔴 승수가 오르고 있다

`kanggle-store` 가 `2026-08-29T11:33:04Z` 부터 상태 행을 붙이기 시작했다(`TASK-MONO-582`).
커밋당 승수가 **2 → 3**. `TASK-MONO-586`(fan) 이후 네 번째가 생기면 또 오른다.
🔴 **그러므로 「지금 아프지 않다」가 「앞으로도 안 아프다」가 아니다.**

---

# ⏳ 선행 — 🔴 **소유자 작업 하나. 이것 없이는 착수 자체가 무의미하다**

> 각 Vercel 프로젝트에서 **`Settings → Git → Deploy Hooks`** 로 훅을 만들고,
> 그 URL 을 이 저장소의 **GitHub Actions secret** 으로 등록한다.

대상은 착수 시점의 프로젝트 전부 — 현재 `kanggle-portfolio` · `kanggle-fan` ·
`kanggle-store` (🔴 **다시 세라**, `586` 이 하나 더 만들 수 있다).

🔴 **그 전에는 워크플로 쪽 절반을 써도 발사할 대상이 없다.** 반쪽만 랜딩시키지 마라 —
아래 AC-2 가 그 실패 모드를 막는 가드이고, **AC-1 과 한 쌍으로만** 랜딩한다.

---

# 🔵 재개 트리거 — **자기 숫자를 갖고 있다**

`590` § 축소 가 우선순위를 내리면서 «언제 다시 올라오는가» 를 셋으로 적었다. 그대로 상속한다:

| # | 트리거 | 지금 값 | 어디서 재나 |
|---|---|---|---|
| ① | 소유자가 **Hobby 로 되돌림** | Pro (2026-08-29 전환, `590` 이 실측) | 소유자 대시보드 |
| ② | Pro 의 **6,000/일에 실제로 접근** (ktg 포함 **계정 전체**) | 🔴 **미측정** — 이 저장소는 계정 카운터를 못 읽는다 | `TASK-MONO-587` 축과 같은 출처 |
| ③ | **프로젝트가 4개**로 늘어 소비가 2배 | **3개** (`08-29T11:33Z` 에 store 추가) | 이 저장소에서 셀 수 있다 (commit-status context 종류) |

🔴 ②는 **「모른다」이지 「안전하다」가 아니다.** 트리거로 쓰려면 먼저 잴 방법이 있어야 하고,
그 출처는 소유자 대시보드뿐이다. [[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]

---

# Goal

main 축의 자동 배포 생성을 끄고, **필요할 때만** Deploy Hook 으로 명시 발사한다.
그 결과 커밋당 생성 수가 **몇으로** 바뀌는지 `590` AC-5 와 **같은 계측기**로 잰다.

---

# Scope

**In:**

- 각 프로젝트 `vercel.json` 의 `git.deploymentEnabled` 에서 **`main` 을 `false` 로** (현재 `true`)
- Deploy Hook 을 쏘는 GitHub Actions 워크플로 — **무엇이 바뀌었을 때 쏘는가**의 술어 포함
- 훅 미발사 가드 (AC-2)
- `scripts/check-vercel-build-triggers.sh` 의 전제 재작성 (AC-3)

**Out:**

- **브랜치 축** — `590` AC-6 이 이미 닫았다(`{"**": false, "main": true, "preview/*": true}`).
  🔴 그 `preview/*` 해치를 지우지 마라, 프로브가 쓴다
- **이미지 변환 축** — `TASK-MONO-587`. 🔴 **두 게이트를 하나의 「플랜 판정」으로 합치지 마라**
- `korea-travel-guide` 몫 — 다른 저장소다

---

# Acceptance Criteria

## AC-0 — 🔴 전제를 다시 잰다 (verify-then-act)

1. **선행이 실제로 됐는가** — 훅 URL secret 이 등록돼 있는가. 없으면 **STOP**.
2. **모집단을 다시 세라** — Vercel 프로젝트가 몇 개인가. 🔴 위 표의 「3개」는
   `2026-08-30` 값이다. [[feedback_recount_population_dont_inherit_scope]]
3. **`590` AC-5 의 숫자를 상속하지 말고 같은 계측기로 다시 세라** — 착수 직전 24h.
   그것이 이 티켓의 before 값이다.

## AC-1 — Deploy Hook 배선

- `vercel.json` 의 `git.deploymentEnabled.main` → `false`
- 워크플로가 훅을 쏘는 조건을 **명시적 술어**로 쓴다 (무엇이 바뀌면 그 프로젝트를 굽는가)
- 🔴 **`ignoreCommand` 와의 관계를 명시하라** — 훅으로 만든 배포에도 `ignoreCommand` 는
  **그대로 돈다**(`590` § Context ⑥ 실측: *"오늘 재배포가 정확히 그렇게 죽었다"*).
  두 기전이 겹치면 **훅을 쐈는데 안 구워지는** 상태가 생긴다. 어느 쪽이 술어를 갖는지 정해라.

## AC-2 — 🔴 **가드: 훅을 안 쏘면 빨개져야 한다**

AC-1 이 들여오는 실패 모드는 **조용한 낡음**이다 — 자동 배포를 껐는데 훅이 안 쏘면
사이트가 낡은 채로 초록이다.

- 🔴 **양성 대조군 필수**: 일부러 훅을 못 쏘게 만들고 가드가 **무는지** 확인한다.
  물지 않으면 그 가드는 없는 것이다. [[feedback_why_a_guard_does_not_bite]]
- 🔴 **모집단 ≥ 1 을 단언하라** — 대상 프로젝트가 0개로 계산되면 가드는 **rc=0 으로
  아무 일도 안 하고 통과한다**. [[feedback_a_runner_that_matches_no_package_exits_zero]]
- 🔴 **AC-1 과 한 쌍으로만 랜딩한다.**

## AC-3 — `scripts/check-vercel-build-triggers.sh` 의 전제가 바뀐다

지금 그 가드는 *"`ignoreCommand` 가 유일 방어"* 를 전제로 **옳은 것을 테스트하고 있다.**
AC-1 이 랜딩하면 그 전제가 깨진다. 🔴 **같은 PR 에서 고쳐라** — 안 고치면 가드가
**옛 세계를 지키며 초록**이다. [[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]

## AC-4 — **이 설계가 안 고치는 것**을 적는다

`590` AC-4 가 이미 대부분 채웠다(이미지 축 · Hobby 비상업 조항 · ktg 몫 ·
프리뷰 URL 상실은 `590` AC-6 이 값 매김). **남은 것은 AC-1 랜딩 후의 최종 정리다** —
상속하지 말고 **그 시점에 다시 확인**해서 적어라.

## AC-5 — 효과를 **재고** 적는다

AC-0 ③ 의 before 와 **같은 계측기·같은 창 길이**로 after 를 잰다.

- 🔴 **국면을 섞은 하나의 합계는 적지 마라** — `590` AC-5 가 그렇게 했다.
- 🔴 **원시 행 수로 before/after 를 비교하지 마라.** 프로젝트가 하나 늘면 승수가 올라
  «늘었다» 로 보인다. **커밋당** 으로 비교하라. [[feedback_comparing_two_extracts_measures_the_extractors]]
- 🔴 **잰 축의 이름을 적어라** — commit-status 행이지 과금 축이 아니다.
  [[feedback_a_reported_figure_must_name_what_was_measured]]

---

# Related Specs

- `tasks/review/TASK-MONO-590-…md` — 이 티켓의 출처. AC-0(결과 A) · AC-5(실측) · AC-6(브랜치 축) 이 거기 있다
- `scripts/check-vercel-build-triggers.sh` — AC-3 대상
- `scripts/vercel-should-build.sh` — 현재 `ignoreCommand` 술어
- `TASK-MONO-575` AC-2 — 계정 한도 판정. 🔴 그 입력(`74/24h`)은 브랜치 축을 포함했고 그 축은 이제 0이다
- `TASK-MONO-587` — 이미지 변환 축. **합치지 마라**

# Related Contracts

없음.

---

# Edge Cases

- 🔴 **새 Vercel 프로젝트가 생기면 자동 배포가 다시 켜진다.** `582` 가 `kanggle-store` 를
  만들 때 정확히 그랬다. AC-1 은 **새 프로젝트가 기본으로 꺼진 채 태어나게** 하는 방법을
  정해야 한다 — 안 그러면 이 티켓은 프로젝트마다 다시 열린다.
- 🔴 훅 URL 은 **secret 이다.** 로그·PR 본문·티켓에 찍지 마라.
- 🔴 훅으로 만든 배포에도 `ignoreCommand` 가 돈다 — AC-1 참조.
- 🔵 `preview/*` 해치는 남긴다. 프로브가 그걸로 라이브를 잰다.
- 🔴 되돌림이 필요하면 `git.deploymentEnabled.main` 을 `true` 로 되돌리는 것으로 끝나지만,
  그 되돌림은 **머지 전에는 잴 수 없다**(`590` § 되돌림 계획이 같은 함정을 적었다).

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 훅만 넣고 가드를 뺌 | 사이트가 낡은 채 CI 초록 | AC-2, AC-1 과 한 쌍 |
| 가드를 넣었는데 안 묾 | 같은 증상, 게다가 «지켜진다»는 착각 | AC-2 양성 대조군 |
| 대상 프로젝트 0개로 가드가 통과 | rc=0, 아무 일도 안 함 | AC-2 모집단 ≥ 1 단언 |
| `check-vercel-build-triggers.sh` 를 안 고침 | 옛 전제를 지키며 초록 | AC-3, 같은 PR |
| before/after 를 원시 행 수로 비교 | 프로젝트 증가를 «악화» 로 오독 | AC-5 커밋당 비교 |
| 소유자 선행 없이 착수 | 발사할 대상이 없다 | AC-0 ① STOP |
