# Task ID

TASK-MONO-085

# Title

`refactor-spec all --dry-run` 2026-05-14 axis-wide dead-reference batch fix — 138 path-level broken md link + 1 BOM (UTF-8 prefix) 일괄 정정

# Status

ready

# Owner

monorepo

# Task Tags

- platform
- spec
- refactor
- governance
- batch
- dead-reference
- cross-project

---

# Goal

`/refactor-spec all --dry-run` (2026-05-14, TASK-MONO-084 직후) audit 결과 발견된 **140 broken md link + 1 BOM** 중 **mechanical path-level 138 + BOM 1 = 139건 일괄 정정**. file 자체 부재 3건은 refactor scope 밖 — 별 finding 으로 기록 (out-of-scope).

**핵심 finding**: GAP service / contract / feature spec 들이 systemic 하게 `../../../rules/...` / `../../../platform/...` 같은 잘못된 `..` level 사용 — file 의 directory depth 와 안 맞음. 동일 패턴이 WMS notification-service runbook (어제 BE-145 머지 시 도입), fan-platform v1-e2e-scenarios, ecommerce gateway public-routes 에도 잔존. SCM procurement-service architecture.md 는 UTF-8 BOM prefix (sibling 들 모두 BOM 없음).

provenance: `/refactor-spec all --dry-run` 2026-05-14 (Tier 2 추상적 finding 의 정체 = dead-reference 140건 — 옵션 C 봉합 sequence 의 B 단계 결과).

---

# Scope

## In Scope (139 fix)

### A. GAP path-level batch (131 broken)

GAP 의 service / contract / feature spec 들이 잘못된 `..` level 사용. file depth 별 정정:

| file group | depth | broken pattern | correct pattern |
|---|---|---|---|
| `projects/global-account-platform/specs/services/<svc>/<file>.md` | 5 | `../../../rules/...` (3) / `../../../platform/...` (3) | `../../../../../rules/...` (5) / `../../../../../platform/...` (5) |
| `projects/global-account-platform/specs/features/<file>.md` | 4 | `../../rules/...` (2) | `../../../../rules/...` (4) |
| `projects/global-account-platform/specs/contracts/http/<file>.md` | 4 | `../../../rules/...` (3) | `../../../../rules/...` (4) |
| `projects/global-account-platform/specs/contracts/http/internal/<file>.md` | 5 | `../../../rules/...` (3) | `../../../../../rules/...` (5) |
| `projects/global-account-platform/specs/contracts/events/<file>.md` | 4 | `../../../rules/...` (3) | `../../../../rules/...` (4) |

영향 file 군 (broken count 빈도 ≥ 4 file):

- `services/security-service/architecture.md` (12 broken)
- `services/account-service/retention.md` (10)
- `services/admin-service/architecture.md` (9)
- `services/account-service/architecture.md` (7)
- `services/auth-service/architecture.md` (6)
- `services/admin-service/data-model.md` (6)
- `services/security-service/dependencies.md` (5)
- `services/account-service/data-model.md` (5)
- `services/admin-service/overview.md` (4)
- `services/admin-service/dependencies.md` (4)
- `services/account-service/dependencies.md` (4)
- 외 25+ file (1-3 broken / file)

target 분포 (broken target frequency):

- `rules/traits/audit-heavy.md` × 35
- `rules/traits/regulated.md` × 31
- `rules/traits/transactional.md` × 16
- `rules/domains/saas.md` × 13
- `rules/traits/integration-heavy.md` × 10
- `platform/service-types/event-consumer.md` × 6
- `platform/service-types/rest-api.md` × 5
- `platform/observability.md` × 5
- `platform/error-handling.md` × 3

모든 target file 은 **실제로 존재** (`rules/domains/saas.md` ✅ / `rules/traits/{audit-heavy,regulated,transactional,integration-heavy,multi-tenant}.md` ✅). path level 만 정정하면 즉시 valid.

### B. WMS notification-service/runbooks/dlt-replay.md (4 broken)

