-- =============================================================================
-- REPEATABLE (R__) — see R__01's header for the full R__-vs-versioned rationale
-- (TASK-MONO-524/207/531).
-- =============================================================================
-- !!! DEV/DEMO ONLY — never reaches production. !!!
-- Loaded via spring.flyway.locations ONLY under the `e2e` profile
-- (application-e2e.yml); application.yml pins production to db/migration alone.
--
-- TASK-MONO-766 — pairs with account-service's
-- `db/migration-dev/R__07_seed_demo_corp_new_hire_account.sql` (read that file's
-- header first — it has the full "why not the real API" account). That file
-- creates the `accounts`/`identities` rows; this one gives the same account a
-- login credential so it is a complete person, not a dangling row — exactly
-- the same split R__05 (account-service) / R__01 (this service) already use for
-- `demo@demo.com`.
--
-- account_id below MUST equal that file's `accounts.id`
-- (`0199de70-0000-7000-8000-00000000ad07`) — `credentials.account_id` carries a
-- GLOBAL unique index (V0001), so every seeded person needs a distinct UUID here.
--
-- Password: `Demo1234!` — the SAME repo-wide demo password R__01 uses for
-- `demo@demo.com`, so the hash below is copied byte-for-byte from that file
-- (Argon2id is salted per-hash but verifies by recomputation, so one stored hash
-- is valid for any row whose plaintext is the same password — no new hash had to
-- be minted, and none was minted by running the app). `DemoSeedCredentialTest`
-- (auth-service) re-verifies R__01's copy of this hash on every build; this file
-- is not in scope for that test since the account it seeds is not a login this
-- ticket exercises (AC-4, next window) — if a future ticket adds that coverage,
-- verify this hash against the same Argon2idPasswordHasher params first.
--
-- Idempotent: INSERT IGNORE (re-runs and pre-existing rows are a no-op).

INSERT IGNORE INTO credentials (
    tenant_id, account_id, email,
    credential_hash, hash_algorithm, created_at, updated_at, version
) VALUES
(
    'demo-corp', '0199de70-0000-7000-8000-00000000ad07', 'newhire@demo-corp.example',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
);
