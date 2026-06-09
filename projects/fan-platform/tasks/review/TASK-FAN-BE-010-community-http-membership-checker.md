# TASK-FAN-BE-010 — community-service `HttpMembershipChecker` adapter swap

Status: review
Type: backend (TASK-FAN-BE)
Project: fan-platform
Service: community-service (caller) → membership-service (callee, FAN-BE-009)

---

## Goal

Replace community-service's v1 `AlwaysAllowMembershipChecker` stub with a real
`HttpMembershipChecker` adapter that calls membership-service's internal
access-check endpoint over workload-identity (IAM `client_credentials` JWT,
ADR-MONO-005), thereby **enforcing the real membership gate** for `MEMBERS_ONLY`
**and** `PREMIUM` posts. This completes the membership vertical-slice backend leg
(spec FAN-BE-008 → impl FAN-BE-009 → **this adapter swap FAN-BE-010**).

Both bypasses become real gates:

- `MEMBERS_ONLY` — previously routed through `MembershipChecker` (stub always
  returned `true`); now the real HTTP checker decides.
- `PREMIUM` — previously **hard-coded always-pass + WARN log + TODO** in
  `PostAccessGuard` (membership-service did not exist). Now it routes through the
  same `MembershipChecker.hasAccess(accountId, "PREMIUM", tenantId)`, fail-closed.

## Scope

**In scope (community-service only):**

1. `IamClientCredentialsTokenProvider` — hand-rolled `client_credentials` token
   provider (mirrors `admin-service` ADR-005 pattern: plain `RestClient` +
   Jackson, no `spring-boot-starter-oauth2-client`, lazy + cached with refresh
   skew). Uses the pre-seeded `community-service-client` (IAM V0009, scope
   `membership.read`).
2. `HttpMembershipChecker implements MembershipChecker` — calls
   `GET {base-url}/internal/membership/access?accountId&tier&tenantId`, returns
   the `allowed` boolean, **fail-closed** (timeout / connection error / non-2xx /
   malformed body → `false`). Presents the token provider's Bearer on every call.
3. `MembershipCheckerAutoConfig` — wire the real adapter as the primary
   `MembershipChecker` `@Bean`; keep `AlwaysAllowMembershipChecker` only behind
   `@ConditionalOnMissingBean` **declared after** the real `@Bean` in the same
   `@Configuration` class (deterministic intra-class ordering — avoids the
   `@ConditionalOnMissingBean`-on-component-scan non-determinism, memory §19).
4. `PostAccessGuard` — replace the `PREMIUM` always-pass branch with a real
   fail-closed `membershipChecker.hasAccess(..., "PREMIUM", ...)` gate
   (`MembershipRequiredException(PostVisibility.PREMIUM)` on deny).
5. `application.yml` — config keys with safe defaults:
   `community.membership-service.base-url`,
   `iam.internal-client.{token-uri,client-id,client-secret}`, connect/read
   timeouts. `client-id` default `community-service-client`.
6. Tests (Docker-free `:check` where possible):
   - `HttpMembershipCheckerTest` (MockWebServer): allowed=true→true,
     allowed=false→false, 500→false, malformed body→false, connection error→false,
     Bearer header present.
   - `IamClientCredentialsTokenProviderTest` (MockWebServer): fetch + cache +
     refresh-after-skew, Basic auth header + `grant_type=client_credentials` body.
   - `PostAccessGuardTest` — PREMIUM now denies a non-member, passes a member.
   - `MembershipGateIntegrationTest` — `premium_v1AlwaysPasses` →
     `premium_denyForNonMember` (PREMIUM hard fail-closes through the `@Primary`
     deny checker → 403 `MEMBERSHIP_REQUIRED`).

**Out of scope:** any membership-service change (FAN-BE-009 is done); a live
community→IAM→membership e2e wire test (the MockWebServer adapter test +
gate IT cover the contract; full wire is an e2e concern); FE membership/subscribe
UI (FAN-FE); notification-service consuming membership events (PROJECT § v2).

