# console-web e2e suite

Playwright specs for `console-web`, run by the **console-web CI e2e job** against the
committed `docker-compose.e2e.yml` stack.

## What the CI stack contains

`docker-compose.e2e.yml` = **IAM (auth/account/admin) + finance-account + console-bff +
console-web**. It deliberately does **not** contain the business-domain backends
(`ecommerce` / `scm` / `wms` / `erp`). A spec here may only assert surfaces reachable
from that stack.

## Honest-coverage invariant (TASK-PC-FE-248)

> **No spec in this directory may be unconditionally skipped in CI.**

A spec that never executes is worse than no spec: it is counted in the suite, reads as
green coverage in the report, and silently protects nothing. If a flow cannot run on the
stack above, it does not belong in this directory — file a ticket for the capability
instead of parking an env-gated spec here.

To check the invariant holds:

```bash
pnpm exec playwright test --list   # every listed spec must actually run in the CI job
```

There is no env-var gate in this suite. Do not add one.

## Removed: the federation-stack specs (TASK-PC-FE-248, 2026-07-20)

Two specs were deleted rather than enabled:

| Spec | Flow |
|---|---|
| `federation-ecommerce-sellers-multi.spec.ts` | multi-operator → switch to `ecommerce` → `/ecommerce/sellers` renders |
| `federation-omni-all-domains.spec.ts` | omni-operator → switch to `omni-corp` → all 5 overview domain cards entitled |

Both were gated on `PC_FEDERATION_E2E=1`, a flag set in **zero** workflow files, so both
had skipped in every CI run since they were added (TASK-PC-FE-113).

**Why removal and not enablement.** They needed the root `federation-hardening-e2e`
stack, and the parts of that stack they depend on are **intentionally uncommitted**
(`tests/federation-hardening-e2e/.gitignore`):

- the ecommerce backend overlay — `docker-compose.federation-e2e.ecommerce.yml` +
  `.ecommerce-extra.yml`, ~21 containers — is gitignored; **both** specs need it (the
  omni spec asserts the `ecommerce` card is entitled), and
- the omni-corp tenant + its `tenant_domain_subscription` rows — `fixtures/seed-omni-*.sql`
  — are gitignored, and `omni-corp` appears nowhere in the committed `seed.sql`.

So enabling them was not a matter of setting the flag: their entire runtime substrate is
absent from the repo. Standing up an ecommerce-inclusive nightly stack is a separate
epic, tracked as **TASK-PC-FE-250**.

**Coverage that remains — and what is genuinely uncovered.**

- `/ecommerce/sellers` keeps its component/API coverage in `tests/unit/`
  (`ecommerce-sellers-*.test.ts(x)` / `ecommerce-seller-*.test.tsx`, 8 files). Its
  browser-level coverage is now **nil** — but it was nil before this removal too, since
  the spec never ran.
- Per-domain overview **entitlement** is covered nightly by the root
  `tests/federation-hardening-e2e/specs/entitlement-trust-crossdomain.spec.ts`
  (acme-corp: finance/wms entitled, scm/erp gate-rejected).
- The sibling `operator-overview-composition.spec.ts` does **not** add card-level
  coverage despite its title ("renders 5-card grid with all 5 domains showing ok
  status"): its body is MVP-relaxed to URL + page-title + first-heading-visible
  (TASK-MONO-140 cycle 5). Do not read that title as a coverage claim.
- **No spec anywhere asserts the `ecommerce` overview card**, because no CI stack runs
  an ecommerce backend. That gap is real and is what TASK-PC-FE-250 owns.
