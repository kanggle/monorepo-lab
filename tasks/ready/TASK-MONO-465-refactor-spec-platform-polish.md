# TASK-MONO-465 — refactor-spec platform: residual polish (anchor / glossary dedup / localization)

# Status

ready

# Owner

backend

# Task Tags

- adr

---

# Goal

`/refactor-spec platform` dry-run (2026-07-22) over all 37 `platform/**` files. Prior reconciliation (TASK-MONO-411) already pointer-ized most duplication, so only residual structural polish remained. This task lands the **four meaning-preserving fixes** and records the items deliberately skipped after verification and the semantic findings that are out of refactor-spec scope.

After this task, the four cited anchors/dedup/localization defects are gone; no requirement, contract, or decision changed.

---

# Scope

## In Scope (applied — all meaning-preserving)

1. **dead-anchor** `platform/contracts/jwt-standard-claims.md:37` — `§ Key Management` → `§ Key Management Rules` (the target heading in `service-types/identity-platform.md` is `Key Management Rules`).
2. **dead-anchor** `platform/contracts/jwt-standard-claims.md:91` — `§ Gateway Enforcement` → `§ Gateway Enforcement Rules` (this file's own heading is `Gateway Enforcement Rules`).
3. **duplication** `platform/glossary.md` — `JWKS` was defined twice (Auth §L20 = key material; Infrastructure §L84 = endpoint). Merged both facets into the single Auth row; deleted the Infrastructure duplicate (single-source-of-truth).
4. **localization** `platform/entrypoint.md:71` — Korean parenthetical in the English file → `(when building or consuming a notification surface)`.

## Out of Scope — skipped after verification (findings, NOT fixed)

- **#6 contracts heading-style** — flattening `notification-inbox-contract.md`'s numbered `## 1.`/`### 1.1` to jwt's repeated-`#` would *remove* useful structure; jwt's repeated-H1 is arguably the outlier, not notification. Debatable direction, churn without clear benefit → left as-is.
- **#8 glossary `archive` lifecycle** — the dry-run proposed appending `→ archive` to the Task lifecycle chain (`glossary.md:34`). **Verification: there is no `tasks/archive/` state** — `archive` appears *only* in the glossary table (L97), not in `tasks/INDEX.md`, no directory, no other reference. The inline chain is correct; the *table* carries the questionable term. Appending would endorse a non-existent state. Needs an owner decision (is `archive` a real task state, or should the glossary table row be removed?) — see Findings.
- **#9 error-handling `---` separators** — the `---` between `## <Section> [domain:…]` blocks are inconsistent, but several mark intentional domain-group boundaries (scm/saas/erp starts) while a few mid-group ones look stray. Mechanical normalization risks destroying group markers or adding ~35 noise separators → left for owner judgment.

## Findings — require a decision/reality-alignment (separate tasks; refactor-spec cannot fix)

- **🔴 `/api/` prefix conflict** — `versioning-policy.md:13` (self-declared canonical, "this section wins") says the `/api/` prefix is mandatory "on every externally-routed endpoint — no exceptions", yet `service-types/identity-platform.md:57-63` lists client-facing `/v1/oauth/authorize|token|logout`, `/v1/accounts/me`, `POST /v1/accounts` **without** `/api/`. A requirement/contract conflict: either add an OAuth/OIDC carve-out to versioning-policy, or reprefix the IAM endpoints. **Needs an ADR-adjacent decision — do not silently pick a side.**
- **🟡 `libs/java-gateway` consumer roster stale** — `shared-library-policy.md:22` + `platform/README.md:61` list 4 consumers (wms, scm, fan, ecommerce); `build.gradle` shows **6** (adds **finance, erp**). Code-verified reality-alignment (add finance + erp to both rosters).
- **🟡 MinIO local read URL vs Local Network Convention** — `object-storage-policy.md:100` shows browser URL `http://localhost:9000/{bucket}/{key}` (implies a published host port on a backing service), contra the "backing services `expose:` only" convention (CLAUDE.md § Local Network Convention / TEMPLATE.md, ADR-MONO-001). Should be a Traefik `*.local` hostname or flagged as the dev-overlay host-port exception.

---

# Acceptance Criteria

- [x] Four in-scope fixes applied; each verified against the actual target heading/table.
- [x] No requirement/contract/decision changed — all four are meaning-preserving.
- [ ] Cross-references in the three modified files still resolve (dead-anchor checker).
- [ ] The three Findings are filed as separate tasks (or explicitly deferred by the owner). The `/api/` conflict must not be auto-resolved.

---

# Related Specs

- `platform/entrypoint.md` (spec reading order — modified)
- `platform/contracts/jwt-standard-claims.md`, `platform/glossary.md` (modified)
- `platform/versioning-policy.md`, `platform/service-types/identity-platform.md` (Finding #1 — `/api/`)
- `platform/shared-library-policy.md`, `platform/README.md` (Finding #2 — roster)
- `platform/object-storage-policy.md` (Finding #3 — MinIO)

# Related Contracts

- None. No API or event contract semantics changed (jwt-standard-claims edits are prose-anchor fixes only).

---

# Edge Cases

- `platform/` is not classifier-blocked → edits/commit/push proceed normally.
- The glossary `archive` term (#8) must NOT be added to the canonical lifecycle line — verification shows it is not a real task state.
- Finding `/api/` conflict touches a "this section wins" canonical file; resolving it wrongly propagates a path-shape violation into IAM implementations.

# Failure Scenarios

- **F1 — appending `→ archive`** would assert a task state that has no directory/reference (guarded: #8 skipped).
- **F2 — normalizing #6/#9 mechanically** would remove load-bearing structure (numbered sections / group separators). Guarded by out-of-scope skips.
- **F3 — auto-fixing the `/api/` conflict** by editing one side without a decision would silently change a contract. Guarded: reported as a Finding, not fixed.
