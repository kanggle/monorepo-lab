-- TASK-BE-336 (completes TASK-PC-FE-046 runtime — console erp department write)
-- Grant the `erp.write` scope to the platform-console-web public client so the
-- assume-tenant domain-facing token can carry it (AssumeTenantAuthentication
-- Provider propagates the registered client scopes into the token's `scope`
-- claim). erp masterdata-service authorizes department WRITE on
-- `erp.write ∨ operator-role` (entitlement-trust widens READ only — ADR-MONO-019
-- § D5); the console delegate therefore needs the explicit scope. This is the
-- scope-based delegation model (ADR-MONO-020 / ADR-001), mirroring how
-- erp-platform-internal-services-client (V0018) is granted erp.write.
--
-- Idempotent: only appends when `erp.write` is not already present (mirrors
-- V0020's JSON_ARRAY_APPEND + JSON_SEARCH guard). The stored column is a JSON
-- array string (e.g. ["openid","profile","email","tenant.read"]).
--
-- NOTE: this does NOT change the base authorization_code login token — the
-- console requests only `openid profile email tenant.read` at authorize, so the
-- base token still does not carry erp.write. The write capability rides ONLY in
-- the tenant-scoped assume-tenant token (least-privilege).

UPDATE oauth_clients
SET scopes = JSON_ARRAY_APPEND(scopes, '$', 'erp.write')
WHERE client_id = 'platform-console-web'
  AND JSON_SEARCH(scopes, 'one', 'erp.write') IS NULL;
