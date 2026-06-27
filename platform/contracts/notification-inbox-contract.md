# Notification Inbox Contract

This contract defines the **domain-agnostic notification envelope** and the **inbox REST shape** that every per-domain notification surface conforms to, plus the **aggregator consumption contract** the platform-console shared-shell notification bell relies on.

It is the **D3** deliverable of [ADR-MONO-043](../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) (ACCEPTED — notification architecture unification). Per that ADR:

- **Only the *shape* is shared** (the envelope fields + the inbox verb/paging/read semantics). The **base path, authentication, recipient resolution, and tenancy stay domain-owned** (ADR-MONO-043 D6 / ADR-MONO-017 D4 / [jwt-standard-claims.md](jwt-standard-claims.md)). This contract does **not** unify auth.
- The four per-domain notification-services (erp, ecommerce, wms, fan) remain four independent deployables (ADR-MONO-043 D1). They **conform** to this shape; they are not merged.
- This contract is **spec-only**. The shared `libs/` library that implements the consumer/dedupe/delivery machinery is the **D4** deliverable (a separate task); per-domain conformance is **P2**; the `console-bff` aggregator is **P3**.

> **HARDSTOP-03 note.** This file lives under `platform/contracts/` (shared regulation) and is **project-agnostic**: § 1–§ 4 are normative and name no service. § 5 (Conformance matrix) is an **informative** appendix that maps the existing per-domain surfaces to the shape — it references the four domains as conformance targets, mirroring how `platform/error-handling.md` carries per-domain sections.

---

## 1. The notification envelope (REST item shape)

