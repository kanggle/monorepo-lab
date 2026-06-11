# Task ID

TASK-MONO-223

# Title

ADR-MONO-028 (PROPOSED → ACCEPTED) — fix the `TIME_WINDOW` pilot gates. Flip `ADR-MONO-028` Status to ACCEPTED, fixing **D2 = D2-A** (iam admin-service, `TIME_WINDOW` composed AND-only with the existing `SOURCE_IP` — the multi-condition composition pilot) and **D3** (the proposed schema: IANA zone + days-of-week + same-day `[start,end)` local window; fail-safe on bad input; net-zero on unset; **midnight-wrap deferred** to a fast-follow). Inherits ADR-026's framework unchanged (D1/D4-D6). Authorises the § 3.3 execution roadmap. Doc-only.

# Status

ready

# Owner

backend

# Task Tags

- adr
- access-conditions
- conditional-policy
- iam
- doc

---

# Dependency Markers

- **transitions**: `ADR-MONO-028` PROPOSED (TASK-MONO-222, DONE #1309) → ACCEPTED. Same staged ADR discipline as ADR-023/024/025/026's ACCEPTED transitions.
- **authorises**: the ADR § 3.3 execution roadmap — `TASK-MONO-224` (`TimeWindowCondition` evaluator + contract flip) → iam pilot enforcement (compose with `SOURCE_IP`) + IT → optional federation-e2e composition proof. None start until this ACCEPTED main exists (dependency-correct base).
- **inherits**: ADR-MONO-026 framework (closed enum / AND-only / restriction-only + fail-safe + net-zero / D3-B carrier) — unchanged.

# Goal

Record the user's gate decision so the execution roadmap has a dependency-correct ACCEPTED base: the pilot is the iam admin mutation surface with `TIME_WINDOW` composed AND-only with the existing `SOURCE_IP` (exercising the multi-condition composition ADR-026 blessed but never proved), and the `TIME_WINDOW` schema is fixed (same-day window over an IANA zone, fail-safe, net-zero, midnight-wrap deferred).

# Scope

- Update `docs/adr/ADR-MONO-028-time-window-access-condition.md`: Status PROPOSED → ACCEPTED; D2 marked CHOSEN = D2-A (iam compose-AND-with-`SOURCE_IP`); D3 marked FIXED (schema as proposed, midnight-wrap deferred); § 3.3 roadmap task numbers filled (223 ACCEPTED, 224 evaluator+contract, iam BE enforcement); Status Transition History ACCEPTED row with the user intent quote.
- This task file.

**Out of scope** (post-ACCEPTED, separate tasks): the `TimeWindowCondition` evaluator, the `platform/access-conditions.md` flip, the `RequiresPermissionAspect` multi-condition generalisation, any pilot enforcement code, the optional fed-e2e proof.

# Acceptance Criteria

- **AC-1** `ADR-MONO-028` Status = ACCEPTED.
- **AC-2** D2 fixed = D2-A (iam admin-service, `TIME_WINDOW` composed AND-only with `SOURCE_IP`); D3 fixed = the proposed schema with midnight-wrap deferred. D1/D4-D6 unchanged (inherited from ADR-026).
- **AC-3** § 3.3 roadmap names the concrete follow-up tasks (224 evaluator+contract → iam BE enforcement → optional fed-e2e), dependency-rooted on this ACCEPTED main.
- **AC-4** Status Transition History has the PROPOSED → ACCEPTED row with the user intent quote ("A: iam admin + SOURCE_IP와 AND 합성").
- **AC-5** Doc-only — no code, no contract change, no evaluator in this PR.

# Related Specs

- `docs/adr/ADR-MONO-028-time-window-access-condition.md` (the ADR being transitioned)
- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the parent framework)
- `platform/access-conditions.md` § 1 (the closed enum where `TIME_WINDOW` is "reserved", flipped to implemented by the post-ACCEPTED evaluator task)

# Related Contracts

- none in this PR (the `platform/access-conditions.md` flip is a post-ACCEPTED deliverable)

# Edge Cases

- The ACCEPTED transition is doc-only and decision-recording — it must NOT introduce code, the contract flip, or the evaluator (those are TASK-MONO-224+).
- D1/D4-D6 must remain inherited-unchanged from ADR-026 — only D2/D3 are fixed at this gate.
- midnight-wrap must be explicitly recorded as DEFERRED (not silently dropped) so the fast-follow is traceable.

# Failure Scenarios

- If the transition fixed D2/D3 differently from the user's choice (D2-A iam compose-AND + same-day schema), the execution roadmap would build the wrong pilot — AC-2/AC-4 pin the user's selection.
- If the ACCEPTED row omitted the user intent quote, the audit trail (Status Transition History) would lose the decision provenance — AC-4 requires it.
- If the transition reopened the inherited framework (D1/D4-D6), it would contradict the parent ADR-026 — AC-2 pins them unchanged.
