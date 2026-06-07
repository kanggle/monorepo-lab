# fan-platform v1 — End-to-End Test Scenarios

> Source of truth for the end-to-end test suite under
> `projects/fan-platform/tests/e2e/`. Each scenario class in
> `src/test/java/com/example/fanplatform/e2e/scenario/` maps 1:1 to a section
> below.
>
> Spec authored as part of [TASK-FAN-INT-001](../../tasks/done/TASK-FAN-INT-001-v1-services-e2e.md).

---

## Goal

Validate that the three v1 fan-platform backend services
(`gateway-service`, `community-service`, `artist-service`) work correctly
**together** — booted on the same Docker network with the canonical Postgres
+ Redis + Kafka backing infra and a JWKS stand-in — and that gateway-issued
JWTs flow end-to-end through cross-service business operations.

Slice / unit / per-service integration tests verify each service in
isolation; this suite covers the **composition** that no single service-level
test can. Per portfolio demo intent, GREEN here is the green light for
"`pnpm fan-platform:up` works and the service map matches the contracts".

---

## Test infrastructure

### Containers (boot order)

1. **Postgres 16** (`postgres:16-alpine`) — initialises the per-service
   databases (`fanplatform_community`, `fanplatform_artist`) via the same
   init script the docker-compose uses
   (`projects/fan-platform/infra/postgres/init/01-create-databases.sh`).
2. **Redis 7** (`redis:7-alpine`).
3. **Kafka KRaft single-broker** (`apache/kafka:3.7.0`) — listens on the
   internal port `:9095` so peer containers reach the broker via
   `fan-e2e-kafka:9095` (the default `:9092` listener advertises
   `localhost:MAPPED_PORT` which is unreachable from inside other
   containers — same trick as the wms-platform e2e harness).
4. **JWKS stand-in** — a `MockWebServer` running in the host JVM, bound to
   `0.0.0.0:<ephemeral>`. Each service container reaches it via
   `host.docker.internal:<port>` (added with `withExtraHost(...)`).
5. **artist-service** — pre-built image (CI: tagged `fan-platform-artist-service:e2e`,
   passed via `-Dfan.e2e.artistImage`) or `ImageFromDockerfile` (local).
6. **community-service** — same dual path.
7. **gateway-service** — same dual path; receives a `SPRING_APPLICATION_JSON`
   override that injects two `RewritePath` filters (see
   *Production routing bug — known issue* below).

### JWT helper

`com.example.fanplatform.e2e.testsupport.JwtTestHelper` mints RS256 tokens
with the issuer `http://iam.local` (matches `OIDC_ISSUER_URL` default) and
the configured `tenant_id` claim. Convenience methods:

| Method | sub | tenant_id | role/roles claims |
|---|---|---|---|
| `signFanToken(sub)` | sub | `fan-platform` | `FAN` |
| `signAdminToken(sub)` | sub | `fan-platform` | `ADMIN` |
| `signOperatorToken(sub)` | sub | `fan-platform` | `OPERATOR` |
| `signSuperAdminToken(sub)` | sub | `*` | `SUPER_ADMIN` |
| `signCrossTenantToken(sub)` | sub | `wms` | `OPERATOR` |

Tokens carry a 10-minute TTL — comfortably outliving even a slow CI run.

### JWKS server

`JwksMockServer` serves `/.well-known/jwks.json` from the host JVM. Two URL
forms:

- `hostJwksUrl()` — `http://localhost:<port>/...`, usable from the test
  process itself.
- `containerJwksUrl()` — `http://host.docker.internal:<port>/...`, wired
  into each service container as `JWT_JWKS_URI`.

### Kafka consumer helper

`KafkaTestConsumer` polls the broker on a daemon thread and exposes
`drain()` for Awaitility loops. Each instance subscribes with a unique
group id so concurrent scenarios do not contend for partitions.

---

## Routing — production state (post TASK-FAN-BE-005 merge)

The fan-platform gateway production `application.yml` declares 4 routes with
`RewritePath` filters that strip the `/api/v1/...` public prefix to the
service-internal path:

