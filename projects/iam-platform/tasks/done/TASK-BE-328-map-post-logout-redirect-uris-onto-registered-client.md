# Task ID

TASK-BE-328

# Title

`OAuthClientMapper` must copy `settings.client.post-logout-redirect-uris` onto the SAS `RegisteredClient.postLogoutRedirectUris` field — completes the RP-initiated OIDC logout fix (TASK-PC-FE-033). The V0021 seed registered the URIs only inside `client_settings` JSON, but SAS's `OidcLogoutAuthenticationProvider` validates `post_logout_redirect_uri` exclusively against the dedicated `RegisteredClient.getPostLogoutRedirectUris()` field, which the custom mapper never populated → `/connect/logout` returned **403 Forbidden** for every URI. This is a post-merge defect of impl PR #996 (the IT only asserted the inert ClientSettings key, not the field SAS reads).

# Status

done

> **완료 (2026-06-01)**: impl PR #998 (squash `fc1d70b5`). RP-initiated OIDC logout 403 fix — completes TASK-PC-FE-033. **근본원인**: V0021 이 post-logout URI 를 `client_settings` JSON 커스텀 키(`settings.client.post-logout-redirect-uris`)에만 등록했으나, SAS `OidcLogoutAuthenticationProvider` 는 `post_logout_redirect_uri` 를 **오직 `RegisteredClient.getPostLogoutRedirectUris()` 전용 필드**로만 검증함. 커스텀 `OAuthClientMapper.toRegisteredClient` 가 그 setting 을 해당 필드로 **복사하지 않아** inert → `/connect/logout` 이 모든 URI 를 403. (덤: V0011/12/16 fan/ecommerce post-logout 시드도 동일하게 inert — RP-logout 한 번도 구동 안 됨.) #996 IT 는 inert 한 ClientSettings 키만 단언해 결함을 놓침. **수정**: 매퍼가 setting 을 `builder.postLogoutRedirectUri(...)` 로 복사(additive — ClientSettings 키 유지, round-trip 무변경). **테스트**: 매퍼 단위 2건(`getPostLogoutRedirectUris()` populated/empty) + seed IT 강화(field 단언 = load-bearing). **실증**: federation-e2e live `curl /connect/logout` → `status=302 redirect=http://localhost:3000/login`(이전 403). **3차원**(MERGED `fc1d70b5` / origin/main tip 일치 / pre-merge 0 failing — Build&Test JDK21 + GAP Integration PASS). 분석=Opus 4.8 / 구현=Opus. **메타: SAS post-logout-redirect-uris 는 ClientSettings 키가 아니라 RegisteredClient 전용 필드 — 커스텀 client 매퍼는 명시적 복사 필요; IT 는 실제 SAS 가 읽는 필드를 단언해야(setting 존재만으론 inert 검증).**

# Owner

backend

# Task Tags

- code
- security
- bugfix

---

# Dependency Markers

- **fixes**: TASK-PC-FE-033 (impl PR #996 `c4062f57`) — the console RP-initiated logout shipped non-functional (live `/connect/logout` → 403). This task makes the registered post-logout URIs effective.
- **depends on**: V0021 migration (already on `origin/main`, ships the `settings.client.post-logout-redirect-uris` setting on `platform-console-web`). No new migration. dependency-correct base = current `origin/main`.

# Goal

Make SAS RP-initiated logout (`/connect/logout`, `end_session`) accept the `platform-console-web` client's `post_logout_redirect_uri`, so the console logout terminates the IdP session and the next login re-presents the credential form.

# Scope

- `OAuthClientMapper.toRegisteredClient` — read the `settings.client.post-logout-redirect-uris` custom setting (a `List<String>` under SAS default typing) and call `builder.postLogoutRedirectUri(...)` for each entry. Additive; the setting also remains in `ClientSettings` (existing round-trip behaviour unchanged).
- New constant `SETTING_POST_LOGOUT_REDIRECT_URIS` documenting why the copy is required.
- `OAuthClientMapperTest` — assert `RegisteredClient.getPostLogoutRedirectUris()` is populated when the setting is present, and empty when absent.
- `OAuthClientPostLogoutRedirectUriSeedIntegrationTest` — strengthen `platformConsoleClient_hasV0021PostLogoutRedirectUris` to assert `console.getPostLogoutRedirectUris()` (the load-bearing field), not only the ClientSettings key.

Out of scope: console-web (already correct from #996), V0021 (already on main), the write path `toEntity` (seeded clients never use it; the setting still survives via `client_settings`).

# Acceptance Criteria

- **AC-1** `GET /connect/logout?id_token_hint=<valid>&post_logout_redirect_uri=http://localhost:3000/login&client_id=platform-console-web` returns **302** to `http://localhost:3000/login` (was 403). Verified on the live federation-e2e stack: `curl` → `status=302 redirect=http://localhost:3000/login`.
- **AC-2** `OAuthClientMapper.toRegisteredClient` populates `RegisteredClient.getPostLogoutRedirectUris()` from the custom setting (unit test).
- **AC-3** A client with no post-logout setting yields an empty `postLogoutRedirectUris` (no injection / NPE).
- **AC-4** The seed IT asserts the `platform-console-web` RegisteredClient's `postLogoutRedirectUris` == `[console.local/login, localhost:3000/login]`.
- **AC-5** No regression: all existing `OAuthClientMapperTest` round-trip + post-logout-typed-array tests stay GREEN; full `auth-service:integrationTest` GREEN.

# Related Specs

- `projects/global-account-platform/specs/services/auth-service/architecture.md` (SAS RegisteredClient mapping, OIDC provider)

# Related Contracts

- `projects/global-account-platform/specs/contracts/auth-api.md` (§ OIDC `end_session` / `/connect/logout`)

# Edge Cases

- Setting present but not a `Collection` (malformed seed) → skip copy (no crash); the existing typed-array negative-control test already guards the deserialization envelope.
- Null elements inside the collection → skipped.
- Multiple post-logout URIs (console.local + localhost) → all registered; SAS matches the sent URI exactly.

# Failure Scenarios

- If the mapper copy is removed/reverted, AC-1 (302) and AC-4 (field populated) fail — the strengthened IT now catches it pre-merge (the original IT did not, which is how #996 shipped broken).
- Mapper deserialization failure of `client_settings` → existing `OAuthClientMappingException` path (unchanged).
