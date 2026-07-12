# Task ID

TASK-MONO-374

# Title

**close-chore 가 자기 impl 의 CI 기준선을 먹는다** — main 의 사후 검증 런은 다음 머지에 취소되고, `cancelled` 는 실패가 아니다

# Status

review

# Owner

monorepo

# Task Tags

- ci
- drift-guard
- reachability

---

# Dependency Markers

- **선행 (머지 필요)**: `TASK-MONO-376` (PR #2468) — outbound-service 의 12-consumer 리밸런스 폭풍. **이 task 가 main 기준선을 되살리면 wms integration 스위트가 매 코드 머지마다 실제로 완주한다.** 376 이 먼저 랜딩하지 않으면 되살린 기준선이 첫날부터 RED 이고, **첫날 RED 인 가드는 꺼진다**(`TASK-MONO-360`). **순서를 뒤집지 말 것.**
- **원리 선행**: `TASK-MONO-359` / `TASK-MONO-360` — *실행되지 않는 검사는 초록으로 보고된다* · *트리거는 결함의 도착 경로를 따른다*.

---

# Goal

## 이 티켓의 원래 주장은 틀렸다 — 먼저 그것부터

초안(`…-wms-integration-suite-unwatched.md`)은 이렇게 적었다:

> *"main 최근 12 런에서 \<job absent — path-gated skip\> (12/12 전부)"*

**그 표는 깨진 측정식의 산물이다.** `jq` 의 `test("Integration \\(master-service")` 에서 `\(` 는 **문자열 보간**이라 필터가 아무것도 매칭하지 않았고, 빈 결과가 *"잡이 없다"* 로 읽혔다. 필터를 고쳐 다시 재면:

| main 최근 20 런에서 wms integration 잡 | |
|---|---|
| **success (실제로 완주)** | **5** |
| skipped | 13 |
| cancelled | 1 |
| (잡 자체가 생성 안 됨) | 1 |

**굶고 있지 않다.** 게다가 main **push** 의 게이트는 `code-changed != 'false'` 라서, **어느 프로젝트의 코드가 바뀌든 9개 통합 잡이 전부 돈다**(ecommerce 수정에도 wms 통합이 돈다). path-gating 가설은 **기각**이다.

> ⚠️ **초안의 결론(§3 "main 기준선이 없다")은 맞았고, 이유가 틀렸다.** 이것은 `TASK-MONO-375` 가 ADR-049 에 한 것과 같은 종류의 정정이고, 같은 뿌리다 — **선행 문서의 숫자는 출처가 아니라 가설이다.**

## 진짜 결함

```yaml
concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}   # push 의 ref = refs/heads/main
  cancel-in-progress: true                             #   → main 의 모든 push 가 한 그룹
```

`push` 이벤트에서 `github.ref` 는 **모든 커밋에 대해 `refs/heads/main`** 이다. 연속된 머지가 한 concurrency 그룹으로 뭉개지고, **각 머지가 직전 머지를 검증 중이던 런을 취소한다.**

그리고 **이 저장소 자신의 task 규율이 그것을 상례로 만든다**:

```
impl PR 머지  →  main CI 시작 (통합 잡 ~10분+)
                     ↓  몇 분 뒤
close-chore PR 머지  →  cancel-in-progress 가 앞의 런을 죽임
                     ↓
chore 런 (markdown-only)  →  통합 잡 9개 전부 skip  →  ✅ success
```

### 실측 (2026-07-13, `origin/main` `a7d3a6862`, 최근 20 main 런)

| main 커밋 | 통합 잡 |
|---|---|
| `c4417b432` (JWT 계약, MONO-371) | ok=1 · **CANCELLED=8** |
| `875e583cd` (wms 키잉, MONO-370) | ok=7 · **CANCELLED=2** |
| `03a5aa609` (ecommerce rate-limit) | ok=8 · **CANCELLED=1** |
| `3bfdd5d10` (ADR 인덱스 가드) | ok=8 · **CANCELLED=1** |
| `bfa5d1c6b` | **잡이 하나도 생성되지 않음** (`changes` 잡째로 취소) |
| | **합계 12건이 실행 중 살해** |

**`MONO-371` 의 코드 머지는 main 에서 통합 커버리지가 사실상 0인데 초록으로 보고됐다** — 8개가 살해됐고, 뒤이은 close-chore 런이 9개 전부 skip 했으므로.

## 왜 아무도 몰랐나

**`cancelled` 는 `failure` 가 아니다.** 알림이 없고, 빨간 X 가 없고, 실패를 세는 어떤 대시보드에도 안 잡힌다. **스위트는 돌지 않았고, 저장소는 돌지 않았다는 사실조차 말해주지 못했다.**

> `TASK-MONO-359`/`360` 이 **가드**에 대해 배운 명제 — *실행되지 않는 검사는 초록으로 보고된다* — 가 **워크플로 자신의 사후 검증 런**에도 성립한다.

## 두 번째 얼굴 — nightly

`nightly-e2e.yml` 도 `group: nightly-e2e-${{ github.ref }}` + `cancel-in-progress: true` 이고 `schedule` **과** `push(main)` 둘 다 트리거다. **`schedule` 이벤트의 `github.ref` 도 `refs/heads/main`** 이다 ⇒ **main 머지가 돌고 있는 nightly 를 죽인다.** 역시 조용히.

(`federation-hardening-e2e.yml` 은 cron-only — `push` 트리거가 없어 해당 없음.)

---

# Scope

## In Scope

1. **`ci.yml` · `nightly-e2e.yml` 의 concurrency 그룹을 push 마다 고유하게**:
   `group: …-${{ github.event_name == 'push' && github.sha || github.ref }}`
   `cancel-in-progress: true` 는 **그대로 둔다** — 그룹이 커밋마다 다르면 push 런은 애초에 취소 대상이 없고, **PR 런은 ref-keyed 그룹을 유지해 덮어쓴 push 를 계속 취소한다**(그게 이 설정의 본래 목적이다).
2. **재발 가드** — `scripts/check-ci-baseline-reachable.sh`: `push` 트리거 + 취소 가능 워크플로는 **커밋마다 고유 그룹**이거나 **push 에서 취소가 꺼져** 있어야 한다.
3. **가드의 도달성** — 전용 `ci-baseline` 필터(`.github/workflows/**` + **가드 스크립트 자신**). `workflows` 필터는 `scripts/` 를 안 덮으므로, 그것만 썼다면 **가드가 자기 무력화를 못 본다**.

## Out of Scope

- **wms 스위트의 flakiness** — `TASK-MONO-376` 이 원인을 특정하고 고쳤다(12개 `@KafkaListener` 가 한 group-id 를 공유 + 서로소 구독 = 리밸런스 엔진; CI 러너 실측 join 45회/성공 1회). **초안이 지목한 `SQLSTATE(08006)` / `Closed by interrupt` 는 콘솔의 하류 증상**이었다 — Awaitility 가 포기하며 스레드를 interrupt → 소켓 절단 → JDBC 가 08006. **진짜 메시지는 test-report XML 에 있었다**(`waitForAssignment: Expected 1 but got 0 partitions`).
- **`ci.yml` 에 `schedule:` 추가** — `dorny/paths-filter` 는 schedule 이벤트에 비교 base 가 없어 필터 의미가 미정의다(MONO-359). **불필요해졌다**: 기준선을 되살리면 main 은 **모든 코드 머지마다** 통합 스위트를 완주한다. 시계는 애초에 취소된 기준선의 **대체재**였지 목적이 아니었다.

---

# Acceptance Criteria

- [ ] **AC-1 (도달성 실측 — 조치 전)** — 최근 20 main 런의 통합 잡 결과를 **측정해 기록**한다. *(§ Goal 의 표. 완료 — 12건 살해.)* **측정식 자체를 먼저 자기검증**할 것: 알려진 정답을 가진 런에 돌려 필터가 매칭하는지 확인한다. 초안의 표는 그것을 안 해서 틀렸다.
- [ ] **AC-2 (가드가 무는가 — mutation)** — 4개 축 전부:
  - ref-keyed 그룹 + `cancel: true` → **RED**
  - `cancel-in-progress: ${{ github.event_name != 'push' }}` (**올바른** 대안) → **OK, 오탐 없음**
  - `${{ github.event_name == 'push' }}` (뒤집힌 형태) → **RED**
  - 검사 대상 0건 → **exit 2** (**초록 금지** — `--require-coverage` 규율)
  - **mutation 이 실제로 적용됐는지 결과를 읽기 전에 확인**한다. 이 세션에서만 조용한 미적용이 세 번 나왔다.
- [ ] **AC-3 (가드의 도달성)** — 가드가 **자기 스크립트 편집**으로도 트리거된다(`ci-baseline` 필터에 스크립트 경로 포함). `code-changed` 와 **AND 하지 않는다** — 워크플로 파일도 bash 가드도 그 필터의 "코드" 가 아니므로, AND 하면 **정확히 그것을 깨뜨리는 편집에서 도달 불가**가 된다(MONO-360 실측).
- [ ] **AC-4 (조치 후 관측 — 머지 후에만 가능)** — 이 PR 머지 **다음의 코드 머지**에서 main 런의 통합 잡 `cancelled` 가 **0** 임을 **실제 런으로 확인**한다. **PR 에서는 증명할 수 없다** — PR 이벤트의 ref 는 `refs/pull/N/merge` 라 push 경로를 타지 않는다. *주장하지 말고 관측할 것.*
- [ ] **AC-5 (형제 조사)** — `push` 트리거를 갖는 워크플로 전수를 조사해 기록한다. *(완료: `ci.yml`, `nightly-e2e.yml` 둘 다 해당. `federation-hardening-e2e.yml` 은 cron-only 로 해당 없음. `_integration.yml`/`_platform-e2e.yml` 은 `workflow_call` 재사용 워크플로.)*

---

# Related Specs

- `.github/workflows/ci.yml` — concurrency, `changes` 잡 filters/outputs, 9개 Integration 잡
- `.github/workflows/nightly-e2e.yml` — concurrency (schedule + push)
- `scripts/check-ci-baseline-reachable.sh` (신규)
- `TASK-MONO-359` / `TASK-MONO-360` — 도달성 원칙
- `TASK-MONO-376` — wms 스위트의 실제 원인 (선행)

# Related Contracts

없다.

---

# Edge Cases

- **CI 분(minute) 이 늘어난다.** 지금은 코드 머지의 main 런이 조기 사살돼 "절약" 되고 있었다 — **테스트를 안 해서 아낀 것이다.** chore/docs 런은 무거운 잡이 전부 path-gated off 라 거의 공짜이므로, 증가분은 **실제 코드 머지 수**로 상한이 잡힌다.
- **main 이 더 자주 빨개질 수 있다** — 런이 완주하니까. 그게 목적이다. 다만 **`MONO-376` 이 먼저 랜딩해야** 첫 완주가 flake RED 가 아니다. § Dependency Markers.
- **`cancel-in-progress: false` 로 고치지 말 것** — 그러면 GitHub 이 그룹당 PENDING 을 1개만 유지해 **세 번째 push 가 큐에 있던 것을 축출**한다. 커밋별 그룹은 그 문제 자체가 없다.

# Failure Scenarios

- **`cancel-in-progress` 를 통째로 끈다** → PR 에서 force-push 할 때마다 낡은 런이 계속 살아남아 러너를 먹는다. **PR 취소는 옳은 동작이다.** 고쳐야 할 것은 **그룹의 키**지 취소 자체가 아니다. Guard: AC-2 의 오탐 축.
- **가드 스크립트를 `workflows` 필터에만 걸어둔다** → 스크립트를 지우거나 약화시키는 편집이 **가드를 트리거하지 못한다.** 자기 눈을 가린 가드. Guard: AC-3.
- **376 을 기다리지 않고 머지한다** → 되살린 기준선의 첫 완주가 flake RED → *"이 변경이 main 을 깨뜨렸다"* 로 읽힘 → 되돌려짐. Guard: § Dependency Markers.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-370` CI 에서 wms Integration 이 RED 였고, **그것이 내 변경 탓인지 판정하려다** 시작됐다. 판정에는 성공했다(인과 경로 0). 문제는 **비교할 `main` 기준선이 없다**는 것이었다.

2026-07-13 재측정에서 **초안의 원인 진단이 틀렸음**이 드러났다. path-gating 이 아니었다 — 잡은 실제로 돈다. **다음 머지가 죽이고 있었다.**

그리고 그 진단 오류의 출처가 요점이다: **깨진 `jq` 필터의 빈 출력을 "잡이 없다" 로 읽었다.** 같은 형태를 이 세션에서 반복해서 만났다 — `grep -c` 가 Javadoc 을 세고(MONO-375), `perl` 치환이 CRLF 에 조용히 실패하고(MONO-376), 이번엔 `jq` 의 `\(` 가 보간으로 먹혔다. **탐지식이 아무것도 못 찾았을 때 그것을 "없음" 으로 읽지 말 것 — 먼저 탐지식이 아는 답을 찾아내는지 확인할 것.**

분석=Opus 4.8 / 구현 권장=Opus (진단이 본체였다).
