# Task ID

TASK-FAN-BE-030

# Title

community-service must request the membership.read scope on its client_credentials token

# Status

done

# Owner

backend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Completes the fix started in TASK-FAN-BE-029. Post-merge **live** verification of BE-029 revealed it was only half the fix.

BE-029 correctly changed membership-service to authorize `/internal/**` on the **scope axis** (require `membership.read`, tenant_id-agnostic). But community-service's `IamClientCredentialsTokenProvider` sends a bare `grant_type=client_credentials` body **with no `scope` param** (`IamClientCredentialsTokenProvider.java`). The IAM authorization server (SAS) **omits the `scope` claim entirely** when the token request does not ask for it (live-verified: `grant_type=client_credentials` → `scope=undefined`; `…&scope=membership.read` → `scope=["membership.read"]`). So the real community token carries **no scope**, membership-service's (now correct) converter rejects it, and every `MEMBERS_ONLY`/`PREMIUM` post read still 403s for active members.

> This half was masked in CI because BE-029's corrected test helper minted a token that *did* carry `membership.read` — matching the converter but NOT the real community request. The live member→PREMIUM check (200 expected) is what exposed it. Fixing the real request closes the loop and makes the test faithful.

After this task: community-service requests `scope=membership.read` (the scope IAM V0009 registered to `community-service-client`), the minted token carries it, membership-service grants `ROLE_INTERNAL`, and an active PREMIUM member reads a PREMIUM post → 200.

Live-verified end-to-end after the fix: community (with scope request) → membership `/internal/membership/access` returns `{"allowed":true}` → gateway `GET /api/v1/community/posts/{premiumPostId}` → 200.

---

# Scope

## In Scope

- `IamClientCredentialsTokenProvider`: include `scope=membership.read` in the `client_credentials` token request body (configurable via `iam.internal-client.scope`, default `membership.read`; URL-encoded).
- Update the tests that assert the request body / construct the provider (`IamClientCredentialsTokenProviderTest`, `MembershipCheckerAutoConfigTest`).

## Out of Scope

- membership-service converter (already correct — BE-029, merged).
- IAM / V0009 (the scope is already registered to `community-service-client`).
- Any docker-compose / overlay change.

---

# Acceptance Criteria

- [ ] community-service's `client_credentials` request body is `grant_type=client_credentials&scope=membership.read`.
- [ ] `IamClientCredentialsTokenProviderTest` asserts the scope is requested; both test constructor sites pass the scope arg.
- [ ] End-to-end (live demo + CI): an active PREMIUM member reads a PREMIUM/MEMBERS_ONLY post → 200; the internal access-check returns `{"allowed":true}`.
- [ ] fail-closed preserved: a non-member still → 403; a token without the scope still → rejected by membership.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/contracts/jwt-standard-claims.md` (machine tokens authorize on the scope axis)
- `specs/services/community-service/architecture.md`
- `specs/integration/iam-integration.md`

# Related Skills

- `.claude/skills/backend/` (see `.claude/skills/INDEX.md`)

---

# Related Contracts

- `specs/contracts/http/membership-api.md` — internal `GET /internal/membership/access` (workload identity, scope `membership.read`)

---

# Target Service

- `community-service`

---

# Architecture

Follow:

- `specs/services/community-service/architecture.md`
- Pairs with TASK-FAN-BE-029 (membership receiver requires the scope; this supplies it).

---

# Implementation Notes

- SAS emits no `scope` claim unless the request asks for it — a bare `client_credentials` body yields a scope-less token. The scope MUST be requested.
- Keep the scope configurable (default `membership.read`) so it can track the receiver's required scope without a code change.

---

# Edge Cases

- IAM grants `community-service-client` `account.read` + `membership.read`; requesting only `membership.read` is a valid subset.
- Token caching unaffected (the request body is constant per provider instance).
- author/operator reads short-circuit before the HTTP call (unaffected).

---

# Failure Scenarios

- Requesting a scope the client is not granted → SAS `invalid_scope` → token acquisition fails → fail-closed deny (by design).
- Omitting the scope again → scope-less token → membership rejects → 403 (the bug this fixes).

---

# Test Requirements

- Unit: `IamClientCredentialsTokenProviderTest` — request body includes `scope=membership.read`.
- The pair is covered end-to-end by membership-service's `AccessCheckIntegrationTest` / `InternalAuthIntegrationTest` (BE-029) on the fan Integration Testcontainers lane.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests updated
- [ ] Tests passing
- [ ] Contracts updated if needed (none expected)
- [ ] Specs updated first if required (none expected)
- [ ] Ready for review
