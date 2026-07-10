# Task ID

TASK-MONO-343

# Title

`ci.yml` push-to-main 폴백이 `code-changed` 판정을 무시 → doc-only 커밋이 전 프로젝트 Testcontainers 스위트를 돌려 main 을 상시 flake-RED 로 만듦. `github.event_name == 'push'` 무조건 OR → `push && code-changed != 'false'` 로 축소 (필터-실패 폴백은 보존)

# Status

done

# Owner

monorepo

# Task Tags

- ci
- chore

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능.
- **관련 (순서 권장, 비차단)**: `TASK-MONO-328`(게이팅 `if:` hoist). 328 이 먼저 머지되면 본 task 의 수정 지점이 **~15곳 → 1곳**(named 플래그 계산부)으로 줄어든다. 328 은 ⏳DEFERRED(AC-0 트리거 미관측)이므로 **본 task 가 328 을 기다릴 필요는 없다.** 328 을 먼저 하지 않고 진행하면 `if:` 블록 ~15개를 각각 고쳐야 한다.
- **328 과의 관계(중요, 혼동 금지)**: 328 은 **표현 리팩토링**이며 "게이팅 의미 무변경"이 AC 이고 Edge Cases 에서 *"각 named 플래그에 `github.event_name == 'push'` 를 포함해야 main 보호 유지"* 라고 현 동작 **보존**을 명시한다. 즉 328 은 본 task 가 고치려는 결함을 **그대로 옮겨 담는다**. 본 task 는 **의미 변경**이므로 328 에 흡수될 수 없다. 두 task 를 합치려는 시도는 328 의 "동작 보존" AC 를 깨뜨린다.

---

# Goal

`ci.yml` 의 무거운 잡(build-and-test / 각 프로젝트 integration / e2e / frontend)은 모두 아래 형태로 게이팅된다:

```yaml
if: >-
  github.event_name == 'push' ||
  needs.changes.outputs.<flag> == 'true' || ...
```

`github.event_name == 'push'` 가 **OR 의 첫 항**이라, main 푸시에서는 `changes` 잡의 판정이 **무조건 덮어써진다**. 그 결과 `code-changed` 게이트(MONO-074/075 가 negation quirk 를 우회하려 도입한 pure-positive 플래그)는 **사실상 PR 전용**이 되고, **문서 한 줄만 바꾼 커밋도 main 에서 전 프로젝트 Testcontainers 스위트를 실행**한다.

실행할 코드가 **0 줄**이므로 이 실행은 **검출할 회귀가 원리적으로 존재하지 않는다.** 얻는 신호는 없고 비용만 남는다:

1. **main 이 상시 flake-RED 로 보인다** — main GREEN 이 신뢰 신호로 기능하지 못하고, "머지 전 main 이 GREEN 인가" 규율(CLAUDE.md 3-dim 검증)의 판단 근거가 오염된다.
2. **러너 시간 낭비** — doc-only 커밋마다 8+ 개 Testcontainers 레인(수십 분).
3. **flake 소진(desensitization)** — 무해한 RED 가 반복되면 진짜 RED 를 놓친다.

**목표**: push 폴백을 "무조건"에서 **"필터가 코드 변경을 부정하지 않았을 때"** 로 좁힌다. 세 요구를 동시에 만족해야 한다.

| # | 요구 | 보존 방법 |
|---|---|---|
| R1 | 코드 있는 main 푸시는 **전량 실행** (PR 이 못 잡는 semantic conflict 검출 — PR base 가 낡아 다른 프로젝트와의 상호작용 회귀가 머지 후에만 드러나는 경우) | `code-changed == 'true'` → 실행 |
| R2 | doc-only main 푸시는 **skip** | `code-changed == 'false'` → skip |
| R3 | `paths-filter` 스텝 자체가 실패/스킵되면 **전량 실행**(main 보호 폴백 — 현행 주석이 명시한 의도) | 실패 시 output = `''`(빈 문자열) → `!= 'false'` 가 **참** → 실행 |

`!= 'false'` 비교가 R2 와 R3 을 동시에 만족시키는 유일한 형태다. `== 'true'` 로 쓰면 필터 실패 시 `'' == 'true'` 가 거짓이 되어 **R3(main 보호 폴백)이 소멸**한다 — 이 task 의 가장 중요한 함정.

---

# Scope

## In Scope

