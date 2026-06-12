# TASK-BE-354 — Enforce the RESOURCE_TAG **require** mode (complete the condition's mode surface)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (authorization enforcement — security-sensitive)

---

## Goal

Wire the second RESOURCE_TAG enforcement mode — **require** (deny-if-absent) — into iam admin-service, completing the condition's mode surface. The shared `com.example.security.access.ResourceTagCondition` already implements both `forbidden(...)` (deny-if-present, enforced by TASK-BE-353) and `required(...)` (allow-only-when-all-tags-present), but admin-service only wires the `forbidden` mode. This task closes that gap so an operator can require, e.g., that only operators tagged `certified` may have their roles changed.

To carry both modes at the single authorization decision site, generalize `RequiresPermissionAspect`'s RESOURCE_TAG gate from a **single** `ResourceTagCondition` to **all configured** `ResourceTagCondition` beans, composed **AND-only** (the existing access-condition composition invariant). Each is net-zero when unconfigured; the resolver is still consulted once (single decision site, ADR-029 § D2-A).

**Scope note:** this completes the RESOURCE_TAG **mode** axis (forbidden + require) on the existing **operator** resource. Extending RESOURCE_TAG to additional **resources** (tenants / accounts) is a separate, architecturally heavier effort (those entities are owned by account-service, not admin-service — a cross-service tag resolution) and is explicitly out of scope here.

## Scope

**In scope (iam admin-service):**
1. `RequiresPermissionAspect.anyConditionUnmet` — replace the single `resourceTagConditionProvider.getIfAvailable()` consumption with `resourceTagConditionProvider.orderedStream().filter(isConfigured)` and evaluate the resolved tags against **every** configured `ResourceTagCondition` (AND-only: any unsatisfied → deny). The resolver is still consulted once.
2. `AdminAccessConditionProperties.ResourceTag` — add a null-safe `required` list (default empty ⇒ net-zero), alongside the existing `forbidden`.
3. `AccessConditionConfig` — add a second `ResourceTagCondition` bean `requiredResourceTagCondition` built from `ResourceTagCondition.required(props.getResourceTag().getRequired())`. Keep the existing `forbidden` bean unchanged. (Two same-type beans are consumed only via the aspect's `ObjectProvider.orderedStream()`; no by-name or single-type injection exists.)
4. `application.yml` — document the new `admin.access.resource-tag.required: ${ADMIN_ACCESS_RESOURCE_TAG_REQUIRED:}` (empty default = net-zero).
5. `rbac.md` (iam project doc) — extend the RESOURCE_TAG access-condition subsection to record that both modes (deny-if-present + require) are now enforced, AND-only.
6. Slice tests proving the require mode + AND composition.

**Out of scope:**
- The shared `ResourceTagCondition` lib (`required()` already exists — no change).
- The shared `platform/access-conditions.md` contract (§ 1 already documents both modes as part of the type — consumer wiring is not a contract change).
- New resources (tenants / accounts) — cross-service, separate task.
- Any producer / JWT / token change (D3-B guard-config only).
- A federation-e2e proof — RESOURCE_TAG is already federation-proven for the type (MONO-228); the new mode is deterministically slice- + Integration-provable. (Optional follow-on.)

## Acceptance Criteria

- **AC-1** — With `admin.access.resource-tag.required=[certified]` configured, an RBAC-granted admin mutation whose target resource (resolved by the `ResourceTagResolver`) does NOT carry `certified` is denied `403 ACCESS_CONDITION_UNMET` (DENIED audit row written, mutation not executed). A target carrying `certified` proceeds.
- **AC-2 (AND composition)** — With BOTH `forbidden=[protected]` AND `required=[certified]` configured: a target tagged `{certified}` → allowed; `{certified, protected}` → denied (forbidden fails); `{}` (untagged) → denied (require fails). Any one unsatisfied condition denies.
- **AC-3 (net-zero)** — With `required` empty (default), behaviour is byte-identical to today (the existing deny-if-present-only enforcement). The existing `AdminResourceTagConditionEnforcementTest` (single forbidden bean) passes unchanged. A request that targets no resolvable resource (resolver returns `Optional.empty()`) is skipped regardless of configured modes.
- **AC-4 (fail-safe preserved)** — A `null` resolved tag set still denies (unresolved tags fail closed); an empty set is allowed under forbidden but denied under require (no required tag present).
- **AC-5** — `:admin-service:check` BUILD SUCCESSFUL (the slice tests exercise the aspect composition); CI "Integration (iam, Testcontainers)" GREEN (no regression to the SOURCE_IP / TIME_WINDOW / forbidden-tag enforcement or the broader admin surface).

## Related Specs

- `projects/iam-platform/apps/admin-service/specs/.../rbac.md` (access-condition subsection — the doc updated here)
- `projects/iam-platform/apps/admin-service/specs/.../architecture.md` (authorization model)

## Related Contracts

- `platform/access-conditions.md` § 1 (RESOURCE_TAG type — both modes already defined) + § 2 (restriction-only / fail-safe / net-zero invariants) + § 4 (single decision site).
- `docs/adr/ADR-MONO-029-resource-tag-access-condition.md` (the type decision).

## Edge Cases

- **Two same-type beans** — `AccessConditionConfig` will declare two `ResourceTagCondition` beans (forbidden + required). Verify nothing injects `ResourceTagCondition` by single type / by name (only the aspect's `ObjectProvider.orderedStream()` consumes them). The existing enforcement slice test defines ONE such bean via `@TestConfiguration` — `orderedStream()` over a single bean must still work.
- **Require + untagged** — `ResourceTagCondition.required(...).isSatisfiedBy(emptySet)` returns `false` (no required tag present) → deny. This is the intended deny-if-absent semantic, distinct from forbidden's allow-untagged. Assert both in tests.
- **Ordering irrelevance** — AND composition is order-independent; the aspect must deny if ANY configured condition is unsatisfied regardless of bean order.
- **`orderedStream().filter(isConfigured)`** — an unconfigured (empty-list) condition is filtered out, so the loop only evaluates active modes; with both empty the list is empty and the gate is skipped (net-zero).

## Failure Scenarios

- **F1 — silently dropping the forbidden mode** — if the generalization mis-handled multiple beans (e.g. `getIfAvailable()` throwing `NoUniqueBeanDefinitionException` once two beans exist), the existing deny-if-present enforcement (and the MONO-228 fed-e2e behaviour) would break. Guarded by AC-3 (existing test passes) + `orderedStream()` (no uniqueness requirement) + CI Integration.
- **F2 — require mode failing open** — if a missing-required-tag target were allowed, the governance control is void. Guarded by AC-1/AC-4 + slice assertions on the empty-set/null cases.
- **F3 — net-zero regression** — if the empty `required` default somehow configured a gate, every untagged operator mutation would be denied. Guarded by AC-3 (empty default = net-zero) + the existing slice/Integration suites.
