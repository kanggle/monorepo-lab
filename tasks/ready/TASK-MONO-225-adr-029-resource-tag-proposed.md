# Task ID

TASK-MONO-225

# Title

ADR-MONO-029 (PROPOSED) — `RESOURCE_TAG` Access Condition (3rd / final condition type under ADR-026's closed enum). Author the committed PROPOSED ADR that pilots the last reserved member of the closed access-condition enum, completing `{SOURCE_IP ✓, TIME_WINDOW ✓, RESOURCE_TAG}`. Inherits ADR-026's framework unchanged and decides the options a *resource-attribute* condition raises (its input is the target resource's tags, not request context): **D2** enforcement seam (A: aspect + `ResourceTagResolver` preserving the single decision site — chosen-PROPOSED; vs B: domain/service-layer post-load — a 2nd decision site, no double-read; C: client-supplied tags rejected as spoofable) and **D3** pilot resource + semantics (candidate: iam admin operators tagged `protected`, deny-if-present; vs require-tag; + the tag-model lift). Notes that `RESOURCE_TAG` uniquely restores deterministic, net-zero-safe federation-provability (per-resource input, unlike ADR-028's global clock). Doc-only.

# Status

ready

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

- **executes**: ADR-MONO-026 § D7.4 ("additional condition types … each its own ADR/task") for the last enum member `RESOURCE_TAG` — ADR-026's framework inherited unchanged.
- **follows**: ADR-MONO-026 (`SOURCE_IP`) + ADR-MONO-028 (`TIME_WINDOW`) — both CLOSED; this completes the closed enum, ADR-first per the 019…028 staged pattern.
- **blocks**: the ADR § 3.3 roadmap (ACCEPTED → `ResourceTagCondition` evaluator + contract flip → pilot tag model + enforcement + IT → deterministic federation-e2e proof). None start until ACCEPTED.

# Goal

Record the `RESOURCE_TAG` pilot decision so the final access-condition type is added via the framework's blessed mechanism (a new shared evaluator + tests), while surfacing the genuinely-new architectural fork the resource-attribute input forces — the enforcement seam (the aspect, pre-resource, vs the domain layer, post-load) — and the pilot resource + semantics, for the ACCEPTED gate to fix. Reject a tag policy language and client-supplied (spoofable) tags.

# Scope

- NEW `docs/adr/ADR-MONO-029-resource-tag-access-condition.md` (Status PROPOSED) — D1 (inherited) + D2 (seam gate) + D3 (pilot/semantics gate) + D4-D6 + alternatives + relationship to ADR-026/028 + Status Transition History.
- This task file.

**Out of scope** (post-ACCEPTED): the `ResourceTagCondition` evaluator, the contract flip, the tag model (migration + resource field), the resolver / service-layer enforcement, the IT, the federation-e2e proof.

# Acceptance Criteria

- **AC-1** `ADR-MONO-029` exists with Status PROPOSED and explicitly **inherits ADR-026's framework unchanged**.
- **AC-2** The ADR names the new architectural question: `RESOURCE_TAG`'s input is the target resource's tags (a domain attribute), which the `RequiresPermissionAspect` seam lacks at evaluation time — forcing the **D2 enforcement-seam** gate (aspect+resolver vs domain-layer), absent for `SOURCE_IP`/`TIME_WINDOW`.
- **AC-3** The ADR decides only the open gates: D2 (seam) + D3 (pilot resource + deny-if-present vs require-tag + tag-model lift). D1/D4-D6 inherited.
- **AC-4** The ADR records that `RESOURCE_TAG` restores **deterministic, net-zero-safe federation-provability** (per-resource input), closing the gap ADR-028 § D6 noted for the global-clock `TIME_WINDOW`.
- **AC-5** The ADR rejects a tag policy/expression language (§ D5) and client-supplied tags (§ D2-C, spoofable); keeps the signed-claim carrier (D3-A) deferred.
- **AC-6** Status Transition History has the PROPOSED row with the user intent quote ("RESOURCE_TAG (3번째·마지막 조건타입)"). Doc-only — no code/contract/evaluator/migration in this PR.

# Related Specs

- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the framework + § D7.4) + `docs/adr/ADR-MONO-028-time-window-access-condition.md` (the sibling 2nd type)
- `platform/access-conditions.md` § 1 (the closed enum where `RESOURCE_TAG` is "reserved") + § 4 (the enforcement seam this may extend)

# Related Contracts

- none in this PR (the `platform/access-conditions.md` flip is a post-ACCEPTED deliverable)

# Edge Cases

- **Net-zero/opt-in** must hold: no configured gating tag (and/or no resolver) ⟺ unchanged; only a resource carrying the gating tag is affected.
- **Fail-safe**: an unresolvable / null tag set ⟹ the evaluator denies (for a require-tag) or is treated per the type's safe default — to be pinned in the evaluator task; the ADR states fail-safe as the invariant.
- **Spoofing**: tags MUST come from trusted domain data, never the request — D2-C is rejected for exactly this.
- **Seam divergence**: D2-B (domain-layer) departs from the contract § 4 "single authorization decision site" invariant — named so the gate weighs it against D2-A (aspect+resolver, single site, one extra read).
- The tag-model lift (a `tags`/sensitivity field on the pilot resource + migration) is the feature cost unique to `RESOURCE_TAG` — flagged but out of scope for this doc-only PROPOSED.

# Failure Scenarios

- If the ADR let the client supply tags, a deny-if-present gate would be trivially bypassed — D2-C rejection guards it.
- If the ADR re-decided the inherited framework (D1/D4-D6), it would contradict ADR-026 — AC-1/AC-3 pin inherit-unchanged.
- If the seam choice were hard-decided in PROPOSED, the ACCEPTED gate would lose its decision surface — D2/D3 are deliberately PROPOSED-as-options.
- If the ADR named a tag expression language, it would re-open the policy-engine scope ADR-026 § D6 closed — § D5 + Alternatives bound it to a single tag check.