```
/api/v1/community/**     -> community-service     RewritePath=/api/v1/community/(?<seg>.*),/api/community/${seg}
/api/v1/artists/**       -> artist-service        RewritePath=/api/v1/artists/(?<seg>.*),/api/artists/${seg}
/api/v1/artist-groups/** -> artist-service        RewritePath=/api/v1/artist-groups/(?<seg>.*),/api/artist-groups/${seg}
/api/v1/fandoms/**       -> artist-service        RewritePath=/api/v1/fandoms/(?<seg>.*),/api/fandoms/${seg}
```

See `services/gateway-service/architecture.md` § Routes for the canonical filter set.

> Historical note: an earlier draft of this spec described an e2e
> `SPRING_APPLICATION_JSON` workaround for missing RewritePath filters; that
> workaround is no longer required. The TASK-FAN-INT-001 e2e suite still does
> not modify production code, but the production gateway already declares the
> filters above.

---

## Scenario matrix

| Scenario | Auth | Tenant | Role | Visibility | Expected status |
|---|---|---|---|---|---|
| `ArtistAndPostFlowE2ETest.fullArtistAndPostFlow` (step 1, 2) | Bearer | `fan-platform` | `ADMIN` | n/a | 201 / 200 |
| `ArtistAndPostFlowE2ETest.fullArtistAndPostFlow` (step 3, 4, 5b) | Bearer | `fan-platform` | `FAN` | `PUBLIC` | 201 / 200 |
| `ArtistAndPostFlowE2ETest.fullArtistAndPostFlow` (step 5a) | Bearer | `fan-platform` | `FAN` | n/a | 200 |
| `MultiTenantIsolationE2ETest.crossTenantTokenIsBlocked` | Bearer | `wms` | `OPERATOR` | n/a | 403 `TENANT_FORBIDDEN` |
| `MultiTenantIsolationE2ETest.unauthenticatedRequestIsRejected` | none | — | — | n/a | 401 `UNAUTHORIZED` |
| `MultiTenantIsolationE2ETest.fanPlatformTenantTokenIsAllowed` | Bearer | `fan-platform` | `FAN` | n/a | 200 |
| `VisibilityTierE2ETest.publicPostIsReadableByAnyTenantMember` | Bearer | `fan-platform` | `FAN` | `PUBLIC` | 200 |
| `VisibilityTierE2ETest.premiumPostBypassesGateAndLogsWarn` | Bearer | `fan-platform` | `FAN` | `PREMIUM` | 200 + WARN log |
| `VisibilityTierE2ETest.membersOnlyPostUnderV1StubAllowsAccess` | Bearer | `fan-platform` | `FAN` | `MEMBERS_ONLY` | 200 (v1 stub only) |

---

## Scenario 1 — `ArtistAndPostFlowE2ETest`

### Premise

Three identities — admin, fan, fan2 — operate against a fresh stack.
Tokens carry `tenant_id=fan-platform`.

### Steps

1. **Admin registers an artist (DRAFT)** — `POST /api/v1/artists` with
   `artistType=SOLO`, a unique `stageName`, and a small bio. Asserts
   - 201 status,
   - envelope `{ data: { id, status: DRAFT, stageName, tenantId, ... }, meta }`,
   - id parses as a UUID.
2. **Admin publishes** — `PATCH /api/v1/artists/{id}/status` with
   `{"status":"PUBLISHED"}`. Asserts 200 + `data.status=PUBLISHED` +
   `data.publishedAt != null`.
3. **Outbox -> Kafka assertion (artist)** — Awaitility (30 s, 500 ms poll)
   verifies the test consumer receives:
   - `artist.registered.v1` keyed by the artist resource id, payload echoes
     the unique stageName,
   - `artist.published.v1` keyed by the same id, payload contains a non-null
     `publishedAt`.
   - Each envelope is asserted on `eventType`, `source=fan-platform-artist-service`,
     `partitionKey`, and `eventId` parseability (UUID v7 per
     [TASK-MONO-025](../../../../tasks/done/TASK-MONO-025-base-event-publisher-uuidv7.md)
     머지 완료).
