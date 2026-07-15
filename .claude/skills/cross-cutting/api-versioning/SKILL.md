---
name: api-versioning
description: API/event versioning, deprecation, coexistence
category: cross-cutting
---

# Skill: API Versioning

Cross-cutting policy for evolving HTTP and event contracts without breaking consumers.

Prerequisite: read `platform/versioning-policy.md` and `platform/api-gateway-policy.md` before using this skill. Concrete contract files live in `specs/contracts/`.

---

## Versioning Strategy

| Channel | Strategy | Rationale |
|---|---|---|
| Public REST | URI path (`/v1/`, `/v2/`) | Discoverable, cache-friendly, gateway routing |
| Internal REST | Header (`Api-Version: 2`) | No URL churn, internal-only |
| Events | `eventVersion` field on the envelope (`eventVersion: 2`) | Decoupled producer/consumer rollout |
| gRPC | Proto package version (`auth.v2`) | Binary compat enforced by codegen |

Default for new services: URI versioning for public endpoints, `eventVersion` field for events.

---

## When to Bump

| Change | Bump? |
|---|---|
| Add optional field | No (additive, backward-compatible) |
| Add optional query parameter | No |
| Make optional field required | **Yes (major)** |
| Remove field | **Yes (major)** |
| Rename field | **Yes (major)** |
| Change type or semantics | **Yes (major)** |
| Change error code mapping | **Yes (major)** |

Patch and minor bumps are not used at the contract level — every breaking change is a major version.

---

## Coexistence Window

When `vN+1` is introduced, `vN` must continue serving for **one full release cycle** (default: 90 days). Both versions live side-by-side in the same service.

```
specs/contracts/http/<service>-api.md         # latest (v2)
specs/contracts/http/<service>-api-v1.md      # legacy (deprecated, sunset date)
```

The deprecated contract file must include:
- `Status: Deprecated`
- `Sunset Date: YYYY-MM-DD`
- `Replaced By: <link to new contract>`

---

## Deprecation Headers

Add to every response from a deprecated endpoint:

```
Deprecation: true
Sunset: Wed, 31 Dec 2026 23:59:59 GMT
Link: </v2/orders>; rel="successor-version"
Warning: 299 - "v1 is deprecated; migrate to v2 by 2026-12-31"
```

---

## Consumer Compatibility Tests

Every contract version must have a contract test (see `testing/contract-test/SKILL.md`) that:
- Pins the exact request/response shape of the version
- Runs in CI on every PR touching the producer service
- Fails the build if the producer breaks the pinned shape

Producer must publish both `vN` and `vN+1` contract tests during the coexistence window.

---

## Event Schema Evolution

The envelope field is **`eventVersion`**, not `schemaVersion` — `platform/event-driven-policy.md` § Event Envelope Format defines `eventVersion` as the canonical field, and `platform/service-types/event-consumer.md` § Schema Versioning states "Consumers MUST branch on `eventVersion`." (Some services' wire envelopes use a `schemaVersion` field instead — this is a documented, live divergence per each project's events contract index (the `specs/contracts/events/` directory's `README.md`), not something to copy into new code; new events use `eventVersion`.)

```json
{
  "eventId": "...",
  "eventType": "...",
  "eventVersion": 2,
  "occurredAt": "...",
  "payload": { ... }
}
```

Consumers must:
- Tolerate unknown fields (forward compat)
- Branch on `eventVersion` if semantics changed
- Reject events with unsupported version into DLQ (see `messaging/consumer-retry-dlq/SKILL.md`)

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Removing a field "because nobody uses it" | Always major bump + sunset window |
| Adding new required field to existing version | Forbidden — make it optional or bump |
| Reusing version number after rollback | Forbidden — always increment forward |
| No sunset date | Required on every deprecated contract |
| Skipping contract tests | CI must enforce; no exception |
| Teaching `schemaVersion` as the event-version field | Canonical field is `eventVersion` (`platform/event-driven-policy.md`, `platform/service-types/event-consumer.md`) |

---

## Verification Checklist

- [ ] Strategy declared in contract file header
- [ ] Coexistence window documented with sunset date
- [ ] Deprecation headers emitted on legacy endpoints
- [ ] Contract tests cover both old and new version during window
- [ ] Consumer services confirmed compatible before sunset