1. `.github/workflows/ci.yml` — 무거운 잡의 `if:` 에서

   ```yaml
   github.event_name == 'push' ||
   ```

   를

   ```yaml
   (github.event_name == 'push' && needs.changes.outputs.code-changed != 'false') ||
   ```

   로 교체. 대상은 `needs: changes` 를 갖고 push 폴백을 쓰는 모든 잡 — **실측 22곳**(2026-07-10, `ci.yml` 기준 line 309·476·517·581·625·671·739·840·892·921·962·1002·1070·1097·1123·1152·1186·1241·1300·1336·1375·1434). 착수 시 `grep -n "github.event_name == 'push'" .github/workflows/ci.yml` 로 재열거(주석 라인 2건은 제외).

2. **`changes.outputs.code-changed` 는 이미 raw 로 노출되어 있다** (2026-07-10 확인 — `outputs` 키: `libs, workflows, wms, ecommerce, iam, fan, scm, finance, erp, platform-console, contracts, observability, demo-wrapper, **code-changed**`). 따라서 **outputs 수정은 불필요**하며, 소비만 하면 된다. `changes` 잡은 무수정.

3. `ci.yml` 헤더 주석 갱신 — "push-to-main 폴백" 설명(현행 73~78행 부근)이 **무조건 실행**을 전제로 쓰여 있다. `code-changed` 를 존중하되 필터 실패 시에만 전량 실행함을 명시하고, `!= 'false'` 를 쓴 이유(R3)를 함정으로 박아둔다.

## Out of Scope

- **`changes` 잡의 `filters:` 정의 수정 금지** — MONO-074/075 의 pure-positive `code-changed` + outputs-AND 설계는 `paths-filter@v3` negation quirk 를 의도적으로 회피한 것. 본 task 는 기존 output 을 **소비만** 한다.
- **`if:` 표현 hoist** — `TASK-MONO-328` 의 범위. 본 task 는 각 `if:` 의 **첫 항만** 고친다.
- `nightly-e2e.yml` / `federation-hardening-e2e.yml` — 이들은 스케줄/수동 트리거이며 push 폴백 패턴이 아니다(대부분 repo-guard). 범위 밖.
- **PR 게이팅 의미 변경** — PR 경로는 `github.event_name == 'push'` 가 거짓이므로 **byte-불변**.

---

# Acceptance Criteria

