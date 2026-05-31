-- TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2)
-- Register the RFC 8693 token-exchange grant on the platform-console-web public
-- client so its `authorization_grant_types` advertises the assume-tenant
-- exchange (`urn:ietf:params:oauth:grant-type:token-exchange`) alongside the
-- existing authorization_code + refresh_token grants.
--
-- The assume-tenant grant is handled by a custom converter/provider pair
-- (AssumeTenantAuthenticationConverter / AssumeTenantAuthenticationProvider)
-- registered on the SAS token endpoint; this migration keeps the registered
-- client's advertised grant types consistent with what it can actually request
-- (the converter fires on grant_type only; the provider does not gate on the
-- registered grant-type set, but the metadata should still reflect reality —
-- mirrors how V0010/V0011 list every grant a client may use).
--
-- Idempotent: only appends the value when it is not already present, so a re-run
-- (or a fresh DB where a future seed already includes it) is a no-op. The stored
-- column is a JSON array string (e.g. ["authorization_code","refresh_token"]);
-- JSON_ARRAY_APPEND adds the new element without disturbing the existing ones.

UPDATE oauth_clients
SET authorization_grant_types = JSON_ARRAY_APPEND(
        authorization_grant_types,
        '$',
        'urn:ietf:params:oauth:grant-type:token-exchange')
WHERE client_id = 'platform-console-web'
  AND JSON_SEARCH(
        authorization_grant_types,
        'one',
        'urn:ietf:params:oauth:grant-type:token-exchange') IS NULL;
