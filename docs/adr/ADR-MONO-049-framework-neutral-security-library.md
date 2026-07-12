# ADR-MONO-049 — `libs/java-security`: a **framework-neutral** security library, because 18 hand-copied validators are only 8 tests away from a fix that lands nowhere

**Status:** PROPOSED

**Date:** 2026-07-12 (PROPOSED)

**Decision driver:** `ADR-MONO-048` extracted `libs/java-gateway` from four copy-pasted **reactive** edges. While closing its last step, § 1.1 was corrected twice — and it was still wrong. The two security validators it counted at four copies each exist as **six more copies each inside the finance/erp servlet services**, plus a third class it never counted at all. `TASK-MONO-361` measured the real shape: **3 classes × 6 copies = 18 files**. This ADR decides whether to remove them.

**Supersedes:** none. **Extends** `ADR-MONO-048` § 1.1, whose duplication count was low twice over (4 copies → the corrected 10 → the measured **18**, across **3** classes, not 2).

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

### D1 — Two modules, because one of the three classes is not neutral

| module | contents | depends on | consumed by |
|---|---|---|---|
| **`libs/java-security`** | `AllowedIssuersValidator`, `TenantClaimValidator` | `spring-security-oauth2-jose` only | `libs/java-gateway` **and** the 6 servlet services |
| **`libs/java-security-servlet`** | `TenantClaimEnforcer` | `libs/java-security` + `spring-web` + `jakarta.servlet-api` | the 6 servlet services **only** |

**The rejected shortcut, named so it is not quietly re-attempted:** put all three in one module and mark the servlet dependencies `compileOnly`. That compiles. It also puts a `jakarta.servlet`-bound class on the **reactive gateways'** classpath, where the servlet API does not exist at runtime. It would work — until something scans or reflects over it, and then it fails at boot, in the edge, in production. **A class that only works because nobody has loaded it yet is the failure class this entire line of work has been chasing.** Two modules cost one `settings.gradle` line. Take the line.

`libs/java-gateway` depends on `java-security` and **never** on `java-security-servlet`. The reactive edge cannot see the servlet filter at all.

### D2 — Every consumer declares `java-security` directly. No `api`, anywhere.

The six gateway `OAuth2ResourceServerConfig` classes import `TenantClaimValidator` **by name**. The tempting fix is `api project(':libs:java-security')` in `java-gateway`, so it transits.

**No.** `ADR-MONO-048` § D1 banned `api` on the gateway library, and the ban should not acquire an exception the first time it is inconvenient — an `api` edge is how a "neutral" module starts transiting things that are not. Each of the 12 consumers (6 gateways + 6 servlet services) declares `implementation project(':libs:java-security')` itself. Twelve explicit lines beat one implicit graph.

### D3 — The isolation is asserted by the build, not by intent

`ADR-MONO-048` § D1's AC-6 already asserts *"SCG/WebFlux on a servlet service's `runtimeClasspath` = 0."* Extend it, and add the converse:

- `libs:java-security` `runtimeClasspath` contains **0** WebFlux, **0** Spring Cloud Gateway, **0** `jakarta.servlet` entries. *(Neutrality is a property of the module, so the build should be the one holding it — not a comment.)*
- `libs:java-gateway` `runtimeClasspath` contains **0** `jakarta.servlet` entries.
- Each servlet service's `runtimeClasspath` contains **0** WebFlux / SCG entries. *(Unchanged, extended to the new module.)*

**A mutation must show each assertion fails when violated.** An assertion nobody has watched fail is a comment with a `Task` around it.

### D4 — The servlet services adopt the MONO-355 builder, and inherit its closed-by-default discipline

All six servlet `TenantClaimValidator` copies implement exactly: *legacy `tenant_id ∈ {expected, "*"}`* **or** *signed `entitled_domains` contains the domain*. That maps onto the existing builder with no behavioural change:

```java
TenantClaimValidator.forTenant("finance")
        .allowSuperAdminWildcard()
        .trustEntitledDomains()
        .build();
```

**The risk this creates, stated plainly:** one file now decides the tenant gate for **12 edges** — 6 gateways and 6 services. `TASK-MONO-355` already met this and answered it: ***every switch defaults closed; a shared security class whose defaults open the gate is one typo away from opening all of them.*** That discipline is **carried forward verbatim**, and the module's own suite must assert, for each switch, both what it **admits** and what it **refuses** — MONO-355's other lesson, learned when it found that wms's rejection of the `*` wildcard, the single most distinctive gate in the fleet, had **zero test coverage**.

### D5 — Migration in four PRs, each leaving `main` green

1. **`libs/java-security`** — move the two neutral classes out of `java-gateway`; `java-gateway` and the 6 gateways consume it. **No servlet service touched.** Behaviour identical; the 6 gateway suites are the proof.
2. **`libs/java-security-servlet`** — canonical `TenantClaimEnforcer`. No consumer yet.
3. **finance** (2 services) — delete 6 copies, wire the modules.
4. **erp** (4 services) — delete 12 copies, wire the modules.

