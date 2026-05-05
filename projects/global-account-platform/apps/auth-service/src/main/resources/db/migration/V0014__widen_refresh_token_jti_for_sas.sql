-- TASK-MONO-046-1 Cluster A
--
-- Widen refresh_tokens.jti / rotated_from from VARCHAR(36) to VARCHAR(255).
--
-- Rationale: V0001 sized these columns for our pre-SAS UUID-format jti values
-- (36 chars). Spring Authorization Server's default StringKeyGenerator and our
-- {@link com.example.auth.infrastructure.oauth2.PublicClientRefreshTokenGenerator}
-- both produce 96-byte URL-safe base64 strings (~128 chars). With the old size
-- the DomainSyncOAuth2AuthorizationService.save() path silently fails on every
-- SAS-issued refresh token ("Data too long for column 'jti'") and the domain
-- store stays empty — breaking reuse-detection and the refresh_token grant
-- rotation flow.
--
-- We also widen rotated_from for symmetry: SAS rotates by emitting a new RT
-- whose value follows the same generator, and our domain store records the
-- previous value here.

ALTER TABLE refresh_tokens
    MODIFY COLUMN jti          VARCHAR(255) NOT NULL,
    MODIFY COLUMN rotated_from VARCHAR(255) NULL;
