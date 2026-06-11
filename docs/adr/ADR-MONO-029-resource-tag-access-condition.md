# ADR-MONO-029 — `RESOURCE_TAG` Access Condition (3rd / final condition type under ADR-026's closed enum)

**Status:** ACCEPTED

**Date:** 2026-06-11

**Deciders:** platform (IAM axis)

**Parent / inherits:** **ADR-MONO-026** (axis ② 2단계 — access conditions); sibling of **ADR-MONO-028** (`TIME_WINDOW`). This ADR executes ADR-026 § D7.4 ("additional condition types … each its own ADR/task") for the **`RESOURCE_TAG`** member of the ADR-026 closed enum — the **last reserved type**, completing the closed set `{SOURCE_IP ✓, TIME_WINDOW ✓, RESOURCE_TAG}`. It inherits ADR-026's framework **unchanged** (closed enum, AND-only, restriction-only + fail-safe + net-zero, D3-B carrier) and decides only the options a *resource-attribute* condition raises: the **enforcement seam** and the **pilot resource + semantics**.

---

## 1. Context

ADR-026 fixed the closed enum and piloted `SOURCE_IP` (BE-351 + MONO-221 federation proof); ADR-028 added `TIME_WINDOW` (BE-352, the first AND-only multi-condition composition). Both `SOURCE_IP` and `TIME_WINDOW` evaluate **request context** — the source IP (a header) and the request time (the clock) — inputs available at the `RequiresPermissionAspect` seam BEFORE the target resource is loaded. `RESOURCE_TAG` is categorically different.

> contract `platform/access-conditions.md` § 1: `RESOURCE_TAG` | targeted resource's tags | the resource carries / lacks a required tag | **reserved (added when first piloted)**.

### 1.1 What is genuinely new here — the input is a *resource attribute*, not request context

`RESOURCE_TAG`'s input is **the targeted resource's tag set** (e.g. "is this operator tagged `protected`?"). That is **domain data about the mutation target**, which the request-context conditions never needed. Two consequences shape this ADR:

1. **Enforcement seam (the headline gate).** `RequiresPermissionAspect` runs *before* the controller/use-case loads the target resource — it has the request path + permission, not the resource's tags. So `RESOURCE_TAG` cannot be evaluated at the existing aspect seam without either (a) the aspect resolving + loading the resource to read its tags, or (b) moving this condition's evaluation to where the resource is already loaded. This is a real architectural fork (§ D2), absent for `SOURCE_IP`/`TIME_WINDOW`.

2. **Federation-testability (a property `TIME_WINDOW` lacked).** Because the input is **per-resource** (not a global like the server clock), a federation proof IS deterministic and net-zero-safe: seed one resource WITH the gating tag and one WITHOUT; a mutation on the tagged resource → 403, on the untagged resource → 200, and every other resource (and every other spec) is unaffected. This closes the gap ADR-028 § D6 recorded (a global-clock `TIME_WINDOW` could not be federation-proven without breaking suite-level net-zero; a per-resource tag can).

### 1.2 The raw material

- The composed-authorization stack + the `RequiresPermissionAspect` 4th gate (ADR-026/028).
- The shared evaluator pattern in `libs/java-security` (`SourceIpCondition` / `TimeWindowCondition`) — `ResourceTagCondition` is the 3rd sibling.
- iam admin resources mutated by the admin surface (`admin_operators` in admin_db, via `OperatorAdminController` create/role/status; tenants via `TenantAdminController`) — candidate pilot resources, none of which carry tags today (a tag model is the feature lift).

### 1.3 Why an ADR

Same as ADR-028: ADR-026 § D7.4 routes additional types to their own ADR/task, and `RESOURCE_TAG` raises two genuine gate decisions ADR-026 left open — the **enforcement seam** (new — the resource-attribute input forces it) and the **pilot resource + semantics** (deny-if-present vs require-tag). Staged PROPOSED → ACCEPTED, per the 019…028 pattern.