depth 7 file 에서 5 `..` 사용 → 6 `..` 정정:

- `../../../../../CLAUDE.md` → `../../../../../../CLAUDE.md`
- `../../../../../platform/event-driven-policy.md` → `../../../../../../platform/event-driven-policy.md`
- `../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` → `../../../../../../docs/adr/ADR-MONO-005-...md`
- `../../../../../platform/error-handling.md` → `../../../../../../platform/error-handling.md`

provenance: 2026-05-14 TASK-BE-145 머지 시 도입된 신규 broken (runbook 신규 작성). 같은 day-of audit 으로 즉시 발견.

### C. fan-platform v1-e2e-scenarios.md (2 broken)

depth 4 file 에서 3 `..` 사용 → 5 `..` 정정:

- `../../../tasks/done/TASK-MONO-025-base-event-publisher-uuidv7.md` → `../../../../../tasks/done/TASK-MONO-025-...md`
- `../../../tasks/done/TASK-MONO-023d-outbox-related-failures.md` → `../../../../../tasks/done/TASK-MONO-023d-...md`

target task file 모두 root `tasks/done/` 에 실재.

### D. ecommerce gateway-service/public-routes.md (1 broken)

depth 5 file 에서 6 `..` (one extra) → 5 `..` 정정:

- `../../../../../../platform/api-gateway-policy.md` → `../../../../../platform/api-gateway-policy.md`

### E. SCM procurement-service/architecture.md BOM 제거 (1 fix)

L1 byte order mark (U+FEFF) prefix → no-BOM UTF-8. sibling architecture.md (다른 4 axis × N service = 40+ file) 모두 no-BOM — 본 file 만 BOM. grep `^# ` 매치 실패의 원인 (H1 inventory 가 0 으로 잘못 보임).

## Out of Scope (3 finding — refactor scope 밖)

다음 3건은 **link target 자체 부재 (path-level mistake 아님)** — refactor 가 아니라 spec authoring 또는 link 제거 결정 필요. 본 task 본문에 finding 으로 기록만, 별 후속 task 후보:

