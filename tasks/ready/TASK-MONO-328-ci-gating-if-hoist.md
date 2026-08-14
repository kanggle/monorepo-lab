# Task ID

TASK-MONO-328

# Title

⏳ DEFERRED — CI 게이팅 `if:` 중복 hoist (잡별 OR-블록 → `changes.outputs.run-*` named 플래그). **신호 관측 전 착수 금지.**

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- chore

---

# ⏳ DEFERRAL GUARD (read first)

이 task는 **의도적으로 보류된 백로그**다. root 리프사이클에 `backlog/` 폴더가 없어 `ready/`에 두되, **AC-0(아래)를 먼저 검증**해야 한다. 신호가 없으면 **구현하지 말고 그대로 둔다**(no-op = 올바른 구현). `/process-tasks` 등 일괄 구현 픽업 시에도 AC-0에서 STOP.

**착수 트리거 (둘 중 하나 관측 시)**:
1. **프로젝트 추가가 잦아짐** — 신규 프로젝트/서비스 추가 시 `ci.yml`의 여러 잡 `if:`를 손으로 갱신하다 **누락이 실제 발생**(잡이 잘못 skip/run).
2. **게이팅 실수로 회귀** — path-filter/`if:` 오류로 돌아야 할 e2e가 skip되어 **회귀가 main에 샌 사건**이 1건 이상 발생.

둘 다 없으면: 지금의 명시적 per-job `if:`가 "동작하고 투명한" 상태이므로 유지.

---

# 🔍 AC-0 실측 #1 (2026-08-15) — **트리거 미관측 → STOP. 보류 유지.**

재는 김에 **이 티켓의 페이오프 추정치도 재봤고, 그쪽이 더 중요한 결과다.**

## 트리거 ① — 프로젝트 추가로 `if:` 갱신을 누락한 사건: **0건**

`ci.yml` 에서 프로젝트 필터를 참조하는 잡 **21개 전수**의 게이팅 집합을 뽑아 각 잡의
**이름·실제 스텝**과 대조했다. 빠진 프로젝트를 가진 잡은 **없다.**

한 건 의심했다가 **읽어 보고 철회**했다: `frontend-checks` 는 `platform-console` 을 게이팅에
넣지 않는데, 잡 이름이 *"Frontend lint & build (ecommerce + fan-platform)"* 이고 스텝도 그 둘만
돈다 — console 의 lint/typecheck 는 `frontend-unit-tests` 안에 있고 그 잡은 `platform-console` 을
**넣고 있다**. ⇒ 누락이 아니라 정확한 게이팅이다. (술어가 의심스러우면 산출물을 열어라.
이 티켓이 우려하는 것이 바로 "게이팅이 안 보여서 오해하는 것" 이므로 더더욱.)

**가까웠지만 트리거가 아닌 두 사건** — 둘 다 이 티켓의 hoist 로는 예방되지 않는다:

| 사건 | 왜 트리거가 아닌가 |
|---|---|
| `TASK-MONO-407` — 프로젝트 필터 8개가 전부 항상-참 | 방향이 **반대**다(오-skip 이 아니라 **전량 오-run**). 원인도 필터 **정의**의 negation 이고, 이 티켓은 필터 정의를 **Out of Scope** 로 명시한다 |
| nightly-e2e 서비스 추가 드리프트 | 손으로 유지하는 것이 `if:` 가 아니라 잡 **안의 스텝 목록 4곳**(bootJar·업로드·복원 배열·`.env`)이다. named 플래그는 스텝 목록을 건드리지 않는다 |

## 트리거 ② — 게이팅 오류로 e2e 가 skip 되어 회귀가 main 에 샌 사건: **0건**

기록에 남은 main-RED 사건(`TASK-MONO-516` federation nightly RED · nightly 전용 스펙이 초록으로
머지된 뒤 main RED)의 원인은 **nightly 전용 커버리지**(설계상 PR CI 에 없음)이지 `if:`/path-filter
**오류**가 아니다. 잡이 잘못 skip 된 것이 아니라 **애초에 PR 에서 도는 잡이 아니었다.**
⇒ 트리거 문구("돌아야 할 e2e 가 skip 되어")에 해당하지 않는다. 그 축의 개선은 별개 문제다.

## 🔴 더 중요한 결과 — **페이오프 추정치가 틀렸다 (21 → 17, 절약 4)**

이 티켓은 *"OR-패턴 ~15복사 제거"* 라고 적었다. 실측:

```
프로젝트 필터를 참조하는 잡      21
서로 다른 게이팅 집합            17      ← 거의 전부가 유일하다
중복으로 절약되는 블록            4      (x3 wms · x2 ecommerce · x2 scm)
```

named 플래그를 도입해도 **플래그 17개가 잡 21개를 게이팅**한다 — 1:1 에 가깝다.
*"~15복사"* 는 **잡 인스턴스를 센 것**이지 **서로 다른 게이팅 집합을 센 것**이 아니었다.
§ Scope 의 *"대략 8~12개 플래그"* 도 낙관이었다(실측 **17**).

