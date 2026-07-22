# TASK-FAN-BE-027 — refactor-spec: fan-platform title normalization (clean Tier-1)

- **Type**: TASK-FAN-BE (spec-refactor — structural/format only, NO requirement/contract/decision change)
- **Status**: done
- **Service**: fan-platform (contracts)
- **Domain/traits**: fan / [event-driven, transactional, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (mechanical spec polish)

## Goal

Apply the single **clean, meaning-preserving** title-normalization surfaced by a `/refactor-spec
fan-platform` scan (29 spec files; all H1 titles + relative `.md` links inventoried; the
highest-risk cross-project / root-task / ADR / anchor link targets existence-verified; **0 broken
links**). Per `contracts/events/README.md`, fan-platform's conventions are internally consistent, so
the clean batch is intentionally tiny.

No API path, schema field, event payload, status code, or business rule is touched.

## Scope

**Applied — 1 edit / 1 file:**

| # | File | Fix | Category |
|---|---|---|---|
| 1 | `contracts/events/artist-events.md:1` | Title `# artist-events (artist-service Kafka contract)` → `# artist-events — Kafka contract` — aligns with the 2 sibling event contracts `community-events.md` (`# community-events — Kafka contract`) and `fan-membership-events.md` (`# fan-membership-events — Kafka contract`); artist was the sole parenthetical outlier, and `(artist-service …)` is redundant (the filename already names the service). | naming |

## Acceptance Criteria

- [x] 1 edit applied in isolated worktree (`task/fan-be-027-refactor-spec-fan`).
- [x] No markdown link / heading anchor broken (title is an H1 referenced by path, not anchor; the fan/root/cross-project link targets — `TASK-FAN-INT-001`, `TASK-FAN-BE-004`, root `TASK-MONO-023d/025`, `platform/api-gateway-policy.md`, `platform/contracts/notification-inbox-contract.md`, iam `multi-tenancy.md` / `consumer-integration-guide.md` / `account-internal-provisioning.md`, and the iam `admin-api.md#tenant-lifecycle-task-be-256` + `auth-api.md#oauth2--oidc-endpoints-standard-adr-001` anchors — all verified present).
- [x] Zero requirement / contract / schema / status-code / event-payload / business-rule changes.
- [x] Divergence re-verified against the 2 event-contract siblings before applying.

## Related Specs

- `projects/fan-platform/specs/services/{artist,community,membership}-service/` (each: architecture / overview / data-model / dependencies / observability), `services/{fan-platform-web,gateway-service,notification-service}/`
- `projects/fan-platform/specs/contracts/events/README.md` (census — conventions internally consistent; no unification needed)

## Related Contracts

- `projects/fan-platform/specs/contracts/{events,http}/*` — one title normalized, all else semantically unchanged.

## Out of Scope (rejected / deferred — recorded, NOT applied)

**Rejected (intentional, NOT drift):**

- **HTTP contract titles** `artist-api.md` / `community-api.md` / `membership-api.md` all use `# <file> (<service> HTTP contract)` — internally consistent as a set (all three), so no outlier to normalize. Left as-is (not forced to any other project's `# API Contract — <svc>` form; cross-project uniformity is not a refactor-spec goal).

**Report-only (content / code-spec subtlety — NOT a mechanical fix):**

- `artist-events.md:17` — inside the `eventId` field's JSON-example **description** string there is a Korean status annotation (`… — 머지 완료, libs/java-messaging BaseEventPublisher 이 UuidV7.randomString() 발급`). This is Korean prose embedded in example content plus a stale "머지 완료 (merge-complete)" authoring note referencing TASK-MONO-025. Touching an example payload's description text is content editing, not a structural refactor — left for a deliberate doc-fix if desired.
- `contracts/events/README.md` Notes — two producer/spec subtleties recorded there (`artist.*`/`community.*` produced-but-unconsumed; `artist-events.md` `partitionKey` documented as populated but persisted `null` with a relay fallback to `aggregateId`) are honest code/spec observations, not refactor items.

**Tier-2 structural (not swept this round):**

- Cross-service section-set / ordering parity across the 3 domain services' `architecture/overview/data-model/dependencies/observability` docs was not exhaustively diffed under this session's constraint. Titles are uniformly `# <service> — <Doc>` (template-authored), so parity is likely intact; a later pass can confirm and handle any outlier as Tier-2 judgment.

## Edge Cases

- Title (H1) edit has no inbound anchor links — docs referenced by path, not by their own H1 fragment.

## Failure Scenarios

- If any doc linked `artist-events.md` by an H1 anchor fragment, the title change would 404 it — not the case (all references are path-based).