---

## 2. Decision

### D1 — Framework inherited from ADR-026 (NOT re-decided)

Closed enum (`RESOURCE_TAG` is the last existing member), AND-only, restriction-only, fail-safe, net-zero, D3-B carrier — all carried verbatim. Adding `RESOURCE_TAG` is a code change (a new evaluator class + tests), never runtime registration.

### D2 — Enforcement seam (FIXED at ACCEPTED 2026-06-11 = D2-A) — the new architectural question

Where `RESOURCE_TAG` is evaluated, given its input is the target resource's tags. **User-selected at the ACCEPTED gate: D2-A** — keep the single authorization decision site; the aspect resolves the target resource's tags via a domain `ResourceTagResolver` and composes the condition AND-only with `SOURCE_IP`/`TIME_WINDOW`.

- **D2-A (CHOSEN, ACCEPTED 2026-06-11): aspect + a `ResourceTagResolver`.** Keep the **single authorization decision site** (`RequiresPermissionAspect`, the contract § 4 invariant): the aspect resolves the target resource id from the request path and consults a small domain-provided `ResourceTagResolver` to fetch its tags, then evaluates `ResourceTagCondition` AND-only alongside `SOURCE_IP`/`TIME_WINDOW`. *Pro:* preserves the uniform "all conditions at one seam" model + the `isConfigured()/isSatisfiedBy()` enforcement shape; net-zero (no resolver / no config ⇒ no gate). *Con:* a pre-mutation tag lookup (one extra read) that the use-case may repeat — acceptable for a pilot (one endpoint, one resource type).
- **D2-B (alternative): domain/service-layer evaluation (post-load).** Evaluate `RESOURCE_TAG` inside the use-case AFTER it loads the target resource (the resource — and its tags — is already in hand; no extra read). Mirrors how ADR-025 data-scope is enforced at the domain layer. *Pro:* no double-load; natural for resource-heavy flows. *Con:* introduces a **second** authorization decision site (the aspect is no longer the only one), departing from the contract § 4 "single decision site" invariant — a meaningful divergence to weigh.
- **D2-C (rejected): the client supplies the resource's tags on the request.** Spoofable — the caller could assert a benign tag set to dodge a deny-if-present gate. Tags MUST come from trusted domain data, never the request body.

### D3 — Pilot resource + semantics (FIXED at ACCEPTED 2026-06-11)

Which resource carries a tag and how the tag gates. **User-selected at the gate: iam admin `operators` tagged `protected`, deny-if-present** (self-contained in admin_db; no cross-service tag read).

