# Task ID

TASK-MONO-075

# Title

Fix TASK-MONO-074 — dorny/paths-filter v3 `predicate-quantifier: 'some'` quirk 로 인한 markdown-skip axis A 회귀

# Status

review

# Owner

monorepo

# Task Tags

- ci
- chore
- fix

---

# Goal

TASK-MONO-074 axis A (markdown skip) 가 실측 회귀 — close chore PR #423 (squash `38aeb98b`) 의 self-CI 가 직접 증거.

PR #423 변경: `tasks/INDEX.md` + `tasks/{review → done}/TASK-MONO-074-*.md` (rename) — markdown-only.
기대: 모든 flag false → e2e + build-and-test 모두 SKIP.
실측 `changes` job output:

| flag | 기대 | 실측 |
|---|---|---|
| `libs` | false | ✅ false |
| `workflows` | false | ✅ false |
| `wms` | false | ❌ **true** |
| `ecommerce` | false | ❌ **true** |
| `gap` | false | ❌ **true** |
| `fan` | false | ❌ **true** |
| `scm` | false | ❌ **true** |
| `contracts` | false | ✅ false |

결과: 16/16 job trigger (의도된 SKIP 0건). **axis A 완전 실패**.

## 진단 (root cause)

`dorny/paths-filter@v3` 의 기본 `predicate-quantifier: 'some'` semantics 에서 **negation-only pattern (`!...`) 도 매칭 카운트에 포함**된다 — 즉:

```yaml
wms:
  - 'projects/wms-platform/**'           # positive
  - '!projects/wms-platform/tasks/**'    # negation
  - '!projects/wms-platform/**/*.md'     # negation
```