⇒ 이득 축이 줄었으므로 **보류 판단은 오히려 강해졌다.** 손실(가시성 저하)과 리스크
(correctness-critical 오-skip)는 그대로인데 이득만 1/4 이다.

## 다음에 다시 잴 때 쓸 명령 (손으로 세지 말 것)

```bash
python - <<'PY'   # 잡별 needs.changes.outputs.* 집합을 뽑아 서명별로 묶는다
import re, io
from collections import defaultdict
s = io.open('.github/workflows/ci.yml', encoding='utf-8').read()
P = {'wms','ecommerce','iam','fan','scm','finance','erp','platform-console'}
sig = defaultdict(list)
for j in re.split(r'\n  (?=[a-zA-Z0-9_-]+:\n)', s):
    m = re.match(r'([a-zA-Z0-9_-]+):\n', j)
    i = re.search(r'\n    if: (>-\n(?:      .*\n)+|.*\n)', j)
    if not (m and i):
        continue
    o = tuple(sorted(set(re.findall(r'needs\.changes\.outputs\.([a-z0-9-]+)', i.group(1)))))
    if set(o) & P:
        sig[o].append(m.group(1))
print(sum(len(v) for v in sig.values()), len(sig))
PY
```

**재착수 조건은 그대로다** — 위 두 트리거 중 하나가 실제로 관측될 때. 그때 이 숫자(21/17/4)를
**다시 재고** 시작하라. 프로젝트가 늘면 잡도 같이 늘어 비율이 달라질 수 있다.

---

# Goal

`TASK-MONO-326`은 CI 잡 **본문** 중복(composite + reusable)을 제거했으나 **게이팅 로직**은 의도적으로 보존했다. 각 잡의 `if:`가 아래 OR-블록을 ~15회 반복한다:

```yaml
if: >-
  github.event_name == 'push' ||
  needs.changes.outputs.libs == 'true' ||
  needs.changes.outputs.workflows == 'true' ||
  needs.changes.outputs.<project> == 'true' [ || contracts ]
```

GitHub Actions는 `if:`를 공유하는 프리미티브가 없다(reusable로 못 뺌). 유일한 레버는 **`changes` 잡이 "돌려야 하나?" 불린을 미리 계산해 named output으로 노출** → 각 `if:`가 `needs.changes.outputs.run-<x> == 'true'` 한 줄로 축약. 게이팅이 단일 소스로 모이고, 신규 프로젝트 추가 시 한 곳만 수정.

**단, 신호가 오기 전에는 하지 않는다** — 순수 이득이 아니라 트레이드오프이기 때문(아래 § Trade-off).

---

# Scope

## In Scope (트리거 관측 후에만)

- `ci.yml`의 `changes` 잡 `outputs`에 잡-유형별 named 플래그 추가(예: `run-build`, `run-wms-e2e`, `run-wms-integration`, `run-frontend`, …). 잡마다 게이팅이 **미묘하게 다름**(프로젝트 집합·`contracts` 유무·`github.repository` repo-guard 유무) → 서로 다른 게이팅 패턴마다 별도 플래그가 필요(대략 8~12개).
- 각 다운스트림 잡의 `if:`를 해당 named 플래그 1개 읽기로 축약. `github.repository == '...'` repo-guard는 caller `if:`에 유지할지 output에 접을지 결정(가시성 vs DRY).
- `nightly-e2e.yml` / `federation-hardening-e2e.yml`의 반복 `if:`도 동일 적용 여부 판단(대부분 repo-guard만 있어 이득 적음 → 선택).

## Out of Scope

- **`changes`의 필터 정의(`filters:`) 자체 수정 금지** — MONO-074/075의 pure-positive `code-changed` + outputs-AND 설계는 negation quirk를 의도적으로 회피한 것. 플래그는 기존 output들을 **조합만** 한다.
- 게이팅 **의미 변경** — 어떤 PR에서 어떤 잡이 run/skip 되는지는 **byte-동일 보존**. 순수 표현 리팩토링.
- 잡 본문(MONO-326에서 완료).

---

# Trade-off (왜 기본이 "보류"인가)

| | |
|---|---|
| 이득 | 게이팅 SoT 확보, ~~OR-패턴 ~15복사 제거~~ → **실측 절약은 4** (잡 21 · 서로 다른 게이팅 집합 **17**, 2026-08-15), 신규 프로젝트 추가 시 실수↓ |
| 손실 | 게이팅이 잡에서 **덜 보임** — "왜 이 잡이 도나"를 알려면 `changes` 잡을 봐야 함(지금은 잡마다 `if:` 명시 = 더 투명) |
| 리스크 | correctness-critical — 플래그 하나 틀리면 잡 오-skip/run → main 회귀. MONO-326과 동일한 "동작 보존" CI 매트릭스 대조 필요 |
| 결론 | 페이오프 中·리스크 中·통증 신호 無 → **신호 올 때만** |

