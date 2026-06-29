# Task ID

TASK-MONO-317

# Title

`ADR-MONO-043` § 7 follow-up close-out — record the remaining P2 surface-expansion + engine-integration follow-ups as DECLINED (no operator-console driver / HARDSTOP-06)

# Status

ready

# Owner

architect

# Task Tags

- adr
- architecture
- notification
- close-out

---

# Goal

ADR-MONO-043 (notification architecture unification) is functionally complete: ACCEPTED + D7 phases 1–3 for erp/fan landed (P1a contract, P1b `libs/java-notification`, P2 erp/fan shape, P3a console-bff aggregator, P3b bell rewire), resolving the originating incident — the shared-shell console bell no longer couples to a single domain's availability (D5 failure-isolation live).

The ADR § 7 still lists the remaining surface-expansion follow-ups as **open**, but they have been deliberately decided **not** to be done (user-decided 2026-06-29):
- **wms inbox** — DECLINED → delivery-only (no inbox surface, no UI consumer, no operator demand).
- **ecommerce shape-conformance** — DECLINED (category mismatch: customer-facing notifications have no place in the operator bell).
- **engine-level integration into `libs/java-notification`** — DECLINED (the net-zero premise is false — per-domain engines genuinely diverge; forcing convergence = HARDSTOP-06; ADR-038 § 5 M3 "share the leaf shape, keep the engine service-side").

This task records that decision in the ADR § 7 (resolution) + § 6 (audit-trail row), so a future backlog-sweep does not re-discover the items as phantom-actionable (the auto-memory `project_adr043_notification_unification` already carries the same close-out).

**근거 메모**: auto-memory `project_adr043_notification_unification` § "잔여 P2 = DEFERRED/DECLINED".

---

# Scope

## In Scope

| 산출물 | 위치 | 설명 |
|---|---|---|
| ADR § 7 갱신 | `docs/adr/ADR-MONO-043-notification-architecture-unification.md` | "Outstanding follow-ups" 의 open 리스트를 **Resolution(2026-06-29)** 으로 교체: wms→delivery-only, ecommerce shape + engine-integration → DECLINED (사유 + ADR-038 M3 선례 + re-open 조건). |
| ADR § 6 audit row | 同 파일 | audit-trail 테이블에 `Follow-up close-out / TASK-MONO-317 / (this PR)` row 추가. |
| INDEX | `tasks/INDEX.md` ready | 본 task 한 줄. |

## Out of Scope

- D1–D8 / § 1–§ 5 본문 변경 (byte-unchanged — ACCEPTED ADR 의 finalised 결정; § 6/§ 7 만 갱신).
- 어떤 구현 (declined 항목을 실제로 만들지 않음 — 이 task 는 결정 기록만).
- wms/ecommerce notification-service 코드, `libs/java-notification`, console-bff/console-web 변경 0.

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-043-...` § 7 이 open 3-bullet → **Resolution** 블록으로 교체 (wms delivery-only / ecommerce DECLINED / engine-integration DECLINED + 각 사유 + ADR-038 M3 + re-open 조건 명시).
- [ ] § 6 audit-trail 에 `Follow-up close-out | TASK-MONO-317 | (this PR)` row 추가.
- [ ] **D1–D8 + § 1–§ 5 byte-unchanged** (`git diff` 로 §2 Decision / §1 Context / §3–§5 0 변경 확인 — § 6/§ 7 만 수정).
- [ ] impl code / contract / library 변경 0 (doc-only PR).
- [ ] `tasks/INDEX.md` ready entry.

---

# Related Specs

- [ADR-MONO-043 § 7](../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — the follow-ups being resolved.
- [ADR-MONO-038 § 5 M3](../../docs/adr/ADR-MONO-038-shared-idempotency-filter-abstraction.md) — "share the leaf shape, keep the divergent engine service-side" 선례 (engine-integration DECLINE 근거).

# Related Skills

- `.claude/skills/common/architect/`

# Related Contracts

- `platform/contracts/notification-inbox-contract.md` (P1a — the unification boundary that stays; engine stays service-side).

---

# Edge Cases

- **§ 7 follow-up 중 하나라도 실제 수요가 드러남** → 그 항목만 re-open (ADR § 7 re-open 조건대로: shape-conformance 1건 + aggregator config 한 줄). 본 close-out 이 영구 금지가 아니라 "선제구현 금지 + 수요-게이트".
- **D1–D8 본문에 손이 가야 하는 상황** → STOP. 이 task 는 § 6/§ 7 만. D-decision 변경은 별도 ADR amendment.

# Failure Scenarios

- **D1–D8/§1–§5 byte 변경** → commit 직전 `git diff docs/adr/ADR-MONO-043-...` 로 §2/§1/§3–§5 0 변경 검증.
- **declined 항목을 구현으로 오해** → 본 task 는 결정 기록만 (코드 0). PR diff 에 doc 외 파일 있으면 fail.

---

# Test Requirements

ADR=spec → 테스트 0. 검증:
- [ ] markdown lint pass.
- [ ] `git diff` 로 ADR-043 §1–§5 + D1–D8 byte-unchanged, §6(+1 row)/§7(resolution) 만 변경.
- [ ] PR diff = doc 2-3개만 (ADR-043 + 본 task md + INDEX), impl 0.

---

# Definition of Done

- [ ] ADR-043 §7 resolution + §6 row 작성.
- [ ] byte-unchanged 검증 (§1–§5 + D1–D8).
- [ ] `tasks/INDEX.md` ready entry.
- [ ] commit + push (branch `task/mono-317-adr-043-followup-declines`).
- [ ] PR open.