- **Pilot (CHOSEN): iam admin `operators` tagged `protected`** — self-contained in admin_db (`admin_operators`), mutated by `OperatorAdminController` (role/status change). A `protected` operator's role/status mutations are denied (a guardrail against accidental modification of a sensitive operator). Alternatives: tenants (cross-service — tags live in account_db) or accounts.
- **Semantics (PROPOSED = deny-if-present):** the condition denies when the resource **carries** a forbidden tag (e.g. `protected`). The mirror **require-tag** variant (allow only if the resource carries a required tag) is the same evaluator with inverted config; the pilot picks **deny-if-present** (ADR-026 § D4's "deny mutating actions on rows tagged confidential" example). A single negation lives *within* the type (ADR-026 § D1 — not a combinator).
- **Tag model lift:** the pilot adds a minimal `tags` set (or a single sensitivity label) to the chosen resource + a migration. This is the feature cost `RESOURCE_TAG` carries that `SOURCE_IP`/`TIME_WINDOW` (request-context) did not.

### D4 — Net-zero / opt-in (inherited)

No configured forbidden/required tag (and/or no resolver) ⟺ no gate ⟺ behaviour byte-identical. Only a resource carrying the configured gating tag is affected; every untagged resource and every other endpoint is unchanged.

### D5 — Still no full engine (inherited boundary)

A single configured tag check (deny-if-present or require) over a resource's tag set — no tag expression language, no boolean nesting, no cross-resource policy. Richer tag logic is out of scope (ADR-026 § D6).

### D6 — Staged execution (zero-regression), mirroring ADR-026/028

1. This ADR **PROPOSED → ACCEPTED** (doc-only; the gate fixes D2 seam + D3 pilot/semantics + the tag model).
2. **Shared evaluator** `com.example.security.access.ResourceTagCondition` (libs/java-security: `forbidden(tags)` / `required(tags)` factories, `isConfigured()`, `isSatisfiedBy(Set<String> resourceTags)` — fail-safe on null/blank tag set, net-zero when unconfigured) + **contract** `platform/access-conditions.md` § 1 flip `RESOURCE_TAG` reserved → implemented + § 4 adopter + the seam note. Unit tests.
3. **Pilot enforcement** (the chosen D2 + D3) — the tag model (migration + resource field) + the resolver (D2-A) or the service-layer check (D2-B) + config + IT (carries-tag → deny / lacks-tag → allow / unconfigured → net-zero / composition with `SOURCE_IP`/`TIME_WINDOW`).
4. **federation-e2e proof (deterministic, unlike `TIME_WINDOW`)** — seed a tagged + an untagged resource; a mutation on the tagged one → 403 `ACCESS_CONDITION_UNMET`, on the untagged one → 2xx; every other spec unaffected (per-resource net-zero). Reuses the MONO-221 harness.

---

## 3. Scope

### 3.1 Hard invariants (inherited from ADR-026 § 3.1)

Restriction-only · fail-safe · net-zero · closed enum (code-not-data, AND-only). Unchanged.

### 3.2 What this ADR does NOT do

- No change to the ADR-026 framework, `SOURCE_IP`/`TIME_WINDOW`, or ADR-025's data-scope.
- No tag expression language / boolean tag combinators / cross-resource policy (§ D5).
- No client-supplied tags (§ D2-C) — tags come from trusted domain data only.
- No producer / token-customizer change (the tag is a domain resource attribute, not a JWT claim — D3-B; the signed-claim D3-A carrier stays deferred).
- No additive / elevation (owned by ADR-020 / ADR-024).

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

0. **`TASK-MONO-225` (PROPOSED, DONE #1318)** — doc-only. Model = Opus.
1. **`TASK-MONO-226` (ACCEPTED transition, this)** — the gate fixed **D2 = D2-A** (aspect + `ResourceTagResolver`) + **D3** (iam admin operators tagged `protected`, deny-if-present). Doc-only. Model = Opus.
2. **`TASK-MONO-227`** — `ResourceTagCondition` evaluator (libs/java-security: `forbidden(tags)` / `required(tags)`, `isSatisfiedBy(Set<String> resourceTags)`, fail-safe + net-zero) + `platform/access-conditions.md` § 1 `RESOURCE_TAG` reserved → implemented + § 4 the resolver-seam note. Unit tests. Model = Opus (security-lib).
3. **`TASK-<iam>-BE-xxx`** — the pilot: a minimal `tags` model on `admin_operators` (migration) + a `ResourceTagResolver` (operator-path-aware) + `RequiresPermissionAspect` wiring (compose `RESOURCE_TAG` AND-only with `SOURCE_IP`/`TIME_WINDOW`) + config (the forbidden `protected` tag) + IT (tagged → deny / untagged → allow / unconfigured → net-zero / composition). Model = Opus (authorization enforcement).
4. **`TASK-MONO-2xx`** — federation-e2e proof (seed a `protected`-tagged + an untagged operator; a role/status mutation on the tagged one → 403 `ACCESS_CONDITION_UNMET`, on the untagged one → 2xx; every other spec unaffected). **Deterministic + net-zero-safe** (per-resource input). Model = Opus.

---

## 4. Alternatives Considered

- **A tag policy expression / boolean tag logic.** Rejected — the policy-engine slope ADR-026 § D6 closed; a single deny-if-present (or require) check is the whole type.
- **Client-supplied tags on the request (D2-C).** Rejected — spoofable; defeats the gate's purpose.
- **Per-operator signed-claim carrier (D3-A).** Deferred — inherits ADR-026's D3-B-first stance; a resource tag is domain data, not an operator-grant claim, so D3-B (domain attribute + guard-config) is the natural carrier.
- **Skip `RESOURCE_TAG` (leave the enum at 2 of 3).** Rejected — completing the closed enum demonstrates the full conditional vocabulary, and `RESOURCE_TAG` uniquely restores deterministic federation-provability (the property the global-clock `TIME_WINDOW` lacked).

---

## 5. Relationship to ADR-MONO-026 / 028

| | ADR-026 (`SOURCE_IP`) | ADR-028 (`TIME_WINDOW`) | **ADR-029 (`RESOURCE_TAG`, this)** |
|---|---|---|---|
| Input | request source IP (header) | request time (server clock) | **target resource's tags (domain attribute)** |
| Seam | aspect (request-context) | aspect (request-context) | **aspect+resolver OR domain-layer (needs the resource) — D2 gate** |
| Federation-provable | yes (per-request header) | no (global clock breaks net-zero) | **yes (per-resource — deterministic + net-zero-safe)** |
| Feature lift | none | none | **a tag model on the pilot resource** |

`RESOURCE_TAG` completes the closed enum: RBAC → tenant-scope → data-scope → **access condition(s)** (`SOURCE_IP` AND `TIME_WINDOW` AND `RESOURCE_TAG`), AND-only, restriction-only.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-06-11 | created PROPOSED | Executes ADR-026 § D7.4 for the last enum member `RESOURCE_TAG`; inherits ADR-026's framework unchanged; decides the gates a resource-attribute condition raises — **D2** enforcement seam (A: aspect + `ResourceTagResolver`, preserves the single decision site [chosen-PROPOSED] vs B: domain/service-layer post-load, no double-read but a 2nd decision site; C: client-supplied tags rejected as spoofable) and **D3** pilot resource + semantics (candidate: iam admin operators tagged `protected`, deny-if-present; vs require-tag; + the tag-model lift). D4-D5 inherited (net-zero; no full engine). D6 staged, and step 4 federation-e2e IS deterministic (per-resource input, unlike ADR-028's global clock). | "RESOURCE_TAG (3번째·마지막 조건타입)" — after `TIME_WINDOW` (ADR-028) closed and with the non-SCM ready queue drained, the user selected completing the closed condition enum with the final type. ADR-first per the established ADR-019…028 staged pattern. | #1318 (TASK-MONO-225) |
| 2026-06-11 | PROPOSED → ACCEPTED | D1, D4-D6 directions **finalised unchanged** from PROPOSED #1318 squash `d2770300` (framework inherited); **D2 finalised = D2-A** (aspect + `ResourceTagResolver` — the single authorization decision site is preserved; the aspect resolves the target resource's tags and composes `RESOURCE_TAG` AND-only with `SOURCE_IP`/`TIME_WINDOW`); **D3 finalised** = iam admin `operators` tagged `protected`, **deny-if-present** (self-contained in admin_db; a `protected` operator's role/status mutation is denied). Authorises the § 3.3 roadmap: `ResourceTagCondition` evaluator (`libs/java-security`) + contract reserved→implemented flip → the pilot (a `tags` model on `admin_operators` + `ResourceTagResolver` + aspect wiring + config) + IT → the deterministic federation-e2e proof. Same one-off Meta-policy category as the sibling ACCEPTED transitions (ADR-023/024/025/026/028). | "A: aspect + ResourceTagResolver" + "iam operators + protected (deny-if-present)" — the user fixed the seam = aspect+resolver and the pilot = iam operators / `protected` / deny-if-present at the gate and authorised ACCEPTED + execution. | #<this> (TASK-MONO-226) |
