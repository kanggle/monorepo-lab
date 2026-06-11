# Task ID

TASK-MONO-226

# Title

ADR-MONO-029 (PROPOSED → ACCEPTED) — fix the `RESOURCE_TAG` pilot gates. Flip `ADR-MONO-029` Status to ACCEPTED, fixing **D2 = D2-A** (aspect + `ResourceTagResolver` — the single authorization decision site is preserved; the aspect resolves the target resource's tags and composes `RESOURCE_TAG` AND-only with `SOURCE_IP`/`TIME_WINDOW`) and **D3** (iam admin `operators` tagged `protected`, **deny-if-present** — self-contained in admin_db). Inherits ADR-026's framework unchanged. Authorises the § 3.3 execution roadmap. Doc-only.

# Status

done

> **완료 (2026-06-11)**: ACCEPTED 전환 PR #1320 (squash `340ac222`). 3차원 ✓ (MERGED / origin/main tip 일치 / 코드 all-skip, 0 failing). `ADR-MONO-029` Status=ACCEPTED — **D2=D2-A**(aspect + `ResourceTagResolver`=단일 결정지점 유지, RESOURCE_TAG을 SOURCE_IP/TIME_WINDOW와 AND 합성) + **D3**(iam admin operators `protected` 태그 deny-if-present, admin_db 자체완결). D1/D4-D6 inherit-unchanged. §3.3 로드맵 인가: MONO-227(evaluator+계약)→iam BE 태그모델+resolver+enforcement→**결정적 fed-e2e**. 다음=MONO-227. 분석=Opus 4.8.

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

- **transitions**: `ADR-MONO-029` PROPOSED (TASK-MONO-225, DONE #1318) → ACCEPTED. Same staged discipline as ADR-023/024/025/026/028's ACCEPTED transitions.
- **authorises**: the ADR § 3.3 roadmap — `TASK-MONO-227` (`ResourceTagCondition` evaluator + contract flip) → the iam pilot (tag model + resolver + aspect wiring) + IT → the deterministic federation-e2e proof. None start until this ACCEPTED main exists.
- **inherits**: ADR-MONO-026 framework — unchanged.

# Goal

Record the user's gate decision so the execution roadmap has a dependency-correct ACCEPTED base: the seam is the aspect (single decision site preserved) with a `ResourceTagResolver`, and the pilot is iam admin operators tagged `protected` with deny-if-present semantics.

# Scope

- Update `docs/adr/ADR-MONO-029-resource-tag-access-condition.md`: Status PROPOSED → ACCEPTED; D2 marked CHOSEN = D2-A; D3 marked FIXED (iam operators / `protected` / deny-if-present); § 3.3 roadmap task numbers filled (226 ACCEPTED, 227 evaluator+contract, iam BE enforcement, fed-e2e); Status Transition History ACCEPTED row with the user intent quote.
- This task file.

**Out of scope** (post-ACCEPTED): the `ResourceTagCondition` evaluator, the contract flip, the tag model + resolver + enforcement, the IT, the federation-e2e proof.

# Acceptance Criteria

- **AC-1** `ADR-MONO-029` Status = ACCEPTED.
- **AC-2** D2 fixed = D2-A (aspect + `ResourceTagResolver`, single decision site); D3 fixed = iam operators / `protected` / deny-if-present. D1/D4-D6 unchanged (inherited).
- **AC-3** § 3.3 roadmap names the concrete follow-up tasks (227 evaluator+contract → iam BE pilot → fed-e2e), dependency-rooted on this ACCEPTED main.
- **AC-4** Status Transition History has the PROPOSED → ACCEPTED row with the user intent quote ("A: aspect + ResourceTagResolver" + "iam operators + protected (deny-if-present)").
- **AC-5** Doc-only — no code/contract/evaluator/migration in this PR.

# Related Specs

- `docs/adr/ADR-MONO-029-resource-tag-access-condition.md` (the ADR being transitioned)
- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the parent framework) + `platform/access-conditions.md` § 1 (`RESOURCE_TAG` reserved → implemented by the post-ACCEPTED evaluator task) + § 4 (the single-decision-site invariant D2-A preserves)

# Related Contracts

- none in this PR (the contract flip is a post-ACCEPTED deliverable)

# Edge Cases

- The ACCEPTED transition is doc-only — it must NOT introduce code, the contract flip, the tag model, or the evaluator.
- D1/D4-D6 must remain inherited-unchanged from ADR-026 — only D2/D3 are fixed at this gate.
- D2-A preserves the contract § 4 "single authorization decision site" invariant (the aspect remains the only authz site) — recorded so the evaluator/enforcement tasks honour it (resolver consulted FROM the aspect, not a 2nd site).

# Failure Scenarios

- If the transition fixed D2/D3 differently from the user's choice, the execution roadmap would build the wrong pilot — AC-2/AC-4 pin the selection.
- If the ACCEPTED row omitted the user intent quote, the audit trail would lose provenance — AC-4 requires it.
- If the transition reopened the inherited framework, it would contradict ADR-026 — AC-2 pins D1/D4-D6 unchanged.