- `tasks/INDEX.md`: `!projects/wms-platform/**/*.md` 평가 시 — 이 패턴 자체는 "negation" 표시이지만 path matching 으로는 `projects/wms-platform/**/*.md` 와 비교. `tasks/INDEX.md` 는 매칭 안 함. 그런데도 `predicate-quantifier: 'some'` 의 평가 로직이 "패턴 중 하나에 file 이 관여됨 = some pattern matched" 으로 판단 → file 이 wms flag 의 "in" 후보가 됨.
- 보다 정확한 인용 (dorny/paths-filter [issue #184](https://github.com/dorny/paths-filter/issues/184)): "exclude paths always produce a false positive" — 즉 negation 만으로 구성된 평가 흐름에 `'some'` 이 적용되면 의도와 정반대 결과.

`libs` flag 가 false 였던 이유는 그 negation `!**/*.md` 가 *모든* `.md` 파일에 매칭 → 그 단계에서 file 이 "out" 으로 분류된 것으로 보임 (단, 정확한 evaluation 순서는 source code 확인 필요). 결국 quirk 는 positive 와 negation 의 **scope 비대칭** 때문이며, project flag 처럼 positive 가 `projects/<n>/**` 인데 negation 이 그 sub-scope (`**/*.md`) 인 경우 quirk 가 두드러진다.

## Scope of fix

`.github/workflows/ci.yml` 의 path-filter 패턴만 재설계. 4 e2e job 의 `if:` 조건과 `contracts` flag 는 그대로 (axis B 는 정상). markdown skip axis 만 진짜로 작동하도록 정정.

---

# Scope

## In Scope

`.github/workflows/ci.yml` 의 `changes` job filter 재설계. 다음 3 옵션을 PoC 로 비교 후 가장 깔끔한 하나 채택 (impl 단계에서 결정).

### 옵션 1 — `predicate-quantifier: 'every'` (filter-level 옵션)

dorny/paths-filter v3 doc 인용: `every` quantifier 는 "file is 'in' iff *every* pattern matches it" (positive = path glob 매칭, negation = path glob 비매칭).

```yaml
- uses: dorny/paths-filter@v3
  id: filter
  with:
    predicate-quantifier: 'every'
    filters: |
      wms:
        - 'projects/wms-platform/**'
        - '!projects/wms-platform/tasks/**'
        - '!projects/wms-platform/**/*.md'
```

**검증 필요사항** (impl 단계 PoC):
- `libs` flag 처럼 여러 positive 가 OR 관계여야 하는 filter 에서도 'every' 가 의도대로 작동하는가 (positive 끼리 OR + negation AND 조합인지, 아니면 모든 패턴이 AND 인지).
- 만약 positive OR + negation AND 가 아니면, `libs` flag 가 깨질 위험 → 그 경우 옵션 1 단독 사용 불가.

### 옵션 2 — positive 패턴을 코드 확장자만 (narrow positive)

```yaml
wms:
  - 'projects/wms-platform/**/*.java'
  - 'projects/wms-platform/**/*.kt'
  - 'projects/wms-platform/**/*.ts'
  - 'projects/wms-platform/**/*.tsx'
  - 'projects/wms-platform/**/*.gradle'
  - 'projects/wms-platform/**/*.gradle.kts'
  - 'projects/wms-platform/**/*.yaml'
  - 'projects/wms-platform/**/*.yml'
  - 'projects/wms-platform/**/*.json'
  - 'projects/wms-platform/**/*.sql'
  - 'projects/wms-platform/**/*.properties'
  - 'projects/wms-platform/**/Dockerfile*'
  - 'projects/wms-platform/**/*.sh'
```

→ negation 불필요. positive 가 코드 file 만 매칭. markdown 자동 skip.

**trade-off**:
- 명시적, paths-filter quirk 무관.
- 5 project flag 각각 enumerate → 약 65 줄 추가 (per project ~13).
- 새 file extension (예: `.tf`, `.kt`, `.proto`) 추가 시 모든 flag 에 backfill 필요 — 유지보수 부담.
- `Dockerfile` 등 확장자 없는 file 패턴 안전성 필요.

### 옵션 3 — 별도 `code-changed` positive flag + 모든 job if 에 AND 조건

```yaml
code-changed:
  - '**/*.java'
  - '**/*.kt'
  - '**/*.ts'
  - '**/*.tsx'
  - '**/*.gradle'
  - '**/*.gradle.kts'
  - '**/*.yaml'
  - '**/*.yml'
  - '**/*.json'
  - '**/*.sql'
  - '**/*.properties'
  - '**/Dockerfile*'
  - '**/*.sh'
  - '**/*.ps1'
  - 'tasks/templates/**'    # template 변경은 코드 영향
  - '.github/workflows/**'  # workflows 자체 변경
```

각 job if 조건에 `&& needs.changes.outputs.code-changed == 'true'` 추가.

**trade-off**:
- 단일 flag 한 곳만 유지보수.
- 모든 job if 줄이 길어짐 (`||` 체인 끝에 `&& code-changed` 추가, AND 우선순위 주의 — 괄호 필요).
- contracts force-full 조건과 충돌 안 함 (`|| contracts` 가 AND 보다 약함, 명시적 grouping 필요).

### 추천 시작점 (impl 단계 fallback)

옵션 1 `predicate-quantifier: 'every'` 가 작동하면 가장 깔끔. PoC 실패 시 옵션 2 (확장자 enumerate) 로 전환. 옵션 3 은 옵션 2 와 거의 동가이나 if 조건 복잡도가 더 높아 마지막 fallback.

## Out of Scope

- e2e job 의 `if:` 조건 변경 (axis B contracts force-full 은 정상 작동).
- Phase 2 (`@Tag` 표준화) / Phase 3 (nightly 이전) — 기존 별도 task 후보.
- `predicate-quantifier` 옵션 도입에 따른 paths-filter v3 → v4 upgrade 검토 — 본 task scope 외, 별도 ADR/task 후보.

---

# Acceptance Criteria

- [ ] `.github/workflows/ci.yml` path-filter 재설계 — 위 옵션 중 PoC 검증 1개 채택.
- [ ] Self-CI: 본 fix PR 은 `.github/workflows/ci.yml` 변경 → `workflows` flag = true → full pipeline 회귀 가드 통과.
- [ ] **deferred 자연 실측 1 (axis A 회복 검증)**: fix 머지 후 다음 자연발생 markdown-only PR (예: 다음 `chore(tasks): close ...`) 의 `changes` job output 에서 **5 project flag 모두 false** 확인 + 모든 e2e + build-and-test SKIP 확인.
- [ ] **deferred 자연 실측 2 (axis B 회귀 없음)**: fix 머지 후 contracts 변경 PR 에서 5 project + frontend smoke e2e 강제 trigger 가 유지됨을 확인.
- [ ] 헤더 주석 블록 (`changes` job 위) 의 Filter rules / Edge case 목록을 새 패턴에 맞게 갱신, TASK-MONO-075 reference 표기.

---

# Related Specs

- `tasks/done/TASK-MONO-074-ci-e2e-skip-and-force-full-flags.md` (original, 본 fix 의 referencing task)
- `tasks/done/TASK-MONO-058-path-filter-tasks-exclusion.md` (앞선 negation 사용 패턴)
- `tasks/done/TASK-MONO-045-*.md` (baseline path-filter)
- 외부: https://github.com/dorny/paths-filter — 특히 README v3 의 `predicate-quantifier` 섹션 + issue [#184](https://github.com/dorny/paths-filter/issues/184) (exclude paths false positive)

# Related Skills

N/A — CI infrastructure edit.

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only.

---

# Architecture

N/A — single-file YAML edit. ADR 동반 안 함 (paths-filter mechanic 이해 + 옵션 채택만, 의사결정 부담 낮음).

---

# Implementation Notes

## 실측 증거 (PR #423 의 `changes` job log)

```
Detected 3 changed files
##[group]Filter libs = false
##[group]Filter workflows = false
##[group]Filter wms = true
##[group]Filter ecommerce = true
##[group]Filter gap = true
##[group]Filter fan = true
##[group]Filter scm = true
##[group]Filter contracts = false
```

3 changed files = `tasks/INDEX.md` + (rename: `tasks/review/X.md` deleted + `tasks/done/X.md` added).

## TASK-MONO-058 가 왜 회귀를 안 잡았는지

TASK-MONO-058 의 추가는 `!projects/<n>/tasks/**` 뿐이었다. 그 시점의 실측 검증 = `projects/<n>/tasks/` *안의* file 변경 PR. 그 file 들은 positive `projects/<n>/**` 와 negation `!projects/<n>/tasks/**` 둘 다 매칭 → 'some' 의 quirk 가 false positive 로 작용 → flag = true. 하지만 그 결과는 의도와 충돌하지 않음 (project tasks/-only PR 은 project flag = true 가 의도 — TASK-MONO-058 의 본래 목적은 그 flag 가 false 가 되는 것이었으나, 실제로는 quirk 때문에 true 로 남았고 그게 의도와 우연히 일치). 즉 **TASK-MONO-058 도 사실은 작동 안 한 가능성**.

⇒ **본 fix task 가 TASK-MONO-058 의 의도까지 함께 검증 + 정정**. 옵션 채택 시 `!tasks/**` exclusion 도 함께 진짜로 작동하도록 보장.

## PoC 절차 (impl 단계)

1. 옵션 1 `predicate-quantifier: 'every'` 채택 시도.
2. branch 분기 후 `.github/workflows/ci.yml` 1 줄 추가 (filter-level 옵션).
3. 로컬에서 paths-filter 의 evaluation 을 검증할 수 없으므로 (action 은 GitHub-only) — PR 의 self-CI 가 직접 검증 cycle.
4. 동시에 sanity check 더미 commit (예: `libs/java-common/foo.txt` 추가) 으로 `libs` flag 가 여전히 true 가 되는지 cross-check — 옵션 1 의 'every' 가 multi-positive filter 를 깨지 않는지 확인.
5. 옵션 1 깨지면 옵션 2 로 전환 (각 flag 의 positive 를 코드 확장자 enumerate).

## TASK-MONO-074 의 axis B (contracts force-full) 은 정상

PR #423 의 `Filter contracts = false` 는 의도대로 (markdown 만 변경, contracts/** 매칭 file 없음). axis B 의 deferred 자연 실측은 별도 (TASK-MONO-074 의 Test Requirements 항목 그대로 유효).

---

# Edge Cases

- **옵션 1 'every' 가 libs flag 의 multi-positive 와 충돌**: positive 끼리 OR 평가 아니면 `libs` 가 영원히 false → impl 단계 PoC 가 catch. fallback 옵션 2.
- **옵션 2 확장자 누락**: 새 file extension (예: `.tf`, `.tfvars`, `.proto`, `.avsc`) 추가 시 path-filter 가 false-negative — fix 시 enumeration 보강. impl 단계에서 현재 repo 의 모든 file extension 을 grep 으로 정찰 후 enumerate (`find . -name '*.*' | rg -o '\.[a-zA-Z0-9]+$' | sort -u`).
- **옵션 3 if 조건 AND grouping**: `(libs || wms || ... || contracts) && code-changed` 같은 grouping 누락 시 contracts force-full 이 깨질 위험. impl 단계 cross-check.
- **mixed code + markdown PR**: 어느 옵션을 채택해도 mixed PR 은 code path 가 별도 매칭 → 정상 trigger.
- **task close chore PR (이게 본 회귀 case)**: fix 후 close chore PR 은 모든 e2e SKIP 되어야 정상.

---

# Failure Scenarios

- **옵션 1 채택 후 `libs` flag 가 영원히 false** (multi-positive AND 효과): cycle 1 self-CI 가 catch — 본 PR 자체가 ci.yml 변경 이므로 `workflows` flag 가 true 이어야 하지만 quantifier='every' 가 모든 positive 다 매칭 강요하면 false 가 될 수 있음. revert 후 옵션 2.
- **옵션 2 enumerate 누락**: 다음 자연발생 PR (예: `.proto` 변경) 에서 false-negative — 별도 fix task 또는 hotfix PR.
- **paths-filter v3 의 'every' 가 single filter scope 가 아니라 step-level scope**: 그러면 한 filter 에 적용한 것이 다른 filter 에도 영향. impl 단계에서 paths-filter README 정확히 확인 후 결정.

---

# Test Requirements

- **Self-CI cycle 1** (본 PR 의 fix 가 `.github/workflows/ci.yml` 변경): `workflows` flag = true → full pipeline. fix 가 회귀 안 일으켜야 (모든 16 job green).
- **deferred 자연 실측 1 (axis A 회복)**: 본 fix 머지 후 다음 자연발생 markdown-only PR 의 `changes` job log 확인 — 5 project flag 모두 false + 모든 e2e/integration/boot-jars SKIP.
- **deferred 자연 실측 2 (axis B 보존)**: 본 fix 머지 후 contracts 변경 PR 에서 5 project + frontend smoke e2e 강제 trigger 확인.

---

# Definition of Done

- [ ] `.github/workflows/ci.yml` path-filter 재설계 (옵션 1/2/3 중 1개 채택, PoC 검증 완료).
- [ ] self-CI green (full pipeline 회귀 가드).
- [ ] 헤더 주석 블록 갱신 (TASK-MONO-075 reference + 채택 옵션 mechanics 명시).
- [ ] `tasks/INDEX.md` `## done` 에 1-line outcome (close chore 단계).
- [ ] task 파일 lifecycle: `ready → review → done`.
- [ ] axis A 회복 실측 1 case 기록 (close chore 단계 outcome line 또는 후속 PR comment).

---

# Provenance

Surfaced 2026-05-13 — TASK-MONO-074 의 close chore PR #423 (squash `38aeb98b`) self-CI 가 axis A 회귀를 실측으로 노출. 본 fix task 는 CLAUDE.md § Review Rules ("If review reveals a bug, create a new fix task in `ready/` referencing the original task ID") 정공법.

dorny/paths-filter v3 의 `predicate-quantifier: 'some'` quirk 가 root cause — issue [#184](https://github.com/dorny/paths-filter/issues/184) 에서 동일 증상 보고됨 ("exclude paths always produce a false positive"). 본 task 는 동시에 TASK-MONO-058 (`!tasks/**` exclusion) 의 실효성도 검증/정정한다.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical yaml edit + 잘 정의된 PoC 절차; paths-filter mechanic 이해는 본 spec 에서 이미 정리).
