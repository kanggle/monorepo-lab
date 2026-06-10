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

`AbacDataScope` classifies the parsed token set into exactly three cases:

| Case | Token set | Meaning | Rule |
|---|---|---|---|
| **Unrestricted** | contains `"*"` | platform / unscoped | no data filter — the operator sees everything (within their RBAC + tenant scope). |
| **Scoped** | non-empty, no `"*"` | deliberately scoped | the domain filters to rows **reachable from** those tokens; **deny-by-default** for anything outside. |
| **Empty / absent** | no tokens | fail-closed | **deny** — NOT "allow all". `isUnrestricted()` is `false`; `allows(x)` is `false` for every `x`. |

**Net-zero:** the producer maps an UNSCOPED assignment (`operator_tenant_assignment.org_scope` NULL) to the claim `["*"]`. So every operator who has not been deliberately scoped carries `["*"]` ⇒ unrestricted ⇒ identical behaviour to before data-scoping. The empty/absent case is therefore a defensive fail-closed path (a correctly minted token always carries at least `["*"]`), never the net-zero default.

**Narrowing-only invariant:** data-scope can only *reduce* what an already-authorised operator reaches. It is composed with — never a substitute for — the RBAC permission check and the tenant-scope check. All three must pass. Data-scope MUST NOT be consulted to *grant* access.

---

## 3. Per-domain interpretation

The scope tokens mean whatever the owning domain decides; each domain documents its mapping. Current adopters:

| Domain | Token meaning | Reach rule |
|---|---|---|
| erp | department **subtree-root** ids | a target is in-scope iff it equals, or is a descendant of, a scoped root (ancestor-chain walk). |
| wms | warehouse ids | a row is in-scope iff its warehouse (or the warehouse of its zone/location) is a scoped id (ADR-025 D3, first extension). |
| finance | accounting-unit ids | *(future)* a row is in-scope iff its accounting unit is a scoped id. |

A new domain adopts data-scope by: (a) reading the claim via `AbacDataScope.fromClaimValues(jwt.getClaim("data_scope"), jwt.getClaim("org_scope"))`; (b) on `isUnrestricted()` → no filter; (c) else filter its query/results so only rows reachable from `tokens()` are returned (deny-by-default); (d) treating empty as deny (fail-closed). Adoption is **opt-in and net-zero** — a domain that has not adopted data-scope behaves exactly as today.

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
