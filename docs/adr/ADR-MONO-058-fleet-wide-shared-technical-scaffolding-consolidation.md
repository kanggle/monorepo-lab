# ADR-MONO-058 — consolidate fleet-wide technical scaffolding duplicated across all 8 projects (actor/JWT context, error envelope, pagination, security-chain assembly, `PublicPaths`, service-to-service token provider, dedupe/resilience adoption)

**Status:** PROPOSED
**Date:** 2026-07-29
**History:** PROPOSED 2026-07-29 (this record). **ACCEPT is a human gate — this record authorises no code.** Per this repo's ADR convention, acceptance requires the owner's exact-form instruction (e.g. `"ADR-MONO-058 ACCEPTED"`); a general "proceed" does not accept it, and the authoring agent must not self-accept.
**Decision driver:** an 8-project parallel commonality/naming audit run 2026-07-29 (one agent per project, grepping/diffing each project's services against `platform/shared-library-policy.md`'s Decision Rule). Every one of the 8 projects independently reinvented the same handful of technical wheels, usually without importing the `libs/` module that already ships the closest equivalent.
**Related:** `platform/shared-library-policy.md` (the Decision Rule and Review/Change Rule gating this ADR), `ADR-MONO-048`/`ADR-MONO-049` (the prior, narrower consolidation of `AllowedIssuersValidator`/`TenantClaimValidator`/`TenantClaimEnforcer` into `libs/java-security`/`libs/java-security-servlet` — the precedent this ADR extends), `TASK-MONO-491` (the companion docs-only naming-convention reconciliation filed alongside this ADR, already in review, no ADR gate needed since it renames nothing).

> **Why an ADR, not just a task.** Every candidate below is shared-library content crossing project boundaries — `platform/shared-library-policy.md § Review Rule` requires "any new shared library or major shared-library expansion" to be reviewed against the policy before implementation, and `§ Change Rule` requires a human decision before any `libs/` touch of this shape. None of it can land as a project-local task per `CLAUDE.md`'s shared-vs-project boundary. This ADR exists to make the target-module and scope calls once, centrally, rather than have each project's implementer guess independently (which is exactly the failure mode `ADR-MONO-048`/`049` already documented once for a narrower slice of the same problem).

---

## 1. Context

### 1.1 The scale of the finding

The audit surveyed all 8 projects' `apps/` trees plus `libs/`. Selected headline counts (full evidence lives in the audit's per-project reports, not reproduced here — this ADR states conclusions, not the grep transcripts):

| Pattern | Confirmed in | Approx. copies |
|---|---|---|
| Actor/JWT-claim extraction (`ActorContext`, `ActorContextResolver`, `ActorContextJwtAuthenticationConverter`, `@CurrentActor`) | finance, erp, fan, scm, iam | ~15+ services, 5 projects |
| Error envelope + generic exception-handler tail (`ApiErrorBody`/`ApiEnvelope` + non-domain half of `GlobalExceptionHandler`) | all 8 projects | ~30+ classes |
| Pagination carrier (re-declaring `libs/java-common.PageResult`/`PageQuery`) | finance, erp, scm, wms, ecommerce, fan | ~15 hand-rolled shapes |
| Security-chain assembly (`ServiceLevelOAuth2Config` + generic half of `SecurityConfig`) | scm, erp, wms, fan | ~17 copies |
| `PublicPaths` (actuator/swagger allow-list mechanism) | scm, erp, fan, wms | ~16 copies |
| `IamClientCredentialsTokenProvider` (OAuth2 client-credentials acquisition) | iam, ecommerce, fan | 7 copies, 3 projects |
| `EventDedupePort` hand-rolled instead of adopted | wms, erp, ecommerce, fan | ~9 services |
| Outbound HTTP client bootstrap (`ResilienceClientFactory` un-adopted) | console-bff, iam, ecommerce | ~14 hand-rolls, several with **no read timeout at all** |
| `TenantContext`/`TenantContextFilter` (ecommerce only — the rest of the fleet already consolidated this once) | ecommerce | 10 copies |

### 1.2 Why this happened

