# Task ID

TASK-MONO-214

# Title

ADR-MONO-025 § 3.3 step 1 — the ABAC data-scope **contract + shared reader**. Add `platform/abac-data-scope.md` (the authoritative cross-domain data-scope regulation) and `com.example.security.jwt.AbacDataScope` (`libs/java-security`) — a framework-agnostic reader codifying the canonical dual-read (`data_scope` then `org_scope` alias) + the precise semantics (`"*"`=unrestricted; non-empty-non-`*`=scoped deny-by-default; empty/absent=fail-closed deny; net-zero via the producer mapping an unscoped assignment NULL→`["*"]`). Verified against erp's reference `RoleScopeAuthorizationAdapter`; corrected the ADR's PROPOSED net-zero wording to this precise form. erp re-point onto the shared reader is deferred (its verified inline reader is the behavioural reference; re-point = regression risk for no behaviour change). New consumers (wms, § step 2) use the shared reader.

# Status

ready

# Owner

backend

# Task Tags

- abac
- lib
- iam
- doc
- adr

---

# Dependency Markers

- **implements**: ADR-MONO-025 § 3.3 step 1 (ACCEPTED #1270 `1999b82b`).
- **prerequisite for**: ADR-025 step 2 (wms warehouse data-scope enforcement — consumes `AbacDataScope` + follows `platform/abac-data-scope.md`).
- **references (verified against)**: erp `RoleScopeAuthorizationAdapter` (the existing reference implementation whose semantics the contract + reader codify).

# Goal

Turn the erp-only org_scope pattern into a named, documented, reusable contract + one shared reader, so a second domain adopts data-scoping from a spec + a library call instead of re-deriving erp's code — with the net-zero/fail-closed semantics stated precisely.

# Scope

- NEW `platform/abac-data-scope.md` — claim (names/aliases/shape/opacity), the 3-case semantics table (unrestricted/scoped/empty-fail-closed), net-zero, narrowing-only invariant, per-domain interpretation table (erp/wms/finance), where-set, out-of-scope (2단계).
- NEW `libs/java-security/.../jwt/AbacDataScope.java` — `fromClaimValues(Object...)` (Collection/delimited-string/scalar parse, trim, drop blanks, union) + `tokens()` / `isEmpty()` / `isUnrestricted()` (=contains `"*"`) / `allows(token)` (deny-by-default incl. empty).
- NEW `libs/java-security/.../jwt/AbacDataScopeTest.java` — dual-read, array/string parse, wildcard=unrestricted, scoped deny-by-default, empty=fail-closed, null-token.
- `docs/adr/ADR-MONO-025-...md` — D1/D2/D4 net-zero wording correction + D5/D7 erp-re-point→optional + History rows (ACCEPTED PR# backfill + accuracy-correction row).

**Out of scope**: wms enforcement (step 2); re-pointing erp (deferred); producer change; 2단계 conditions.

# Acceptance Criteria

- **AC-1** `platform/abac-data-scope.md` exists and states: canonical `data_scope` + `org_scope` alias (dual-read); the 3 semantics cases with empty=**fail-closed deny** (not unrestricted); net-zero via producer NULL→`["*"]`; narrowing-only; per-domain interpretation; 2단계 out-of-scope.
- **AC-2** `AbacDataScope.fromClaimValues(...)` parses Collection + delimited-string + scalar, trims/drops blanks, unions the aliases; `isUnrestricted()` is true iff `"*"` present; `allows()` denies on empty (fail-closed) and on unlisted tokens; `tokens()` never null.
- **AC-3** `:libs:java-security:test` green incl. the new `AbacDataScopeTest` (all 3 semantics cases + dual-read + parse shapes).
- **AC-4** ADR-025 net-zero wording matches the verified erp semantics; erp re-point recorded as optional/deferred.
- **AC-5** Net-zero: no behaviour change anywhere (new lib class + doc only; erp untouched; producer untouched).

# Related Specs

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md`
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` § D3
- `platform/shared-library-policy.md` (the shared-lib boundary — `AbacDataScope` is project-agnostic)

# Related Contracts

- `platform/abac-data-scope.md` (this task creates it)

# Edge Cases

- `AbacDataScope` is framework-agnostic (raw claim values, not Spring `Jwt`) so both Spring resource servers (erp/wms) and the Map-based `JwtVerifier` path can use it.
- empty/absent ≠ unrestricted (fail-closed) — the single most important semantic to get right; pinned by a dedicated test (the PROPOSED wording wrongly implied absent=unrestricted; corrected here).
- The contract documents erp's EXISTING behaviour (it is the reference) — no erp code changes, so erp stays green.

# Failure Scenarios

- If `isUnrestricted()` returned true on empty, an operator whose token somehow carried no scope would see ALL data (privilege widening) — AC-2 + the empty test fail-close it.
- If the contract said absent=unrestricted, a future domain would implement allow-all-on-absent and leak data — AC-1/AC-4 pin empty=deny.
- If `AbacDataScope` depended on Spring `Jwt`, non-Spring consumers couldn't reuse it — AC-Edge keeps it framework-agnostic.
