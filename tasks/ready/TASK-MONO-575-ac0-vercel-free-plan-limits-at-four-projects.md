# Task ID

TASK-MONO-575

# Title

`ADR-MONO-067` **AC-0 ④** — 무료 플랜 한도를 **프로젝트 4개 기준**으로 다시 잰다. 이관은 커밋당 배포를 2배로 만든다.

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- measurement
- ci

---

# Goal

`ADR-MONO-067` § AC-0 4번을 실측한다.

> **Vercel 무료 플랜의 한도** — 배포 rate limit 외에 함수 호출/실행시간 한도가 데모 트래픽을
> 견디는지. 오늘 CI 를 빨갛게 만든 것이 같은 플랜의 다른 한도였다.

🔴 **그리고 ADR 이 세지 않은 축을 하나 더 잰다: 배포 *건수* 자체.**

---

# Context — 이관은 배포를 2배로 만든다 (2026-08-23 실측)

지금 `vercel.json` 은 **2개**다:

```
infra/demo/aws/site/vercel.json                              → kanggle-portfolio
projects/fan-platform/web/fan-platform-web/vercel.json       → kanggle-fan
```

단계 2·3 은 web-store 와 console 에 각각 **새 Vercel 프로젝트**를 만든다 ⇒ **2 → 4**.
`scripts/vercel-should-build.sh` 의 헤더가 왜 그게 문제인지 이미 적어 뒀다:

> 이 저장소는 Vercel 프로젝트가 **둘**이라 커밋 하나가 배포 **둘**을 굽고, 문서 전용 PR 도
> 예외가 아니었다 ⇒ 무료 플랜의 일일 한도에 닿아 24시간 동안 모든 PR 이 빨개지고,
> 그동안 **론처는 낡은 판을 계속 서빙했다.**

프로젝트 2개로도 그 한도에 닿았다. **2026-08-23 하루에만 두 번 물렸다**(06:12Z 이후 24시간
차단). 4개면 같은 커밋 흐름에 배포 압력이 **2배**가 된다.

🔵 `ignoreCommand` 가 그걸 줄이도록 설계돼 있지만, **`TASK-MONO-572` 가 그 판정 창이 한 커밋뿐임을
밝혔다** — 여러 커밋 push 에서는 판정이 빗나간다. 즉 **완화 장치 자체가 지금 새는 중**이다.

---

# Acceptance Criteria

## AC-0 — 한도를 **문서가 아니라 계정에서** 확인한다

Vercel 무료(Hobby) 플랜의 실제 한도 4종을 **이 계정의 사용량 화면에서** 읽는다:

| 축 | 왜 |
|---|---|
| 일일 배포 수 | 오늘 두 번 물린 축 |
| 함수 호출 수 | (B) 는 **모든 데모 트래픽**을 함수로 보낸다 |
| 함수 실행 시간 | 프록시 한 홉이 늘어난다 |
| 대역폭 | 이미지·정적 자산 포함 |

🔴 **문서 값을 적지 마라.** 플랜 한도는 바뀌고, 계정에 적용된 값이 진실이다.
🔴 이 단계는 **소유자 대시보드 작업**이다 — 이 저장소에 Vercel 토큰·CLI 가 없다(실측).

## AC-1 — 배포 건수를 **실제 커밋 흐름으로** 추정한다

최근 30일의 `main` 커밋 수와 PR 수를 세고, 프로젝트당 `ignoreCommand` 통과율을 곱한다.

```bash
git log --since=30.days --oneline origin/main | wc -l
gh pr list --state merged --limit 100 --json mergedAt --jq '[.[]|select(.mergedAt>"...")]|length'
```

🔴 **추정치임을 명시한다.** 이것은 *"앞으로 이만큼 구울 것"* 의 산술이지 측정이 아니다.
[[feedback_local_proves_behaviour_not_performance]]

🔵 **대조군**: 같은 창에서 실제로 몇 건이 구워졌는지도 센다(커밋 상태 API 의 Vercel context
개수). 산술과 실측이 어긋나면 **`ignoreCommand` 의 통과율 가정이 틀린 것**이고, 그 자체가 발견이다.

## AC-2 — 4개 프로젝트에서 한도를 넘는지 판정한다

AC-0 의 한도 ÷ AC-1 의 추정 배포량. **넘으면 이관 순서와 속도가 바뀐다.**

판정은 셋 중 하나로 적는다:

- **견딘다** — 근거 숫자와 여유율.
- **못 견딘다** — 그러면 선택지는 ① 유료 플랜 ② 프로젝트 수를 늘리지 않는 다른 배치
  ③ `ignoreCommand` 를 더 촘촘하게(단, `TASK-MONO-572` 가 먼저) 중 하나이고, **이 티켓은
  고르지 않는다** — `ADR-MONO-067` 의 후속 결정 입력이다.
- **판정 불가** — 어느 입력이 없어서인지 적는다.

## AC-3 — `TASK-MONO-572` 와의 순서를 명시한다

572 가 고쳐지기 전에는 `ignoreCommand` 통과율이 **실제보다 높게** 측정된다(빗나간 판정이
"건너뜀"으로 집계되므로). 572 이후에 재는 것이 정확하고, 그 전에 잰다면 **그 사실을 숫자 옆에
적는다**.

## AC-4 — 결과를 `ADR-MONO-067` § AC-0 4번에 기록한다

프로젝트 2 → 4 라는 사실도 함께 적는다. ADR 본문은 그 축을 세지 않았다.

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § AC-0 (4), § Consequences
- `scripts/vercel-should-build.sh` (헤더가 배포 수 문제의 1차 기록)
- `tasks/ready/TASK-MONO-572-vercel-ignore-window-is-one-commit-wide.md`

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 측정 시점에 rate limit 이 걸려 있다 | 사용량 화면은 여전히 읽힌다. **차단 상태가 곧 데이터**다 — 언제 걸렸고 언제 풀리는지 기록한다 |
| 무료 플랜 한도가 **문서와 계정이 다르다** | 계정 값이 진실이다. 차이를 기록한다 |
| 데모가 꺼져 있어 함수 호출이 0 | 호출 한도는 **트래픽 가정** 위에서만 잴 수 있다. 가정을 숫자로 적고 **측정이 아니라고 표시**한다 |
| 프로젝트를 아직 안 만들었다 | 4개 기준은 **예측**이다. 2개 실측 + 배수로 적되, 배수가 정확히 2가 아닐 수 있음을 적는다(앱마다 빌드 시간·빈도가 다르다) |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 한도를 못 읽는다 | 대시보드 접근 불가 | 소유자 작업이다. **추정으로 대체하지 마라** — AC-2 를 판정 불가로 적는다 |
| 산술과 실측이 크게 어긋난다 | AC-1 대조군 불일치 | `ignoreCommand` 통과율 가정이 틀렸다. **그쪽이 이 티켓보다 급하다** |
| "견딘다"로 적었는데 이관 후 물린다 | 배포 차단 재발 | 가정(커밋 빈도)이 창 밖으로 나간 것. 🔴 **게이트 없는 숫자는 반드시 낡는다** — 결론에 **언제 다시 재야 하는지**를 함께 적는다 [[feedback_a_figure_nothing_can_fail_on_will_drift]] |