`ADR-MONO-048`/`ADR-MONO-049` already fixed the narrowest, oldest instance of this exact problem (`AllowedIssuersValidator`/`TenantClaimValidator`/`TenantClaimEnforcer`, 49 copies → 0) and documented the mechanism plainly: "duplication was the symptom; the disease was that none of these properties were being watched." Each project since then independently built the *next* layer of security/pagination/error-handling scaffolding on top of the newly-shared validators, and each one re-duplicated it across its own services, because there was no shared home for that next layer either. `libs/java-web` and `libs/java-web-servlet` already contain real, usable pieces of that next layer (`ErrorResponse`, `CommonGlobalExceptionHandler`, `BodyHashUtil`, an idempotency filter/store) — the audit found these libraries declared as dependencies in dozens of services and imported by almost none of them.

### 1.3 What this ADR is and is not

This ADR decides **where each pattern's technical core belongs** and **what stays per-service**, following `shared-library-policy.md`'s existing Decision Rule and Ownership Rule — it does not itself perform any extraction. Per-project adoption is separate, sequenced implementation work (§ 6) that only starts after ACCEPT.

---

## 2. Decision (proposed)

### D1 — Actor/JWT-claim extraction cluster → `libs/java-security-servlet`

`ActorContextResolver` + `ActorContextJwtAuthenticationConverter` (+ the `@CurrentActor`/`CurrentActorArgumentResolver` request-scoping mechanism where present) are, in every copy examined, framework/JWT-claim mechanics with zero domain content: lift `sub`/`tenant_id`, normalize `roles`-or-`role` (array or delimited string) into `ROLE_`-prefixed authorities, expose a `JwtAuthenticationToken` subclass. Promote:

- The **mechanism** — claim-lifting, authority-prefixing, the resolver/converter classes, the `@CurrentActor` annotation + argument-resolver plumbing.
- **Not** the `ActorContext` record's per-service convenience methods (`isAdmin()`, `isOperator()`, `canReadErp()`, `owns()`, role-set literals like `ERP_OPERATOR`/`FAN_OPERATOR`) — these are project-specific authorization policy (Ownership Rule) and must stay per-service, parameterized into the shared record as plain `Set<String> roles`, not baked in.
- Target module: `libs/java-security-servlet` (servlet-only; a reactive gateway already gets the equivalent from `libs/java-gateway` per the existing reactive/servlet split — do not cross that boundary, `ADR-MONO-048 § D1`).

### D2 — Error envelope + generic exception-handler tail → adopt existing `libs/java-web` / `libs/java-web-servlet`, do not build new types

`libs/java-web.ErrorResponse` and `libs/java-web-servlet.CommonGlobalExceptionHandler` already exist and already cover the non-domain arms every project re-implements (`NoResourceFound`, `HttpMediaTypeNotSupported`, `HttpRequestMethodNotSupported`, the catch-all 500). This is **not a new extraction** — it is an adoption gap. Two blockers must be resolved before mechanical adoption, not worked around per-service:

1. **Wire-shape conflict.** `ErrorResponse` is `{code, message, timestamp}`. Several services' `ApiErrorBody` carries a 4th field, `details: Map<String,Object>` (and one — `finance-platform`'s — flips `timestamp` between `String` and `Instant` across its own two services). Before any service adopts the shared type, decide: does `ErrorResponse` gain an optional `details` field (widening it for every consumer), or does a service that needs `details` compose its own envelope around the shared base? This ADR does not decide that trade-off — it is deferred to whoever implements D2, as a small design note in that implementation's own task, because it's a wire-format call best made with the actual consumers in front of the implementer.
2. **Status-code conflict.** At least one project (fan-platform) maps validation failures to `422`; `CommonGlobalExceptionHandler` maps the equivalent case to `400`. The shared handler must expose this as a configurable/overridable mapping, not force one status on every adopter.

### D3 — Pagination carrier → adopt existing `libs/java-common.PageResult`/`PageQuery`

No new type. This is purely an adoption gap — the lib type is frequently already on the consuming service's classpath (used for `UuidV7`) and simply never imported for paging. Wire-shape divergences already exist in the wild (`content` vs `items` field naming; some hand-rolled shapes omit `totalPages`) — services adopting the shared type should treat this as an opportunity to fix those inconsistencies, not preserve them by wrapping the shared type in another local record.

