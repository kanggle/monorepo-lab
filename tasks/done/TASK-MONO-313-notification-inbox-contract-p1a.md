# Task ID

TASK-MONO-313

# Title

`ADR-MONO-043 P1a` — Author the shared notification inbox contract (D3)

# Status

done

# Owner

architect

# Task Tags

- adr
- architecture
- notification
- contract
- platform

---

# Goal

First half of ADR-MONO-043 **P1** (the ADR is ACCEPTED, D7 chain UNPAUSED): author the **D3** deliverable — the domain-agnostic **notification envelope + inbox REST contract** + the **aggregator consumption contract** — as a shared `platform/contracts/` spec. This is the foundation the **D4** `libs/` library (P1b, separate task) implements and the **P2** per-domain conformance tasks target.

Spec-only. Defines the shared *shape*; **base path, auth, recipient resolution, tenancy stay domain-owned** (ADR-MONO-043 D6 / jwt-standard-claims.md).

# Scope

## In Scope

| 산출물 | 위치 | 설명 |
|---|---|---|
| 신규 계약 spec | `platform/contracts/notification-inbox-contract.md` | § 1 envelope (REST item shape, field table + JSON example) · § 2 inbox REST shape (GET list paged + `unread`, GET `{id}`, idempotent POST `{id}/read`) · § 3 domain-owned boundary (auth/path/tenancy NOT unified) · § 4 aggregator consumption contract (D2/D5 — uniform shape, per-domain attribution, per-domain credential dispatch, failure isolation hard invariant, read-through) · § 5 conformance matrix (informative, per-domain current→required, wms inbox-vs-delivery-only deferred to P2). |
| README 인덱스 | `platform/README.md` | contracts 테이블에 신규 계약 한 줄. |
| INDEX | `tasks/INDEX.md` done 섹션 | 본 task done entry. |

## Out of Scope

- **D4** `libs/java-notification` library (consumer/dedupe/DLT/Category-C/channel-SPI) — **P1b**, separate task.
- **P2** per-domain conformance (erp/ecommerce/wms/fan) + the wms inbox-vs-delivery-only decision.
- **P3** console-bff aggregator + shell-bell rewire.
- New error codes — `NOTIFICATION_NOT_FOUND` + shared auth codes already registered in `error-handling.md`; the contract reuses them (no registry change).
- Any service/producer code change (zero-retrofit; HARDSTOP-09 — ADR authorises, this task only specs).

---

# Acceptance Criteria

- [x] **AC-1** — `platform/contracts/notification-inbox-contract.md` exists with § 1 envelope (field table: id, sourceDomain, type, title, body, deepLink?, read, readAt?, createdAt) + JSON example.
- [x] **AC-2** — § 2 inbox REST shape: `GET <base>/notifications` (paged `page`/`size`/`unread`), `GET <base>/notifications/{id}`, idempotent `POST <base>/notifications/{id}/read` (no body / no Idempotency-Key); error codes reuse `NOTIFICATION_NOT_FOUND`/`UNAUTHORIZED`/`PERMISSION_DENIED`.
- [x] **AC-3** — § 3 domain-owned boundary explicitly excludes base path / auth / recipient resolution / tenancy / type vocabulary / persistence-channels from the shared shape (ADR-MONO-043 D6).
- [x] **AC-4** — § 4 aggregator consumption contract states the D5 failure-isolation HARD INVARIANT (one domain down ≠ whole bell down), per-domain credential dispatch (D6), per-domain attribution (`sourceDomain`), and read-through (no central store).
- [x] **AC-5** — § 5 conformance matrix maps erp/ecommerce/fan current shape → required delta; wms inbox-vs-delivery-only explicitly deferred to P2.
- [x] **AC-6 (HARDSTOP-03)** — file is project-agnostic in § 1–§ 4 (normative, names no service); § 5 is an informative conformance appendix (domains referenced as targets, mirroring error-handling.md). Lives under `platform/contracts/`.
- [x] **AC-7 (spec-only)** — no service/library code; doc-only PR.

---

# Related Specs

- [ADR-MONO-043](../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — ACCEPTED; D3 = this contract.
- [platform/contracts/jwt-standard-claims.md](../../platform/contracts/jwt-standard-claims.md) — per-domain credential model the § 3 boundary preserves.
- [platform/error-handling.md](../../platform/error-handling.md) — `NOTIFICATION_NOT_FOUND` registry (reused).
- [ADR-MONO-017](../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) D4/D5/D7 — aggregator fan-out machinery the § 4 contract reuses.

# Related Contracts

- This task **authors** the contract (`platform/contracts/notification-inbox-contract.md`). The shared `libs/` types that implement it = P1b.

---

# Edge Cases

- **Shared file carries service-specific normative content** → HARDSTOP-03. Kept § 1–§ 4 project-agnostic; § 5 conformance is informative.
- **Contract redefines per-domain auth** → ADR-MONO-043 D6 / ADR-017 D4 invariant breach. § 3 explicitly keeps auth domain-owned.
- **New error code invented** → unnecessary; inbox reuses existing `NOTIFICATION_NOT_FOUND`.

# Failure Scenarios

- **Contract drifts from ADR-043 D3 decision** → review fail. The envelope + inbox shape mirror the D3 CHOSEN direction.
- **wms inbox decision pre-empted here** → § 5 defers it to P2 (ADR-043 D7).

---

# Definition of Done

- [x] `platform/contracts/notification-inbox-contract.md` authored (§ 1–§ 5 + Relationship).
- [x] `platform/README.md` contracts index row.
- [x] `tasks/INDEX.md` done entry.
- [x] Doc-only PR (no code).
- [ ] commit + push (branch `task/mono-313-notification-contract-p1a`) + PR + merge (3-dim verify).
- [ ] P1b (`libs/java-notification` library, D4) — next separate task.
