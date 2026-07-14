# ADR-MONO-049 — `libs/java-security`: a **framework-neutral** security library, because 18 hand-copied validators are only 8 tests away from a fix that lands nowhere

**Status:** ACCEPTED

**Date:** 2026-07-12 (PROPOSED)

**History:** PROPOSED 2026-07-12 → **ACCEPTED 2026-07-13 with scope A** (`TASK-MONO-377`) — D5 widened to **all 20 services**; see § 1.7.

**Decision driver:** `ADR-MONO-048` extracted `libs/java-gateway` from four copy-pasted **reactive** edges. While closing its last step, § 1.1 was corrected twice — and it was still wrong. The two security validators it counted at four copies each exist as **six more copies each inside the finance/erp servlet services**, plus a third class it never counted at all. `TASK-MONO-361` measured 3 classes × 6 copies = 18 files **in finance and erp**. This ADR decides whether to remove them.

> 🔴 **And that count was low too — see § 1.6 (`TASK-MONO-375`).** The same three classes also live in **wms, fan, scm and iam**. Fleet-wide: **49 copies / 3,522 lines / 20 services / 6 projects**. Read § 1.1 and § 4 as *"finance and erp"*, not *"the fleet"*.
>
> ✅ **Settled 2026-07-13 — `ACCEPTED`, scope A (`TASK-MONO-377`): D5 covers all 20 services.** § 1.7 records the exact per-service population, which had never been enumerated. **Two more estimates were wrong in the safe direction** — § 1.7 corrects them, so the price paid is the measured one, not the feared one.

**Supersedes:** none. **Extends** `ADR-MONO-048` § 1.1, whose duplication count was low **three** times over (4 copies → the corrected 10 → 18 → the fleet-wide **49**). Each pass looked only where the previous task happened to be standing; that is the pattern, not arithmetic.

**Related:** [`ADR-MONO-048`](ADR-MONO-048-shared-reactive-gateway-library.md) (`libs/java-gateway`; its § D1 is the constraint that forces a *second* module here) · [`ADR-MONO-019`](ADR-MONO-019-platform-console-customer-tenant-model.md) § D5 (entitlement-trust dual-accept — the policy these validators implement) · `TASK-MONO-355` (builder parameterisation + closed-by-default discipline) · `TASK-MONO-360` (why a text guard is the wrong instrument here) · `TASK-MONO-361` (the measurement behind § 1) · `TASK-MONO-044a` (the servlet/reactive bleed incident this must not repeat — recorded in `ADR-MONO-048` § D1, not as an ADR of its own)

