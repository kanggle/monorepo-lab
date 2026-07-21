-- TASK-MONO-460
-- Per-app frontend dev-server port convention: console 3000 / web-store 3001 /
-- fan 3002. console keeps localhost:3000 (V0015, unchanged). web-store and fan
-- move their standalone `next dev` / `next start` port, so their localhost
-- OIDC callback + RP-initiated-logout landing must be registered at the new
-- port or login fails with redirect_uri_mismatch.
--
-- ADDITIVE — the existing localhost:3000 entries stay so any CI/e2e or workflow
-- that still assumes :3000 keeps working; we only append the new-port URIs.
--
-- The seeding migrations V0011 (fan) / V0012 (web-store) are checksum-locked
-- historical record and MUST NOT be edited — like V0024, the value change lands
-- here as a forward UPDATE. String REPLACE only (no JSON_SET / JSON_ARRAY, which
-- are MySQL-only) so this stays portable across MySQL (prod / Testcontainers)
-- and H2 (auth-service SAS slice tests). Each UPDATE is idempotent via a
-- NOT LIKE guard on the new port, so a re-run is a no-op.

-- web-store (ecommerce-web-store-client): add localhost:3001 callback + post-logout.
UPDATE oauth_clients
   SET redirect_uris = REPLACE(
           redirect_uris,
           'http://localhost:3000/api/auth/callback/iam',
           'http://localhost:3000/api/auth/callback/iam","http://localhost:3001/api/auth/callback/iam'),
       client_settings = REPLACE(
           client_settings,
           '"http://localhost:3000/"',
           '"http://localhost:3000/","http://localhost:3001/"'),
       updated_at = NOW()
 WHERE client_id = 'ecommerce-web-store-client'
   AND redirect_uris NOT LIKE '%localhost:3001%';

-- fan (fan-platform-user-flow-client): add localhost:3002 callback + post-logout.
UPDATE oauth_clients
   SET redirect_uris = REPLACE(
           redirect_uris,
           'http://localhost:3000/api/auth/callback/iam',
           'http://localhost:3000/api/auth/callback/iam","http://localhost:3002/api/auth/callback/iam'),
       client_settings = REPLACE(
           client_settings,
           '"http://localhost:3000/"',
           '"http://localhost:3000/","http://localhost:3002/"'),
       updated_at = NOW()
 WHERE client_id = 'fan-platform-user-flow-client'
   AND redirect_uris NOT LIKE '%localhost:3002%';