## Acceptance Criteria

- **AC-1** `MembershipChecker` production bean is `HttpMembershipChecker`; the
  `AlwaysAllowMembershipChecker` stub is selected ONLY when no other
  `MembershipChecker` is present (deterministic intra-`@Configuration` ordering).
- **AC-2** `HttpMembershipChecker` returns the endpoint's `allowed` verbatim on
  2xx, and `false` on any error (timeout, connection refused, non-2xx, malformed)
  — fail-closed. Bearer token attached on every request.
- **AC-3** `IamClientCredentialsTokenProvider` obtains a `community-service-client`
  token via Basic-auth `grant_type=client_credentials`, caches it, and refreshes
  it `REFRESH_SKEW` before expiry. Token acquisition is lazy (no startup coupling
  to IAM availability).
- **AC-4** `PREMIUM` posts now hard fail-close: a non-member fan gets 403
  `MEMBERSHIP_REQUIRED` (`requiredTier=PREMIUM`); the author/operator still
  bypass; a PREMIUM member passes.
- **AC-5** `MEMBERS_ONLY` behavior unchanged except the gate is now real.
- **AC-6** No remaining `TODO(TASK-FAN-BE-MEMBERSHIP)` / "v1 stub" / "always-pass"
  bypass markers in `PostAccessGuard` / membership infra (grep gate).
- **AC-7** `./gradlew :community-service:check` GREEN (Docker-free); the
  `fan-integration-tests` Testcontainers job GREEN (authoritative gate).

## Related Specs

- `specs/services/membership-service/architecture.md` § Internal Access-Check
  Contract (1:1 `MembershipChecker` mapping, fail-closed, workload-identity auth).
- `specs/services/community-service/architecture.md` § Visibility Tiers +
  `MembershipChecker` port.
- ADR-MONO-005 (workload identity — IAM `client_credentials` JWT for `/internal/**`).

## Related Contracts

- `specs/contracts/http/membership-api.md` § internal
  `GET /internal/membership/access`.
- IAM `V0009__seed_community_membership_oauth_clients.sql` — `community-service-client`
  (`client_credentials`, scope `account.read`,`membership.read`).

## Edge Cases

- membership-service down / slow → fail-closed deny (403 at the gate), never a
  bypass; a WARN log records the downstream failure.
- IAM token endpoint down → token acquisition throws inside the adapter call →
  caught → fail-closed deny. Startup is NOT coupled to IAM (lazy fetch).
- Malformed / missing `allowed` field → `false`.
- Tier hierarchy: a `MEMBERS_ONLY` post is satisfied by a `PREMIUM` membership —
  enforced server-side in membership-service `AccessPolicy` (community passes the
  **required** tier verbatim; no client-side tier reasoning).
- Author / operator short-circuit BEFORE any HTTP call (no token spend, no
  downstream dependency for the common author path).

## Failure Scenarios

- **Non-deterministic bean wiring** — if `@ConditionalOnMissingBean` were on a
  component-scanned `@Component` (or split across two `@Configuration` classes),
  the stub could win non-deterministically (memory §19). Mitigation: both beans in
  ONE `@Configuration`, real `@Bean` declared first, stub `@ConditionalOnMissingBean`
  second (intra-class top-to-bottom order is guaranteed).
- **Fail-OPEN regression** — if the adapter caught an error and returned `true`,
  the gate would silently bypass. Mitigation: AC-2 explicit error→false unit tests
  (500 / malformed / connection error).
- **Issuer/tenant drift** — community-service-client token must NOT carry a
  `tenant_id` claim (else membership receiver's `WorkloadIdentityAuthoritiesConverter`
  denies ROLE_INTERNAL → 403 → fail-closed deny). Proven by FAN-BE-009
  `AccessCheckIntegrationTest` on the receiver side.
