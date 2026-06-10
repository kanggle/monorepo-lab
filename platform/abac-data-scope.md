# ABAC Data-Scope — cross-domain attribute-based data visibility

**Status:** authoritative contract (ADR-MONO-025 ACCEPTED).
**Shared regulation** — project-agnostic; no service names or domain entities define the rule, only illustrate it.

This contract formalises the **attribute-based data-scope** pattern first built one-off for erp (ADR-MONO-020 § D3 amendment) into the reusable convention every domain follows when it narrows an operator's *data visibility* by a signed attribute. It is the ABAC (attribute-based) complement to the RBAC axes (ADR-019/020/021/024): RBAC answers *what may this operator do*; data-scope answers *over which slice of a tenant's data*.

The canonical reader is `com.example.security.jwt.AbacDataScope` (`libs/java-security`); every domain MUST use it (or replicate its semantics exactly) so the rules below hold uniformly.

---

## 1. The claim

A domain-facing (assume-tenant) token MAY carry a **data-scope** claim: an array of **opaque scope tokens**.

- **Canonical name:** `data_scope`.
- **Legacy alias:** `org_scope` (the original erp name). Consumers **dual-read** `data_scope` then `org_scope` and union them, so the producer never has to migrate (ADR-025 D5 — the producer currently emits `org_scope`).
- **Shape:** a JSON array of strings, or a comma/whitespace-delimited string. Tokens are trimmed; blanks dropped.
- **Opacity:** the producer copies the tokens verbatim from `operator_tenant_assignment.org_scope`. Neither the producer nor IAM interprets them — only the owning **domain** does (§ 3).

---

## 2. Semantics (uniform across domains)

`AbacDataScope` classifies the parsed token set into three cases. The **data-scope filter applies only when the operator is _deliberately scoped_** (`!isEmpty() && !isUnrestricted()`):

| Case | Token set | `isUnrestricted()` | Domain action |
|---|---|---|---|
| **Unrestricted** | contains `"*"` | `true` | no filter — sees everything (within RBAC + tenant scope). |
| **Deliberately scoped** | non-empty, no `"*"` | `false` | filter to rows **reachable from** those tokens; **deny-by-default** outside. |
| **Unscoped (empty / absent)** | no tokens | `false` | **no filter (net-zero)** — the operator has not been scoped, so behaves as before data-scoping. |

**Why empty = net-zero (not fail-closed):** the `org_scope` claim is injected ONLY on the assume-tenant operator token, and the producer maps an UNSCOPED assignment (`operator_tenant_assignment.org_scope` NULL) to `["*"]`. A **base authorization_code token** and a **client_credentials machine token** carry NO data-scope claim at all — those legitimately reach a domain (e.g. a platform `tenant_id='*'` operator) and MUST keep working unchanged. So a domain's **data-scope filter treats empty/absent as unrestricted** (filter iff deliberately scoped) — the only net-zero-safe default for an opt-in feature.

> `AbacDataScope.allows(token)` is a strict per-token primitive that returns `false` on empty (it answers "is this exact token listed"). A domain calls it ONLY inside the deliberately-scoped branch — never to decide the net-zero default. A domain MAY additionally **fail-closed** for a single high-security *targeted* decision when it can guarantee real operators always carry a scope (erp does this for department-targeted writes); that is a domain-local hardening, not the default for the visibility filter.

**Narrowing-only invariant:** data-scope can only *reduce* what an already-authorised operator reaches. It is composed with — never a substitute for — the RBAC permission check and the tenant-scope check. All three must pass. Data-scope MUST NOT be consulted to *grant* access.

---

## 3. Per-domain interpretation

The scope tokens mean whatever the owning domain decides; each domain documents its mapping. Current adopters:

| Domain | Token meaning | Reach rule |
|---|---|---|
| erp | department **subtree-root** ids | a target is in-scope iff it equals, or is a descendant of, a scoped root (ancestor-chain walk). |
| wms | warehouse ids | a row is in-scope iff its warehouse (or the warehouse of its zone/location) is a scoped id (ADR-025 D3, first extension). |
| finance | accounting-unit ids | *(future)* a row is in-scope iff its accounting unit is a scoped id. |

A new domain adopts data-scope by: (a) reading the claim via `AbacDataScope.fromClaimValues(jwt.getClaim("data_scope"), jwt.getClaim("org_scope"))`; (b) computing `restricted = !scope.isEmpty() && !scope.isUnrestricted()`; (c) when `restricted` → filter its query/results so only rows reachable from `scope.tokens()` are returned (deny-by-default outside, e.g. via `scope.allows(rowToken)`); (d) otherwise (unrestricted OR unscoped) → **no filter (net-zero)**. Adoption is **opt-in and net-zero** — a domain that has not adopted data-scope, or an operator that has not been deliberately scoped, behaves exactly as today.

wms (ADR-025 step 2) is the first adopter, enforced on both the single-resource read and the collection:

- `WarehouseController.getById` denies (403 `DATA_SCOPE_FORBIDDEN`) a deliberately-scoped operator targeting a warehouse whose code is outside its scope.
- `WarehouseController.list` (`GET /api/v1/master/warehouses`) confines a deliberately-scoped operator's page — and its `totalElements`/`totalPages` — to in-scope warehouse codes via a DB-side `WHERE … IN (:codes)` filter (TASK-BE-349), so a scoped operator cannot enumerate out-of-scope warehouses after being 403'd on `getById`. The net-zero path runs the unchanged query (no `IN`).

In both cases unrestricted (`"*"`) and unscoped (empty/absent) operators are unaffected.

---

## 4. Where it is set

The scope lives on `operator_tenant_assignment.org_scope` and is managed by the existing admin surface (`PATCH /api/admin/operators/{id}/assignments/{tenant}/org-scope`, TASK-BE-339). It is resolved at the assume-tenant exchange and signed into the domain-facing token. This contract does NOT add a new grant surface — it generalises the *reach* of the existing one.

---

## 5. Out of scope (ADR-025 § D6)

Conditional policy — attaching a **condition** (time-of-day / source-IP / resource-tag) to a role grant (an AWS-IAM-Condition miniature) — is the deferred 2단계 and is **not** part of this contract. There is no policy language and no condition-evaluation engine. Data-scope is a single signed attribute, nothing more.

---

## References

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` (the decision)
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` § D3 (the erp origin)
- `libs/java-security` `com.example.security.jwt.AbacDataScope` (the canonical reader)