**Why an ADR (HARDSTOP-09):** [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule — *"New shared-library introduction or breaking expansion requires an ADR."* This proposes a new shared library consumed by two projects and six services. It cannot proceed without an ACCEPTED decision here. **That gate has now visibly held three times** (MONO-349 → ADR-048; MONO-357, which stopped short of this exact work; MONO-364, this document).

---

## 1. Context

### 1.1 What exists (verified 2026-07-12, against `origin/main` `f89c7425f`)

| class | servlet copies | lines | reactive refs | `jakarta.servlet` refs | canonical in `libs/java-gateway`? |
|---|---|---|---|---|---|
| `AllowedIssuersValidator` | 6 | 244 | **0** | **0** | ✅ 48 lines — **body byte-identical to all six** |
| `TenantClaimValidator` | 6 | 557 | **0** | **0** | ✅ 196 lines — but **parameterised into a builder** by `TASK-MONO-355`; the servlet copies are the pre-355 simple form |
| `TenantClaimEnforcer` | 6 | 626 | **0** | **6** | ❌ **none** — servlet-only (`OncePerRequestFilter`); the reactive edge has no counterpart |
| | **18** | **1,427** | | | |

Consumers: `finance-platform`{`account-service`, `ledger-service`} · `erp-platform`{`approval-service`, `masterdata-service`, `notification-service`, `read-model-service`}.

Counting the library's own copies, **`AllowedIssuersValidator` exists 7 times.**

### 1.2 Three facts, measured — and the third is not the one you expect

1. **Framework-neutral, confirmed.** `AllowedIssuersValidator` and `TenantClaimValidator` reference **zero** reactor / WebFlux / `ServerHttp` **and zero** `jakarta.servlet` types. They are pure `OAuth2TokenValidator<Jwt>`. **`TenantClaimEnforcer` is not** — it is bound to `jakarta.servlet` in all six copies. § 3.1 is about that class and nothing else.

2. **Behavioural drift: zero.** All six copies of each class behave identically today. **Nothing is broken.** Writing otherwise would be false, and the exaggeration would take the real argument down with it.

3. **Textual drift: already happened.** Normalised body comparison (package/import/comment stripped): **4 of 6** `TenantClaimValidator` and **4 of 6** `TenantClaimEnforcer` copies differ from one another. Every difference is formatting (line wrapping, `java.util.ArrayList` FQN vs import, an extracted local) or a legitimately per-domain property key (`erpplatform…:erp` vs `financeplatform…:finance`). **No behavioural difference.**

**These are hand-maintained. The evidence is already on the ground. They simply have not broken yet.**

### 1.3 The actual argument: **10 of the 18 copies have no test at all**

This is not a DRY complaint. `ADR-MONO-048`'s argument was never "duplication is ugly" — it was that **copies of a security boundary are a mechanism for losing fixes**, and that was *demonstrated*: `FailOpenRateLimiter`'s "swallow every `Throwable`" fix reached scm, fan and ecommerce and **silently missed wms** — whose test was named `failsOpenOnAnyReactiveError`. **The defect had been written down as the contract.**

Here it is worse. Test coverage across the 18 copies:

| service | `AllowedIssuersValidator` | `TenantClaimValidator` | `TenantClaimEnforcer` |
|---|---|---|---|
| finance `account-service` | ❌ | ✅ | ✅ |
| finance **`ledger-service`** | ❌ | ❌ | ❌ |
| erp `approval-service` | ❌ | ✅ | ❌ |
| erp `masterdata-service` | ❌ | ✅ | ✅ |
| erp `notification-service` | ✅ | ✅ | ❌ |
| erp `read-model-service` | ✅ | ✅ | ✅ |
| **tested / copies** | **2 / 6** | **5 / 6** | **3 / 6** |

**8 of 18 copies have a test. Ten do not.** And **`finance/ledger-service` carries all three security classes with zero tests for any of them** — an issuer allowlist, a tenant gate and a cross-tenant request filter, none of them asserted anywhere.

In the wms case a fix could at least have been *caught* by a test, if that test had asserted the right thing. **In ten of these copies there is no test to assert anything.** A fix that lands in four services and misses two is not merely silent — it is **unobservable**.

That is the substrate this ADR proposes to remove.

### 1.4 Why `libs/java-gateway` cannot be the home

Its `implementation` dependencies land on a **consumer's runtime classpath**. A servlet service that consumed it would pull **WebFlux + Spring Cloud Gateway** along with it — the exact leak `ADR-MONO-048` § D1 exists to isolate, and the mirror image of the `TASK-MONO-044a` servlet/reactive bleed incident.

The classes are framework-neutral; **the module they live in is not.**

### 1.5 ⚠️ Correction (`TASK-MONO-365`) — **`libs/java-security` already exists**

**The first draft of this ADR proposed creating it. It is already there, and this changes § 3 and § 4.**

I found it while auditing the iam gateway (§ D6), whose `TokenValidator` turned out to delegate to `com.example.security.jwt.Rs256JwtVerifier` — **a class in `libs/java-security`**. Verified against `origin/main` `8ffe49793`:

| | |
|---|---|
| module | **`libs/java-security`**, registered at `settings.gradle:17` |
| contents | `Rs256JwtVerifier` / `Rs256JwtSigner` / `JwtVerifier` / `JwksProvider` / `AbacDataScope` · `Argon2idPasswordHasher` · `PiiMaskingUtils` · ABAC conditions · `RedisKeyHelper` |
| dependencies | **jjwt + password4j. Zero Spring Web, zero WebFlux, zero servlet.** |
| consumers | **32 `build.gradle` files** — including **all six** finance/erp servlet services that hold the duplicated validators, **and** the wms / scm / fan / iam gateways |

**It is already the framework-neutral security module this ADR was asking for, and every target consumer already depends on it.**

**What this changes:**

- **The neutral pair needs no new module and no new wiring.** `AllowedIssuersValidator` and `TenantClaimValidator` move *into* `libs/java-security`. The 12 consumers already declare it. There is nothing to add to `settings.gradle`, and nothing to wire in 12 `build.gradle` files.
- **One dependency must be added to `libs/java-security`**: `spring-security-oauth2-jose` (the two classes implement `OAuth2TokenValidator<Jwt>`). That dependency is **neither servlet nor reactive** — but it lands on the runtime classpath of **all 32 consumers**, and that is a real consequence, not a footnote. § D3's V1 must be rewritten to assert what the module must *not* pull in (WebFlux, SCG, `jakarta.servlet`) rather than a blanket "no Spring".
- **`TenantClaimEnforcer` still cannot go there** — it is `jakarta.servlet`-bound, and `libs/java-security` is consumed by four *reactive* gateways. § D1's second module stands, and § 1.2 fact 1 is why.

**How the first draft got this wrong:** I measured the duplication (§ 1.1–1.3) and never asked whether a home already existed. The three classes I was counting sit *next to* `Rs256JwtVerifier` in the same six services' `build.gradle` files. **The evidence was one `grep` away and I proposed building what was already built.** § 4 has been re-costed accordingly.

### 1.6 🔴 Correction (`TASK-MONO-375`) — **the count is low a third time, and § 1.1 is not "the real shape"**

This document opens by noting that `ADR-MONO-048`'s duplication count *"was low twice over (4 copies → the corrected 10 → the measured **18**)"*. **It is low a third time, and this ADR is the one that is wrong now.**

§ 1.1 counted the servlet copies in **finance and erp**. It then presented that number as the fleet's shape (*"`TASK-MONO-361` measured the real shape: 3 classes × 6 copies = 18 files"*). The same three classes also exist in **wms, fan, scm and iam**, which this document never mentions.

**Re-measured against `origin/main` `1929c2627` (2026-07-12):**

| class | copies | lines | distinct normalised bodies |
|---|---|---|---|
| `AllowedIssuersValidator` | **18** | 760 | **1** — all eighteen identical |
| `TenantClaimValidator` | **18** | 1,409 | **10** |
| `TenantClaimEnforcer` | **13** | 1,353 | **8** |
| | **49** | **3,522** | |

Spread over **20 services in 6 projects** — erp (4), fan (4), wms (5), finance (2), scm (3), iam (2) — not the 6 services in 2 projects § 1.1 names. **§ 1.1's 18 is 37% of the copies and its 6 consumers are 30% of the services.**

Normalised body comparison confirms these are the *same* class, not namesakes: wms's, fan's, scm's and iam's `AllowedIssuersValidator` are **byte-identical** to finance's after stripping package/import/comments.

#### And § 1.2 fact 2 — *"Behavioural drift: zero"* — is true only inside § 1.1's scope

Fleet-wide, the servlet tenant gates implement **four different policies**:

| project | wildcard `"*"` | `entitled_domains` |
|---|---|---|
| wms | ❌ | ✅ |
| fan | ✅ | ❌ |
| scm / finance / erp | ✅ | ✅ |
| iam | ❌ | ❌ (strict equality only) |

So **D4's premise is false as written**: *"All six servlet `TenantClaimValidator` copies implement exactly: legacy `tenant_id ∈ {expected, "*"}` **or** signed `entitled_domains`"* holds for finance and erp — and for nobody else.

**But there is no live defect here, and saying otherwise would be the exaggeration § 1.2 warns against.** Each project's servlet policy **mirrors its own gateway's** exactly (verified by comparing the servlet copies against each gateway's `OAuth2ResourceServerConfig` builder calls, with comments stripped — a first pass that grepped raw text false-positived on wms, whose Javadoc *explains* that it rejects the wildcard):

| project | gateway | servlet | |
|---|---|---|---|
| wms | entitled, no wildcard | entitled, no wildcard | ✅ |
| fan | wildcard, no entitled | wildcard, no entitled | ✅ |
| scm / finance / erp | wildcard + entitled | wildcard + entitled | ✅ |

**Both layers of the defence agree in every project.** The divergence across the 18 copies is **deliberate per-project policy**, not rot.

#### What this changes for the decision

1. **D4's mechanism survives; its risk statement does not.** The builder is still the right shape — `TASK-MONO-355` parameterised `libs/java-gateway`'s validator for exactly these switches, and the servlet copies **hard-code what the gateway already parameterises**. But *"one file now decides the tenant gate for **12 edges** — 6 gateways and 6 services"* is really **26 edges: 6 gateways and 20 services**, and that file must express **four** policy shapes, not one. The closed-by-default discipline it inherits from MONO-355 matters more, not less.
2. **D5 as written consolidates 6 of 20 services and leaves 14.** That is *this ADR's own thesis happening to this ADR*: a change that reaches some services and not others. `AllowedIssuersValidator` makes it starkest — **all 18 copies are identical**, so migrating 6 would leave **12 identical copies of a class we just declared canonical**.
3. **§ 4's numbers are all scoped to § 1.1** and must be read as such: `AllowedIssuersValidator` copies "before: 7" is really **19** (18 + the library's); copies with no test "10 of 18" is, fleet-wide, **9 of 49** (listed below). The `finance/ledger-service` finding in § 1.3 **holds** — it carries all three classes and has **zero** tests for any of them.

**The nine untested copies (fleet-wide):** `AllowedIssuersValidator` in erp/approval, erp/masterdata, finance/ledger, scm/procurement, wms/admin · `TenantClaimValidator` in finance/ledger · `TenantClaimEnforcer` in erp/approval, finance/ledger, scm/demand-planning.

**How this draft got it wrong:** § 1.1 was measured by `TASK-MONO-361`, whose job was finance and erp. I inherited its scope without re-asking *"is this everywhere?"* — the same failure as § 1.5, one level up: **§ 1.5 asked "does a home already exist"; nobody asked "is this the whole population".** A count that has now been wrong three times is not a measurement problem. It is that each pass looked only where the previous task happened to be standing.

**This section does not decide the scope — § 3 does.** The choice is between widening D5 to all 20 services and explicitly recording why 14 are left behind. What it does is make sure the choice is made against the real number.

### 1.7 ✅ The scope decision, and the population it was finally measured against (`TASK-MONO-377`, 2026-07-13, `origin/main` `ae54af58d`)

**Scope A. D5 covers all 20 services.** § 1.6's argument is accepted as stated: `AllowedIssuersValidator`'s 18 copies are byte-identical after normalisation, so migrating 6 would leave **12 identical copies of the class this ADR is in the act of declaring canonical**. There is no coherent line to draw at finance+erp — that boundary is an artefact of which task last did the measuring, not a property of the code.

**Nobody had ever enumerated the 20.** § 1.6 gave per-project totals; this is the per-service matrix, and it is not the uniform sweep the step list implied:

| project | `AllowedIssuersValidator` + `TenantClaimValidator` | `TenantClaimEnforcer` | services |
|---|---|---|---|
| erp | 4 | 4 | approval · masterdata · notification · read-model |
| fan | 4 | 4 | artist · community · membership · notification |
| finance | 2 | 2 | account · ledger |
| scm | **1** | **3** | procurement *(all three)* · demand-planning, inventory-visibility *(**Enforcer only**)* |
| wms | **5** | **0** | admin · inbound · inventory · master · outbound *(**validators only**)* |
| iam | **2** | **0** | community · membership *(**validators only**)* |
| **total** | **18** | **13** | **20 services** |

**Two things fall out of that table that the step list would have got wrong:**

- **wms (5) and iam (2) hold no `TenantClaimEnforcer` at all** — they need `libs/java-security` only, never `java-security-servlet`.
- **scm's demand-planning and inventory-visibility hold *only* the Enforcer** — they need `java-security-servlet` and no validator migration.

#### Three more estimates were wrong — **two harmlessly, one not**

| | ADR said | measured (`TASK-MONO-377`) |
|---|---|---|
| servlet services needing **new** `java-security` wiring | *(§ 4.1: 6)* · § 1.6 feared *"6 → 20"* | **1** — `wms/admin-service` is the **only** one of the 20 whose `build.gradle` does not already declare `libs:java-security` |
| consumers needing `java-security-servlet` wiring | § 1.6: *"6 → 20"* | **13** — only the Enforcer holders |
| framework of the 20 | *(assumed servlet)* | **✅ all 20 are servlet.** No reactive service holds a copy — D1's `compileOnly` trap is real, but nothing is standing in it today |

**So scope A costs less than the ADR's own worst case.** The deletion roughly triples (18 → 49 copies) but the *build-graph* cost barely moves: **one new module, one new `settings.gradle` line, and 13 `java-security-servlet` lines.**

##### 🔴 And one estimate was wrong in the **dangerous** direction — D5-1 is not free

D1 and D5 both assert, of the very first step:

> *"`java-gateway` and the 6 gateways **already depend on it** — **no new wiring.**"*

**Both halves are false, and the second one breaks the build:**

| claim | measured |
|---|---|
| `libs/java-gateway` depends on `libs/java-security` | **It does not.** Its `build.gradle` declares SCG, `starter-security`, `oauth2-resource-server`, `data-redis-reactive`, micrometer, jackson. **No project dependency at all.** And it **uses both classes in code** — `GatewayJwtDecoders` constructs `new AllowedIssuersValidator(...)` and takes a `TenantClaimValidator` parameter; `JwtClaims` reads `TenantClaimValidator.CLAIM_TENANT_ID`. ⇒ it needs the dependency the moment they move. |
| the 6 gateways already depend on `java-security` | **Only 3 do** — fan, scm, wms. **ecommerce, erp and finance do not.** They compile against these classes **today** only because the classes live *inside* `java-gateway`, which they declare directly. Gradle `implementation` does **not** put a module's own dependencies on a consumer's compile classpath, so once the classes move, **those three stop compiling.** D2 forbids the tempting fix (`api project(':libs:java-security')`), and D2 is right — so each declares it itself. |

**D5-1's real cost: 4 new dependency lines** (`java-gateway` + ecommerce/erp/finance gateways) **and an import change in all 6 gateways.** Still small — but *"no new wiring"* would have been discovered by a red build, not by reading.

**This is the fourth time a count in this lineage moved.** Two of the three corrections above are *down*, which is new — and they moved because they were measured against the code rather than inherited from the previous document. **The one that went the other way is the one that mattered**: an underestimate of cost is an inconvenience; an underestimate of *breakage* is a broken `main`.

#### What is now settled, and what is not

- **Settled:** D5 covers all 20. The per-project policy differences (§ 1.6's four shapes) are **expressed through the MONO-355 builder, not preserved as copies** — that is what makes them expressible at all.
- **Still true, still not a defect:** each project's servlet policy exactly mirrors its own gateway. **There is no live defect here**, and this migration must not create one. § 6 V6 is the guard: *if a per-domain suite has to change, the migration changed behaviour.*
- **Not in scope, unchanged:** the **iam gateway** (§ D6 — a different, defensible design; audited by `TASK-MONO-365`). **Note the distinction, because it is easy to misread:** iam's *gateway* is out of scope; iam's *servlet services* (community, membership) are **in** scope — they hold 4 of the 49 copies.

### 1.8 🔴 The thirteen `TenantClaimEnforcer` copies, and the axis nobody had counted (`TASK-MONO-382`, D5-2)

§ 1.6 measured **behavioural drift on the axis of "does each project's servlet policy mirror its own gateway?"** and answered: **yes, everywhere.** That answer still stands.

**It is not the only axis.** D5-2 compared all thirteen `TenantClaimEnforcer` copies before writing the canonical one. They hold **eight distinct normalised bodies** — but the eight reduce to **three policy axes**; the rest was line-wrapping, an inlined `@Value` import, and locally re-declared claim constants:

| axis | what the fleet actually does |
|---|---|
| **wildcard `"*"`** | **all 13 allow it** |
| **`entitled_domains`** | erp (4) · finance (2) · scm (3) = **9 honour it** · **fan (4) do not** *(fan sits outside the entitlement plane — the branch would be dead code)* |
| 🔴 **public-path exemption (`shouldNotFilter`)** | **three different answers — and this is a security boundary** |

#### The exemption axis, in full

| shape | services |
|---|---|
| `PublicPaths.isPublic(request)` | **10** |
| `PublicPaths.isPublic(request) \|\| uri.startsWith("/internal/")` | **`fan/membership`** — its three fan siblings do **not** |
| `path.startsWith("/actuator/")` — **`PublicPaths` is not consulted at all** | **`scm/demand-planning`, `scm/inventory-visibility`** |

**The scm pair's exemption is *wider* than their own sibling's.** `scm/procurement`'s `PublicPaths` exempts exactly `/actuator/health`, `/actuator/info`, `/actuator/prometheus` (+ a webhook prefix). The other two exempt **every** `/actuator/**` path — `env`, `beans`, `heapdump`, `loggers`.

#### 🟢 It is not a live vulnerability — and saying otherwise would cost the real argument

Both services' `SecurityConfig` permits **only** `health` / `info` / `prometheus` and ends with **`anyRequest().denyAll()`**. Spring Security rejects `/actuator/env` before the enforcer's exemption is ever consulted. **Measured, not assumed.**

**But the line is being held in one place while the exemption is maintained in another.** Add `.requestMatchers("/actuator/**").permitAll()` to either service — an ordinary-looking change — and the tenant gate silently disappears for every actuator endpoint there. And these two services hold **no `TenantClaimValidator`** (§ 1.7): the enforcer is their *only* servlet-layer tenant check.

#### What this changes for D5-3 … D5-8

**The canonical `TenantClaimEnforcer` takes its exemption as a `Predicate<HttpServletRequest>`, defaulting to *exempt nothing*.** Each adopting service passes its own rule — which means **each adoption step must decide, explicitly, which of the three shapes that service is actually entitled to**, rather than inheriting whichever body got copied into it.

Two of those decisions are already known to be non-trivial:

- **`fan/membership`** — keep the `/internal/**` exemption, or remove it? Keeping it preserves behaviour (§ 6 V6). Removing it is a *narrowing*, and would need its internal callers checked first.

  **Measured while ticketing `TASK-MONO-387`:** membership runs **two** `SecurityFilterChain`s — an `@Order(1)` `securityMatcher("/internal/**")` workload-identity chain (`JwtDecoder` + `WorkloadIdentityAuthoritiesConverter` + `hasRole("INTERNAL")`) and an `@Order(2)` end-user chain. The internal chain **does** put a `JwtAuthenticationToken` in the context, and the workload-identity token carries **no `tenant_id`**. Since `TenantClaimEnforcer` is a servlet filter registered *outside* the chains, it runs on `/internal/**` too — so without the exemption it would 401 a request that has no tenant claim to give, and `community/HttpMembershipChecker` would break.
  ⇒ **The exemption looks load-bearing.** *Looks* is not the standard D5-5 set: `TASK-MONO-387` AC-6 decides it by **mutation** — remove the clause, run membership's internal suites. Red ⇒ keep it. Green ⇒ it is dead code, and the interesting question becomes why nobody noticed.

#### ✅ Decided (`TASK-MONO-387`): **load-bearing, kept** — and the mutation exposed something else

Removing only the `/internal/` clause (verified: the semantic diff was that one clause, and the guard confirmed both other switches were untouched) turned the suite **red in exactly one group**, with **no false positives**. The exemption stays.

**But the group that went red was the one this task had just written.** Not one of membership's pre-existing tests could see the filter at all:

| test | sees the filter? |
|---|---|
| `InternalAuthIntegrationTest` | ❌ no Spring annotations — a plain unit test |
| `AccessCheckIntegrationTest` | ❌ same |
| `InternalAccessControllerSliceTest` | ❌ `@WebMvcTest` + `SliceTestSecurityConfig`, **which registered no enforcer bean** |

> **So before D5-6, deleting the `/internal/` clause in production would have 401'd every internal call — and membership's entire suite would have stayed green.** The exemption was undefended. This is the same class as `TASK-MONO-355` (wms's wildcard *rejection* had zero coverage, because the suite only recorded what the gate accepted) and `TASK-MONO-373` (specs that ran in no job at all): **a property nothing can observe is a property nothing is holding.**

The chain of facts, each measured rather than reasoned: `internalJwtDecoder`'s own Javadoc says it *"does NOT pin tenant_id"*; the `@Order(1)` chain nonetheless puts a `JwtAuthenticationToken` in the context; the enforcer 401s a `JwtAuthenticationToken` with no `tenant_id`; and the enforcer runs *outside* the chains at `LOWEST_PRECEDENCE-100` (§ 1.8 D5-5). Remove the exemption and `community/HttpMembershipChecker` stops working.

**It is not a hole in the tenant gate.** `/internal/**` is not an end-user route, and the chain guarding it requires `hasRole("INTERNAL")`. A tenant claim was never the thing standing between a caller and that surface.
- **`scm/demand-planning`, `scm/inventory-visibility`** — pass `PublicPaths::isPublic` like every other service (a **narrowing**, and the safer default), or reproduce the blanket `/actuator/` exemption? **Narrowing is a behaviour change and must be an explicit decision, not a silent one.**

#### The three facts D5-5's decision needs (measured while ticketing `TASK-MONO-385`)

- **A.** Both services' `SecurityConfig` `permitAll`s **only** `/actuator/{health,info,prometheus}` and ends `anyRequest().denyAll()`. `/actuator/env` is refused by Spring Security *before* the enforcer's exemption is consulted.
- **B.** It follows that the actuator paths which are *exempt but not permitted* are **unreachable even with a token**. So narrowing the exemption to those three is very likely a **zero-reachable-behaviour-change** narrowing — but that is a claim to be **proven**, not assumed, and `TASK-MONO-385` AC-3 makes the proof the price of choosing it. *An unproven narrowing is not a decision, it is a bet.*
- **C.** `inventory-visibility` also `permitAll`s `/internal/inventory-visibility/**`. **This is not a defect** — `InternalSnapshotController` is the network-trusted, tenant-agnostic batch read that `ADR-MONO-027` § D7.1 documents, called by a scheduler that holds no token, and the gateway does not route it. Because it is `permitAll`, no JWT arrives, and the enforcer passes non-`JwtAuthenticationToken` requests through regardless of its predicate. **The exemption axis does not reach that path, and narrowing the exemption cannot break it.**

Neither service holds a `PublicPaths` class today (only `procurement` does), so choosing the narrow shape means creating one.

#### ✅ Decided (`TASK-MONO-385`): **narrowed — and the narrowing is proven unobservable**

**The proof turned on filter order, not on inspecting paths.** `TenantClaimEnforcer` registers at `Ordered.LOWEST_PRECEDENCE - 100`; Spring Security's chain registers at `SecurityProperties.DEFAULT_FILTER_ORDER` (`-100`). **Spring Security runs first.** A request its authorization filter refuses is answered before the enforcer is ever invoked.

⇒ every actuator path that *lost* the exemption (`env`, `beans`, `loggers`, `heapdump`, `health/liveness`) is `denyAll`'d, so **the filter never saw it in the first place**. The change cannot be observed. That is asserted, per service, by `ExemptionEqualsThePermitList` — not argued in prose.

**And the hazard § 1.8 actually named is now structurally gone.** Both services got a `PublicPaths`, and **`SecurityConfig` builds its `permitAll` matchers from it**. The permit list and the exemption are one object; they can no longer be edited independently. The old danger — *"add `permitAll("/actuator/**")` and the tenant gate silently disappears"* — required two files to disagree, and there is now only one.

> **`PREFIXES` is deliberately empty in both.** `procurement`'s `PublicPaths` carries `/actuator/health/`, but these two services' `SecurityConfig` has never permitted `/actuator/health/liveness`. Copying the prefix across would have **widened the permit list under cover of a refactor** — the exact silent behaviour change this section exists to forbid.

Fact **C** held: `/internal/inventory-visibility/**` is `permitAll`, no JWT arrives, and the enforcer passes non-`JwtAuthenticationToken` requests straight through. The exemption axis never reached it, and narrowing could not break it. Pinned by `internalPathIsNotAnExemptionConcern`.

#### 🔴 And one copy had already rejected the shape another copy shipped

While ticketing D5-6, `fan/community`'s enforcer turned out to carry this comment, written by hand, above its `shouldNotFilter`:

> *"Whitelist only the actuator endpoints we deliberately expose unauthenticated. **A blanket `/actuator/` prefix would bypass the tenant gate for endpoints that may be added later** (`/actuator/env`, `/actuator/heapdump`, …); we want a fail-closed posture there."*

**That is an exact description of what `scm/demand-planning` and `scm/inventory-visibility` did** — considered, named as a hazard, and deliberately refused. In a different project, by someone who had no way of knowing the other two had done it anyway.

> **This sharpens the argument rather than weakening it.** Hand-maintained copies are not always wrong — `fan` was *right* on this axis, and reasoned about it explicitly. The defect is that **being right was invisible**: nothing carried community's judgement to scm, nothing flagged that scm had chosen the opposite, and the disagreement survived because **nothing ever compared them**. A shared class is not merely a way to avoid duplicating a mistake. It is the only place a decision like community's can be *stated once and hold*.

> **The copies did not disagree because anyone decided they should.** They disagreed because thirteen files were maintained by hand and nothing ever compared them. **Extracting them is what made the disagreement visible** — which is the whole argument of this ADR, arriving as evidence rather than assertion.

### 1.9 🔴 The canonical class had reintroduced the split the copies were written to avoid (`TASK-MONO-383`, D5-3)

D5-3 is the first step that adopts the canonical `TenantClaimEnforcer` in a live service, and § 6 V6 (**behaviour invariance**) is the check it has to pass. **It did not pass on the first attempt** — and what it caught was in the shared class, not in finance.

The thirteen copies split **two ways** on how they reject a token with no `tenant_id`:

| shape | services |
|---|---|
| `if ((tenantId == null \|\| blank) && !entitled)` → 401 | **9** — erp (4), finance (2), scm (3) |
| `if (tenantId == null \|\| blank)` → 401, unconditionally | **4** — fan |

**That is not a fourth axis.** The two sets are exactly the `entitled_domains` axis (§ 1.8): fan has no `entitled` variable at all, so its condition *degenerates* to the unconditional form. **The three-axis model holds.**

But D5-2's canonical class rejected **unconditionally, regardless of the switch** — so adopting it would have *narrowed* the gate for the nine entitlement-honouring services. And narrowed it into a specific, named failure:

> **`TenantClaimValidator` — the decode-time gate running in the very same services — consults the entitlement relaxation *before* it rejects an absent claim** (`TenantClaimValidator#validate`: the `trustEntitledDomains && isEntitled(...)` branch precedes the null check). An enforcer that rejected unconditionally would **refuse a token the decoder had just admitted.**

The copies' own Javadoc names this hazard verbatim — *"both enforcement points share a single source of truth (**mismatch would create a decode-pass / filter-block split**)"* — and finance's pre-existing `TenantClaimValidatorTest` already **asserted the admitted side** (`entitled_domains containing finance grants even when tenant_id absent` → `hasErrors()` is `false`). The property was pinned on one layer and about to be contradicted on the other.

**Not live-reachable today, and the ADR should say so plainly.** IAM only ever populates `entitled_domains` *after* a `tenant_id` is resolved, and every issuance path fails closed (`IllegalStateException`) when it is absent — so no issuable token has one without the other. This was a **latent** defect in a class with **zero consumers**, caught by the first adoption. It was not a live vulnerability, and claiming otherwise would cost the real point.

**The real point is the shape of the failure.** The enforcer exists to still be standing when the decoder is misconfigured. Two layers of a defence in depth that reach *opposite verdicts* on the same token are not defence in depth — the inner one is contradicting the outer one, which is precisely the state the enforcer was built to survive.

**Fix (in D5-3):** the null branch consults the switch, exactly as all thirteen copies do —

```java
boolean entitled = trustEntitledDomains && TenantClaimValidator.isEntitled(token, expectedTenantId);
if ((tenantId == null || tenantId.isBlank()) && !entitled) { /* 401 */ }
```

With `trustEntitledDomains` off (fan), `entitled` is always `false` and this **is** the unconditional rejection those four copies wrote by hand. One expression, both shapes, no new axis.

**Guarded, both ways.** `TenantClaimEnforcerTest` gains a nested `AgreesWithTheDecoder` group that runs a token set through **both** the validator and the enforcer and asserts the verdicts match — in *both* directions (nothing the decoder admits is refused; nothing it refuses is admitted). Each finance service's `FinanceTenantGatePolicyTest` asserts the same agreement over its own wiring. Mutation-verified: restoring the unconditional shape turns both red.

> **Whether an entitlement without a `tenant_id` *should* be admitted is a real policy question — and it is not this refactor's to answer.** Today both layers say yes. If that is wrong, it is wrong at the *validator* too, fleet-wide, and belongs in its own ticket with its own ADR. What D5 may not do is change the answer silently, on one layer only, while claiming behaviour invariance.

### 1.10 🔴 The population was wrong a sixth time — and this time the detector, not the scope, was too narrow (`TASK-MONO-385`, D5-5)

Every count in this lineage has been measured with the same predicate: **a file whose body starts `public class TenantClaimValidator` (or the other two).** Five corrections later — 4 → 10 → 18 → 49, plus D5-3's arithmetic slip — the predicate itself had never been questioned.

Ticketing D5-5 questioned it. **`scm/demand-planning` and `scm/inventory-visibility` hold the same validator logic as everyone else, written as inline lambdas inside their `ServiceLevelOAuth2Config`:**

```java
public static OAuth2TokenValidator<Jwt> tenantClaimValidator(String expectedTenantId) {
    return jwt -> { ... };     // wildcard + entitled_domains + "tenant_mismatch" — the class policy
}
private static OAuth2TokenValidator<Jwt> allowedIssuersValidator(List<String> allowed) {
    return jwt -> { ... };
}
static boolean isEntitled(Jwt jwt, String domain) { ... }
```

Fleet-wide, only these two files do this (measured). Which is exactly why nothing caught them: **`^public class` cannot see a lambda.** § 1.7's "scm: 1 validator / 3 enforcers" is arithmetically right about *files* and wrong about *what is being hand-maintained there* — the truth is **one validator class and two inline implementations of it**.

> **The first five corrections were about where we looked. This one is about what we agreed to call a copy.** A count is only as honest as its predicate, and a predicate that has never been wrong is a predicate nobody has tested. (The same lesson, in a different costume, as the `grep -c` that counted Javadoc in `TASK-MONO-375`.)

**And the drift is already there, as always.** The inline issuer check fails with error code **`invalid_token`**; every class copy — including the canonical one — uses **`invalid_issuer`**. Adopting the shared class therefore changes an observable response code on those two services. That is a real behaviour change, it is small, and D5-5 must state it rather than let it ride along (`TASK-MONO-385` AC-2).

**The file count is unaffected** — D5-5 still deletes five class files, 31 → 26. What changes is the work: it must also delete the two inline implementations. Leaving them would mean the class this ADR just declared canonical still has hand-maintained copies in the fleet — **the ADR executing its own thesis on itself**, which is the argument § 1.6 used to widen D5 to scope A in the first place.

---

### 1.11 wms has no `TenantClaimEnforcer` — and that is a finding, not a gap (`TASK-MONO-390`, D5-7)

D5-6 left 14 copies. **All 14 are validators. `TenantClaimEnforcer` is at zero across the fleet** (measured on `origin/main` after `TASK-MONO-387` merged). One of the three classes this ADR set out to unify is *finished*.

That is not a coincidence, and it is the shape of the two remaining steps: **wms and iam never had an enforcer.** Which means three things that dominated D5-3…D5-6 **do not exist in D5-7**:

- no `.exempt(...)` axis, so no `PublicPaths`, no permit-list coupling — **§ 1.8's hazard cannot occur here**;
- no filter-order argument — D5-5's `ExemptionEqualsThePermitList` has nothing to pin;
- and **§ 1.9's decode-pass / filter-block split is structurally impossible.** That defect needed *two* layers that could disagree. wms's tenant gate has **one**: the decode-time validator. The thing D5-3 caught cannot happen in a service with nothing to disagree with.

**The obvious next thought is the wrong one.** "wms is missing its defence-in-depth layer, we should add the enforcer" is a *behaviour change* wearing an extraction's clothes — and wms's own gateway config already wrote the rule against it: *"whether a platform operator should be able to reach the wms edge during an incident is a real question, but changing the answer is a behaviour change and does not belong in an extraction."* D5-7 moves what is there. Whether wms *wants* a second layer is a question for a ticket that is allowed to answer it.

#### The mutation direction inverts, and the quiet direction changes sides

Every step so far has had the same failure mode: *forget* a switch, narrow the gate, watch something go red. D5-6 was the first to run the mutation in both directions and found the reverse — an *added* switch widens the gate, and widening is quiet.

**wms is where that stops being a curiosity.** It is the **only platform in the fleet that rejects the SUPER_ADMIN `"*"` wildcard** (its copies say so in a comment; `ADR-MONO-048` § D5 preserves it deliberately). So in D5-7 the dangerous mistake is not omission — the builder defaults closed, omission is safe here — it is **habit**: writing `.allowSuperAdminWildcard()` because the last four projects did.

And `TASK-MONO-355` already told us what that would cost: **this exact gate had zero coverage for its rejection.** The copies D5-7 deletes assert the tenant match, the cross-tenant refusal, the entitlement grant, the malformed-claim fail-closed — and **never once assert that `"*"` is refused.** The one property that distinguishes wms from the rest of the fleet was the one property nothing was holding. `WmsTenantGatePolicyTest` is that missing assertion, and AC-5 mutates in the **adding** direction to prove it bites.

#### The canonical chain was already in production, in the same project

D5-7 needed no design. **`wms/gateway-service` has been running the shared `TenantClaimValidator` since the gateway-lineage convergence** (`ADR-MONO-048` § D7):

```java
public TenantClaimValidator tenantGate() {
    return TenantClaimValidator.forTenant(requiredTenantId)
            .trustEntitledDomains()
            .build();                 // no .allowSuperAdminWildcard() — deliberate
}
```

…and its policy pin already takes its subject from that production method. So the servlet side is not adopting a *proposed* wiring; it is **aligning with one the same project already proves.** Branch equivalence with the copies was measured rather than argued — three success branches, two failure branches, identical down to the failure-message strings, and the error codes (`tenant_mismatch`, `invalid_issuer`) unchanged.

The error codes mattering is not cosmetic: **all five wms `SecurityConfig`s branch on `ERROR_CODE_TENANT_MISMATCH` in their `AuthenticationEntryPoint` to raise 403 `TENANT_FORBIDDEN`**, which `specs/integration/iam-integration.md` states as a contract. A changed code degrades 403 → 401 silently. D5-5 *did* change an error code, so this was checked, not assumed.

#### Two services are out of scope, measured

- **`wms/notification-service` has no OAuth2 resource server at all** — zero `jwtDecoder`, zero `oauth2ResourceServer`, zero `OAuth2TokenValidator`. There is no gate to move. *(Whether it should have one is not this ADR's question; it is recorded here because "we checked and there is nothing" is a different statement from "we did not look.")*
- **`wms/gateway-service`** — already shared (§ D6).

---

### 1.12 The last four files were the easy part — the policy had a second home nobody had counted (`TASK-MONO-392`, D5-8)

D5-8 deleted the final four copies and took the fleet to **zero**. It also found that **deleting them was not the work.**

#### The census could never have seen this

Every count in this lineage — 4 → 10 → 18 → 49, and § 1.10's correction for inline lambdas — has asked the same question: *where else is this class written?* In iam, the answer was also: **in the tests, twice per service.**

`SliceTestSecurityConfig` and the integration bases (`CommunityIntegrationTestBase`, `MembershipJwtTestSupport`) each **hand-built their own validator chain**:

```java
OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(),
        new AllowedIssuersValidator(List.of(SAS_ISSUER, LEGACY_ISSUER)),
        new TenantClaimValidator(DEFAULT_TENANT_ID));       // ← not production's
```

Swapping the decoder's **key** is legitimate — there is no auth-service in a slice test, so it verifies against a local key instead of fetching JWKS. Swapping the **policy** is not. And these files did both, in the same breath, which is precisely why nobody noticed: *the legitimate reason to touch the decoder carried the illegitimate change in with it.*

#### What that cost, measured rather than asserted

The claim "these tests would stay green while the real gate changed" is easy to make and easy to hand-wave. So D5-8 measured it. A **canary mutation** was inserted into production's `jwtTokenValidator()` — a validator that rejects **every** token — and both projects' full suites were run:

| production's tenant gate rejects **everything** | test-side failures |
|---|---|
| **before D5-8** (tests build their own chain) | **0**, across **50 suites** |
| **after D5-8** (tests delegate to production) | **36** (community 22 · membership 14) |

**The production gate refused every token in existence and fifty test suites went green.** That is what a second copy of the policy buys you. It is the same defect as `TASK-MONO-355` (wms's wildcard refusal had zero coverage) and `TASK-MONO-387` (membership's `/internal/**` exemption was undefended), and it earns the same sentence: **a property nothing can observe is a property nothing is holding.**

> **The fix is not a shared `TenantGateTestConfig`.** That would be a *third* copy of the policy — and a test helper is exactly where a third copy would hide, because it looks like de-duplication while it is doing the opposite. The tests now call `OAuth2ResourceServerConfig#jwtTokenValidator()` — the bean production wires — and keep only the key swap.

#### And a count was wrong one last time, in the ticket that boasted the counting was fixed

`TASK-MONO-390`'s notes recorded that D5-7 was the first step where **grep was complete** and the compiler found nothing further. D5-8's ticket then measured iam's dependencies and stated: *both services already declare `libs:java-security`; no new dependency line is needed.*

**`membership` declares it as `testImplementation`.** The grep counted the *string* and returned `1` for both services; it never looked at the **configuration**, which is the only part that matters — `testImplementation` does not reach the production compile classpath. The build failed on the first compile with *"package `com.example.security.oauth2` does not exist"*.

**The compiler refuted the measurement, again — and this time the measurement was the one that had just declared itself reliable.** A count is only as honest as its predicate, and `grep -c 'java-security'` is not a predicate about dependencies. It is a predicate about text.

#### And then the author of this section shipped the mutation

The first push of D5-8 went **red on CI**, and the two failing tests were `EntitlementIsRefused` — the assertions this very task had written to prove iam refuses the entitlement switch.

The cause: two mutation runs were executing **concurrently against the same worktree** (one backgrounded, one launched in the foreground before it had finished). One process's *restore* raced the other's *apply*, and the apply won. `.trustEntitledDomains()` was left in both production configs — **directly beneath the comment that says `no .trustEntitledDomains()`** — and committed.

The residue check afterwards looked for leftover `.premut` files and stray canary validators. It found none, and reported clean. **It never checked for the switches themselves.**

> **The guard was blind to precisely the failure mode it existed to prevent** — the third time in this series that a guard's *predicate* was wrong rather than its *subject* (§ D5-6 aborted a good mutation on a bad count; § D5-7's count was off by the gateway). And it is the third time the fix was the same: **assert the property you are protecting, not a proxy for it.** The check that catches this is one line — *zero switch calls in iam's production configs* — and it is now what runs.
>
> **What caught it was the policy pin this task wrote.** A test authored in this commit turned CI red against its own commit. That is the entire thesis of § 1.12, executed on the person writing § 1.12: *the value of a suite is not that it passes; it is that it is watching.* It was watching. **And "the tests passed locally" was never the claim worth making — the artefact I tested and the artefact I committed had drifted apart, and only one of them was real.**

#### Two gates guard the same tenant and answer the same question differently (`TASK-MONO-394`)

Writing iam's policy pin required asking *"which tenant does this service actually gate?"* The answer was **`fan-platform`** — and that is when it became visible that **`fan-platform` has services of the same names**, which D5-6 had migrated a day earlier.

| | SUPER_ADMIN wildcard (`tenant_id="*"`) | entitlement |
|---|---|---|
| **`fan-platform/community`** — deployed | **✅ accepts** | ✗ |
| **`iam-platform/community`** — **deployed nowhere** | **❌ refuses** | ✗ |

**Two gates guarding the same tenant slug answer the SUPER_ADMIN question in opposite directions.** § 1.7's table recorded both projects and **never connected them** — the same failure as § 1.6's counts, one row apart.

Which answer is right is a question **about behaviour**, and an extraction does not change behaviour, so D5-8 preserved iam's OFF/OFF and changed nothing. **But the follow-up cannot start with the policy.** Measured on `origin/main` `480b8f8ae`: `iam-platform/docker-compose.yml` starts `auth`, `account`, `security`, `admin` and `gateway` — **not these two.** Nothing routes to them, nothing references them outside `settings.gradle` and `ci.yml`. They are **built and tested by CI and deployed nowhere**, while `iam-platform/infra/prometheus/prometheus.yml` still scrapes them on `:8086` / `:8087` — *a monitoring config for services its own compose does not start*, which is why they looked alive.

So the question is not *"which wildcard policy is correct"*. It is **"should these two services exist at all"** — and reconciling the policy of a dead module would be work that protects nothing. **`TASK-MONO-394` asks the live-or-dead question first, and it is a human's decision because the likely answer is deletion.**

> ⚠️ **And this subsection is late.** `TASK-MONO-392` § AC-8 required the divergence to be recorded *here*, and its PR body said it had been. It had not — it went into the task file and the INDEX and **not into the ADR**. **A declaration-versus-truth drift, inside the document whose thesis is that declarations drift.** Caught while ticketing the follow-up, by re-reading the section instead of trusting what the PR claimed about it.

#### The answer (`TASK-MONO-394`, 2026-07-14) — they were retired, and the question above was asked wrong

**Decision (human): retire both.** iam's `community-service` and `membership-service` are gone; the fleet-wide copy count and the divergence go with them.

**But the paragraph above got its own measurement wrong, twice, and that is the part worth keeping.**

| what § 1.12 asserted | what re-measuring found |
|---|---|
| *"`iam-platform/docker-compose.yml` starts `auth`, `account`, `security`, `admin` and `gateway`"* | That file is **infrastructure-only**. The five apps are in **`docker-compose.e2e.yml`**. The conclusion held; **the evidence pointed at the wrong file.** |
| *"nothing references them outside `settings.gradle` and `ci.yml`"* | **16 spec files, `PROJECT.md`, `docs/project-overview.md`, iam `ADR-001`, `docker/mysql/init.sh`, `docker-compose.yml`, and the service-map drift guard.** |
| *"they are deployed nowhere"* — framed as a **discovery** | They were **declared `FROZEN` in six places**, with a stated purpose (`PROJECT.md`: *"a portfolio integration example"*), and `check-service-map-drift.sh` **encoded the exemption in a guard**. Nobody had lost them. |

**So the follow-up's real question was never "live or dead".** It was **"is the exhibit still worth its shelf space"** — and the answer is no, because **`fan-platform` performs the exhibit's stated purpose for real and in production.** The exhibit had become a duplicate that every platform-wide sweep still had to carry (`TASK-BE-454/455`, and D5-8 itself).

Two things fall out that the divergence table above could not show:

- **The divergence was not a regression D5-8 introduced.** Reading the pre-D5-8 hand-rolled validator (`82a5859e5`) shows **pure equality, no wildcard branch** — iam refused `*` before the extraction and after it. **D5-8's "behaviour preserved" claim is true.** The drift was born at fan's bootstrap (`ba916c9d3`, 2026-05-03), which **added** a wildcard the GAP original never had.
- **fan is not a copy of iam's copy.** Copy-detection (self-validated against a known rename, which yields 1923 `R` entries) reports **30 copies from `global-account-platform` at 58–78% similarity** — reworked from birth — and today **zero byte-identical files** across 24 fan commits versus 6. Deleting iam's side cost fan nothing.

> **The lesson lands on this ADR itself.** § 1.12 was written by the session that had just finished D5-8, and it **inherited its own scope instead of re-counting** — the same error § 1.6 diagnoses in § 1.1, committed one subsection later by the person diagnosing it. **A prior document's numbers are a hypothesis, not a source** — including when the prior document is your own, and including when it is this one.

---

## 2. Alternatives considered

### 2.1 A CI guard asserting the copies stay identical — **rejected**

Superficially the cheapest fix, and the one this monorepo has reached for four times (MONO-345, 352, 359, 360). It is wrong **here**, for reasons the repo has already written down:

- **There is no formatter in this repo** — no spotless, no checkstyle, no google-java-format. The copies' formatting has *already* diverged (§ 1.2 fact 3). A "bodies must match" guard is therefore **RED on day one** — and `TASK-MONO-360` nailed exactly that failure mode: ***a guard that is red on day one gets switched off, and a switched-off guard is worse than no guard, because a skip reports green.*** Building it would be me violating a rule I wrote three days ago.
- **There is no text to compare against.** `libs/java-gateway`'s `TenantClaimValidator` was parameterised into a builder by `TASK-MONO-355`; the servlet copies are the pre-355 simple form. They are behaviourally equivalent and textually unrelated.
- Forcing the formatting to converge so a guard *could* stand **is itself a decision to keep the copies** — which this ADR would then have to make explicitly anyway.
- **And it would not close § 1.3.** A text guard makes the copies identical. It does not give the ten untested copies a test.

### 2.2 Reuse `libs/java-gateway` from the servlet services — **rejected**

§ 1.4. Runtime bleed. This is the failure `ADR-MONO-048` § D1 was built to prevent.

### 2.3 Status quo, with the reasoning recorded — **rejected, but it was a live option**

`TASK-MONO-364` § AC-5 required this ADR to *measure* what disappears rather than assume the extraction pays for itself, and to accept "keep the copies" if the number came out small. **It did not come out small** (§ 4). Combined with § 1.3 — ten untested copies of a security boundary — status quo means keeping a mechanism that has already fired once, in a shape where the next firing is invisible.

---

## 3. Decision

### D1 — **Join** the existing `libs/java-security`; add **one** module, because one of the three classes is not neutral

> **Revised by § 1.5.** The first draft said "create `libs/java-security`". **It already exists**, it is already framework-neutral, and **all twelve target consumers already depend on it.** The neutral pair has a home; only the servlet-bound class needs a new one.

| module | contents | status | consumed by |
|---|---|---|---|
| **`libs/java-security`** *(exists — `settings.gradle:17`)* | **+ `AllowedIssuersValidator`, `TenantClaimValidator`** (moved out of `libs/java-gateway`) | **already wired into 32 `build.gradle` files** — 🔴 **but not into the four that matter first.** § 1.7 measured it: `libs/java-gateway` **does not** declare it (and uses both classes in `GatewayJwtDecoders`/`JwtClaims`), and of the 6 gateways **only fan, scm and wms** do — **ecommerce, erp and finance do not.** D5-1 adds **4** lines. | unchanged |
| **`libs/java-security-servlet`** *(new)* | `TenantClaimEnforcer` | **the only new module** | the 6 servlet services **only** |

`libs/java-security` gains exactly one dependency: **`spring-security-oauth2-jose`** (the two classes implement `OAuth2TokenValidator<Jwt>`). It is neither servlet nor reactive — but it reaches the runtime classpath of **all 32 consumers**, which § D3 V1 must therefore assert *precisely* (no WebFlux, no SCG, no `jakarta.servlet`) rather than as a blanket "no Spring".

**The rejected shortcut, named so it is not quietly re-attempted:** put all three in `libs/java-security` and mark the servlet dependency `compileOnly`. That compiles. It also puts a `jakarta.servlet`-bound class on the classpath of the **four reactive gateways that already consume this module**, where the servlet API does not exist at runtime. It would work — until something scans or reflects over it, and then it fails at boot, in the edge, in production. **A class that only works because nobody has loaded it yet is the failure class this entire line of work has been chasing.** One new module costs one `settings.gradle` line. Take the line.

`libs/java-gateway` keeps depending on `java-security` and **never** on `java-security-servlet`. The reactive edge cannot see the servlet filter at all.

### D2 — Every consumer declares `java-security` directly. No `api`, anywhere.

The six gateway `OAuth2ResourceServerConfig` classes import `TenantClaimValidator` **by name**. The tempting fix is `api project(':libs:java-security')` in `java-gateway`, so it transits.

**No.** `ADR-MONO-048` § D1 banned `api` on the gateway library, and the ban should not acquire an exception the first time it is inconvenient — an `api` edge is how a "neutral" module starts transiting things that are not. Each of the 12 consumers (6 gateways + 6 servlet services) declares `implementation project(':libs:java-security')` itself. Twelve explicit lines beat one implicit graph.

### D3 — The isolation is asserted by the build, not by intent

> 🔴 **Corrected by `TASK-MONO-378`.** The sentence below says AC-6 *"already asserts"* this. **It does not.** `ADR-MONO-048` § D1's AC-6 was written as prose and **never became a task** — grep the repo before believing it. D5-1 built the first executable form: `assertClasspathNeutrality` (`libs:java-security`), `assertNoServletOnReactiveEdge` (`libs:java-gateway`) and `assertNoApiOnSharedLibs` (root), all wired into `check` and all mutation-verified. **This is the fifth claim in this ADR's lineage that was true only as an intention.** *(An assertion nobody has watched fail is a comment with a `Task` around it — § 6 says so, and § D3 was itself the counter-example.)*

`ADR-MONO-048` § D1's AC-6 ~~already asserts~~ **states, as prose only,** *"SCG/WebFlux on a servlet service's `runtimeClasspath` = 0."* Make it executable, and add the converse:

- `libs:java-security` `runtimeClasspath` contains **0** WebFlux, **0** Spring Cloud Gateway, **0** `jakarta.servlet` entries. *(Neutrality is a property of the module, so the build should be the one holding it — not a comment.)*
- `libs:java-gateway` `runtimeClasspath` contains **0** `jakarta.servlet` entries.
- Each servlet service's `runtimeClasspath` contains **0** WebFlux / SCG entries. *(Unchanged, extended to the new module.)*

**A mutation must show each assertion fails when violated.** An assertion nobody has watched fail is a comment with a `Task` around it.

### D4 — The servlet services adopt the MONO-355 builder, and inherit its closed-by-default discipline

> 🔴 **Corrected by § 1.6.** The paragraph below is true of finance and erp and of **no other project**. Fleet-wide the servlet gates implement **four** policies (wms: no wildcard; fan: no `entitled_domains`; iam: neither), each **correctly mirroring its own gateway**. The builder is still the right mechanism — that is what makes the four expressible — but it decides the gate for **26 edges (6 gateways + 20 services)**, not 12.

All six servlet `TenantClaimValidator` copies implement exactly: *legacy `tenant_id ∈ {expected, "*"}`* **or** *signed `entitled_domains` contains the domain*. That maps onto the existing builder with no behavioural change:

```java
TenantClaimValidator.forTenant("finance")
        .allowSuperAdminWildcard()
        .trustEntitledDomains()
        .build();
```

**The risk this creates, stated plainly:** one file now decides the tenant gate for **12 edges** — 6 gateways and 6 services. `TASK-MONO-355` already met this and answered it: ***every switch defaults closed; a shared security class whose defaults open the gate is one typo away from opening all of them.*** That discipline is **carried forward verbatim**, and the module's own suite must assert, for each switch, both what it **admits** and what it **refuses** — MONO-355's other lesson, learned when it found that wms's rejection of the `*` wildcard, the single most distinctive gate in the fleet, had **zero test coverage**.

### D5 — Migration in **eight** PRs, each leaving `main` green — **scope A: all 20 services**

> ✅ **Widened by § 1.7 (`TASK-MONO-377`, ACCEPT).** The four-step list below used to stop at finance+erp — 6 of 20 — which would have left **12 byte-identical copies of `AllowedIssuersValidator`**, a class this ADR declares canonical in the same breath. § 1.6 called that *"this ADR's own thesis happening to this ADR"*, and it was right. **The steps below are derived from § 1.7's per-service matrix, not from the project list** — which is why wms and iam never wire the servlet module, and two scm services never touch a validator.

**Every step is independently mergeable and leaves `main` green. They are strictly serialised** — steps 1–2 create the shared modules that 3–8 consume, and all of them touch `libs/` and `settings.gradle`, so a parallel step is a merge conflict by construction. **Ticket each step only when its predecessor has landed** (a `ready/` queue full of blocked, conflicting tasks is an invitation for two sessions to collide).

| step | scope | copies deleted | new wiring |
|---|---|---|---|
| **D5-1** | Move `AllowedIssuersValidator` + `TenantClaimValidator` (and their two tests) **out of `libs/java-gateway`, into the existing `libs/java-security`**; add `spring-security-oauth2-jose` there. **No servlet service touched.** Behaviour identical; the 6 gateway suites are the proof. **⚠️ Not "no new wiring" — see § 1.7:** `java-gateway` itself uses both classes (`GatewayJwtDecoders`, `JwtClaims`) and **3 of the 6 gateways do not declare `java-security`**, so **4 dependency lines are new** and all 6 gateways' imports change. | 0 | **4** × `java-security` |
| **D5-2** | **`libs/java-security-servlet`** — the one new module; canonical `TenantClaimEnforcer`. No consumer yet. | 0 | 1 `settings.gradle` line |
| **D5-3** | **finance** — account, ledger | 6 | 2 × servlet |
| **D5-4** | **erp** — approval, masterdata, notification, read-model | 12 | 4 × servlet |
| **D5-5** | **scm** — procurement *(all three)*; demand-planning + inventory-visibility *(**Enforcer only** — no validator migration)* | 5 | 3 × servlet |
| **D5-6** | **fan** — artist, community, membership, notification | 12 | 4 × servlet |
| **D5-7** | **wms** — admin, inbound, inventory, master, outbound. **Validators only; wms holds no Enforcer, so it never wires the servlet module.** `admin-service` is the **one service in the fleet** whose `build.gradle` lacks `libs:java-security` — that single line is the only new `java-security` wiring in the whole migration. | 10 | 1 × `java-security` |
| **D5-8** | **iam** — community, membership. **Validators only** (no Enforcer). *(The iam **gateway** stays out of scope — § D6. Its **servlet services** do not.)* | 4 | — |
| | | **49** | |

Per-domain tests **stay per-domain**: they pin *that domain's* gate policy, and MONO-355 is the standing evidence that a suite which only records what a gate accepts will not notice when what it refuses changes. **The four policy shapes (§ 1.6) are expressed through the builder, never preserved as copies** — that is what makes them expressible at all. What moves into the modules' own suite is the **class-level** contract (malformed `entitled_domains` → fail-closed; absent `tenant_id` → reject; issuer not in the allowlist → reject). **That is what closes § 1.3: all six domains inherit that coverage by construction, including the nine copies that have none today.**

Per-domain tests **stay per-domain**: they pin *that domain's* gate policy, and MONO-355 is the standing evidence that a suite which only records what a gate accepts will not notice when what it refuses changes. What moves into the modules' own suite is the **class-level** contract (malformed `entitled_domains` → fail-closed; absent `tenant_id` → reject; issuer not in the allowlist → reject). **That is what closes § 1.3: all six domains inherit that coverage by construction, including the ten copies that have none today.**

### D6 — Non-goals

- **Removing the service-level validators.** They are **load-bearing**, not vestigial: `TASK-MONO-361` verified that console-bff still reaches these backends **directly** (`CONSOLE_BFF_OUTBOUND_FINANCE_BASE_URL: http://finance-account-service:8080`), never crossing the gateway. The gateway fronts only the `finance.local` / `erp.local` hostname. **This ADR de-duplicates the second layer. It does not delete it.**
- **The iam gateway — ✅ audited (`TASK-MONO-365`), and the earlier framing here was wrong.** The first draft said it *"hand-rolls `TokenValidator` / `JwksCache` / `TokenBucketRateLimiter`"* and implied a safety gap. **It does not hand-roll the crypto**: its `TokenValidator` delegates to **`libs/java-security`'s `Rs256JwtVerifier`** — the same shared verifier the rest of the platform uses (and the discovery that produced § 1.5). The audit found it **strips spoofed identity headers**, and its strip set (`X-Account-ID` / `X-Device-Id` / `X-Tenant-Id`) **exactly matches what its downstream actually reads** — no forgeable-header gap; the set is narrower because an IdP's downstream vocabulary is narrower, not because it is missing anything. Its tenant legacy-fallback **defaults closed**, and its rate limiter is **Redis + an atomic Lua script** (distributed, not per-instance). **It is not a shared-library gap. It is a different, defensible design** — and this ADR's scope does not include it.
  **What the audit *did* find is a live issuer defect, and it is `TASK-MONO-365`'s subject, not this ADR's:** the iam gateway pins a **single** `expected-issuer`, defaulting to the **legacy** `iam` — while the other six gateways carry a CSV **allowlist** (SAS issuer **+** legacy). `TASK-BE-398` retires the legacy custom-JWT flow that mints `iss=iam`. **When it lands, iam's own edge accepts nothing.**
- ~~**wms rate-limit keying** (IP-only, unnamespaced, no recorded rationale — open since MONO-355, awaiting a human).~~ **✅ Resolved 2026-07-12, and it did not resolve the way this line assumed.** wms was not the outlier: `platform/api-gateway-policy.md` made it the *only conformer*. The real defect was **ecommerce**, whose per-tenant bucket was **vacuous** — the `tenant_id` claim it keyed on is minted from the initiating OAuth client, so it is a **constant** for all shopper traffic and every shopper shared one bucket (`TASK-MONO-368`). `TASK-MONO-370` then aligned wms to the rule and removed five synthetic `acct:null` buckets across scm/fan/finance/erp. The policy now states the rule (*"a claim that your own config pins to a constant is not a bucket"*) and `check-gateway-drift.sh` I4 guards it. **This ADR's non-goal list was itself a stale declaration** — the exact class it exists to argue against.

---

## 4. Cost — measured, per `TASK-MONO-364` AC-5

> 🔴 **Every number in § 4 and § 4.1 is scoped to § 1.1 — i.e. to finance and erp only. See § 1.6.** Fleet-wide: **49 copies / 3,522 lines / 20 services / 6 projects**; `AllowedIssuersValidator` exists **19** times, not 7; copies with no test are **9 of 49**, not 10 of 18.
>
> ✅ **D5 *was* widened (scope A, `TASK-MONO-377`). The fleet-wide cost is § 1.7's table, not this one — and it is cheaper than this note feared.** The deletion triples (18 → **49**), but the build-graph cost barely moves: **1** new module, **1** new `settings.gradle` line, **13** `java-security-servlet` wirings (only the Enforcer holders — not 20), and **1** new `java-security` wiring in the entire fleet (`wms/admin-service`, the sole service of the 20 that does not already declare it). *"The consumers needing servlet-module wiring go from 6 to 20"*, written above before anyone counted, was **itself an inherited guess**.

`ADR-MONO-048` claimed a gateway was ~15 hand-copied classes and only proved it at the last step (it was 16; **~81% of a gateway is the library**). This ADR answers its own version of that question **before** asking for a decision.

| | files | lines |
|---|---|---|
| copies deleted | **18** | **−1,427** |
| canonical retained | 3 | ~344 (2 of which — 244 lines — **already exist** in `libs/java-gateway` and merely relocate) |
| genuinely new code | 1 (`TenantClaimEnforcer`) | **~104** |
| | | |
| **net source removed** | **−15 files** | **≈ −1,320 lines** |

And the number that actually matters:

| | before | after |
|---|---|---|
| copies of `AllowedIssuersValidator` | **7** | **1** |
| copies of the tenant gate | 6 servlet + 1 lib | **1** |
| security copies with **no test** | **10 of 18** | **0 of 3** |

**The extraction is not marginal, and "keep the copies" would have been the wrong call.** But that conclusion is a *result* here, not a premise — § 2.3 was a live option until § 4 was measured.

### 4.1 Re-costed after § 1.5 — **it is cheaper than the table above says**

The first draft priced in **two new Gradle modules and twelve consumers to wire**. `libs/java-security` **already exists and all twelve already consume it**, so:

| | first draft | actual |
|---|---|---|
| new Gradle modules | 2 | **1** (`libs/java-security-servlet`) |
| `settings.gradle` lines added | 2 | **1** |
| consumers needing new wiring | 12 | **6** (the servlet services, for the servlet module only) |
| new dependency on an existing module | — | **1** (`spring-security-oauth2-jose` on `libs/java-security` — reaches **32** consumers' runtime classpath; § D3 V1 asserts what it must *not* bring) |

**The source-deletion numbers above are unchanged.** What shrinks is the build-graph cost — which is the half of the price § 2.3 was weighing.

---

## 5. Consequences

**Good.** One definition of "which issuers are valid" and "which tenants may pass" across 12 edges. The ten untested copies stop existing. A fix reaches everything or nothing — which is the entire point, and the thing `FailOpenRateLimiter` proved we do not currently get.

**Bad / risky.** One file decides the tenant gate for 12 edges (D4). The blast radius of a mistake is now the whole platform rather than one service — which is exactly why D4 carries MONO-355's closed-by-default rule forward and why D3 makes the build, not a comment, hold the isolation. **This trade is the point of the ADR, not a footnote to it:** we are exchanging *six quiet failures* for *one loud one*.

**Cost.** Two new Gradle modules; four PRs; 18 files deleted and 12 build files touched. No runtime surface changes, no contract changes, no behaviour change — every step is provable by the existing suites.

### 5.1 ✅ D5 is complete (`TASK-MONO-392`, 2026-07-13) — and the population was never the interesting number

**Copies: 49 → 0**, across eight serialised steps, `main` green at every one. Zero behaviour changes; one observable error-code change, stated rather than smuggled (§ D5-5). The three classes are one class each.

**What the migration actually turned up is worth more than the deletion.** Every step was supposed to be mechanical, and every step found something the census could not see:

| step | what it found |
|---|---|
| D5-3 | the *canonical* class had reintroduced the decode-pass / filter-block split the copies were written to avoid (§ 1.9) |
| D5-5 | two services held the same logic as **inline lambdas** — `^public class` cannot see a lambda (§ 1.10) |
| D5-6 | membership's `/internal/**` exemption was **load-bearing and completely undefended**; the mutation that proved it load-bearing also proved nothing was watching it |
| D5-7 | wms refuses the SUPER_ADMIN wildcard, and **not one test asserted the refusal** — the single property distinguishing it from the fleet (`TASK-MONO-355` called this shot) |
| D5-8 | the policy had a **second and third home in the tests**; production could be made to reject every token and **50 suites stayed green** (§ 1.12) |

**The through-line is one sentence, and it is not about duplication.** Duplication was the *symptom* — the thing that let six copies drift. The disease is that **none of these properties were being watched.** A copy that nothing observes and a canonical class that nothing observes fail the same way; unifying them only helps because it makes one place worth watching.

> So the ADR's title undersold it. *Eighteen hand-copied validators are only eight tests away from a fix that lands nowhere* — true, but the eight tests were the point all along. **We did not have a duplication problem with a testing footnote. We had a testing problem that duplication made expensive.**

**And the counting never did become reliable.** The population was wrong six times before D5 began (§ 1.6, § 1.10), and D5-8 — in the very step after the one whose notes recorded that "grep was finally complete" — found `membership` declaring the library as `testImplementation` while a `grep -c` reported it present. *A count is only as honest as its predicate, and every predicate here was eventually wrong.* What caught each one was never a better grep. It was the compiler, or a mutation.

---

## 6. Verification

How each decision is shown to hold — **and how each is shown to fail when violated.** An assertion nobody has watched fail is a comment with a `Task` around it.

| # | assertion | mutation that must turn it red |
|---|---|---|
| V1 (D1) | `libs:java-security` `runtimeClasspath` has **0** WebFlux, **0** Spring Cloud Gateway, **0** `jakarta.servlet` entries | add `spring-boot-starter-web` to the module → red |
| V2 (D1) | `libs:java-gateway` `runtimeClasspath` has **0** `jakarta.servlet` entries | make `java-gateway` depend on `java-security-servlet` → red |
| V3 (D1) | each servlet service's `runtimeClasspath` has **0** WebFlux / SCG entries *(extends ADR-048 § D1 AC-6)* | make a servlet service depend on `java-gateway` → red |
| V4 (D2) | no `api project(':libs:java-security')` anywhere | change one consumer's `implementation` to `api` → red |
| V5 (D4) | the shared `TenantClaimValidator`'s switches are **closed by default**, and **every switch narrows**; each domain's suite asserts both what its gate **admits** and what it **refuses** | drop `trustEntitledDomains` from one domain → that domain's suite goes red *(MONO-357 ran the equivalent mutation on `acceptAnyWellFormedTenant`; it bit. That switch — the one switch here that **opened** a gate — was removed by **TASK-MONO-388**: ecommerce reaches its edge through entitlement like every other domain, and `TenantGatePolicyLeakTest` now asserts the builder exposes no opening switch at all.)* |
| V6 (D5) | behaviour unchanged at every step | the existing per-domain suites pass **unmodified** — if a suite has to change, the migration changed behaviour |
| **V7 (D5-8)** | **each service's tests watch the gate production wires** — slice configs and integration bases take their validator chain from `OAuth2ResourceServerConfig#jwtTokenValidator()`, never a hand-built one | **poison production's chain so it rejects every token → that service's suites go red.** If they stay green, the tests are asserting against a copy of the policy and the gate is unwatched *(§ 1.12: before D5-8 this mutation left **50 suites green**)* |

**V5 is the one that matters.** MONO-355 found that wms's rejection of the `*` SUPER_ADMIN wildcard — the most distinctive gate in the fleet, and the one ADR-048 § D5 names by reason — had **zero test coverage**. A suite that records only what a gate accepts will not notice when what it refuses changes.

**V7 is V5's shadow, and it took until the last step to see it.** V5 asks whether a suite asserts the refusals. V7 asks a question that has to come *first*: **is the suite even looking at the real gate?** iam's tests asserted refusals diligently — against a validator chain they had built themselves. Every assertion passed, and none of them was watching production. *A test that owns its own copy of the subject is not a test of the subject.*

## 7. Roadmap — **ACCEPTED, scope A** (`TASK-MONO-377`, 2026-07-13)

Per `TASK-MONO-364` § AC-6 the roadmap lives here, not in `tasks/ready/`. **Ticket each step only once its predecessor has landed** — the steps are strictly serialised (they all touch `libs/` and `settings.gradle`), so a `ready/` queue holding all eight would be eight tasks that cannot be started and two sessions that collide trying.

| step | scope | copies | status |
|---|---|---|---|
| **D5-1** | move the two neutral validators `java-gateway` → `java-security` (`com.example.security.oauth2`) | 0 | **✅ `TASK-MONO-378`** — 4 dependency lines, 6 gateways' imports, **0 assertions changed**. V1/V2/V4 built and mutation-verified. |
| **D5-2** | `libs/java-security-servlet` + canonical `TenantClaimEnforcer` | 0 | **✅ `TASK-MONO-382`.** The 13 copies hold **eight distinct bodies**, which reduce to **three policy axes** — everything else was line-wrapping and an inlined `@Value`. **The axes are recorded in § 1.8, because one of them is a security boundary and D5-3…D5-8 have to carry it.** Canonical class is builder-parameterised, **every switch closed by default** (including the wildcard, which all 13 copies enable — *a default is what you get when someone forgets*). 19 tests; each switch asserted **on *and* off**. |
| **D5-3** | finance — account, ledger | 6 | **✅ `TASK-MONO-383`** — the first deletion: **49 → 43**. All three switches wired explicitly and **mutation-verified** (remove any one → finance's suite goes red). **`ledger-service` has test coverage for these classes for the first time** — the service § 1.3 names (all three classes, zero tests) now holds 20 policy assertions. **V6 earned its keep on its first run: it caught a latent decode-pass / filter-block split in D5-2's canonical class — see § 1.9.** |
| **D5-4** | erp — approval, masterdata, notification, read-model | 12 | **✅ `TASK-MONO-384`** — **43 → 31**, the largest single-project deletion. Policy measured, not inherited: wildcard 4/4, entitlement 4/4, `PublicPaths` 4/4 identical. **Mutation-verified 12 times** (3 switches × 4 services), each run printing the removed line first. 477 tests / 0 skipped / 0 failures. **Two referencer surfaces finance did not have**: `ReadAuthorizationGate` calls `TenantClaimValidator.isEntitled` from production code, and a `@WebMvcTest` slice `@Import`-ed the filter *as a component class* — the shared one has a private constructor. The slice now imports the real `ServiceLevelOAuth2Config` rather than a test double that re-states the switches: **a second place to write the policy is the thing this ADR exists to remove.** |
| **D5-5** | scm — procurement (all 3); demand-planning + inventory-visibility (Enforcer + **two inline validator implementations** — § 1.10) | 5 | **✅ `TASK-MONO-385`** — **31 → 26**, and the two inline implementations are gone (fleet-wide zero). **§ 1.8's exemption axis is decided: narrowed, and the narrowing is proven unobservable** — Spring Security's chain runs *before* the enforcer, so the actuator paths that lost the exemption were already `denyAll`'d and the filter never saw them. **The permit list and the exemption are now one object**, which is what § 1.8 actually asked for. One observable change, stated: the issuer error code moves `invalid_token` → `invalid_issuer` (status and response `code` unchanged; zero consumers; it aligns scm with erp/fan/finance). Mutation-verified 9×. 351 tests / 0 failures. |
| **D5-6** | fan — artist, community, membership, notification | 12 | **✅ `TASK-MONO-387`** — **26 → 14**. **The first project that leaves a switch OFF**, and the first whose mutation runs in *both* directions: removing `.allowSuperAdminWildcard()` reds 2 assertions, **and *adding* `.trustEntitledDomains()` reds 3.** A switch only ever tested in the ON position is not tested; here the untested position was OFF. § 1.9's null branch degenerates to the unconditional 401 fan's copies wrote by hand — asserted. **`membership`'s `/internal/**` exemption: load-bearing, kept** — and § 1.8 records what the mutation actually exposed. 381 tests / 0 failures. |
| **D5-7** | wms — admin, inbound, outbound, inventory, master (**validators only**) | 10 | **✅ `TASK-MONO-390`** — **14 → 4**, and **`TenantClaimEnforcer` was already at zero: one of the three classes is finished.** The mutation direction **inverts here** — wms is the only platform that refuses the SUPER_ADMIN wildcard, so the dangerous edit is *adding* `.allowSuperAdminWildcard()`, not omitting it. **Mutated in both directions, 5/5 services each:** adding the wildcard reds **1 per service** (the assertion that did not exist before this task — `TASK-MONO-355` found this gate had **zero coverage for its rejection**), removing `.trustEntitledDomains()` reds **3 per service**. **No design was needed: wms's own gateway has been running the identical shared chain since `ADR-MONO-048` § D7**, so the servlet side aligned with a wiring the same project already proves — branch equivalence measured down to the failure-message strings, error codes unchanged (which matters: all five `SecurityConfig`s key their 403 `TENANT_FORBIDDEN` off `ERROR_CODE_TENANT_MISMATCH`). 75 policy assertions / 0 skipped / 0 failures; `compileTestJava` green across all 7 wms modules. See § 1.11. |
| **D5-8** | iam — community, membership (**validators only**; the **gateway** stays out — § D6) | 4 | **✅ `TASK-MONO-392`** — **4 → 0. D5 is complete.** iam is the only project that calls **neither** switch, so every meaningful mutation runs in the *adding* direction: with a closed-by-default builder, *forgetting* one is not a reachable mistake. Both refusals asserted; adding either switch reds both services. **But the four files were the easy part.** Both services wrote the tenant policy a **second and third time** — in `SliceTestSecurityConfig` and in their integration bases — and a canary mutation proved the cost: **poison production's gate so it rejects every token, and before this task 50 test suites stayed green. After it, 36 assertions fail.** The tests now delegate to `OAuth2ResourceServerConfig#jwtTokenValidator()` and keep only the (legitimate) key swap. A shared `TenantGateTestConfig` was deliberately **not** created — that would be a third copy wearing de-duplication's clothes. See § 1.12, which also records the last miscount: `membership` declared the library as `testImplementation`, and the grep that said otherwise was counting text, not dependencies. 237 tests / 0 failures / 0 skipped. |
| | | **49 → 0** | **✅ complete** |

Carried forward, **not** resolved by this ADR (§ D6):

- **iam gateway audit** — the only gateway that does not consume `libs/java-gateway`; it hand-rolls JWT validation, JWKS caching and rate limiting. Excluded from ADR-048 on **cost** grounds; **never approved on safety grounds.** ⚠️ **Do not read this as excluding iam.** `TASK-MONO-365` audited the gateway and found it defensible; iam's **servlet services** (community, membership) are **in scope** — D5-8.
- ~~**wms rate-limit keying.**~~ **✅ Resolved** — `TASK-MONO-368` / `TASK-MONO-370`. See § D6.

---

**`ACCEPTED` 2026-07-13 (`TASK-MONO-377`), scope A.** The gate held: this document sat at `PROPOSED` through three tasks that each wanted to start implementing, and the count it would have been implemented against was wrong every one of those times.
</content>
