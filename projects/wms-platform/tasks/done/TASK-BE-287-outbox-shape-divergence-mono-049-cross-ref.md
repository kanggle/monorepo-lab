# Task ID

TASK-BE-287

# Title

WMS outbox shape divergence cross-reference + MONO-049 deferred pointer (refactor-spec Tier 2 F-06+F-07 spec-only closure)

# Status

done

# Owner

wms-platform

# Task Tags

- wms
- spec
- duplication
- governance
- refactor-spec
- documentation

---

# Goal

`/refactor-spec all --dry-run` non-deadref **Tier 2 F-06+F-07 spec-only closure** — master vs 5 sibling outbox schema divergence 의 cross-reference + deferred decision pointer 명시.

## Finding analysis

- **master-service/database-design.md § 2**: `outbox` (BIGSERIAL PK + TEXT payload + `status` enum + TIMESTAMP) + `processed_events` — `libs/java-messaging/OutboxJpaEntity` 공유 schema.
- **5 sibling** (admin/inbound/inventory/notification/outbound): `<service>_outbox` (UUID PK + JSONB payload + `partition_key` + TIMESTAMPTZ) + `<service>_event_dedupe` — wms-specific modern shape.

## Existing decision (memory `project_mono_049_followups_deferred.md` 답습)

- TASK-MONO-049 § 6 follow-up #1 (master 마이그레이션) **deferred** per ADR-MONO-003 D2 cadence (≥ 2026-06-10).
- master 의 production code 변경 / libs/java-messaging breaking change / Flyway migration 은 본 task 범위 외.

## Spec-only scope

본 task = master + 5 sibling database-design.md spec 에 divergence rationale + deferred pointer 명시. 0 production code, 0 schema migration, 0 libs touch. refactor-spec Tier 2 spec-only closure 의 자연 path.

Current state:
- master § 2 가 이미 "Shape divergence vs. wms-specific outbox" paragraph 보유 — MONO-049 § 6 pointer 만 1-line 추가.
- 5 sibling 의 outbox section 에 cross-reference 없음 → 각 sibling 에 reverse pointer 추가 (master § 2 + MONO-049 § 6 deferred reference).

# Scope

## In Scope

- `master-service/database-design.md` § 2 (outbox section): 기존 "shape stays" paragraph 끝에 MONO-049 § 6 cross-reference 1 line 추가.
- 5 sibling `<service>-service/database-design.md` (admin / inbound / inventory / notification / outbound) outbox CREATE TABLE block 뒤에 reverse cross-reference paragraph 추가 (master § 2 + MONO-049 § 6 deferred 명시).

## Out of Scope

- master / 5 sibling Flyway migration (production schema change) — MONO-049 § 6 follow-up #1 deferred.
- libs/java-messaging `OutboxJpaEntity` refactor — same deferred decision.
- master / 5 sibling Java code change (`@Entity`, repository, publisher) — production code 0.
- Tier 2 F-01+F-02 / F-08 (별 task, 이미 closure).
- Tier 3 backlog (out-of-scope per refactor-spec audit).

# Acceptance Criteria

- [x] master-service/database-design.md § 2 paragraph 끝에 "Tracked as TASK-MONO-049 § 6 follow-up #1, deferred per ADR-MONO-003 D2 cadence (≥ 2026-06-10)" 명시.
- [x] 5 sibling database-design.md (admin/inbound/inventory/notification/outbound) 의 outbox section 에 reverse cross-reference (master § 2 link + MONO-049 § 6 deferred) 명시.
- [x] Production code / Flyway / libs/java-messaging 0 변경.
- [x] 6 file 모두 `grep "MONO-049"` + `grep "ADR-MONO-003"` 양쪽 hit (6/6 verified).
- [x] 신규 paragraph 가 sibling 의 기존 outbox section 본문 일관성 유지 (각 sibling 의 existing master/libs reference 와 augment 패턴, 형식 align).

# Related Specs