### D4 — Security-chain assembly (`ServiceLevelOAuth2Config` + generic `SecurityConfig` tail) → `libs/java-security-servlet`

The `NimbusJwtDecoder` + `AllowedIssuersValidator`/`TenantClaimValidator` chain-assembly wiring (not the validators themselves — already shared per `ADR-MONO-049`) is near-byte-identical across every servlet service examined. Promote the assembly as a builder/factory the service configures with its own property keys and exempt-path predicate — **not** as an auto-configuration that installs itself unconditionally (see `shared-library-policy.md § No context-wide annotations` — this must remain an opt-in call, not a component-scanned bean).

### D5 — `PublicPaths` → a shared value type, not a shared dataset

The `EXACT`/`PREFIXES` set + `isPublic(String)`/`isPublic(HttpServletRequest)` mechanism is identical everywhere; the actual path lists are service policy (each service decides what's public) and must not move. Promote a `PublicPathSet`-shaped value object to `libs/java-security-servlet`; each service continues to supply its own data to it.

### D6 — `IamClientCredentialsTokenProvider` → `libs/java-security`

7 copies, 3 projects, already diverged in ways that matter: one copy (`fan-platform/community-service`) generalized the hardcoded `scope` into a constructor parameter (strictly better — promote *that* shape, not an older one); one copy (`ecommerce/batch-worker`) carries a UTF-8-encoding fix (RFC 7617 requires UTF-8 for HTTP Basic credentials) and explicit connect/read timeouts that a sibling copy (`ecommerce/product-service`) and several iam copies still lack. This is a live defect distributed unevenly across copies, not just duplication — promoting one canonical, already-fixed version closes it everywhere at once instead of requiring N separate defect-fix tasks.

### D7 — `EventDedupePort` / `ResilienceClientFactory` adoption → no new code, close the adoption gap

Both already exist in `libs/java-messaging` and `libs/java-common` respectively. `.claude/skills/messaging/idempotent-consumer/SKILL.md` already instructs "use the shared port — do not hand-roll" and is being ignored by roughly 9 services. Several `ResilienceClientFactory` non-adopters have **zero read timeout** on outbound HTTP calls — this is a live production-risk gap (a hung downstream call blocks the calling thread indefinitely), not a style preference. No ADR content needed here beyond stating the adoption is expected — implementation is straightforward per-service substitution work, sequenced in § 6.

### D8 — ecommerce's `TenantContext`/`TenantContextFilter` (10 copies) → adopt the already-shared `libs/java-security-servlet.TenantClaimEnforcer`

The rest of the fleet already went through this exact consolidation once (`ADR-MONO-049`, replacing "13 hand-maintained copies" per that library's own javadoc). `ecommerce-microservices-platform` is the one project that never adopted it and instead grew to 10 local copies — one of which (`settlement-service`) has already silently diverged (`.trim()`s the tenant id where the other 9 don't; tracked as a separate live-defect fix, `TASK-BE-557`, filed alongside this ADR). This is not a new decision — it is closing ecommerce's gap against a decision already made.

---

## 3. Options considered

| Option | Verdict |
|---|---|
| **A. Consolidate per D1–D8 above, adoption-first where a lib already exists, new-extraction only where none does** | **Chosen** — matches the Decision Rule, minimizes new shared-library surface, and directly closes several live defects (D6, D7's timeout gap, D8's settlement drift) as a side effect |
| B. Leave every project's copy in place; document the duplication and move on | Rejected — this is the status quo that already produced at least 3 confirmed live defects (idempotency-hash canonicalization in finance, the UTF-8/timeout split in D6, the settlement `.trim()` divergence in D8) hiding inside near-identical copies. Duplication at this scale is not neutral; it is actively producing the "same defect fixed in one copy, not its siblings" pattern this repo has hit before (`ADR-MONO-049`'s own history, `FailOpenRateLimiter` fix reaching 3 of 4 copies and silently missing wms). |
| C. One giant `libs/java-platform-servlet` mega-module bundling everything in D1–D5 | Rejected — `shared-library-policy.md`'s existing reactive/servlet split (`java-web` vs `java-web-servlet`, `java-gateway`) is load-bearing, not stylistic; a mega-module would re-create the exact classpath-leak risk that split was created to prevent. Each pattern goes to the most specific existing module that already fits it. |
| D. New shared library specifically for D1 (actor/JWT context) | Considered, rejected in favor of extending `libs/java-security-servlet` — that module is already the servlet-side home for JWT/tenant claim mechanics (`TenantClaimValidator`, `TenantClaimEnforcer`); actor-context extraction is the same category of mechanics, not a new category. |

