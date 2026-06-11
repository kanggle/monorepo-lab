# Task ID

TASK-MONO-222

# Title

ADR-MONO-028 (PROPOSED) — `TIME_WINDOW` Access Condition (2nd condition type under ADR-026's closed enum). Author the committed PROPOSED ADR that pilots the **`TIME_WINDOW`** member of the ADR-MONO-026 closed access-condition enum (currently "reserved" in `platform/access-conditions.md` § 1). Inherits ADR-026's framework **unchanged** (closed enum / AND-only / restriction-only + fail-safe + net-zero / D3-B domain guard-config carrier) and decides only the options ADR-026 left open for a new type: **D2** pilot domain + composition (A: iam admin composed AND-only with the existing `SOURCE_IP` — exercises the blessed-but-unproven multi-condition composition + reuses the MONO-221 harness; vs B: wms fresh single `TIME_WINDOW`) and **D3** `TIME_WINDOW` semantics + config schema (IANA zone + days-of-week + same-day `[start,end)` local window; fail-safe; net-zero; cross-midnight deferred). Doc-only; the ACCEPTED transition + execution (`TimeWindowCondition` evaluator + contract flip + pilot enforcement) are separate follow-up tasks (ADR § 3.3).

# Status

done

> **완료 (2026-06-11)**: PROPOSED ADR 작성 PR #1309 (squash `c0f8ac4c`). 3차원 ✓ (MERGED / origin/main tip=`c0f8ac4c` 일치 / 코드 체크 all-skip path-filter, 0 failing required). `ADR-MONO-028` Status=PROPOSED — ADR-026 프레임워크 inherit-unchanged + 신규 타입 게이트만 결정: **D2** pilot/composition(A=iam admin SOURCE_IP와 AND 합성[chosen-PROPOSED, 미검증 다중조건 합성 실증+MONO-221 하네스 재사용] vs B=wms fresh 단일) + **D3** TIME_WINDOW semantics/config(IANA zone+days+same-day `[start,end)`·fail-safe·net-zero·midnight-wrap deferred). 핵심=ADR-026이 축복했으나 BE-351이 미검증한 **AND-only 다중조건 합성**을 실증하는 게 진짜 증분. 다음=**ACCEPTED 게이트 사용자 결정**(D2 pilot/composition + D3 schema)→evaluator+계약→pilot enforcement+IT→optional fed-e2e. 분석=Opus 4.8.

# Owner

backend

# Task Tags

- adr
- abac
- conditional-policy
- access-conditions
- iam
- doc

---

# Dependency Markers

- **executes**: ADR-MONO-026 § D7.4 ("additional condition types … each its own ADR/task") for the `TIME_WINDOW` enum member — ADR-026's framework is inherited unchanged, NOT reopened.
- **follows**: ADR-MONO-026 (axis ② 2단계 framework + `SOURCE_IP` pilot, CLOSED: MONO-216/217/218 + BE-351 + MONO-221 federation GREEN). This is the 2nd condition type under the same closed enum, ADR-first per the established 019…026 staged pattern.
- **blocks**: the ADR § 3.3 execution roadmap (ACCEPTED transition → `TimeWindowCondition` evaluator + contract update → pilot enforcement + IT → optional fed-e2e composition proof). None start until ACCEPTED.

# Goal

Record the `TIME_WINDOW` pilot decision so the 2nd access-condition type is added via the framework's blessed mechanism (a new shared evaluator class + tests) rather than re-derived, while keeping the change net-zero and bounding scope to a fixed same-day-per-weekday window — rejecting any cron/recurrence policy language and any boolean nesting (ADR-026 § D6, inherited). Surface, as gate options for the ACCEPTED transition, the pilot domain + composition (D2) and the `TIME_WINDOW` config schema/semantics (D3).

# Scope

- NEW `docs/adr/ADR-MONO-028-time-window-access-condition.md` (Status PROPOSED) — D1 (inherited framework) + D2 (pilot/composition gate) + D3 (semantics/schema gate) + D4-D6 + alternatives + relationship to ADR-026 + Status Transition History.
- This task file.

**Out of scope** (post-ACCEPTED, separate tasks): the `TimeWindowCondition` evaluator (`libs/java-security`), the `platform/access-conditions.md` § 1 reserved→implemented flip + § 4 adopter row + the AND-only multi-condition note, any pilot-domain enforcement code, the `RequiresPermissionAspect` multi-condition generalisation, any producer change (D3-A stays deferred), the optional federation-e2e composition proof.

# Acceptance Criteria

- **AC-1** `ADR-MONO-028` exists with Status PROPOSED and explicitly **inherits ADR-026's framework unchanged** (closed enum, AND-only, restriction-only + fail-safe + net-zero, D3-B carrier) — it re-decides none of them.
- **AC-2** The ADR decides only the open gates for the new type: **D2** pilot domain + composition (A iam-admin compose-AND-with-`SOURCE_IP` [chosen-PROPOSED] vs B wms fresh single) and **D3** `TIME_WINDOW` semantics + config schema (IANA zone + days + same-day `[start,end)`; fail-safe; net-zero; midnight-wrap deferred).
- **AC-3** The ADR names the genuinely-new increment: `TIME_WINDOW`'s pilot is the first to exercise the **AND-only multi-condition composition** that ADR-026 § D1 blessed but never proved (BE-351 had a single condition).
- **AC-4** The ADR explicitly **rejects** a cron/recurrence policy language + boolean nesting (§ D5) and keeps the signed-claim carrier (D3-A) deferred (D3-B inherited).
- **AC-5** Status Transition History has the PROPOSED row with the user intent quote ("TIME_WINDOW 2번째 조건타입") and the queue-empty/next-increment context.
- **AC-6** Doc-only — no code, no contract change, no evaluator, no producer change in this PR.

# Related Specs

- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the parent framework + § D7.4 routing additional types to their own ADR/task)
- `platform/access-conditions.md` § 1 (the closed enum where `TIME_WINDOW` is "reserved") + § 2 (the three invariants inherited)
- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` (axis ② 1단계, the sibling the framework mirrors)

# Related Contracts

- none in this PR (the `platform/access-conditions.md` reserved→implemented flip is a post-ACCEPTED deliverable)

# Edge Cases

- **Net-zero/opt-in** must be explicit: absent `TIME_WINDOW` config ⟺ unchanged behaviour — stated as the § D4 invariant so the gate never bites an existing path until a window is configured.
- **Fail-safe** must be explicit: a missing/unparseable zone, malformed `start`/`end`, or unresolvable request time ⇒ deny, never allow (§ D3) — the security-critical direction.
- **Composition (D2-A)** requires generalising `RequiresPermissionAspect`'s 4th gate from one condition to a **set** evaluated AND-only — flagged as an execution-stage detail (out of scope for this doc-only PROPOSED), but named so the ACCEPTED gate weighs it.
- **Midnight-wrap** (`end < start`) is deferred to a fast-follow — named so the gate can opt to include it if the pilot needs a cross-midnight window.
- **DST** is handled by `java.time` via the IANA zone — the ADR notes this so no manual offset math is implied.

# Failure Scenarios

- If the ADR re-decided the ADR-026 framework (enum/AND-only/invariants/carrier), it would duplicate/contradict the parent — AC-1 pins it as inherit-unchanged.
- If the ADR named a cron/recurrence policy language as the decision, it would re-introduce the 고비용 policy-engine scope ADR-026 § D6 closed — § D5 + Alternatives bound it to a fixed same-day-per-weekday window.
- If the pilot/composition + schema were hard-decided in PROPOSED instead of left to the gate, the ACCEPTED transition would lose its decision surface — D2/D3 are deliberately PROPOSED-as-options (mirrors ADR-026 D3/D4 left to its gate).
- If the ADR allowed a condition to *grant* (additive/elevation), a fail-dangerous path opens — restriction-only is inherited from ADR-026 § D2 and restated.