1. **WMS outbound-service/external-integrations.md → `../../contracts/http/tms-shipment-api.md`** — production code (TMS adapter, TASK-BE-049 PR #315 머지 완료) 는 존재하나 contract spec 자체 미작성. 가치 있는 spec authoring 후보 (TASK-BE-148?).
2. **platform/architecture.md → `../PROJECT.md`** — repo root 에 `PROJECT.md` 부재 (project-level `projects/<name>/PROJECT.md` 만 존재). placeholder/안내 의도 vs 실수 판정 필요. 의도 = 아마 example, link 제거 또는 코드 블록 인용.
3. **GAP contracts/events/account-events.md → `../features/consumer-integration-guide.md`** — file 자체 미작성. consumer integration guide 가 별 file 인지 또는 다른 feature file 로 대체될 것인지 결정 필요.

본 3 finding 은 close chore PR 의 INDEX outcome 에 명시 + 본 task 의 Provenance/Findings 에 기록.

## Out of Scope (audit 결과 carry-over)

- structure / duplication / missing-section / orphan / naming / clarity finding 0 (오늘 closed 9 task 시리즈가 critical/HIGH 처리 완료).
- Service Type 미선언 architecture.md 0 (모든 service architecture.md 에 Service Type 선언 보유).
- overview.md / architecture.md missing 0 (TASK-BE-146 + TASK-SCM-BE-012 + TASK-BE-142 마무리로 portfolio 5/5 일관성 100% 완성).
- Tier 1 의 다른 audit deferred (INDEX.md `# Change Rule` 추가 / 17 file wording consistency / SCM eventType prefix / WMS 5 code registry) = no-action 또는 false positive 판정 (옵션 C A 단계 검증 완료).

---

# Acceptance Criteria

### Impl PR

- [ ] **A. GAP 131 path-level link 정정** — `projects/global-account-platform/specs/` 의 service/contract/feature spec 들에서 `../../../rules/`/`../../../platform/`/`../../rules/` 패턴을 file depth 별 올바른 level 로 sed-class batch 정정.
- [ ] **B. WMS dlt-replay.md 4 link 정정** — 5 `..` → 6 `..` (root CLAUDE.md / platform/event-driven-policy.md / docs/adr/ADR-MONO-005 / platform/error-handling.md).
- [ ] **C. fan-platform v1-e2e-scenarios.md 2 link 정정** — 3 `..` → 5 `..` (TASK-MONO-025 + TASK-MONO-023d task refs).
- [ ] **D. ecommerce gateway-service/public-routes.md 1 link 정정** — 6 `..` → 5 `..`.
- [ ] **E. SCM procurement-service/architecture.md BOM 제거** — UTF-8 no-BOM 으로 재저장. 첫 line `# procurement-service — Architecture` 가 BOM prefix 없이 시작.
- [ ] **Verification**: `bash /tmp/check_links2.sh` (또는 동등) 재실행 = **0 broken** (out-of-scope 3건 제외) — 추가 broken introduction 0.
- [ ] BOM grep: `grep -l $'\xef\xbb\xbf' projects/*/specs -r` = 0 hit.
- [ ] task lifecycle ready → review (mechanical batch single-PR closure 패턴, TASK-MONO-084 precedent 답습).
- [ ] tasks/INDEX.md (root) 동기.
- [ ] CI self-CI PASS (path-filter projects/*/specs 활성화, markdown-only → 15 SKIP + 1 PASS 패턴 답습).

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] tasks/INDEX.md ## review 제거, ## done append outcome (3 out-of-scope finding 포함 명시).

---

# Related Specs

- `platform/entrypoint.md` (refactor-spec dry-run 진입 점검).
- `platform/naming-conventions.md` (file 명명 / path 규칙 — broken link 의 sibling reference).
- `rules/README.md` § Routing Layer (rules/ 의 domain/trait file 실재 확인 기준).
- `tasks/done/TASK-MONO-084-platform-change-rule-batch-backfill.md` (sibling 답습 — mechanical batch single-PR closure 패턴).
- `projects/global-account-platform/PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy, integration-heavy, multi-tenant] — GAP spec 들이 reference 하는 5 trait + 1 domain file 모두 실재 확인 완료).

---

# Related Contracts

본 task = spec markdown link path 정정. HTTP API / event payload / production code 변경 0. cross-service contract 무관.

---

# Target Service

- **cross-axis**: GAP (131건) / WMS (4건 dlt-replay.md) / fan-platform (2건 v1-e2e-scenarios.md) / ecommerce (1건 gateway public-routes.md) / SCM (1건 procurement-service BOM).
- 5 axis 모두 영향 — monorepo-level cross-project structural cleanup → root `tasks/` task.

---

# Architecture

spec markdown 의 cross-file link integrity 보강. `..` level 의 systemic mistake (특히 GAP) 의 root cause = file depth 와 link path 의 mismatch — sibling consistency check 누락의 누적. 본 task = bulk path correction + verification.

`..` level 산정 rule:

- file 의 directory depth (segment count - 1, basename 제외) = root 까지 `..` 횟수.
- e.g. `projects/global-account-platform/specs/services/<svc>/<file>.md` depth = 5 → root `rules/` 가려면 `../../../../../rules/`.

BOM correction: PowerShell `Set-Content -Encoding utf8NoBOM` 또는 `Get-Content -Raw | Set-Content -Encoding UTF8 -NoNewline` 으로 BOM 제거 + 본문 무변경 재저장.

---

# Implementation Notes

## A. GAP path-level batch

가장 효율적 = file 별 multi-Edit (replace_all=true 가능하나 sibling 본문 영향 0 인지 verify 필수). file 마다:

1. file 의 directory depth 계산 (`projects/<axis>/specs/...` segment count).
2. 잘못된 `..` count 와 정정 `..` count 차이 = `2 levels add` 또는 `1 level add`.
3. `grep -oE` 로 broken pattern 추출 → file 별 Edit replace_all.

또는 single shell script (one-liner) 로 GAP 31 file × 평균 4 link = 131 정정 가능. bash 또는 PowerShell `Get-ChildItem … | ForEach-Object { (Get-Content $_) -replace '…','…' | Set-Content $_ }`.

## B-D. 다른 axis path-level

각각 1-4 broken file — Edit replace_all (per file) 로 단순 처리. WMS dlt-replay.md 4 line, fan v1-e2e-scenarios.md 2 line, ecommerce public-routes.md 1 line.

## E. BOM 제거

PowerShell:

```pwsh
$content = Get-Content -Raw -Path "projects/scm-platform/specs/services/procurement-service/architecture.md"
[System.IO.File]::WriteAllText((Resolve-Path "projects/scm-platform/specs/services/procurement-service/architecture.md"), $content, [System.Text.UTF8Encoding]::new($false))
```

또는 bash `sed -i '1s/^\xef\xbb\xbf//'`.

## D4 churn impact

- spec-only 변경 — D4 OVERRIDE 적용 (ADR-MONO-003a § D1.1 연장선; 5 axis × markdown link 정정 = governance polish 범주).
- structural 변경 0 — D2 시계 재시작 영향 약함.
- TASK-MONO-084 / MONO-083 / MONO-082 ... 동일 OVERRIDE precedent.

---

# Edge Cases

- **GAP service spec 의 cross-ref 가 다른 GAP file 도 reference** (`../../services/<other-svc>/<file>.md` 같은 intra-project link). 본 task scope = root-relative (rules/ / platform/ / tasks/) link 만, intra-project link 는 spot-verify 후 별 처리. spot-check 결과 intra-project link 의 broken 사례 = 0 으로 보임 (broken_links2.txt 분석).
- **multi-tenant.md** = 1건 broken — GAP trait list 에 포함된 file 이라 fix 적용.
- BOM 제거 시 file 본문 무변경 — diff 가 line 0 의 binary byte 차이만 보임 (CI 의 markdown lint 가 BOM 감지하면 별도 fail 가능, lint 정상 통과 verify).

---

# Failure Scenarios

- Edit replace_all 의 false-positive replacement (e.g. broken pattern 이 본문 code block 안에 있어 의도치 못한 변경) → file 별 Edit 후 spot-diff verify.
- BOM 제거 후 file 의 encoding 잘못 (예: CRLF → LF mass conversion) → byte-diff 로 verify (line ending 무변경 보장).
- `bash /tmp/check_links2.sh` 재실행 시 BOM file 의 path-handling Windows 차이 — Linux runner CI 에서 final verify.

---

# Test Requirements

- `bash /tmp/check_links2.sh` (또는 동등 dead-link checker) 재실행: **broken count = 3** (out-of-scope finding 3건 정확히 잔존; 138 path-level + 1 BOM = 139 정정 완료).
- BOM grep 0 hit: `grep -rl $'\xef\xbb\xbf' projects platform` = empty.
- CI self-CI PASS (path-filter projects/*/specs 활성화, markdown-only batch).
- production code = 0 (spec only).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### Close chore PR

- [ ] review → done, INDEX 동기 + 3 out-of-scope finding 명시.

---

# Provenance

- `/refactor-spec all --dry-run` 2026-05-14 (옵션 C 봉합 sequence B 단계) 결과 — Tier 2 추상적 finding 의 정체 = dead-reference 140건 + 1 BOM.
- TASK-MONO-084 (PR #463+#464+#465 머지 2026-05-14) 의 sibling 답습 패턴 (mechanical batch single-PR closure, D4 OVERRIDE 적용).
- 옵션 C A 단계 Tier 1 4 finding 검증 결과: actionable 0 (모두 false positive 또는 deferred-by-design), B 단계로 잔존 0 확정 진행.
- 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical batch, ~138 line × spec markdown text replace + 1 BOM 제거 — design judgment 0).
