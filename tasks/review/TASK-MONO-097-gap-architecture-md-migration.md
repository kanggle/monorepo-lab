# Task ID

TASK-MONO-097

# Title

GAP 8 architecture.md migration to WMS Identity-table canonical form (ADR-MONO-012 D3 second batch)

# Status

review

# Owner

monorepo

# Task Tags

- monorepo
- global-account-platform
- architecture-canonical-form
- adr-mono-012

---

# Goal

ADR-MONO-012 D3 second batch (SCM → **GAP** → ecommerce ascending cost). GAP 8 architecture.md (account / admin / admin-web / auth / community / gateway / membership / security) 를 canonical form 으로 align.

# Scope

## In Scope (8 file)

**Standard GAP form (7 file)** — account / admin / auth / community / gateway / membership / security:
- H1 rename: `# Service Architecture — <svc>` → `# <svc> — Architecture`
- intro paragraph 추가 (canonical)
- `## Service` H2 section 제거 (Identity table row 로 흡수)
- `## Service Type` H2 → 본문 narrative 보존하여 `### Service Type Composition` H3 으로 변환 (Identity table 직후)
- `## Identity` H2 table 추가 (Service name / Project / Service Type / Architecture Style / Primary language / Bounded Context / Deployable unit / Data store / Event publication / Event consumption — file 의 본문 narrative 에서 추출)
- 기존 `## Architecture Style` body / `## Why This Architecture` / `## Internal Structure Rule` 등 H2 본문 모두 보존

**admin-web (1 file, partial-migrated)**:
- H1 이미 canonical (`# admin-web — Architecture`)
- `## Service` (Identity-like table) → `## Identity` rename
- `## Service Type` H2 별도 section → 본문을 `### Service Type Composition` H3 으로 변환 후 Identity table 직후 배치
- 기존 narrative 보존

## Out of Scope

- HARDSTOP-10 hook propagation — 별 task **TASK-MONO-096** ✅ 종결 (canonical form fixture coverage).
- ecommerce 13 architecture.md migration — 별 task **TASK-MONO-098**.
- Production code / spec contract / Service Type 값 / 본문 narrative 의미 = 0 변경 (canonical form alignment + restructure only).

# Acceptance Criteria

- [ ] `grep -c "^## Identity$" projects/global-account-platform/specs/services/*/architecture.md` = 8 (8 service 모두 canonical Identity H2).
- [ ] `grep -c "^## Service$" projects/global-account-platform/specs/services/*/architecture.md` = 0 (구 GAP form 의 standalone `## Service` H2 모두 제거).
- [ ] `grep -c "^## Service Type$" projects/global-account-platform/specs/services/*/architecture.md` = 0 (구 GAP form 의 standalone `## Service Type` H2 모두 제거).
- [ ] `grep -c "^### Service Type Composition$" projects/global-account-platform/specs/services/*/architecture.md` = 8 (8 service 모두 Composition H3).
- [ ] `grep -c "^# Service Architecture —" projects/global-account-platform/specs/services/*/architecture.md` = 0 (구 GAP H1 form 모두 제거).
- [ ] `grep -c "^# .* — Architecture$" projects/global-account-platform/specs/services/*/architecture.md` = 8 (canonical H1 form).
- [ ] Production code / spec contract / Service Type 값 / 본문 narrative 의미 = 0 변경.
- [ ] HARDSTOP-10 hook 회귀 없음 (canonical form fixture coverage TASK-MONO-096 검증됨).

# Related Specs

- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` § D1 (canonical form), § D3 (migration order)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` (canonical sample)
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (MONO-095 SCM migration precedent)
- `projects/scm-platform/specs/services/inventory-visibility-service/architecture.md` (MONO-095 dual-type SCM precedent)
- `projects/global-account-platform/specs/services/*/architecture.md` (8 targets)

# Related Contracts

해당 없음.

# Target Service

global-account-platform (8 service: account / admin / admin-web / auth / community / gateway / membership / security).

# Edge Cases

- A: **admin-web partial-migration**: 이미 Identity-like table 보유하나 H2 가 `## Service` (canonical 은 `## Identity`). rename + Service Type H2 의 separate section 을 Composition H3 으로 통합.
- B: **auth-service identity-platform**: Service Type 값이 `identity-platform`. 본문에 ADR-001 + BE-251~274 선언 이력 보존. Composition H3 body 가 길어질 가능성.
- C: **security-service dual-type**: Service Type = `event-consumer (+ 좁은 read-only HTTP 표면)`. Composition H3 body 가 dual-type 분담 설명. (Service Type Composition 의 의미: 본문 narrative 의 dual-type role split.)
- D: **admin-service Thin Layered (Command Gateway)**: Architecture Style 본문이 narrative-rich. Identity row 는 `Architecture Style: **Thin Layered (Command Gateway)**` 만, body 는 Style H2 로 보존.

# Failure Scenarios

- A: 8 file × multiple section restructure 중 narrative 손상 가능. mitigation: file-by-file Edit 진행 + post-edit grep verification + body diff inspection (`git diff` review).
- B: Identity table backfill 값이 본문 narrative 에서 추출 — 일부 file 에서 명시 안 됨 (e.g., Primary language / Data store / Event publication). 합리적 기본값 추출 (Java 21 / PostgreSQL / Kafka — GAP standard stack) 또는 inline reference 로 placeholder.

# Validation Plan

1. 8 file Edit 후 위 § Acceptance Criteria 각 grep 검증.
2. `git diff --stat` 으로 file 별 line 변경 확인 (~15-30 line per file = ~120-240 line total).
3. body content 의 의미 손상 없음 확인 (Architecture Style / Why / Internal Structure Rule 보존).
4. HARDSTOP-10 hook 회귀 없음 (canonical form 호환 검증).

# Implementation Notes

- **D4 OVERRIDE applied** per ADR-MONO-003a § D1.1 (project-internal spec polish, ADR-MONO-012 governance follow-up). 13번째 누적 task (MONO-085~096 + 본 task).
- monorepo-level task 분류 (root `tasks/`) — ADR-MONO-012 D3 second batch.
- 2 commit / 1 branch: (1) ready/ task author + tasks/INDEX.md ready row + 8 file Edit, (2) lifecycle move ready → review + INDEX done row.
- branch name `task/mono-097-gap-architecture-md-migration` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- single-PR closure 패턴 — MONO-084~096 precedent 답습.
- **awk single-pass migration** 패턴 (MONO-095 학습 답습) — HARDSTOP-10 hook CRLF/LF simulation mismatch 우회.
- file 별 transform 의 unique 요소 (backfill 값 + Composition H3 body) → file-by-file specific awk script.

# Outcome

(to be filled after impl commit)
