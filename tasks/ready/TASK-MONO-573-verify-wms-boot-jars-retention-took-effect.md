# Task ID

TASK-MONO-573

# Title

⏳ **SCHEDULED — 2026-08-31 이전 착수 금지.** `TASK-MONO-566` AC-4: `wms-boot-jars` 의 `retention-days: 1` 이 실제로 물었는지 확인한다.

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- verification
- scheduled

---

# ⏳ 착수 게이트

> **2026-08-31 (UTC) 이전에는 시작하지 마라.**
> `TASK-MONO-566` 머지가 2026-08-23 이고, 기존 79벌은 **옛 7일 정책으로 이미 업로드된 것**이라
> 자기 만료일까지 남는다. 8일을 기다려야 옛 물량이 전부 빠지고 새 정책만 남는다.
>
> 🔴 **날짜만 보고 착수하지 마라 — AC-0 이 먼저다.** 머지가 실제로 언제 됐는지부터 확인한다.

이 티켓이 존재하는 이유: 이 호스트의 `CronCreate` 는 `durable: true` 를 줘도 **세션 한정**이라
날짜가 걸린 의무를 세션 경계 너머로 못 옮긴다. 그래서 미래 시점 검증은 **git 이 추적하는 태스크**로만
살아남는다.

---

# Goal

`TASK-MONO-566` 의 한 줄 변경이 **의도한 효과를 냈는지** 산출물에서 확인하고, 안 냈으면 전제가
어디서 틀렸는지 기록한다.

변경 자체는 이미 머지됐다. 이 티켓은 **효과 측정**이지 구현이 아니다.

---

# Acceptance Criteria

## AC-0 — verify-then-act: 전제부터 확인한다

```bash
git log -1 --format=%cI --  .github/workflows/ci.yml   # 566 머지 시각
grep -n "name: wms-boot-jars" -A 12 .github/workflows/ci.yml | grep retention-days
```

- 머지 시각 + 8일이 **아직 안 됐으면 STOP** 하고 이 티켓을 `ready/` 에 그대로 둔다.
- `retention-days` 가 1이 **아니면** 누군가 되돌린 것이다. STOP 하고 왜인지부터 찾는다 —
  그 경우 이 티켓의 질문은 "효과가 있었나"가 아니라 "왜 되돌아갔나"로 바뀐다.

## AC-1 — 판정: 「가장 오래된 것이 24시간 이내인가」

```bash
gh api --paginate "repos/kanggle/monorepo-lab/actions/artifacts?per_page=100" \
  --jq '.artifacts[] | select(.expired==false) | select(.name=="wms-boot-jars")
        | [.created_at, .expires_at, .size_in_bytes] | @tsv'
```

🔴 **판정 기준은 개수가 아니라 나이다.** `TASK-MONO-566` 이 예측한 «≈11벌 / ≈3.2 GB» 는
78 ÷ 7 의 평균에서 나온 **산술**이고, wms 잡은 path-filter 로 게이팅되므로 하루 실행 횟수는
변동한다. 개수로 판정하면 **정상인데 빨간불**이 켜지거나 그 반대가 된다.

**참(PASS)**: 살아있는 `wms-boot-jars` 중 **가장 오래된 `created_at` 이 24시간 이내**이고,
모든 `expires_at - created_at` 이 **1일**이다.

🔵 두 번째 조건이 대조군이다 — 나이만 보면 「최근에 많이 돌아서 다 젊은 것」과 구별되지 않는다.
`expires_at` 은 **정책이 적용됐음**을 직접 말한다.

## AC-2 — 회수량을 **잰다**, 추정하지 않는다

566 이 «≈19.5 GB 회수» 를 예측했다. 같은 쿼리로 전체 `expired==false` 합계를 다시 내고,
**2026-08-23 실측 60.33 GB / 374개** 와 대조한다.

🔴 **차이를 전부 이 변경의 공으로 돌리지 마라.** 같은 창에서 다른 아티팩트도 늘고 줄었다.
정직한 판정은 **`wms-boot-jars` 항목만의 before/after**(23.05 GB → ?)이고, 전체 합계는 맥락이다.
[[feedback_a_reported_figure_must_name_what_was_measured]]

## AC-3 — 가드가 살아 있는지 확인한다

`scripts/check-artifact-retention.sh` 가 8일 사이에 **실제로 CI 에서 돌았는가**를 본다
(워크플로를 건드린 PR 이 하나라도 있었다면 돌았어야 한다).

```bash
gh run list --workflow=ci.yml --limit 40 --json databaseId,headBranch,conclusion
# 그중 하나를 열어 `Artifact retention (build outputs expire in 1 day, not 7)` 잡을 찾는다
```

🔴 **한 번도 안 돌았으면 그 자체가 결함이다** — 배선이 아니라 도달성 문제이고, 이 저장소가
가드를 써 놓고 한 번도 행사하지 못한 전례가 있다. 그 경우 필터 조건을 다시 본다.

## AC-4 — 결과를 `TASK-MONO-566` 에 기록하고 두 티켓을 함께 닫는다

566 은 `review/` 에서 이 검증을 기다리고 있다. 결과를 566 의 § 실측 기록에 append 하고,
**566 → `done/`**, **573 → `done/`** 을 한 close chore 로 처리한다.

---

# Related Specs

- `tasks/review/TASK-MONO-566-wms-boot-jars-retention-outlier.md` § AC-4 (이 티켓의 본체)
- `scripts/check-artifact-retention.sh` (566 이 남긴 가드)

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 8일 사이에 wms 잡이 **한 번도 안 돌았다** | 살아있는 `wms-boot-jars` 가 0벌일 수 있다. **0 은 실패가 아니다** — 「가장 오래된 것이 24시간 이내」는 공집합에서 참이다. 다만 그때는 *아무것도 증명하지 못한 것*이므로 **판정 불가**로 적고, 잡이 한 번 돈 뒤 다시 잰다. |
| 누군가 `retention-days` 를 되돌렸다 | AC-0 이 STOP 시킨다. 가드가 있으므로 되돌리려면 가드도 함께 꺼야 했을 것이다 — 그 흔적을 찾는다. |
| 새 boot-jars 잡이 7일로 추가됐다 | 가드가 그 PR 에서 이미 빨갛게 됐어야 한다. 초록으로 통과했다면 **가드의 술어나 도달성이 결함**이고, 이 티켓보다 그쪽이 급하다. |
| GitHub 이 만료 정책을 바꿨다 | `expires_at - created_at` 이 1일이 아닌 값으로 일관되게 나온다. 우리 결함이 아니므로 관측만 기록한다. |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| e2e 가 jar 을 못 받는다 | `download-artifact` 에서 404 / artifact not found | 566 의 소비-경로 판독이 틀렸다는 뜻. 한 줄 revert 하고 **그 경로를 티켓에 적어** 7일을 정당화한다 — 지금은 정당화가 없다는 것이 566 의 핵심이므로, 발견되면 그 자체가 성과다. |
| 용량이 안 줄었다 | `wms-boot-jars` 합계가 여전히 20 GB 대 | `expires_at` 을 본다. 정책은 붙었는데 잡이 훨씬 자주 도는 것일 수 있다 — 그건 다른 결함이다. **「안 줄었다」를 곧바로 「정책이 안 먹었다」로 읽지 마라.** |
| 가드가 8일간 한 번도 안 돌았다 | AC-3 이 이것만 본다 | 필터가 좁은지, 아니면 정말 워크플로를 건드린 PR 이 없었는지 갈라 본다. 후자면 결함이 아니라 표본 부족이다. |