4. **Fan follows the artist** — `POST /api/v1/community/follows` with
   `{ "artistAccountId": "<random UUID>" }`. The contract treats
   `artistAccountId` as opaque (v1 community-service does not
   cross-validate against artist-service). Asserts 201.
5. **Fan publishes a FAN_POST (PUBLIC)** — `POST /api/v1/community/posts`
   with `postType=FAN_POST`, `visibility=PUBLIC`, unique body. Asserts 201,
   `data.status=PUBLISHED`, `data.authorAccountId == fanAccountId`. Outbox
   delivers `community.post.published.v1` (Awaitility; same shape
   assertions).
6. **Fan2 reacts LIKE** — `PUT /api/v1/community/posts/{postId}/reactions`
   with `{"reactionType":"LIKE"}`. Asserts 200 + `data.reactionType=LIKE`.
7. **Fan reads feed** — `GET /api/v1/community/feed?page=0&size=20`. Asserts
   200 + the just-published `postId` appears in `data.content[]`.

### Race avoidance

Each scenario uses unique-per-class fixture markers
(`uniqueStageName(prefix)`, `uniquePostBody(prefix)`) so the Awaitility
filter on Kafka events cannot accidentally match a record produced by a
parallel scenario class. Mirrors the
[TASK-MONO-023d](../../../../tasks/done/TASK-MONO-023d-outbox-related-failures.md) pattern.

---

## Scenario 2 — `MultiTenantIsolationE2ETest`

### Premise

The fan-platform gateway enforces `tenant_id ∈ { fan-platform, * }`. Any
other tenant value yields 403 `TENANT_FORBIDDEN` BEFORE the request reaches
community-service or artist-service.

### Sub-cases

- **Cross-tenant token (`tenant_id=wms`)** — `GET /api/v1/community/feed`.
  Asserts 403 + envelope `code=TENANT_FORBIDDEN`. The gateway's
  `TenantClaimValidator` (Spring Security `OAuth2TokenValidator`) rejects
  the token at decode time; the resource-server filter chain converts the
  validation error to a 403 envelope via `OAuth2ResourceServerConfig`.
- **Unauthenticated request** — same path, no `Authorization` header.
  Asserts 401 + envelope `code=UNAUTHORIZED`.
- **fan-platform tenant** — same path with `signFanToken(...)`. Asserts
  200 + `data.content` is an array (empty is fine for a brand-new fan).

### v1 limitation acknowledged

The "request never reaches community-service" assertion is verified by
the gateway-service's own integration tests
(`GatewayBootstrapIntegrationTest` § cross-tenant 403). The e2e test
asserts the response envelope alone — using both the gateway's
`TENANT_FORBIDDEN` code AND community-service's `TENANT_FORBIDDEN` code
yields the same envelope, so the e2e cannot distinguish "blocked by gate"
from "rejected by community". Both verify the cross-tenant claim cannot
read fan-platform data; the *layer* of the rejection is verified by the
gateway-only integration test.

---

## Scenario 3 — `VisibilityTierE2ETest`

### Premise

`PostAccessGuard.ensureVisibilityAccessible` (community-service) gates
posts by `visibility ∈ { PUBLIC, MEMBERS_ONLY, PREMIUM }`:

- `PUBLIC` — any authenticated tenant member.
- `MEMBERS_ONLY` — author + members confirmed by `MembershipChecker`.
- `PREMIUM` — v1 always passes + WARN log + TODO. v2 will hard fail-close.

### Sub-cases

- **PUBLIC** — author publishes a `FAN_POST` with `visibility=PUBLIC`. A
  different fan reads via `GET /api/v1/community/posts/{id}` -> 200 with
  `body` populated.
- **PREMIUM** — author publishes with `visibility=PREMIUM`. A different
  fan reads -> 200. Test additionally captures the community container's
  stdout via `GenericContainer.getLogs()` and asserts it contains
  `PREMIUM gate bypassed` AND the post id, verifying the WARN log fires
  per the production code path.