---

## 4. Consequences

**Positive**
- Closes 3+ confirmed live defects as a direct side effect of the consolidation (D6's UTF-8/timeout gap, D7's missing-timeout risk, D8's settlement-service tenant-id drift) rather than requiring separate fix-tasks per copy.
- Estimated LOC removed across the fleet, per the audit's own per-project estimates, is in the low thousands — a meaningful reduction in the surface any future security/pagination/error-handling defect can hide in.
- Establishes, once, the target module for each pattern — future services adding the same scaffolding have somewhere correct to reach for it instead of writing copy #16.

**Negative / risks**
- This is a large, multi-project, multi-PR effort. `shared-library-policy.md § Change Rule` correctly gates it behind this ADR rather than letting it proceed piecemeal, but that also means nothing in § 2 can start until ACCEPT.
- D1 and D4 both touch authentication/authorization-adjacent code across every servlet service in the fleet — the highest-risk category of change in this repo. Each per-project adoption must be its own task with its own test verification; this ADR explicitly does not authorize a single mega-PR touching all 5+ projects at once (see § 6).
- D2's wire-shape and status-code conflicts (details field, 400 vs 422) are real per-project product decisions hiding inside what looks like pure technical duplication — rushing past them to "just adopt the lib" would silently change API contracts for existing clients. Each adopting service's task must treat this as a contract decision, not a mechanical swap.

---

## 5. What acceptance binds

The PROPOSED record authorizes no code. On owner ACCEPT (exact-form `"ADR-MONO-058 ACCEPTED"`), the bound scope is exactly § 2 (D1–D8) — target modules and the promote-mechanism-not-policy boundary stated in each. It does not bind:
- Which project/service adopts first, or in what order (§ 6 is a suggested sequence, not a bound scope).
- The specific resolution of D2's `details`-field / status-code questions (left to that implementation's own task).
- Any work beyond the 8 patterns in § 2 that a future audit might surface.

---

## 6. Suggested implementation sequence (non-binding, for whoever picks this up post-ACCEPT)

Each item below is its own task, its own PR, in its own project, following this repo's ordinary task-driven workflow (`CLAUDE.md`) — this ADR does not authorize a single cross-project mega-PR.

1. **D6** (`IamClientCredentialsTokenProvider`) — smallest, closes a live defect, touches 3 projects but each adoption is a small per-service swap. Root task to promote the class, then 7 per-service adoption tasks (or fewer if some are bundled per-project).
2. **D7 adoption** (`EventDedupePort`, `ResilienceClientFactory`) — no new shared code, pure per-service substitution; can proceed in parallel with everything else once ACCEPT lands, one task per adopting service.
3. **D8** (ecommerce `TenantContext` → `TenantClaimEnforcer`) — single project, precedent already proven fleet-wide; the `settlement-service` `.trim()` defect (`TASK-BE-557`) should land before or alongside this so the migration doesn't have to reconcile the divergence mid-flight.
4. **D3** (pagination adoption) — no new shared code, per-service substitution; do this per-project alongside whichever other work touches that service, to amortize the review cost.
5. **D5** (`PublicPaths` value type) — small, low-risk, no auth-path behavior change.
6. **D2** (error envelope / exception-handler adoption) — requires the `details`-field and status-code design decisions per § 4 before any service adopts; do this after D1/D4 land so the shared auth-error paths and the shared generic-error paths are designed together, not twice.
7. **D1** (actor/JWT-claim cluster) and **D4** (security-chain assembly) — highest risk, do these last per project once the team has practice from D3/D5/D6/D7/D8, and do them as **one task per project** (not one task per service) so a project's own `ActorContext` role-set policy is threaded through consistently in one pass rather than five separate PRs that could each thread it slightly differently.
