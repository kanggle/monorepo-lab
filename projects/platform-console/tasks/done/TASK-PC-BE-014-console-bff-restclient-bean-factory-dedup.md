# TASK-PC-BE-014 — Collapse console-bff RestClient @Bean factory duplication (behavior-preserving)

**Status:** done

**Type:** TASK-PC-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (mechanical extraction, behavior-preserving)

> Filed from the 2026-07-21 reconciliation-audit refactoring rescan of console-bff (candidate C1). Grep-verified 6×.
> console-bff has already been through several dedup passes (`AbstractHealthReadAdapter`, `RestClientHelper`,
> `CompositionEngine`); this is the one clear remaining ≥3 hit.

---

## Goal

`RestClientConfig` defines **6 byte-identical `RestClient` `@Bean` factory methods** — `gapRestClient`, `wmsRestClient`,
`scmRestClient`, `financeRestClient`, `erpRestClient`, `ecommerceRestClient` — each is
`builder.clone().baseUrl(baseUrl).requestFactory(timeoutRequestFactory()).build()`, differing only in bean name, method name,
and the `@Value` property key. Collapse the shared body into one private helper, with **zero behavior change**.

## Scope

**In scope:** `projects/platform-console/apps/console-bff/src/main/java/.../infrastructure/config/RestClientConfig.java` —
extract a private `RestClient build(RestClient.Builder builder, String baseUrl)` (or equivalent) that all 6 `@Bean` methods
delegate to. **Bean names and any `@Qualifier` semantics MUST be preserved verbatim** — downstream adapters `@Qualifier` on
these bean names, so the beans must keep their exact names/types.

**Out of scope:** the 6 single-method health-read port interfaces (candidate C2 — **declined**: the marker-interface-per-bean
pattern is a deliberate Spring DI idiom here, and collapsing to one `@Qualifier`-disambiguated interface is a real wiring
change of debatable value); the `legBody`/`bearerFromCred` (2×) and dashboard `toCard` (2×) items (**deferred**, rule-of-2 —
extract when a 3rd credentialed fan-out / dashboard route appears).

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm at current `main` that the 6 factory methods are still byte-identical
  apart from name/property-key (a 7th domain may have been added, or one may have diverged with a custom interceptor). Adjust
  the set to whatever is still identical.
- **AC-1** — One shared helper; all identical `@Bean` methods delegate to it. Bean names/qualifiers unchanged.
- **AC-2 (behavior-preserving)** — Same base URLs, same `timeoutRequestFactory`, same builder clone semantics. Existing
  console-bff tests stay GREEN unchanged.

## Related Specs
- `projects/platform-console/specs/services/console-bff/architecture.md` (outbound HTTP client design, ADR-MONO-017 D4)

## Related Contracts
- None (behavior-preserving; the per-domain credential-dispatch contract is unaffected — this is client construction only).

## Edge Cases
- A domain bean that has (or later gains) a custom interceptor/timeout must NOT be folded into the shared helper — keep it
  as its own method.

## Failure Scenarios
- **F1 — renaming a bean during extraction** breaks `@Qualifier` injection in the adapters. Guarded by AC-1.