- **MEMBERS_ONLY (v1 stub only)** — author publishes with
  `visibility=MEMBERS_ONLY`. A different fan reads -> 200 because v1 wires
  `AlwaysAllowMembershipChecker` (`@ConditionalOnMissingBean`) which
  always returns `true`. The test asserts the corresponding
  `Membership gate bypassed (v1 stub)` WARN line appears in container logs.

### v1 limitation acknowledged

The full MEMBERS_ONLY contract has two branches — `200 OK when
hasAccess()=true` AND `403 MEMBERSHIP_REQUIRED when hasAccess()=false`.
The 403 branch requires a **deny** stub of `MembershipChecker`. The e2e
suite runs the actual built community-service image, so the bean cannot be
swapped from inside the test classpath. **Follow-up:** add a
`SPRING_PROFILES_ACTIVE=e2e-membership-deny` profile to community-service
that registers a `DenyAllMembershipChecker` and wire a fourth scenario
case asserting 403 `MEMBERSHIP_REQUIRED`. Tracked in the PR summary as
follow-up TASK-FAN-INT-NNN.

---

## Out of scope (v1 deliberately excluded)

- **membership-service / notification-service / admin-service** — v2.
- **Frontend e2e (Playwright)** — TASK-FAN-FE-001.
- **Performance / load testing** — separate task.
- **`community.reaction.added` / `community.comment.added` consumer
  verification** — no v1 consumer exists (notification-service is v2).
- **Cross-project E2E (real IAM IdP)** — IAM is mocked via WireMock JWKS
  here; full IAM login/redirect is the frontend e2e's territory.
- **artist self-service** — admin-only in v1.
- **MEMBERS_ONLY 403 branch** — see scenario 3 v1 limitation above.

---

## CI integration

GitHub Actions job `fan-platform-e2e` (added by TASK-FAN-INT-001 to
`.github/workflows/ci.yml`):

1. `needs: [build-and-test, fan-platform-boot-jars]` — depends on the
   compile-and-unit-test job and a new boot-jar packaging job.
2. Downloads the `fan-platform-boot-jars` artifact (gateway-service,
   community-service, artist-service jars) and restores them under the
   canonical `apps/<svc>/build/libs/<svc>.jar` paths.
3. Builds three docker images via `docker build` CLI (per
   [TASK-MONO-015](../../../../tasks/done/) — Testcontainers'
   `ImageFromDockerfile` hangs on Docker 28 BuildKit gRPC; pre-building
   sidesteps the issue).
4. Runs `./gradlew :projects:fan-platform:tests:e2e:e2eTest
   -Dfan.e2e.gatewayImage=...:e2e -Dfan.e2e.communityImage=...:e2e
   -Dfan.e2e.artistImage=...:e2e`.
5. Uploads `build/reports/tests/e2eTest/` and `build/test-results/e2eTest/`
   on failure for triage.
6. `if: github.repository == 'kanggle/monorepo-lab'` — extracted
   standalone repos do not include the dev-only e2e signal.

Timeout: 20 minutes (3 service images + Postgres + Redis + Kafka + JWKS
+ test execution).

---

## Failure modes & graceful skip

- **Docker daemon unreachable** — `@Testcontainers(disabledWithoutDocker = true)`
  on the base test class causes JUnit to skip the suite cleanly (Windows
  dev machines typically use WSL + Docker Desktop integration; CI Linux
  runs).
- **Boot jar missing AND no `fan.e2e.<svc>Image` system property** — the
  base class throws `IllegalStateException` with a clear message at boot.
- **Kafka topic race** — Awaitility filters by a unique fixture marker
  (stageName / postBody). A parallel scenario's record on the same topic
  cannot accidentally satisfy another scenario's assertion.
- **Outbox polling delay** — the e2e profile sets
  `OUTBOX_POLLING_INTERVAL_MS=500` so the 30 s Awaitility window catches
  publishes promptly.

---

## See also

- `projects/wms-platform/apps/gateway-service/src/e2eTest/...` — primary
  reference pattern (live-pair Testcontainers e2e).
- `projects/fan-platform/apps/gateway-service/src/test/.../testsupport/` —
  source helpers ported here (kept as a copy because Gradle does not
  expose one project's test-source classes to another module).
- `platform/testing-strategy.md` § E2E.
