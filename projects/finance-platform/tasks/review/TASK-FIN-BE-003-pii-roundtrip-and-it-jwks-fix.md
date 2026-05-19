# Task ID

TASK-FIN-BE-003

# Title

account-service: PiiEncryptor `owner_ref` persistence round-trip + cross-tenant IT JWKS wiring — make the finance Testcontainers IT fully green (Fix issues found in TASK-FIN-BE-001)

# Status

review

# Owner

backend

# Task Tags

- code
- bug
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

**Fix issues found in TASK-FIN-BE-001**, surfaced by the **TASK-MONO-115** `finance-integration-tests` CI job run [`26036483067`](https://github.com/kanggle/monorepo-lab/actions/runs/26036483067) once **TASK-FIN-BE-002** (#604, `c4d33fd5`) unblocked the schema-validation context-load cascade. With the enum schema bug fixed the 4 IT classes now boot (`SchemaManagementException` gone, 0→2 PASS); **9 of 11 IT remain red on two distinct pre-existing TASK-FIN-BE-001 defects** that were previously masked by the context-load failure. This task makes the finance Testcontainers IT **fully green (11/11)**.

## Defect 1 — PiiEncryptor `owner_ref` persistence round-trip asymmetry (×8 IT)

`AccountRepositoryAdapter` (`infrastructure/persistence/jpa/AccountRepositoryAdapter.java`):

- L28 (save): `writeField(account, "ownerRef", piiEncryptor.encryptToString(plaintextOwnerRef))` — encrypts before persist ✓
- L31 (post-save): `writeField(saved, "ownerRef", plaintextOwnerRef)` — writes **plaintext back onto the `saved` (JPA-managed) entity**
- L41 (load): `writeField(a, "ownerRef", piiEncryptor.decryptFromString(a.getOwnerRef()))` — decrypts on read

`PiiEncryptor.encryptToString`/`decryptFromString` are standard-Base64 **symmetric in isolation** (verified). The failure is `java.lang.IllegalArgumentException: Illegal base64 character 2d` (`0x2d` = `-`) on the L41 decrypt path — i.e. the value read from `owner_ref` is **plaintext, not the `"v1:"+base64` envelope**. Strong diagnostic lead: L31 mutates the **managed** `saved` entity's `ownerRef` to plaintext after `save()`; that field is now dirty in the persistence context, so on transaction flush JPA **re-persists the plaintext** over the encrypted value. Subsequent reads (same or later Tx) then `decryptFromString(plaintext)` → a hyphen-bearing external ref (e.g. UUID-like) fails Base64 decode. This also **violates fintech F7** (regulated PII must be encrypted at rest — never plaintext in the column).

The implementer must confirm the exact mechanism against the IT and fix the persistence mapping so that: (a) the column always holds the encrypted envelope (F7, never plaintext at rest), and (b) the returned domain object carries plaintext `ownerRef` **without dirtying the managed entity** (e.g. reconstruct/detach a domain instance rather than mutating the persisted JPA entity). `:check` unit/slice (117) must stay green and the F1/F3/F6 behavioural IT must pass.

## Defect 2 — cross-tenant IT JWKS unreachable (×1 IT)

`AbstractAccountIntegrationTest` sets `spring.security.oauth2.resourceserver.jwt.jwk-set-uri = http://localhost:9/oauth2/jwks` (deliberately dead — "application-layer ITs bypass the resource server"). `CrossTenantHttpIntegrationTest > cross-tenant token (tenant_id=wms) → 403 TENANT_FORBIDDEN` drives the **HTTP + JWT** path with a signed token; the resource server tries to fetch the remote JWK set → `Connection refused` → the request fails before tenant-claim enforcement, so the asserted **403 TENANT_FORBIDDEN** never occurs. (`no token → 401` passes — no JWKS needed.) This is a **test-harness gap** in TASK-FIN-BE-001's IT authoring: the one IT that exercises the resource server has no working JWKS. Fix by serving the test RSA public key from a JWKS endpoint (e.g. OkHttp `MockWebServer`, already a test dependency) and wiring `jwk-set-uri` to it via `@DynamicPropertySource` for the class that needs it — so the signed token decodes and the tenant-claim filter produces the expected 403. **No production code change** for this defect.

---

# Scope

## In Scope

1. **Defect 1**: fix the `owner_ref` encrypt/persist/decrypt round-trip in `AccountRepositoryAdapter` (and any sibling adapter that maps `owner_ref`) so the persisted value is always the encrypted envelope (F7) and the returned domain object carries plaintext without re-persisting it. Production code under `projects/finance-platform/apps/account-service/src/main/java/.../infrastructure/persistence/**` (and only there unless the fix demonstrably requires `crypto/**`).
2. **Defect 2**: provide a working JWKS for the resource-server IT path (test code only — `src/test/java/.../integration/**`), e.g. `MockWebServer` serving the signing key's public JWK + `@DynamicPropertySource` `jwk-set-uri` override on the class(es) that exercise HTTP+JWT.
3. Make **all 4 finance Testcontainers IT classes / 11 tests green** on the MONO-115 `finance-integration-tests` job.
4. Keep `:projects:finance-platform:apps:account-service:check` at **117/0/0/0** (no unit/slice regression).

## Out of Scope

- No change to `V1__init.sql` / migrations (schema is correct).
- No change to the enum `@JdbcTypeCode` mapping (TASK-FIN-BE-002, merged & proven).
- No change to the MONO-115 `.github/workflows/ci.yml` job (correct as-is).
- No API/event **contract** change, no `architecture.md`/ADR change (F7 "owner_ref encrypted at rest, never plaintext" is already specified — this restores compliance, it does not redefine it).
- No new domain behaviour; no scope beyond making the existing IT pass + F7 compliance.

---

# Acceptance Criteria

1. MONO-115 `finance-integration-tests` job **green**: **4 IT classes / 11 tests pass** on real MySQL+Redis+Kafka (cite the green run id in the impl PR). main `finance-integration-tests` flips RED→GREEN on merge.
2. `:account-service:check` stays **117/0/0/0** (no unit/slice regression).
3. **F7 preserved/restored**: `owner_ref` is stored **encrypted** (the column never contains plaintext); add/extend a test asserting the persisted column value is the `"v1:"`-prefixed envelope, not plaintext.
4. Diff scope = `infrastructure/persistence/**` (Defect 1, prod) + `src/test/java/.../integration/**` (Defect 2, test) only — no V1/migration/contract/`architecture.md`/ADR/ci.yml/enum-mapping change.
5. Goal cites **TASK-FIN-BE-001** + the surfacing run (project INDEX Review Rule).

---

# Related Specs

- [TASK-FIN-BE-001](../done/TASK-FIN-BE-001-account-service-bootstrap.md) — original impl (the defects' origin; in `done/`)
- [TASK-FIN-BE-002](../done/TASK-FIN-BE-002-enum-schema-validation-fix.md) — the prerequisite (its merge unblocked context-load, surfacing these); its Failure Scenario B prescribed this follow-up
- [TASK-MONO-115](../../../../tasks/done/TASK-MONO-115-finance-integration-ci-job.md) — the CI job that surfaced & verifies these (authoritative; `:check` ≠ sufficient)
- [specs/services/account-service/architecture.md](../../specs/services/account-service/architecture.md) — fintech F7 (owner_ref encrypted at rest, never plaintext/logged/evented) — unchanged, this restores compliance

---

# Related Contracts

- None (no API/event contract change — persistence-mapping fix + test-harness fix).

---

# Target Service / Component

- `projects/finance-platform/apps/account-service` — `infrastructure/persistence/**` (Defect 1) + `src/test/java/.../integration/**` (Defect 2).

---

# Edge Cases

1. **Managed-entity dirty-flush**: mutating the JPA-managed `saved` entity's `ownerRef` back to plaintext (L31) re-persists plaintext. The fix must return plaintext to the caller **without** leaving the managed entity dirty (reconstruct/detached domain object, or map via a non-persisted projection). Verify no other adapter mutates a managed entity post-save similarly.
2. **F7 at-rest invariant**: after the fix, a direct DB read of `owner_ref` must be the encrypted envelope for **every** write path (open account, any owner_ref-touching update). The new assertion (AC-3) must read the raw column (e.g. `JdbcTemplate`) — not the decrypted domain value.
3. **Idempotency/concurrency IT (F1)**: the ×8 failures include `IdempotencyConcurrencyIntegrationTest > F1 identical` — ensure the fix is correct under the concurrent path (the asymmetry, not concurrency, is the cause; confirm F1 passes once round-trip is symmetric).
4. **JWKS scope**: only the class(es) that drive the HTTP+JWT path need the live JWKS; application-layer ITs intentionally bypass the resource server — do not globally change the abstract base in a way that slows or destabilises the other ITs.
5. **Decrypt of legacy/None**: `decryptFromString(null)` returns null (existing). Ensure the fix doesn't introduce an NPE for accounts created via different paths in the IT.

---

# Failure Scenarios

## A. Round-trip fixed but a different IT then fails

Schema-validation already showed this pattern (fix one layer → next surfaces). If, after Defect 1+2 are fixed, a **different** assertion fails (genuine domain/behaviour bug, not the two named here), **STOP and surface a separate follow-up fix-task** — do not silently expand this task's scope or green-wash. If it is the same class of defect (another plaintext/round-trip site), fix it within this task.

## B. F7 regression risk

A naive fix (e.g. making the read tolerate plaintext) would **violate F7** (plaintext at rest). That is NOT acceptable — the column must hold ciphertext. The fix must keep encryption-at-rest; AC-3's raw-column assertion guards this.

## C. Local Docker blocker

Local Testcontainers IT can't be run (known Rancher/Docker-Desktop blocker). The MONO-115 CI job on the impl PR is authoritative; cite the green run id. `:check` green is necessary but NOT sufficient (it never boots the real-DB persistence path — exactly why these defects hid through TASK-FIN-BE-001).

---

# Test Requirements

- MONO-115 `finance-integration-tests`: 4 IT / 11 tests PASS (run id cited in impl PR).
- `:account-service:check`: 117/0/0/0.
- New/extended test asserting `owner_ref` raw column = encrypted envelope (F7, AC-3).
- Diff-scope assertion in the impl PR.

---

# Definition of Done

- [ ] Defect 1 fixed — `owner_ref` round-trip symmetric, column always encrypted (F7)
- [ ] Defect 2 fixed — cross-tenant HTTP+JWT IT has a working JWKS, asserts 403
- [ ] MONO-115 `finance-integration-tests` green (4 IT / 11 tests) — run id cited
- [ ] `:account-service:check` 117/0/0/0
- [ ] F7 raw-column assertion added
- [ ] Diff scope = persistence (prod) + integration test only
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** (분석=Opus 4.7 / 구현 권장=Opus 4.7) — regulated-PII at-rest correctness (fintech F7) + JPA managed-entity dirty-flush subtlety + must keep F1 concurrency / F3 immutable / F6 append-only IT green. Correctness-critical fintech, not a routine fix; CLAUDE.md model rule → Opus for complex domain/persistence-correctness work.
- **Critical verification discipline**: the MONO-115 CI job (real MySQL) is the only authoritative proof — `:check` green is necessary but NOT sufficient (it is the exact gate that hid both these defects through TASK-FIN-BE-001). Do not declare done on `:check` alone (green-wash prohibited). Dispatcher independently re-verifies (BE-301) before commit/push/PR.
- **dependency**:
  - `선행`: **TASK-FIN-BE-002 #604 merged** (`c4d33fd5`) — context-load works; these defects are now reachable/observable.
  - `후속`: none. Merging this = main `finance-integration-tests` RED→GREEN; finance v1's behavioural-proof gap fully closed (the green-wash chain: FIN-BE-001 honest gap → MONO-115 CI → FIN-BE-002 schema → **FIN-BE-003 behavioural** terminates here).
- **green-wash chain**: this is the terminal fix of the honestly-tracked chain. The 2 defects were surfaced (not dropped), scoped out of FIN-BE-002 (Failure Scenario B), user-approved as strategy A, and are fixed here with the MONO-115 job as the non-bypassable proof.