- [x] AC-1 — `ci.yml` 에 `github.event_name == 'push' ||` (무조건 형태) 가 **0건** 남는다. 22곳 모두 `code-changed != 'false'` 와 AND 결합. (`grep -cE "^\s+github\.event_name == 'push' \|\|$"` = 0, 새 형태 22건)
- [x] AC-2 — `changes` 잡 **무수정**. `git diff -U0` 로 변경 라인이 **주석 + 게이팅 표현식 22줄뿐**이며 `filters:`/`outputs:` 는 무손상임을 확인.
- [x] AC-3 (R1 보존) — self-CI(PR #2390, `.github/workflows/**` → `workflows` 플래그) **23 SUCCESS / 0 FAILURE / 2 skipped**(경로 게이팅 잡) = 전량 실행 실증. GREEN.
- [ ] AC-4 (R2 달성) — **doc-only main 푸시**에서 무거운 잡 skip. **이 close chore 의 main run 에서 실증 예정** — 머지 후 Testcontainers 레인이 `skipped` 임을 확인하고 run URL 을 아래 DONE 노트/INDEX 에 기록.
- [x] AC-5 (R3 보존) — 논증: `paths-filter` 실패 → 모든 output `''` → `'' != 'false'` 참 → 전량 실행. `== 'true'` 였다면 소멸. PR #2390 본문 + `ci.yml` 헤더 주석에 함정으로 명시.
- [x] AC-6 — PR 경로 무변경(`github.event_name == 'push'` 가 PR 에서 거짓 → 추가된 AND 항 unreachable). 파싱 검증: 24 잡 중 22 AND-게이팅, `== 'true'` 오용 0.

---

# Related Specs

- `tasks/done/TASK-MONO-074-*.md` / `TASK-MONO-075-*.md` — `paths-filter@v3` negation quirk + pure-positive `code-changed` 도입 근거(**보존 대상**).
- `tasks/done/TASK-MONO-326-ci-workflow-dry-refactor.md` — CI 본문 DRY(게이팅은 의도적 보존).
- `tasks/ready/TASK-MONO-328-ci-gating-if-hoist.md` — 게이팅 **표현** DRY(의미 보존). 본 task 는 **의미** 수정. 위 Dependency Markers 참고.
- Memory `project_ci_path_filter_074_075_quirk`, `project_mono_326_ci_workflow_dry`.

# Related Contracts

None (CI 설정).

---

# Evidence (착수 전 검증 완료 — 2026-07-10)

관측 근거는 **추정이 아니라 실제 main run 로그**다.

| # | 확인 항목 | 결과 |
|---|---|---|
| 1 | push 에서 `paths-filter` 가 동작하는가? | **동작한다.** doc-only 커밋 `85c8e732d` 의 `changes` 잡 로그: `Changes will be detected between 89219cb87... and main` → `Detected 2 changed files` → **`Filter code-changed = false`** |
| 2 | 그런데 왜 무거운 잡이 돌았나? | downstream `if:` 첫 항이 `github.event_name == 'push'` → 필터 판정을 덮어씀 |
| 3 | 실제 피해 | main run `29086967134`(`85c8e732d`, **doc-only**: `tasks/INDEX.md` + task md 2파일, java/wms 파일 0) → `Integration (inventory + inbound-service)` + `Integration (master-service + notification-service + outbound-service)` **FAILURE** |
| 4 | 재현성 | main tip run `29090581544`(`773ef78f9`, 역시 doc-only close chore) → `inventory + inbound` + `ecommerce` **FAILURE**. `rerun --failed` → success ⇒ 100% flake |
| 5 | 실패 지문(코드 무관 증명) | WMS = `PutawayLifecycleIntegrationTest` Kafka `TimeoutException`(:205). ecommerce = `PSQLException: FATAL: terminating connection due to unexpected postmaster exit` + `Connection refused` (postgres 컨테이너 사망 = 러너 리소스 고갈) |
| 6 | 세 번째 사례 | `89219cb87` 도 doc-only(`tasks/INDEX.md` + TASK-MONO-341 이동 2파일) 인데 main CI FAILURE |
| 7 | PR 경로는 정상인가? | 정상. 동일 내용의 PR #2374 는 **1 pass / 23 skipping / 0 failing**(doc-only fast lane). PR 과 push 의 비대칭이 문제의 전부 |

**요지**: 2026-07-10 하루에만 doc-only main 커밋 **3건 중 3건**이 코드와 무관한 이유로 main CI 를 RED 로 만들었다. `main` 의 GREEN 여부는 이 저장소의 머지 규율(3-dim 검증 (c)항)이 딛고 선 신호인데, 그 신호가 코드 변경과 무관하게 흔들리고 있다.

---

# Edge Cases

- **`!= 'false'` vs `== 'true'` (최상위 함정)** — 반드시 `!= 'false'`. 필터 실패 시 output 은 `''` 이며 `'' == 'true'` 는 거짓 → 전량 실행 폴백(R3)이 사라져 **main 보호가 약화**된다. `!= 'false'` 는 `''` 에서 참이므로 폴백이 보존된다. AC-5.
- **`workflows` 플래그와의 상호작용** — `.github/workflows/**` 변경은 `workflows` 플래그(pure-positive, `code-changed` 와 AND 안 됨)로 전량 실행된다. 본 task 의 self-CI 가 여기 해당하며, push 폴백 축소와 **독립적으로** 동작해야 한다(OR 의 다른 항이므로 영향 없음). 확인만 하고 건드리지 말 것.
- **`contracts` force-full** — `specs/contracts/**` 는 markdown 이지만 `code-changed` 는 **false** 다. 현재 `contracts` 플래그는 pure-positive 이고 `code-changed` 와 AND 되지 **않으므로** contracts-only 변경은 여전히 e2e 를 force-full 한다(의도된 동작, MONO-074). push 폴백을 좁혀도 `contracts == 'true'` OR 항이 살아 있어 보존된다. **`contracts` 를 `code-changed` 와 AND 하지 말 것** — cross-service consumer breakage 게이트가 죽는다.
- **`build-and-test` 잡** — 현행 주석상 "root tasks/** 또는 markdown-only 변경 시 push-to-main 폴백으로만 활성화"된다. 본 task 이후 doc-only push 에서 이 잡도 skip 된다. **이것이 의도**인가? → 그렇다. 컴파일할 코드 변경이 0 이므로 검출할 것이 없다. 단, 이 잡은 boot jar artifact 를 nightly 가 소비할 수 있으므로(MONO-014/045) **artifact 소비 경로가 doc-only push 의 build 산출물에 의존하지 않는지** 착수 시 확인하고, 의존한다면 `build-and-test` 만 예외로 두고 사유를 주석에 남긴다.
- **squash merge 전제** — main push 는 통상 커밋 1개이므로 `before..after` diff = 그 커밋. merge-commit 전략으로 바뀌면 diff 범위가 넓어져 `code-changed` 가 true 가 되기 쉬워진다(= 안전 방향). 문제 없음.
- **첫 푸시 / force-push** — `github.event.before` 가 `000...0` 이면 `paths-filter` 는 전체를 changed 로 본다 → `code-changed=true` → 전량 실행(안전 방향).

---

# Failure Scenarios

| # | 시나리오 | 기대 동작 / 완화 |
|---|---|---|
| 1 | `== 'true'` 로 잘못 구현 | 필터 실패 시 main 이 **전 잡 skip** → 회귀가 무검증 통과. AC-5 논증 + 리뷰에서 차단. **가장 위험한 오구현** |
| 2 | `contracts` 를 `code-changed` 와 AND | contracts-only 변경(markdown)이 `code-changed=false` 라 e2e force-full 이 죽음 → cross-service consumer breakage 미검출. Out of Scope 로 명시 |
| 3 | 코드 있는 main push 가 skip 됨 | R1 위반 = semantic conflict 미검출. AC-3(self-CI 전량 실행) 이 가드 |
| 4 | `changes` 잡이 skip 되는 경우(예: `if:` 추가) | `needs.changes.outputs.*` 가 전부 `''` → `!= 'false'` 참 → 전량 실행(안전 방향). 문제 없음 |
| 5 | doc-only push 가 여전히 전량 실행 | AC-4 미달. close chore 의 main run 에서 Testcontainers 레인 skipped 확인으로 검출 |
| 6 | 이 변경을 MONO-328 에 합치려 함 | 328 의 "게이팅 의미 무변경" AC 위반. 별 task 유지 |

---

# Test Requirements

- **self-CI GREEN** — 본 PR 은 `.github/workflows/**` 변경이므로 `workflows` 플래그로 전 파이프라인이 돈다(= R1 케이스 실증).
- **doc-only skip 실증** — close chore(task md + INDEX 만) 머지 후 main run 에서 Testcontainers 레인이 `skipped` 임을 확인, run URL 을 close chore PR 에 기록. (AC-4)
- 로컬 YAML 파싱: `npx --yes js-yaml .github/workflows/ci.yml > /dev/null` (python·jq 부재 — MONO-328 Implementation Notes 와 동일 한계). 실검증은 CI 권위.

---

# Definition of Done

- [x] `ci.yml` push 폴백 22곳 전부 축소, `code-changed` raw output 소비(노출은 기존), 헤더 주석 갱신.
- [x] self-CI GREEN(R1 실증) — PR #2390, 23 SUCCESS.
- [ ] close chore 의 main run 에서 doc-only skip 실증 + run URL 기록(R2 실증) — 이 close chore 머지 후.
- [x] `tasks/INDEX.md` done entry.

---

# Provenance

Surfaced 2026-07-10 — `TASK-BE-495` 종결 직후 사용자의 *"메인은 깨끗한지 확인"* 요청으로 main CI 를 점검하다 발견. main 이 RED 였고, 실패 커밋 3건이 **전부 doc-only** 임이 드러나면서 "PR 은 skip 하는데 push 는 전량 실행" 비대칭을 역추적했다. `changes` 잡 로그의 `Filter code-changed = false` 가 결정적 증거 — 필터는 옳았고, 그 판정을 `if:` 첫 항이 덮고 있었다.

MONO-328(게이팅 hoist, ⏳DEFERRED)과 인접하나 **별개**: 328 은 표현 DRY(의미 보존), 본 task 는 의미 수정. 328 의 AC-0 트리거 중 ②("게이팅 실수로 회귀")의 **거울상** — 회귀가 새어나간 것이 아니라, 돌 필요 없는 잡이 돌아 main GREEN 신호가 오염되는 방향.

분석=Opus 4.8 / 구현 권장=Opus (correctness-critical 게이팅 — 오구현 시 main 무검증 통과. `!= 'false'` 함정이 핵심).