- `projects/wms-platform/specs/services/master-service/database-design.md` § 2 (existing divergence paragraph)
- `projects/wms-platform/specs/services/{admin,inbound,inventory,notification,outbound}-service/database-design.md` (target — 5 sibling)
- `tasks/done/TASK-MONO-049-libs-java-messaging-scaffolding.md` § 6 (deferred follow-up source-of-truth)
- `docs/adr/ADR-MONO-003-phase-5-template-extraction-deferred.md` (D2 cadence)
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 6 (5 deferred follow-up 정의)
- `.claude/commands/refactor-spec.md` § Operational Patterns (Tier 2 closure)

# Related Contracts

해당 없음 (spec polish only, contract 0).

# Target Service

해당 없음 — 6 WMS service database-design.md spec polish.

# Edge Cases

- A: 5 sibling 의 outbox section formatting 이 file 별로 다를 가능성 — 동일 paragraph shape 으로 align (sibling consistency 검증).
- B: master § 2 paragraph 가 이미 충분 detail 보유 — MONO-049 cross-reference 만 1 line append.

# Failure Scenarios

- A: 5 sibling 의 어떤 outbox section 이 이미 cross-reference 보유 시 → 기존 reference 확인 후 augment.
- B: MONO-049 § 6 cross-reference path 가 done/ → archive/ 이동 시 dead-ref 위험 — 본 task 시점 path 검증 + tasks/done 위치 안정 확인.

# Validation Plan

1. Edit 후 6 file 의 `grep -n "MONO-049"` 모두 hit (master + 5 sibling).
2. `grep "ADR-MONO-003"` 도 master + 5 sibling 6 file 모두 hit (D2 cadence reference).
3. `git diff --stat` = 6 file / ~12-18 line edit / 0 production code.
4. dead-ref checker (BE-165 pattern) 6 file = 0 broken.

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) 6 file Edit + lifecycle move ready/ → review/.
- branch name `task/be-287-outbox-shape-divergence-mono-049-cross-ref` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- 8번째 refactor-spec cycle task. F-06+F-07 spec-only closure — implementation deferred per existing decision.

# Outcome

**Status: DONE** (PR #524 squash `6bfe2afa`).

Spec-only documentation cross-reference — refactor-spec Tier 2 F-06+F-07 closure. **0 production code change**.

## File changes (6 file / +35 / -2)

| File | Edit type |
|---|---|
| `master-service/database-design.md` § 2 | existing divergence paragraph augmented with MONO-049 § 6 + ADR-MONO-003 D2 cadence pointer (1 line) |
| `admin-service/database-design.md` § 5 | reverse cross-ref paragraph added (master § 2 link + deferred decision pointer) |
| `inbound-service/database-design.md` § 5 | existing master-link paragraph augmented with deferred sentence |
| `inventory-service/database-design.md` § 3 | reverse cross-ref paragraph added |
| `notification-service/database-design.md` outbox section | existing master-divergence paragraph augmented |
| `outbound-service/database-design.md` § 6 | reverse cross-ref paragraph added (incl. outbound's unique `status`+`retry_count` publisher columns context) |

## Verification

- 6/6 file `grep "MONO-049"` hit + 6/6 file `grep "ADR-MONO-003"` hit
- 0 production code / Flyway / libs/java-messaging touch

## CI

1 SUCCESS (`changes`) / 16 SKIPPED / 0 fail — markdown-only path-filter. mergeStateStatus CLEAN.

## refactor-spec cycle — all Tier 2 종결

| # | Task | Scope | Category | Fix |
|---|---|---|---|---|
| 1-4 | BE-165/283/SCM-BE-013/BE-284 | deadref Tier 1+2+3 | mech+judg | 54 |
| 5 | BE-285 | WMS non-deadref Tier 1 | mech | 6 |
| 6 | MONO-091 | platform Tier 2 (F-08) | mech | 1 |
| 7 | BE-286 | inventory Tier 2 (F-01+F-02) | judgment+additive | 8 sections |
| 8 | **BE-287 (this)** | wms Tier 2 (F-06+F-07) | governance pointer | 6 file cross-ref |

**non-deadref Tier 2 backlog = 0**. **모든 audit finding 종결** (F-01/02/04/05/06/07/08/09/10 + F-03 SKIP). **Implementation migration** (master outbox shape sync to wms-specific UUID+JSONB+partition_key) 은 TASK-MONO-049 § 6 follow-up #1 으로 ADR-MONO-003 D2 cadence (≥ 2026-06-10) 이후 별 cycle.
