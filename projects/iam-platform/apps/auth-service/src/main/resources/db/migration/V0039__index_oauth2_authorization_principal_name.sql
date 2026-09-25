-- TASK-BE-601: revoking an account's sessions (force-logout, and now account.locked)
-- looks up its SAS authorizations by principal_name — the login email, the only
-- account-identifying column oauth2_authorization has (SasAuthorizationRevocationAdapter).
-- SAS never deletes expired authorizations, so without an index every lock scans the
-- whole table. Adding an index does not change any column JdbcOAuth2AuthorizationService
-- depends on (V0008: "Do NOT rename columns").
CREATE INDEX idx_oauth2_authorization_principal_name ON oauth2_authorization (principal_name);
