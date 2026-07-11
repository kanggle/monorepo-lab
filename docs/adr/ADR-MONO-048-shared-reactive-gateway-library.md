# ADR-MONO-048 — `libs/java-gateway`: a shared **reactive** (WebFlux) gateway library, extracted from four copy-pasted edges whose divergence had already started costing security fixes

**Status:** ACCEPTED

**Date:** 2026-07-11 (PROPOSED, PR #2411) → **ACCEPTED 2026-07-11** — user-explicit (`ADR-MONO-048 ACCEPTED`)

**Decision driver:** `TASK-MONO-347` (finance/erp have no gateway although `platform/api-gateway-policy.md` says every project does) prompted a full gateway diagnosis. It found the five gateways are **not** copy-paste siblings but **three lineages**, and that four real defects were living in the gaps between them — including a rate-limiter fix that had propagated to three of four gateways and **silently missed the fourth**. `TASK-BE-501` / `TASK-BE-502` (merged, PR #2409 `b83adf4c3`) closed the defects. This ADR decides whether to remove the substrate that produced them.

**Why an ADR (HARDSTOP-09):** [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule is explicit — *"New shared-library introduction or breaking expansion requires an ADR (`docs/adr/` for monorepo-wide impact)."* Creating `libs/java-gateway` is a new shared-library introduction touching four projects. It cannot proceed without an ACCEPTED decision here.

---

## 1. Context

### 1.1 What exists (verified 2026-07-11, against `origin/main` `a44a142f4`)

Five gateway services. They are **not** one family:

| lineage | members | shape |
|---|---|---|
| **shared family** | `wms` · `scm` · `fan` | ~14–15 classes each, largely the same set |
| **partial + own extensions** | `ecommerce` | 17 classes; shares the security core, adds per-tenant rate-limit overrides, swagger aggregation, `RouteService`, `AccountTypeEnforcementFilter` |
| **fully independent implementation** | `iam` | different class names entirely — `TokenValidator`, `JwksCache`, `JwksClient`, `TokenBucketRateLimiter`, `EdgeGatewayProperties`, `RouteConfig`. Shares **no** class with the others. |

`iam` is therefore **out of scope** for any extraction: it is a different design, not a drifted copy. Any claim of "5 gateways to unify" is wrong; the real number is **4**, and even those are two groups.

### 1.2 Class-level measurement (comments stripped, package names normalised)

**Reproduce it** — the table below is not an impression, and a reviewer should not have to take it on faith:

```bash
norm() {
  perl -0777 -pe 's{/\*.*?\*/}{}gs; s{//[^\n]*}{}g;' "$1" \
  | sed -E 's/^package .*//; s/^import .*//;
            s/com\.wms\.gateway/PKG/g;
            s/com\.example\.scmplatform\.gateway/PKG/g;
            s/com\.example\.fanplatform\.gateway/PKG/g;
            s/com\.example\.gateway/PKG/g;
            s/[[:space:]]+/ /g; /^ *$/d'
}
# then md5sum each domain's copy of a class and compare
```

Comments are stripped because they legitimately differ (each carries its own task references); package names are normalised because that is exactly what extraction changes. What remains is the executable code.

Across `wms` / `scm` / `fan`:

| class | verdict |
|---|---|
| `ApiErrorEnvelope` | **code-identical 3/3** |
| `GatewayErrorHandler` | **code-identical 3/3** |
| `RequestIdFilter` | **code-identical 3/3** |
| `RetryAfterFilter` | **code-identical 3/3** |
| `SecurityConfig` | **code-identical 3/3** (all three permit exactly `/actuator/health`, `/actuator/health/**`, `/actuator/info`) |
| `AllowedIssuersValidator` | **code-identical 3/3 — and identical to `ecommerce` too (4/4)** |
| `FailOpenRateLimiter` | **code-identical 3/3** *(only since `TASK-BE-502`; wms diverged until then — see § 1.3)* |
| `RateLimitConfig` | differs (key namespace) |
| `IdentityHeaderStripFilter` | differs (strip set: wms 8, fan 8, scm 10, ecommerce 6) |
| `JwtHeaderEnrichmentFilter` | differs (injected header set) |
| `TenantClaimValidator` | differs (four distinct tenant-gate policies) |
| `OAuth2ResourceServerConfig` | differs (`@Value` property prefix) |

`ecommerce` additionally **does not have** `ApiErrorEnvelope`, `GatewayErrorHandler`, `RequestIdFilter`, `RetryAfterFilter`, `RateLimitConfig` at all — it uses `com.example.web.dto.ErrorResponse` and its own `RequestLoggingFilter` / `RateLimiterConfig`.

So the duplication is **real and large** (6 classes are byte-for-byte the same across three services; one across four), and the variation is **small and enumerable** (5 classes, each varying along exactly one axis).

### 1.3 The cost is not hypothetical — it has already been paid

`FailOpenRateLimiter` wraps Spring Cloud Gateway's Redis limiter to fail **open** when Redis dies (rate limiting is a soft protection; losing the counter store must not take the edge offline). The original implementation caught **every `Throwable`**:

```java
.onErrorResume(err -> { /* allow */ });   // NPE, CCE, Lua arg error — all "Redis is down"
```

That was recognised as a defect and fixed — narrowed to Redis-class failures via a cause-chain-walking predicate, with a separate counter for non-Redis errors so a programming bug surfaces as a 5xx instead of silently disabling the limiter.

**The fix reached `scm`, `fan` and `ecommerce`. It never reached `wms`.**

Nobody noticed, and nobody *could* notice by reading the code: four copies, no shared definition, no way to tell which one was current. `wms` ran for an unknown period as a gateway that would silently drop all rate limiting on the first NPE anywhere in the limiter chain — while logging a WARN that **blamed Redis**.

Worse, `wms`'s test for this was named **`failsOpenOnAnyReactiveError`** and asserted the defect *as the intended contract*. The bug had been written down as the specification, so the suite could never object to it.

The same shape produced the identity-header defects: `platform/api-gateway-policy.md` L74 mandates stripping `X-Actor-Id`; `ecommerce` did not. `wms`'s own service spec names `X-Account-Id` / `X-Tenant-Id` / `X-Roles`; `wms` stripped none of them. Both were fixed in `TASK-BE-501` / `TASK-BE-502`.

**This is the argument for the library.** Not DRY as an aesthetic — but that *a security fix silently failed to propagate, and the codebase gave no way to detect that*. Four copies of a security boundary is a mechanism for losing fixes.

### 1.4 Why now, and not before

Extracting **before** the defects were fixed would have been the wrong order: merging four already-divergent implementations into one would have silently changed some domain's security behaviour, and that change would have been disguised as "a refactor". With `TASK-BE-501` / `TASK-BE-502` merged, the four gateways now genuinely agree on the extractable classes — so extraction can honestly claim behaviour-invariance and prove it (byte-diff + unchanged tests).

### 1.5 `platform/shared-library-policy.md` § Decision Rule — checked

| # | Question | Answer |
|---|---|---|
| 1 | Used by more than one service? | **Yes** — 3 services for most classes, 4 for `AllowedIssuersValidator`. |
| 2 | Technical/common rather than domain-owned? | **Yes** — JWT issuer validation, error envelope, request-id propagation, retry-after header, a rate-limiter decorator, header hygiene filters. No domain entity, no business rule. |
| 3 | Stable without depending on one service's internal model? | **Yes** for the extractable set. Explicitly **no** for `ecommerce`'s `SecurityConfig` (hardcodes route knowledge like `/api/shippings/carrier-webhook`) and its per-tenant override classes — these stay in the service (§ D4). |
| 4 | Reduce duplication without increasing coupling? | **Yes** — the library depends on Spring Cloud Gateway + Spring Security only; it depends on **no** service module. Dependency stays one-way. |

All four pass. Nothing in § Forbidden applies (no domain logic, no service-specific entity, no cross-service business rule).

---

## 2. Decision

### D1 — Create `libs/java-gateway`, a **reactive** shared library

Every existing `libs/java-*` is servlet-based or framework-neutral. Gateways are Spring Cloud Gateway / WebFlux. Mixing reactive types into `libs/java-web` (a servlet-side module, sibling to `libs/java-web-servlet`) would drag WebFlux onto the classpath of every servlet service that consumes it.

**Chosen:** a **new, separate module** `libs/java-gateway`, depending on `spring-cloud-starter-gateway` + `spring-boot-starter-oauth2-resource-server` + `spring-boot-starter-data-redis-reactive`, consumed **only** by gateway services.

| Option | Verdict |
|---|---|
| **A. New `libs/java-gateway` module** | ✅ **CHOSEN.** Reactive deps stay quarantined to the four consumers. Mirrors the existing `java-web` / `java-web-servlet` split precedent. |
| B. Add to `libs/java-web` | ❌ Rejected — puts WebFlux + SCG on the classpath of every servlet service. The repo already split `java-web-servlet` out precisely to avoid this class of bleed. |
| C. Add to `libs/java-security` | ❌ Rejected — `java-security` is JJWT + password hashing, consumed by non-gateway services. Gateway filters are not "security primitives", they are edge plumbing. |
| D. Do nothing; keep four copies | ❌ Rejected — § 1.3 is the empirical refutation. This is the status quo that lost a security fix. |

### D2 — Scope: extract from `wms` / `scm` / `fan` / `ecommerce`. **`iam` is out of scope.**

`iam`'s gateway is an independent implementation (§ 1.1), not a drifted copy. Rewriting it onto the library would be a **rewrite**, not an extraction — different risk class, no behaviour-invariance proof available, and no defect currently motivates it. It stays as-is. If it is ever unified, that is a separate ADR.

### D3 — Two tiers: **as-is** classes and **parameterized** classes

**Tier 1 — extract unchanged** (proof obligation: byte-identical code body; consuming services' existing tests pass untouched):
`ApiErrorEnvelope`, `GatewayErrorHandler`, `RequestIdFilter`, `RetryAfterFilter`, `AllowedIssuersValidator`, `FailOpenRateLimiter`, `SecurityConfig`.

**Tier 2 — parameterize, then extract.** Each varies along exactly one axis:

| class | parameter | domain values |
|---|---|---|
| `IdentityHeaderStripFilter` | strip set | a shared **baseline** (the union the policy + specs require) that a domain may **only add to**, never subtract from |
| `JwtHeaderEnrichmentFilter` | injected header set (+ tenant-propagation flag) | wms/scm/fan inject `X-Actor-Id`; ecommerce injects `X-Tenant-Id` |
| `TenantClaimValidator` | 3 flags — `requireTenantMatch`, `allowWildcard`, `entitlementTrust` | see D5 |
| `OAuth2ResourceServerConfig` | property prefix | `@Value` → `@ConfigurationProperties` |
| `RateLimitConfig` | key namespace | per-domain slug |

**The strip set is asymmetric on purpose.** A domain may add headers to strip; it may not remove any. Removing is exactly the defect `TASK-BE-501`/`502` fixed, and a library that permits it re-opens the hole with nicer syntax.

### D4 — What stays in the services (explicitly **not** extracted)

- **`ecommerce`'s `SecurityConfig`** — encodes route knowledge (`/api/shippings/carrier-webhook`, `GET /api/products/**` public, CORS preflight permit) and depends on `com.example.web.dto.ErrorResponse` + `GatewayMetrics`. Route policy is domain knowledge; hoisting it would be exactly the "domain ownership over reuse convenience" violation the policy names.
- **`ecommerce`'s per-tenant rate-limit override classes** (`OverrideAwareRateLimiter`, `RateLimitOverrideProperties`, `TenantRouteRateLimitConfig`) — a genuine marketplace requirement no other domain has.
- **`ecommerce`'s `RouteService`, `SwaggerAggregationConfig`, `GatewayMetrics`, `AccountTypeEnforcementFilter`; `wms`'s `AccountTypeValidationFilter`; `scm`/`fan`'s `JwksHealthProbe`** — single-consumer classes. The policy's Decision Rule question 1 fails. **Do not promote a class to `libs/` because it might be shared later.**

### D5 — The four tenant-gate policies are **intentional**, and the library must preserve all four

This was the one point where extraction could have quietly changed behaviour. Checked against source:

| domain | gate | authority |
|---|---|---|
| `wms` | strict `tenant_id == wms` **+** `entitled_domains` dual-accept. **No `*` wildcard** | javadoc states the no-wildcard choice explicitly; ADR-MONO-019 § D5 |
| `scm` | equality **+** `*` wildcard (SUPER_ADMIN incident response) **+** entitlement dual-accept | ADR-MONO-019 § D5 |
| `fan` | equality **+** `*` wildcard. **No entitlement branch** | **correct** — `fan` is not in `ProductCatalog.ENTRIES`, `V0019` seeds subscriptions only for `wms/scm/erp/finance`, `omni-corp` does not subscribe to it, and `fan-platform` is `B2C_CONSUMER`. fan sits **outside the entitlement plane**; the branch would be dead code. |
| `ecommerce` | **any non-blank `tenant_id`** | ADR-MONO-019 § D5/D6 + ADR-MONO-030 § 2.4 — ecommerce is a multi-tenant marketplace SaaS. Entitlement is decided at **IAM issuance time**; the edge trusts issuance and tenant separation is enforced by the persistence-layer `WHERE tenant_id` filter, proven by the M6 cross-tenant-leak IT. |

**All four are documented decisions, not drift.** The 3-flag parameterization (D3) expresses all four exactly, so each domain keeps its current gate and the extraction is behaviour-invariant.

> **Recorded for a future decision, deliberately not taken here:** `wms` rejects the `*` SUPER_ADMIN wildcard that `scm`/`fan` accept. Its javadoc calls this deliberate, so this ADR preserves it. But "can a platform operator reach the wms edge during an incident?" is a real operational question with an inconsistent answer across domains. It is **not** in scope — changing it is a behaviour change, and this extraction's entire value rests on not smuggling one in.

### D6 — Behaviour-invariance is a **proof obligation**, not a claim

Every extraction PR must show:

1. **Tier 1**: the moved class's code body is byte-identical (comments/package excluded) to what each consumer had.
2. **Tier 2**: each consumer's parameters reproduce its previous behaviour exactly; the parameterization is exercised by a test per domain value.
3. **Consuming services' existing tests are not modified** (beyond import statements). A test that had to be *changed* to pass is a behaviour change wearing a refactor's clothes — the `failsOpenOnAnyReactiveError` incident (§ 1.3) is what that looks like when it goes unnoticed.
4. **Mutation check on the security-relevant classes**: re-inject the defect, prove the library's tests bite. A green suite is not evidence; a suite that bites is.

### D7 — Roadmap (sequenced; each step lands independently green)

| step | task | content |
|---|---|---|
| 0 | `TASK-MONO-349` | **This ADR.** No code. |
| 1 | `TASK-MONO-351` | Create the module. Extract **Tier 1** + migrate `wms`/`scm`/`fan`; `ecommerce` adopts `AllowedIssuersValidator` (its only 4/4 class). De-risks the reactive-module wiring first. |
| 2 | `TASK-MONO-352` | **Tier 2** parameterization + migrate `wms`/`scm`/`fan`. |
| 3 | `TASK-MONO-353` | Migrate `ecommerce` onto Tier 2 (needs `FailOpenRateLimiter` delegate-signature reconciliation — ecommerce generalised it to `RateLimiter<Config>` to support its override decorator). |
| 4 | `TASK-MONO-354` | **Create `finance` / `erp` gateways.** After steps 1–3 this is nearly free — route yml + a handful of properties. **Resolves `TASK-MONO-347` direction A** without the policy exception that direction B would have required. |

Step 1 is spawned on acceptance. Steps 2–4 are spawned **as each predecessor lands**, not up front: each step's scope should be written against what the previous step actually *proved*, not against what it was expected to prove.

> **`TASK-MONO-350` is absent from this chain on purpose.** Two concurrent sessions independently claimed `TASK-MONO-349`, and then each tried to yield the number to the other. The resolution that landed (PR #2412) is the correct one — *the ticket with nothing depending on it is the one that moves* — so an unrelated backlog ticket took `350` and this ADR kept `349`. The extraction chain therefore begins at `351`. The gap is a scar, not a mistake; renumbering now would only break references this ADR already ships with.

Step 4 is the payoff that makes this worth doing beyond de-duplication: the reason finance/erp never got gateways is that standing one up meant copying ~15 classes. That is precisely the cost this library removes.

---

## 3. Invariants preserved

- **No domain logic in `libs/`** — every extracted class is edge plumbing (`platform/shared-library-policy.md` § Forbidden: no domain rule, no service entity). HARDSTOP-03 not triggered.
- **One-way dependency** — `libs/java-gateway` depends on Spring only; no service module. Nothing outside the four gateway services depends on it.
- **Reactive quarantine** — WebFlux/SCG does not reach servlet services' classpaths (D1).
- **`platform/api-gateway-policy.md` remains authoritative** — the library implements the policy; it does not redefine it. (A follow-up should tighten L74, which names only 4 headers while the domains strip 6–10 — the under-specification that let the sets drift. Out of scope here.)
- **Behaviour-invariance** — D6 makes it checkable rather than asserted.

---

## 4. Consequences

**Positive**
- A security fix to a gateway boundary lands **once** and reaches every gateway. § 1.3 cannot recur by the same mechanism.
- `finance` / `erp` gateways become cheap → `TASK-MONO-347` resolves via direction A (implement) rather than direction B (a policy exception nobody owns).
- The four tenant-gate policies become **visible as data** (three flags) instead of buried in four hand-written validators, so the next reader can see that they differ *and why*.

**Negative / accepted**
- **`libs/` churn.** Phase 5 already LAUNCHED (ADR-MONO-003b ACCEPTED, 2026-05-13) so no launch gate is blocked, but the template repo `kanggle/project-template` will need a sync. Accepted: the template should carry the gateway library — a new project from the template *wants* a gateway it does not have to hand-copy.
- **A shared class is a shared blast radius.** A bug in `libs/java-gateway` now hits four edges at once instead of one. Mitigated by D6 (mutation-checked tests at the library level, where before there were four partially-tested copies — and one, `wms`, whose test asserted the bug).
- **Parameterization can become a config language.** Guarded by D4 (single-consumer classes stay put) and D3's asymmetric strip set (add-only). If a fifth parameter is needed to fit a domain, that is a signal the class is not actually shared — revisit rather than add a flag.
- **`iam` stays divergent** (D2). Accepted: it is a different design and no defect motivates touching it. The cost is that "gateway" means two things in this repo, which this ADR records rather than hides.

---

## 5. Alternatives considered

| Option | Verdict |
|---|---|
| **Do nothing** — keep four copies, rely on discipline to propagate fixes | ❌ **Empirically refuted.** § 1.3: the discipline already failed, silently, on a security fix, and the codebase offered no way to detect it. |
| **Copy-and-sync via a lint rule** (assert the four copies stay identical) | ❌ Rejected — cannot express the *legitimate* variation (D5's four tenant gates are all correct). A rule that flags them is noise; a rule that ignores them cannot catch real drift either. |
| **Extract first, fix defects inside the library** | ❌ Rejected — merging four divergent security implementations means one domain's behaviour changes, disguised as a refactor. `TASK-BE-501`/`502` deliberately landed **first** (§ 1.4). |
| **Unify all five gateways including `iam`** | ❌ Rejected — a rewrite, not an extraction (D2). No behaviour-invariance proof possible; no defect motivating it. |

---

## 6. Execution gate — **UNPAUSED (ACCEPTED 2026-07-11)**

Accepted by the user, explicitly (`ADR-MONO-048 ACCEPTED`).

This ADR was authored and opened as PROPOSED precisely so acceptance would be a **decision** rather than a fait accompli: it authorises a new shared library and the rewiring of **four production security edges**, which is the class of decision [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule reserves for an ADR rather than a task. **No agent self-accept** — that gate held.

**D7 is live.** Step 1 (`TASK-MONO-351`) is spawned with this commit.

**The gate that remains is D6, and it binds every step:**

> Behaviour-invariance is a **proof obligation**, not a claim. A consuming service's test that had to be **changed** to pass is a behaviour change wearing a refactor's clothes. If a step cannot show byte-identical bodies (Tier 1) / per-domain parameter equivalence (Tier 2) / unmodified consumer tests / a mutation check that bites — **it does not land.**

That obligation is the *entire* reason this extraction is permitted to touch four security edges at once. Drop it and the ADR's argument inverts: instead of removing the mechanism that **loses** fixes, we become a mechanism that **changes four gateways' behaviour simultaneously while calling it cleanup**.

**Related**
- `TASK-BE-501` / `TASK-BE-502` (merged, PR #2409 `b83adf4c3`) — the defects that motivated this; the convergence that makes extraction provable.
- `TASK-MONO-347` — finance/erp gateway drift; resolved by D7 step 4.
- `platform/shared-library-policy.md` § Change Rule — the rule requiring this ADR.
- `platform/api-gateway-policy.md` — the policy the library implements.
- ADR-MONO-019 § D5/D6, ADR-MONO-030 § 2.4 — the authorities behind D5's four tenant gates.
- ADR-MONO-003b — Phase 5 LAUNCHED; template sync consequence.
