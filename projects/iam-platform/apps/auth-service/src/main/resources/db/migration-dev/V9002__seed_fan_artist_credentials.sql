-- !!! DEV/DEMO ONLY — never reaches production. !!!
-- Loaded via spring.flyway.locations ONLY under the `e2e` profile
-- (application-e2e.yml); application.yml pins production to db/migration alone.
--
-- TASK-MONO-512 (ADR-MONO-059 ACCEPTED — A) — login credentials for the three
-- demo artists, the other half of account-service migration-dev V9006.
--
-- WHY THESE EXIST
-- ---------------------------------------------------------------------------
-- `PublishPostUseCase` admits an ARTIST_POST author on `hasRole("ARTIST")` or
-- `isOperator()`. ADR-MONO-059 chose A (the artist writes as themself) and
-- excluded B (an operator writes on their behalf), so the ONLY door left is an
-- artist who can log in. V9006 grants the role; without a credential row there is
-- still no one to hand it to — a role on an account nobody can authenticate as is
-- the same defect one layer down.
--
-- WHY NOT `demo@demo.com`
-- ---------------------------------------------------------------------------
-- `credentials` is UNIQUE (tenant_id, email) since V0007, and these accounts live
-- in `fan-platform` — where the demo consumer credential already sits. They need
-- their own emails, and that is also correct on the merits: the artist is a
-- DIFFERENT actor from the interviewer's demo account, which is the entire reason
-- seed-fan.sh insists the gated posts not be authored by the demo account
-- (`actor.owns()` would open MEMBERS_ONLY/PREMIUM to its own author and make the
-- visibility demo vacuous — TASK-FAN-BE-045 AC-5).
--
-- 🔵 The single-identity demo charter is intact: `demo@demo.com` / `Demo1234!`
-- still logs in on all three surfaces. These three are seed-driven logins the
-- interviewer never types — the seed uses them to author the artist posts through
-- the API instead of reaching into the database.
--
-- Each email exists in exactly ONE tenant, so the scoped lookup hits and the
-- cross-tenant fallback's fail-closed-on-ambiguity branch (TASK-BE-507 D1-a,
-- three-row hazard documented in V9001) is never reached for them.
--
-- The password is the same `Demo1234!`, hence the same Argon2id digest as V9001 —
-- one published demo password, one hash literal. (Sharing a digest means sharing
-- a salt, which would be wrong for real users and is deliberate here: the value is
-- printed in the repo, so there is no secret for a per-row salt to protect, and a
-- second literal is a second thing that can drift out of sync with the password.)
-- FanArtistDemoSeedTest re-verifies it with the login path's hasher on every
-- build, so a hash/password drift turns a test red instead of a demo login.
--
-- account_id is the OIDC `sub` and MUST equal `accounts.id` in account-service
-- V9006 — which in turn equals `artists.id` in infra/demo/seed/seed-fan.sh. All
-- three are pinned against each other by FanArtistDemoSeedTest; the rationale for
-- the id equality is in V9006's header.
--
-- Idempotent: INSERT IGNORE (re-runs and pre-existing rows are a no-op).

INSERT IGNORE INTO credentials (
    tenant_id, account_id, email,
    credential_hash, hash_algorithm, created_at, updated_at, version
) VALUES
-- 루미 (ARTIST_A) — the follow target, author of the three visibility-tier posts.
(
    'fan-platform', '0199de80-0000-7000-8000-00000000a001', 'lumi@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
),
-- 노아 (ARTIST_B) — the second followed artist, so the feed is not one author wide.
(
    'fan-platform', '0199de80-0000-7000-8000-00000000a002', 'noah@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
),
-- 세아 (ARTIST_C) — the group member. Seeded for parity: an artist row whose
-- account_id points at nothing reproduces this ticket's defect on one of three
-- rows, and "green because we only checked the one we fixed" is how it would
-- come back.
(
    'fan-platform', '0199de80-0000-7000-8000-00000000a003', 'sea@demo.com',
    '$argon2id$v=16$m=65536,t=3,p=1$NR1Seql5fgXB0hQ7CmpFL6RyiXvL86lxeZCobfiBdRxzRlTkkcv6iIZDJq9eQ32QmKQMylwsG+IP25S1aaw9vw$kTFrCq8cQG4HVUKioosaD88eiXZkQesTp5Xc8yylaSM',
    'argon2id', NOW(6), NOW(6), 0
);