The canonical inbox item returned by every conforming inbox surface. JSON field names are normative.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | string | no | Stable notification identifier (opaque; UUID recommended). Unique within the owning domain. |
| `sourceDomain` | string | no | The owning domain — `"erp"`, `"ecommerce"`, `"wms"`, `"fan"`, … The aggregator (§ 4) uses this for attribution. A conforming single-domain surface MAY omit it from its own response **iff** the aggregator injects it from the call target; the canonical shape includes it so a merged feed is self-describing. |
| `type` | string | no | Domain notification type discriminator (e.g. `APPROVAL_SUBMITTED`, `MEMBERSHIP_ACTIVATED`). Opaque to the aggregator + shell; the domain owns the vocabulary. |
| `title` | string | no | Short human-readable headline. |
| `body` | string | no | Human-readable detail (plain text; rendering is the client's concern). |
| `deepLink` | string | yes | Optional in-app link the shell may navigate to (e.g. a relative console route). `null`/absent when the domain does not supply one. |
| `read` | boolean | no | Whether the recipient has read it. The single normative read signal (a domain that models read state as an enum maps it to this boolean in its response). |
| `readAt` | string (ISO-8601 UTC) | yes | When it was read. **Omitted** (JSON `NON_NULL`) when `read=false`. |
| `createdAt` | string (ISO-8601 UTC) | no | When the notification was created. The default sort key (newest first). |

### 1.1 Example

```json
{
  "id": "01928c4a-7e9f-7c00-9a40-d2b1f5e8c500",
  "sourceDomain": "erp",
  "type": "APPROVAL_SUBMITTED",
  "title": "결재 요청 도착",
  "body": "구매 품의 #PR-2026-0042 결재가 요청되었습니다.",
  "deepLink": "/erp/approvals/PR-2026-0042",
  "read": false,
  "createdAt": "2026-06-28T01:23:45Z"
}
```

### 1.2 Non-normative domain extensions

A domain MAY carry additional fields in its **own** inbox response (e.g. a `membershipId` back-reference, a `sourceType`/`sourceId` pair). Such fields are **ignored by the aggregator and the shell** — only the table-1 fields are consumed cross-domain. Extensions must not rename or retype the canonical fields.

---

## 2. The inbox REST shape

Every conforming inbox surface exposes the following verbs under its **own domain base path** `<base>` (e.g. `/api/erp/notifications`, `/api/fan/notifications`). The base path and the auth scheme are domain-owned (§ 3); the **verbs, query parameters, paging, and read semantics below are normative**.

| Verb | Path | Query | Semantics |
|---|---|---|---|
| `GET` | `<base>/notifications` | `unread` (boolean, optional), `page` (int ≥ 0, default 0), `size` (int 1–100, default 20) | The caller's inbox, newest-first (`createdAt` desc). `unread=true` filters to unread only; absent = all. Returns a paged list of envelope items (§ 1). |
| `GET` | `<base>/notifications/{id}` | — | A single item the caller owns. Foreign/unknown `id` → `404 NOTIFICATION_NOT_FOUND` (recipient-scoped; see § 2.3). A domain MAY omit this verb if its product has no detail view (the aggregator does not require it). |
| `POST` | `<base>/notifications/{id}/read` | — | **Idempotent** mark-as-read: a state-converging assignment. Re-marking an already-read item is a no-op that preserves the original `readAt`. **No request body, no `Idempotency-Key`** (the operation accumulates no side effect). Returns the updated item. |

### 2.1 Read filter alias

`unread` (boolean) is the normative query parameter. A domain whose internal model uses a read-state enum (e.g. `UNREAD`/`READ`) maps `unread=true → status=UNREAD` and `unread=false → status=READ` at the controller edge. (Existing surfaces that historically exposed a `status` enum query parameter SHOULD add the `unread` alias during conformance; see § 5.)

### 2.2 Pagination + response envelope

The list response carries the items plus pagination metadata. The **wrapper shape is domain-owned** (some domains wrap as `{ data, meta: { page, size, totalElements } }`, others return a bare paged record) — the aggregator (§ 4) adapts per domain. What is normative is that the **items conform to § 1** and the surface supports `page`/`size`/`unread`.

### 2.3 Errors

The inbox surface reuses the existing registered codes in [error-handling.md](../error-handling.md):

- `NOTIFICATION_NOT_FOUND` (404) — unknown id, **or** an id not owned by the caller (foreign-recipient access returns 404, not 403, to avoid an existence leak). Already registered under the erp + ecommerce domain sections.
- `UNAUTHORIZED` (401) — missing/invalid/expired token (Platform-Common Authentication).
- `PERMISSION_DENIED` (403) — caller lacks the domain's inbox-read gate.

No new shared error code is introduced by this contract.

---

## 3. Domain-owned boundary (what this contract does **not** unify)

Per ADR-MONO-043 D6 + [jwt-standard-claims.md](jwt-standard-claims.md), the following stay **domain-owned** and are explicitly **out of this contract's scope**:

- **Base path** — each domain keeps its prefix (`/api/<domain>/notifications` or `/api/notifications`).
- **Authentication + recipient resolution** — each domain resolves the recipient by its own rule (JWT `sub`, an `X-User-Id` header, …) and gates reads by its own authorization. This contract neither prescribes nor relaxes auth (ADR-MONO-017 D4 HARD INVARIANT — the credential rule MUST NOT be retroactively redefined).
- **Tenancy** — tenant resolution (a `tenant_id` JWT claim, a context default, …) is producer/domain-enforced, not contract-imposed.
- **Notification `type` vocabulary** — each domain owns its set; the contract treats `type` as opaque.
- **Persistence schema + idempotency store + external channels** — domain-owned; the shared *machinery* for these is the D4 library (separate task), not this contract.

---

## 4. Aggregator consumption contract (D2 / D5)

The platform-console `console-bff` notification **aggregator** (ADR-MONO-043 D2, the P3 deliverable) fans out to each domain's inbox and merges the feeds into the single shared-shell bell. This contract pins what the aggregator relies on:

1. **Uniform item shape** — every per-domain `GET <base>/notifications` returns items conforming to § 1, so the aggregator parses one model and merges/sorts by `createdAt` desc across domains.
2. **Per-domain attribution** — each merged item carries `sourceDomain` (§ 1). When a domain omits it, the aggregator injects it from the call target. The shell uses it to label + route each item.
3. **Per-domain credential dispatch** (D6) — the aggregator attaches **each domain's own credential** per outbound call (it is a *dispatcher*, never a credential rewrite). It does not mint a unified notification token.
4. **Failure isolation is a HARD INVARIANT** (ADR-MONO-043 D5 / ADR-MONO-017 D5) — the aggregator calls each domain independently with per-domain timeout + circuit-breaker and **degrades per domain**: a `503`/timeout/network from one domain yields a partial feed (that domain marked degraded) while the others render. The shared-shell bell **MUST NOT** be coupled to any single domain's availability. (This is the regression that prompted ADR-MONO-043: a downed single upstream made the bell fail on every console page.)
5. **Read-through, not store-through** — the aggregator holds no notification store of its own; it reads each domain's authoritative inbox live (ADR-MONO-043 D2 rejected a central store). Mark-read is proxied to the owning domain's `POST <base>/notifications/{id}/read`.

---

## 5. Conformance matrix (informative — current state → required for P2)

> Informative appendix. The normative contract is § 1–§ 4. The P2 per-domain conformance tasks (one per domain) reconcile each surface to the shape; the **wms inbox-vs-delivery-only decision is itself deferred to P2** (ADR-MONO-043 D7 / § 3.2).

| Domain | Base path | Current item shape vs § 1 | Read model | Conformance delta (P2) |
|---|---|---|---|---|
| **erp** | `/api/erp/notifications` | `{ id, type, title, body, sourceType, sourceId, read, createdAt, readAt? }` | `is_read` boolean | Add `sourceDomain` (`"erp"`) + optional `deepLink`. Already has `unread` filter, idempotent mark-read, `NOTIFICATION_NOT_FOUND`. Closest to the shape. |
| **fan** | `/api/fan/notifications` | `{ id, type, title, body, status, read, membershipId, createdAt, readAt? }` | `status` `UNREAD`/`READ` enum | Add `sourceDomain` (`"fan"`); add the `unread` query alias (currently `status=`); add the single-item `GET /{id}` if a detail view is wanted (optional). |
| **ecommerce** | `/api/notifications` | `{ notificationId, channel, subject, status, sentAt, createdAt }` | delivery `status` (`PENDING`/`SENT`/`FAILED`) — **no read state** | Largest delta: this is a delivery-status tracker, not an in-app inbox. Map `notificationId→id`, `subject→title`, add `body`/`read`/`readAt`/`sourceDomain`. Adding recipient read-state is a P2 design step (or expose only the operator-facing subset). |
| **wms** | none (event→delivery engine; re-emits `wms.notification.delivered.v1`) | no inbox; `notification_delivery` rows only | n/a | **Deferred decision (P2):** whether wms gains a conforming `/notifications` inbox surface, or stays delivery-only and is **excluded** from the aggregator bell. Not decided here (ADR-MONO-043 D7 / § 3.2). |

---

## Relationship

- **[ADR-MONO-043](../../docs/adr/ADR-MONO-043-notification-architecture-unification.md)** — the ACCEPTED decision this contract realises (D3 = this contract; D4 = the `libs/` library; D2/D5 = the aggregator the § 4 contract serves; D6 = the domain-owned auth boundary in § 3).
- **[error-handling.md](../error-handling.md)** — registry for `NOTIFICATION_NOT_FOUND` and the shared auth codes the inbox surface reuses (§ 2.3). No new code introduced.
- **[jwt-standard-claims.md](jwt-standard-claims.md)** — the per-domain credential/recipient model the § 3 boundary preserves.
- **[ADR-MONO-017](../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md)** D4/D5/D7 — the console-bff fan-out + per-domain credential dispatch + per-section degrade machinery the § 4 aggregator contract reuses.
