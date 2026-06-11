# Access Conditions — closed-enum, restriction-only condition gates

**Status:** authoritative contract (ADR-MONO-026 ACCEPTED).
**Shared regulation** — project-agnostic; no service names or domain entities define the rule, only illustrate it.

This contract formalises the **access-condition** pattern — the **2단계** of axis ② (ADR-MONO-026), complementing the ABAC data-scope of 1단계 (`platform/abac-data-scope.md`, ADR-MONO-025). Where data-scope answers *over which slice of a tenant's data*, an access condition answers *under which circumstances a permission is live* — a deliberately-bounded **AWS-IAM-`Condition` miniature**.

It is the **4th orthogonal gate** in the authorization stack: RBAC permission (ADR-019/024) → tenant-scope → data-scope (ADR-025) → **access condition**. All four must pass; none substitutes for another.

The canonical evaluators live in `com.example.security.access` (`libs/java-security`); every domain MUST use them (or replicate their semantics exactly) so the rules below hold uniformly.

> **Not a policy engine.** This contract introduces NO policy language, NO runtime-registrable condition SPI, NO boolean combinators (OR/NOT nesting), and NO additive/elevation conditions (ADR-026 § D6). The condition vocabulary is a closed, code-defined enum; adding a type is a code change.

---

## 1. The closed condition enum

A condition is one of a **fixed, code-defined** set of types. Adding a type is a code change (a new evaluator class in `com.example.security.access` + tests), never a config/data change — this is the boundary that distinguishes 2단계 from a policy engine (ADR-026 § D1).

| Type | Input | Evaluates | Status |
|---|---|---|---|
| `SOURCE_IP` | request source IP | the IP is within an allowed CIDR set | **implemented** (`SourceIpCondition`) — iam pilot (ADR-026 § D4) |
| `TIME_WINDOW` | request time + zone | request-time within an allowed local time-of-day / day-of-week window | reserved (added when first piloted) |
| `RESOURCE_TAG` | targeted resource's tags | the resource carries / lacks a required tag | reserved (added when first piloted) |

**Combination is AND-only**: when more than one condition guards an action, all must hold. There is no OR/NOT nesting; a single negation is expressed *within* a type (e.g. a `RESOURCE_TAG` deny-if-present variant), never as a combinator.

---

## 2. Semantics (uniform across condition types)

Every access condition obeys three invariants:

| Invariant | Meaning |
|---|---|
| **Restriction-only** | A condition can only GATE (deny when unmet) an action that already passed RBAC + tenant-scope + data-scope. It **never grants**. Additive / elevation ("conditional grant", break-glass) is explicitly out of scope and is owned by ADR-020 (assume-tenant scoping) / ADR-024 (admin delegation), where a privilege is auditable at grant time. |
| **Fail-safe** | When a condition's required input is unavailable / unparseable / ambiguous, the condition is **UNMET → deny**, never allow. A configured-but-all-invalid policy fails closed (matches nothing), it does not fall open. |
| **Net-zero / opt-in** | When a condition is **not configured**, there is **no gate** — the endpoint behaves exactly as before access-conditioning. The gate only bites once a condition is configured. Every existing path is byte-identical until then. |

The enforcement shape (uniform):

```
if (condition.isConfigured() && !condition.isSatisfiedBy(requestInput)) {
    deny → 403 ACCESS_CONDITION_UNMET
}
```

`isConfigured()` is the net-zero short-circuit — a caller MUST check it before denying, so an unconfigured condition never denies. `isSatisfiedBy(...)` is the fail-safe primitive — it returns `false` on bad/blank input.

**Narrowing-only invariant:** a condition is composed with — never a substitute for — the RBAC permission, tenant-scope, and data-scope checks. It is consulted only AFTER those pass, and only to deny, never to grant.

---

## 3. Carrier — domain/endpoint guard-config (ADR-MONO-026 § D3-B)

A condition is attached to a **resource / endpoint policy in the consuming domain** (e.g. "the admin write endpoints require `SOURCE_IP ∈ corp-CIDR`"), configured domain-side. It is **NOT** carried on a JWT claim and requires **no producer / token-customizer / IAM change** — purely consumer-side, exactly as ADR-025 § D5 kept the producer untouched.

- The condition's parameters (e.g. the allowed CIDRs) come from the domain's own configuration (`@ConfigurationProperties` / `application.yml`), defaulting to **empty ⟺ unconfigured ⟺ net-zero**.
- The condition's runtime input (e.g. the source IP) comes from the **request context** at the enforcement seam — no new infrastructure.

> The signed-claim carrier (a per-operator `access_conditions` claim, ADR-026 § D3-A) is deferred/promotable; it would require a producer touch and is not part of this contract.

---

## 4. Per-domain adoption + the iam pilot

A domain adopts an access condition by: (a) reading its parameters from domain config (empty default → net-zero); (b) building the shared evaluator (`SourceIpCondition.fromAllowedCidrs(...)`); (c) at the enforcement seam, after RBAC/tenant/data-scope pass, denying with `403 ACCESS_CONDITION_UNMET` when `condition.isConfigured() && !condition.isSatisfiedBy(input)`; (d) otherwise proceeding. Adoption is **opt-in and net-zero** — an unconfigured domain behaves exactly as today.

Current adopters:

| Domain | Condition | Reach rule |
|---|---|---|
| iam (admin-service) | `SOURCE_IP` | admin **mutation** endpoints (the `@RequiresPermission`-gated POST/PUT/PATCH/DELETE surface) are denied when the request source IP is outside the configured corp-CIDR allowlist. Reads are not gated. Net-zero when no CIDR is configured (ADR-026 § D4, the first pilot). |

The iam pilot composes the condition as the 4th gate **inside the existing `RequiresPermissionAspect`**, after the permission check passes — the single authorization decision site. The source IP is taken from the request (gateway-forwarded `X-Forwarded-For`, falling back to the remote address); an unresolvable IP fails closed.

---

## 5. Where it is set

The condition parameters live in the consuming domain's configuration (e.g. iam admin-service's `application.yml` corp-CIDR list), not in IAM and not on the token. This contract adds **no new grant surface** and **no producer change** (§ 3, ADR-026 § D3-B) — the condition is a domain-side guard.

---

## 6. Out of scope (ADR-026 § D6)

- No full policy engine / policy language (the AWS IAM policy-document equivalent).
- No runtime-registrable condition SPI — the enum is closed (§ 1).
- No boolean combinators (OR/NOT nesting) — AND-only (§ 1).
- No additive / elevation conditions (conditional grant / break-glass) — owned by ADR-020 / ADR-024 (§ 2).
- No signed-claim carrier (ADR-026 § D3-A, deferred) — this contract is the domain/endpoint guard-config carrier only.

---

## References

- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the decision)
- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` + `platform/abac-data-scope.md` (axis ② 1단계 — the sibling ABAC data-scope this complements)
- `libs/java-security` `com.example.security.access.SourceIpCondition` (the canonical `SOURCE_IP` evaluator)
