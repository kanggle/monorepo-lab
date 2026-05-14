# Task ID

TASK-BE-283

# Title

GAP specs dead-reference mechanical batch — 46 `libs/*` depth fix + 1 V0008 SQL depth fix (pre-import era artifact, refactor-spec Tier 3 closure)

# Status

review

# Owner

global-account-platform

# Task Tags

- gap
- spec
- mechanical-batch
- dead-reference
- cleanup
- pre-import-artifact

---

# Goal

`/refactor-spec all --dry-run` (2026-05-14, BE-165 직속 후속) 의 **Tier 3 #1 closure** — GAP scope dead-ref 48 건 중 mechanical 47 건 fix. 모두 GAP 2026-04-30 monorepo import 이전의 standalone repo (`libs/` at repo root) shape 잔재 — monorepo (`libs/` at root) 로 import 시 spec 만 안 따라온 author artifact. TASK-MONO-085 scope (rules+platform) 가 `libs/*` 미커버라 그동안 누락.

## Mechanical fix (47, 동일 카테고리)

- **`../../../libs/java-*` → `../../../../../libs/java-*`** (46 hits): GAP spec 의 `services/<svc>/X.md` (4-level deep) + `contracts/events/X.md` (4-level deep) 가 repo-root 의 `libs/` 가리키려면 5 `../` 필요. 현 3 `../` = `projects/global-account-platform/libs/` (존재 안 함) 가리킴.
- **auth `data-model.md` L191 V0008 SQL** (1 hit): `[Flyway V0008](../../../../apps/auth-service/src/main/resources/db/migration/V0008__create_oauth_tables.sql)` — 4 `../` from 4-level deep file = `projects/apps/...` (broken). GAP project root 의 apps/ 가리키려면 3 `../`. **반대 방향 (1 less up)**.

| Category | Pattern | Count |
|---|---|---|
| libs depth +2 ups | `../../../libs/java-{common,messaging,observability,security,test-support,web}` → `../../../../../libs/...` | 46 |
| V0008 SQL depth -1 up | `../../../../apps/auth-service/.../V0008__create_oauth_tables.sql` → `../../../apps/...` | 1 |

## Affected files (13 spec files)

- `contracts/events/auth-events.md`, `community-events.md`, `membership-events.md` (3 files)
- `services/account-service/{architecture,data-model,dependencies}.md` (3 files)
- `services/admin-service/{data-model,dependencies,retention}.md` (3 files)
- `services/auth-service/{architecture,data-model,dependencies}.md` (3 files)
- `services/community-service/{architecture,data-model}.md` (2 files)
- `services/gateway-service/{architecture,dependencies}.md` (2 files)
- `services/membership-service/{architecture,data-model}.md` (2 files)
- `services/security-service/dependencies.md` (1 file)

(일부 중복 — 동일 file 에 여러 broken ref. 실제 file count = 13.)

# Scope

## In Scope

- 위 47 dead-reference mechanical fix (sed-batch by pattern):
  - Pattern A (46 hits): `../../../libs/java-` → `../../../../../libs/java-`
  - Pattern B (1 hit): auth-service `data-model.md` L191 `../../../../apps/auth-service` → `../../../apps/auth-service` (V0008 SQL line)

## Out of Scope

### Tier 2 finding (별 task 후보 — judgment required)

- **`admin-service/data-model.md` L170 PiiMaskingUtils.java 참조** — `../../../apps/security-service/src/main/java/com/example/security/domain/util/PiiMaskingUtils.java` 의 파일이 production code 에 존재 안 함 (security-service production = `PiiMaskingService.java` 다른 package/class 명, `domain/pii/PiiMaskingRecord.java` + `application/pii/PiiMaskingService.java`). 본 spec line 은 "참조 구현" reference 라 author 가 의도적으로 sample path 박았는데 production 이 rename / restructure 됨. **본 batch 에서 skip** — 별 task (BE-284 후보): rename ref to `application/pii/PiiMaskingService.java` 또는 reference 자체 drop 결정 (sample link 만이라 drop 도 가능).

### 더 멀리 Out of Scope

- SCM 1 dead-ref (`scm-procurement-events.md:44` ADR-MONO-004 path) — refactor-spec Tier 3 #2, 별 task (`projects/scm-platform/tasks/ready/` 후보).
- tasks/done archive dead-refs (lifecycle layer, spec refactor 범위 외).

# Acceptance Criteria

- [ ] 47 dead-references PASS (`bash /tmp/check_gap_links.sh` exit 1 remaining = PiiMaskingUtils Tier 2 만).
- [ ] 13 modified file 각각의 nearby ref 회귀 0건.
- [ ] Production code / spec contract / event payload / API schema 0 변경 (markdown link string only).
- [ ] PiiMaskingUtils Tier 2 finding 명시적 skip + Out of Scope 섹션 기록.

# Related Specs

- 13 GAP service/contract spec files (위 Affected files 카탈로그)
- TASK-MONO-085/086 precedent (sibling mechanical batch closure pattern, 140 broken refs in single PR)
- TASK-BE-165 precedent (sibling, 5 broken refs single-PR closure 직전 세션)

# Related Contracts

해당 없음 (link path 수정만, contract 변경 0).

# Target Service

해당 없음 — markdown link path 수정만 (13 GAP spec file).

# Edge Cases

- A: `libs/java-` substring 이 spec body 의 다른 context (예: 코드 snippet 안의 path string) 에서도 나타날 가능성 → sed 가 over-match. mitigate: pattern 을 `\]\(\.\./\.\./\.\./libs/java-` 로 좁히기 (markdown link `](...)` syntax 안의 것만).
- B: V0008 SQL line 의 4-up 패턴은 1 hit 뿐이라 sed 가 1 hit 보장. 별 Edit.
- C: 다른 4-up path (`../../../../...`) 가 의도적이지 검증 — auth `data-model.md` 외에 동일 broken 패턴 없음 확인.

# Failure Scenarios

- A: sed over-match → 같은 file 의 nearby link 회귀. mitigate: per-pattern verbose sed + post-sed dead-ref checker rerun.
- B: 47 hits 중 일부가 markdown link 아닌 raw text → sed pattern 좁히기로 해결.

# Validation Plan

1. sed batch 후 `bash /tmp/check_gap_links.sh` 재실행 — exit 1 (1 broken = PiiMaskingUtils Tier 2 skip 기대치) 외에는 빈 출력.
2. `git diff --stat` 으로 ~13 file / ~47 line edit 확인.
3. random 3 modified files 의 nearby ref spot-check (회귀 0).

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) sed batch + V0008 Edit + lifecycle move ready/ → review/.
- branch name `task/be-283-gap-spec-dead-ref-libs-depth-batch` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수 (no `master` substring).
- TASK-MONO-085/086 + TASK-BE-165 precedent 답습. 단 scope 4-10x larger (47 fix vs 5).

# Outcome

(완료 후 갱신)
