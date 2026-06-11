# Task ID

TASK-MONO-218

# Title

ADR-MONO-026 § D7 step 1 — Access-Conditions contract + shared evaluator. Author the shared contract `platform/access-conditions.md` (the closed condition enum, restriction-only + fail-safe + net-zero semantics, the 4th-gate composition, the D3-B domain/endpoint guard-config carrier) and the shared `com.example.security.access.SourceIpCondition` evaluator (`libs/java-security`) codifying the `SOURCE_IP` type (CIDR allowlist matching, fail-safe, opt-in), with unit tests. Producer untouched (D3-B). The iam enforcement that wires this into the admin-service is a separate follow-up task.

# Status

ready

# Owner

backend

# Task Tags

- adr
- conditional-policy
- libs
- iam
- contract

---

# Dependency Markers

- **depends on**: TASK-MONO-217 (ADR-MONO-026 ACCEPTED — PR #1286 squash `2914c23b8`). Bases on the ACCEPTED main.
- **blocks**: the iam `SOURCE_IP` enforcement task (admin-service `RequiresPermissionAspect` 4th gate + IT) — it consumes the `SourceIpCondition` evaluator and the contract published here.
- **mirrors**: TASK-MONO-214 (ADR-025 § D7 step 1 — `platform/abac-data-scope.md` + shared `AbacDataScope` reader), the 1단계 sibling this replicates.

# Goal

Publish the access-condition contract + shared evaluator so the iam pilot (and future domains) enforce the `SOURCE_IP` condition from a contract and one shared implementation instead of re-deriving it, keeping the change net-zero (the evaluator is opt-in, the producer is untouched) and bounded to the closed enum.

# Scope

- NEW `platform/access-conditions.md` — the shared contract (closed enum; restriction-only/fail-safe/net-zero semantics; AND-only; 4th-gate composition; D3-B domain/endpoint guard-config carrier; iam pilot adoption recipe; out-of-scope per ADR-026 § D6).
- NEW `libs/java-security/src/main/java/com/example/security/access/SourceIpCondition.java` — framework-agnostic `SOURCE_IP` evaluator (CIDR allowlist; IPv4 + IPv6; bare-IP = /32 //128; DNS-free literal parsing; `isConfigured()` net-zero short-circuit; `isSatisfiedBy()` fail-safe).
- NEW `libs/java-security/src/test/java/com/example/security/access/SourceIpConditionTest.java` — unit tests (net-zero / IPv4 CIDR / non-byte-aligned prefix / bare-IP / multi-CIDR / IPv6 + family-mismatch / fail-safe bad-IP / fail-closed all-invalid / partial-valid / trimming).
- Update `tasks/INDEX.md` ready list.
- This task file.

**Out of scope** (separate tasks): the iam admin-service enforcement (aspect wiring + config + IT); the `TIME_WINDOW` / `RESOURCE_TAG` types (reserved, added when first piloted); any producer change.

# Acceptance Criteria

- **AC-1** `platform/access-conditions.md` exists with: the closed enum (`SOURCE_IP` implemented; `TIME_WINDOW`/`RESOURCE_TAG` reserved), the three invariants (restriction-only / fail-safe / net-zero), AND-only combination, the 4th-gate composition after RBAC/tenant/data-scope, and the D3-B domain/endpoint guard-config carrier (producer untouched).
- **AC-2** `SourceIpCondition` implements: net-zero when unconfigured (`isConfigured()` false ⇒ `isSatisfiedBy` true); CIDR allowlist match for IPv4 + IPv6 (incl. non-byte-aligned prefixes and bare-IP as /32//128); fail-safe deny on blank/unparseable IP; fail-closed (configured-but-all-invalid matches nothing); DNS-free literal parsing.
- **AC-3** Unit tests cover every AC-2 case and pass (`./gradlew :libs:java-security:test`).
- **AC-4** The contract is project-agnostic (shared-library policy) — service names only illustrate, never define the rule; it names the iam pilot as an adopter example.
- **AC-5** No producer/token-customizer change; no iam enforcement code in this PR (libs + contract only).

# Related Specs

- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the decision; § D1 closed enum, § D2 restriction-only/fail-safe, § D3-B carrier, § D7 staged)
- `platform/abac-data-scope.md` (the 1단계 sibling contract whose structure this mirrors)

# Related Contracts

- `platform/access-conditions.md` (authored by this task)

# Edge Cases

- **Net-zero**: an unconfigured condition (empty CIDR list) must never deny — `isConfigured()` short-circuits to satisfied. This is the opt-in invariant that keeps every existing path byte-identical.
- **Fail-safe vs net-zero**: a blank/unparseable IP on a *configured* condition denies (fail-safe), but an *unconfigured* condition with the same IP allows (net-zero). The two cases must not be conflated.
- **Fail-closed misconfig**: a configured allowlist whose entries are all unparseable is still `isConfigured()` → it matches nothing → denies (does not fall open).
- **DNS safety**: IPv4 literals are parsed manually so a hostname-shaped token (e.g. `cafe.babe`) never triggers a DNS lookup; IPv6 (always contains `':'`, invalid in hostnames) is parsed via `getByName`.
- **Family mismatch**: an IPv4 candidate against an IPv6 CIDR (or vice-versa) is a non-match, not a crash.

# Failure Scenarios

- If `isSatisfiedBy` returned `true` on unparseable input, a spoofed/garbage IP could bypass the gate — fail-safe pins it to `false`.
- If an unconfigured condition denied, adopting the contract would regress every existing endpoint — the `isConfigured()` net-zero short-circuit prevents this.
- If the enum were made runtime-extensible, the closed-enum boundary (ADR-026 § D1/D6) would be breached and the 2단계 would drift toward the rejected policy engine — the contract and the per-type-class design keep it closed.