---

# Acceptance Criteria

- [x] **AC-0 (verify-then-act, 필수 선행)**: § DEFERRAL GUARD의 착수 트리거 2개 중 최소 1개가 실제 관측됨을 확인. **관측 없으면 STOP — 구현하지 않고 task를 그대로 둔다.** (관측 근거를 이 task 또는 PR에 기록.)
      → **2026-08-15 수행: 트리거 2개 모두 미관측 ⇒ STOP.** 근거는 § AC-0 실측 #1.
      아래 AC 들은 **의도적으로 미착수**다(no-op = 올바른 구현). 이 체크는 "구현했다" 가 아니라
      **"게이트를 실제로 재고 그 결과를 기록했다"** 는 뜻이다.
- [ ] `changes.outputs`에 잡-유형별 named 플래그 추가, 기존 output 조합만 사용(필터 정의 무수정).
- [ ] 다운스트림 잡 `if:`가 named 플래그 읽기로 축약, 게이팅 의미 무변경.
- [ ] **동작 보존 대조**: 최소 ① markdown-only PR = 전 skip ② 워크플로 self-change PR = 전 run 두 케이스의 잡 집합이 리팩토링 전과 동일함을 CI 매트릭스로 확인.
- [ ] self-CI GREEN.

---

# Related Specs

- `tasks/done/TASK-MONO-326-ci-workflow-dry-refactor.md` (본문 DRY — 이 task는 게이팅 DRY로 그 짝)
- `tasks/done/TASK-MONO-074-*.md` / `TASK-MONO-075-*.md` (path-filter 의미 + negation quirk — 보존 대상)
- Memory `project_mono_326_ci_workflow_dry`, `project_ci_path_filter_074_075_quirk`

# Related Skills

N/A — CI YAML 편집.

---

# Related Contracts

None.

---

# Target Service

N/A — shared CI configuration only.

---

# Architecture

N/A — 표현 리팩토링(동작 불변), ADR 없음.

---

# Implementation Notes

- 잡별 게이팅 차이를 먼저 표로 정리(프로젝트 집합 / contracts / repo-guard) → 중복 없는 최소 플래그 집합 도출.
- `github.repository == 'kanggle/monorepo-lab'` repo-guard를 output에 접으면 포터블리티(추출 리포 skip)가 `changes` 한 곳에 모이지만 가시성↓ → 팀 취향으로 결정.
- 로컬 검증 한계: python·jq·js-yaml 모듈 부재 → `npx --yes js-yaml`로 파싱만. 실검증=CI 권위(MONO-326과 동일).

---

# Edge Cases

- `push`-to-main fallback 누락 주의 — 각 named 플래그에 `github.event_name == 'push'`를 포함해야 main 보호 유지.
- repo-guard가 있는 잡(integration/e2e)과 없는 잡(build/frontend)을 다른 플래그로 분리.
- `contracts` force-full은 e2e/frontend-smoke 플래그에만 포함(다른 잡엔 없음).

---

# Failure Scenarios

- 플래그 계산 오류로 잡 오-skip → main 회귀. 완화: 동작 보존 대조 AC + self-CI.
- `changes` 필터를 "정리"하려다 negation 재도입 → markdown-only e2e 트리거 회귀. 완화: 필터 정의 무수정(Out of Scope).
- 가시성 저하로 리뷰어가 게이팅을 오해 → 각 플래그명에 의도가 드러나게(`run-<what>`) + 주석.

---

# Test Requirements

- Self-CI(workflows flag → full pipeline) GREEN.
- 동작 보존 2케이스 대조(markdown-only=전skip / self-change=전run).

---

# Definition of Done

- [ ] AC-0 트리거 관측 확인(미관측이면 이 task는 done이 아니라 **보류 유지**).
- [ ] 게이팅 named 플래그 도입 + `if:` 축약, 의미 보존.
- [ ] 동작 보존 대조 기록, self-CI GREEN.
- [ ] `tasks/INDEX.md` done entry(close chore 시).

---

# Provenance

Surfaced 2026-07-04 — TASK-MONO-326(CI 본문 DRY) 완료 후 사용자와 "게이팅(무엇을 언제 돌릴지) 리팩토링 여부" 논의. 결론: 레버는 존재(if → named outputs)하나 **페이오프 中·리스크 中·통증 신호 無 → 지금 구현 안 함, 신호 관측 시 착수**하는 백로그로 등록(사용자 지시). 원래 MONO-327로 픽했으나 동시 세션이 327 선점(ADR-045) → 충돌 discipline대로 328로 renumber.

분석=Opus 4.8 / 구현 권장=Opus (correctness-critical 게이팅 + 동작 보존 검증; 단순 표현 축약이나 오류 시 main 회귀).