Per-domain tests **stay per-domain**: they pin *that domain's* gate policy, and MONO-355 is the standing evidence that a suite which only records what a gate accepts will not notice when what it refuses changes. What moves into the modules' own suite is the **class-level** contract (malformed `entitled_domains` → fail-closed; absent `tenant_id` → reject; issuer not in the allowlist → reject). **That is what closes § 1.3: all six domains inherit that coverage by construction, including the ten copies that have none today.**

### D6 — Non-goals

- **Removing the service-level validators.** They are **load-bearing**, not vestigial: `TASK-MONO-361` verified that console-bff still reaches these backends **directly** (`CONSOLE_BFF_OUTBOUND_FINANCE_BASE_URL: http://finance-account-service:8080`), never crossing the gateway. The gateway fronts only the `finance.local` / `erp.local` hostname. **This ADR de-duplicates the second layer. It does not delete it.**
- **The iam gateway.** It is the one gateway that does **not** consume `libs/java-gateway`: it hand-rolls `TokenValidator` / `JwksCache` / `TokenBucketRateLimiter` instead of using Spring Security's Resource Server. `ADR-MONO-048` excluded it on **cost** grounds and never approved it on **safety** grounds. That is a separate audit, and it should happen.
- **wms rate-limit keying** (IP-only, unnamespaced, no recorded rationale — open since MONO-355, awaiting a human).

---

## 4. Cost — measured, per `TASK-MONO-364` AC-5

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

---

## 5. Consequences

**Good.** One definition of "which issuers are valid" and "which tenants may pass" across 12 edges. The ten untested copies stop existing. A fix reaches everything or nothing — which is the entire point, and the thing `FailOpenRateLimiter` proved we do not currently get.

**Bad / risky.** One file decides the tenant gate for 12 edges (D4). The blast radius of a mistake is now the whole platform rather than one service — which is exactly why D4 carries MONO-355's closed-by-default rule forward and why D3 makes the build, not a comment, hold the isolation. **This trade is the point of the ADR, not a footnote to it:** we are exchanging *six quiet failures* for *one loud one*.

**Cost.** Two new Gradle modules; four PRs; 18 files deleted and 12 build files touched. No runtime surface changes, no contract changes, no behaviour change — every step is provable by the existing suites.

---

## 6. Verification

How each decision is shown to hold — **and how each is shown to fail when violated.** An assertion nobody has watched fail is a comment with a `Task` around it.

| # | assertion | mutation that must turn it red |
|---|---|---|
| V1 (D1) | `libs:java-security` `runtimeClasspath` has **0** WebFlux, **0** Spring Cloud Gateway, **0** `jakarta.servlet` entries | add `spring-boot-starter-web` to the module → red |
| V2 (D1) | `libs:java-gateway` `runtimeClasspath` has **0** `jakarta.servlet` entries | make `java-gateway` depend on `java-security-servlet` → red |
| V3 (D1) | each servlet service's `runtimeClasspath` has **0** WebFlux / SCG entries *(extends ADR-048 § D1 AC-6)* | make a servlet service depend on `java-gateway` → red |
| V4 (D2) | no `api project(':libs:java-security')` anywhere | change one consumer's `implementation` to `api` → red |
| V5 (D4) | the shared `TenantClaimValidator`'s switches are **closed by default**; each domain's suite asserts both what its gate **admits** and what it **refuses** | enable `acceptAnyWellFormedTenant` on one domain → that domain's suite goes red *(this is exactly the mutation MONO-357 ran; it bit)* |
| V6 (D5) | behaviour unchanged at every step | the existing per-domain suites pass **unmodified** — if a suite has to change, the migration changed behaviour |

**V5 is the one that matters.** MONO-355 found that wms's rejection of the `*` SUPER_ADMIN wildcard — the most distinctive gate in the fleet, and the one ADR-048 § D5 names by reason — had **zero test coverage**. A suite that records only what a gate accepts will not notice when what it refuses changes.

## 7. Outstanding follow-ups (**do not create these tasks until ACCEPT**)

Per `TASK-MONO-364` § AC-6, the roadmap lives here, not in `tasks/ready/`:

| step | scope |
|---|---|
| D5-1 | `libs/java-security` + `java-gateway` and 6 gateways migrated |
| D5-2 | `libs/java-security-servlet` + canonical `TenantClaimEnforcer` |
| D5-3 | finance (2 services, 6 copies deleted) |
| D5-4 | erp (4 services, 12 copies deleted) |

Carried forward, **not** resolved by this ADR (§ D6):

- **iam gateway audit** — the only gateway that does not consume `libs/java-gateway`; it hand-rolls JWT validation, JWKS caching and rate limiting. Excluded from ADR-048 on **cost** grounds; **never approved on safety grounds.**
- **wms rate-limit keying** — IP-only, unnamespaced, no recorded rationale. Open since MONO-355, awaiting a human.

---

**`PROPOSED → ACCEPTED` requires the user's exact intent (`ADR-MONO-049 ACCEPTED`). Agent self-ACCEPT is forbidden; implementing before ACCEPT is HARDSTOP-09.**
</content>
