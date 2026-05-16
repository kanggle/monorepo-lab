# ADR-MONO-014 — platform-console Operator Authentication Model (GAP OIDC ↔ admin-service Operator Token Exchange)

**Status:** PROPOSED
**Date:** 2026-05-16
**History:** PROPOSED 2026-05-16 (TASK-MONO-109 — resolves a HARDSTOP-06/09 surfaced during ADR-MONO-013 Phase 2 investigation; decision direction **B (RFC 8693-style token exchange)** chosen by user-explicit answer).
**Decision driver:** Phase 2 (console GAP-operator parity) investigation found that every `/api/admin/**` operator endpoint — including the BE-296 registry — requires an **admin-service-issued operator token** (`token_type=admin`, `iss=admin-service`), while `platform-console` authenticates operators via the GAP OIDC public client `platform-console-web` (issued by auth-service / Spring Authorization Server). No spec or ADR defines how the OIDC login token becomes an operator token. The console↔operator integration is unrealizable until this is decided.
**Supersedes:** none. **Amends:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) § D5 (which fixed only the OIDC skeleton and deferred the operator-auth bridge). **Reconciles:** `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1 ↔ § 2.2 (self-contradictory, shipped in PR #569) + `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Authentication.
**Related:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md), GAP [ADR-001](../../projects/global-account-platform/docs/adr/ADR-001-oidc-adoption.md) (OIDC AS), GAP [ADR-002](../../projects/global-account-platform/docs/adr/) (`tenant_id='*'` platform-scope sentinel), GAP [ADR-003](../../projects/global-account-platform/docs/adr/ADR-003-public-client-refresh-token-revoke-converter.md) (public-client lineage), `projects/global-account-platform/specs/services/admin-service/security.md` (operator JWT boundary), TASK-BE-296 (#568), TASK-PC-FE-001 (#569), RFC 8693 (OAuth 2.0 Token Exchange).

---

## 1. Context

### 1.1 The conflict

- **Producer side (BE-296, #568):** `console-registry-api.md` § Authentication — registry (and, by the same `/api/admin/**` invariant, every operator endpoint: `accounts/{id}/lock|unlock`, `accounts/bulk-lock`, `sessions/{id}/revoke`, `accounts/{id}/gdpr-delete|export`, `audit`, `operators/*`) requires `Authorization: Bearer <operator-token>` with `token_type=admin`, `iss=admin-service`, verified by `OperatorAuthenticationFilter`.
- **Consumer side (FE-001, #569):** `console-integration-contract.md` § 2.1 — console authenticates operators via GAP OIDC public client `platform-console-web` (Auth Code + PKCE, issued by auth-service / SAS). § 2.2:L29 then asserts the console "calls this server-side with the logged-in operator's GAP access token from the HttpOnly cookie" — a token that is **not** `token_type=admin` / `iss=admin-service`.
- **Gap:** no spec/ADR defines the transformation from the GAP OIDC token to an admin-service operator token. The contract is internally self-contradictory; the console's registry/operator calls would `401` in a live environment (FE-001 unit tests mock `fetch`, so this latent integration defect shipped to `main` in #569 — tolerable only because console-web has no live deployment and no Phase-2 operator screens consume it yet).

### 1.2 Why an ADR

Per `platform/hardstop-rules.md` HARDSTOP-06 #3 + HARDSTOP-09 #2: the resolution is cross-service (GAP auth-service + GAP admin-service + platform-console), irreversible-ish (defines the operator trust boundary), and shapes ADR-MONO-013 Phase 2–8. It cannot be chosen during implementation. This ADR is that decision record; Phase 2 / `TASK-PC-FE-002` is PAUSED until it is ACCEPTED.

### 1.3 Why PROPOSED, not ACCEPTED

The decision *direction* (Model B) is chosen, but ACCEPTED gates real churn (a new GAP exchange endpoint + OIDC↔operator mapping + contract rewrite + Phase-2 resume). Same staged pattern as ADR-MONO-008/013: PROPOSED records the decision + design + sequencing; ACCEPTED authorises execution.

---

## 2. Decision

### D1 — Model

| Option | Mechanics | Verdict |
|---|---|---|
| **B. Token exchange (RFC 8693-style)** | Console (server-side) presents the operator's GAP OIDC `platform-console-web` access token to a GAP exchange endpoint → receives a short-lived **admin-service operator token** (`token_type=admin`, `iss=admin-service`). | **CHOSEN** (user direction) — preserves ADR-MONO-013 D1 single GAP-OIDC SSO **and** admin-service's operator-token invariant; bounded blast radius (one new endpoint + a mapping). |
| A. admin-service trusts OIDC directly | `OperatorAuthenticationFilter` accepts `platform-console-web` OIDC tokens as a 2nd issuer | Rejected — widens the operator trust boundary to a 2nd issuer for every `/api/admin/**` call; harder to reason about than a single explicit exchange. |
| C. Console uses admin credential login | Console drops OIDC, uses `POST /api/admin/auth/login` (id/pw/TOTP) | Rejected — contradicts ADR-MONO-013 D1 + FE-001 PKCE; defeats the SSO portfolio narrative. |
| D. Operator identity unification | auth-service issues operator-capable OIDC tokens; admin-web auth re-platformed | Rejected (now) — largest scope; re-platforms admin-web auth; deferrable future ADR if ever. |

### D2 — Exchange endpoint (owner + shape; finalised in the GAP impl task)

- **Owner: `admin-service`.** It already mints operator tokens (`AdminAuthController` login), owns `OperatorAuthenticationFilter`, and owns the operator↔tenant scope model (ADR-002). The exchange = "validate the GAP OIDC token + resolve the OIDC subject to an `admin_operators` row + mint the existing operator token."
- **Shape (proposed):** `POST /api/admin/auth/token-exchange` — body/grant per RFC 8693 (`grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token=<GAP OIDC access token>`, `subject_token_type=urn:ietf:params:oauth:token-type:access_token`). Validates the subject token against auth-service JWKS + `platform-console-web` audience/issuer. Returns the standard admin operator token (`token_type=admin`, `iss=admin-service`, operator tenant scope from `admin_operators.tenant_id`). Short-lived (≤ the operator-token TTL already used by login). On the gateway public-path `/api/admin/**` subtree (same invariant as registry).
- **Refresh:** the console re-exchanges using its GAP-rotated access token (FE-001 already does GAP refresh) — admin-service issues a fresh operator token per exchange; no separate operator-refresh state.

### D3 — OIDC subject ↔ operator mapping (principle; mechanism finalised in GAP impl task)

- Deterministic + **fail-closed**: an OIDC principal with no resolvable `admin_operators` row → `401 TOKEN_INVALID` (reuse existing `OperatorUnauthorizedException`). Cross-tenant scope is taken from `admin_operators.tenant_id` (ADR-002 `'*'` platform sentinel) — never from the OIDC token.
- The link key (email vs a provisioned `admin_operators.oidc_subject` column) is a sub-decision for the GAP impl task; the ADR fixes only that it must be explicit, deterministic, and operator-row-authoritative (the OIDC token never elevates scope).

### D4 — Contract reconciliation mandate (spec-first, part of execution)

ACCEPTED execution MUST reconcile, before any console code change:
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1 (add the server-side exchange step) + § 2.2:L29 (auth model = operator token **obtained via the exchange**, not "send the GAP token directly").
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Authentication (note the operator token is obtained via the new exchange; the producer requirement `token_type=admin` is **unchanged**).
- GAP `admin-api.md` + admin-service `architecture.md`/`security.md` — add the exchange endpoint contract + the OIDC-subject→operator mapping + boundary rationale.

### D5 — Downstream task plan + sequencing (post-ACCEPTED)

1. **GAP `TASK-BE-298`** (project-internal, spec-first): implement `POST /api/admin/auth/token-exchange` + OIDC-subject→operator mapping + spec reconciliation (admin-api.md / admin-service architecture.md / security.md) + Testcontainers ITs (valid exchange, no-mapping→401, scope from operator row, expiry, regression to existing operator login).
2. **platform-console `TASK-PC-FE-002a`** (or FE-001 follow-up): console server-side exchange wiring (on session establish/refresh: GAP token → operator token; registry + operator API client use the operator token) + fix the `console-integration-contract.md` self-contradiction shipped in #569.
3. **THEN** Phase 2 operator-parity slices resume: `TASK-PC-FE-002` accounts (search/detail/lock/unlock/bulk-lock/revoke-session/gdpr-delete/export) → `FE-003` audit + security read → `FE-004` operators mgmt → `FE-005` dashboards → `FE-006` parity verification (gates ADR-MONO-013 Phase 3 admin-web retirement).

### D6 — Readiness + ACCEPTED transition

ACCEPTED when: D2/D3 endpoint shape + mapping key finalised in the GAP impl task scope; user-explicit intent ("ADR-014 ACCEPTED" / "토큰 익스체인지 진행" / equivalent). On ACCEPTED: append § 6 row, author `TASK-BE-298`, begin D5 sequence. Until then Phase 2 / `TASK-PC-FE-002` stays unstarted.

---

## 3. Consequences

- **PROPOSED merge (this PR):** the HARDSTOP-06/09 is recorded + resolved-in-principle; Phase 2 has an unblock path; the #569 latent contract defect is now tracked (fixed in D5 step 2). No code; no churn-clock effect (doc-only).
- **ACCEPTED moment:** `TASK-BE-298` authored; GAP exchange endpoint built spec-first; contracts reconciled; Phase 2 resumes.
- **Post:** ADR-MONO-013 Model B is realizable for operator actions; `admin-web` retirement (Phase 3) remains parity-gated and now has a coherent auth path.
- **Future-self:** a session asked to "resume Phase 2 / build TASK-PC-FE-002" must first confirm ADR-MONO-014 ACCEPTED + `TASK-BE-298` merged; otherwise re-enter this Hard Stop.

## 4. Alternatives Considered

A / C / D — see § D1 table (rejected with reasons). Doing nothing (implement parity against the self-contradictory contract) — rejected: produces `401` in any live environment and bakes an undefendable implicit operator-trust decision (the exact HARDSTOP-09 failure mode).

## 5. Relationship to ADR-MONO-013 / BE-296 / FE-001

| | ADR-MONO-013 | BE-296 (#568) | FE-001 (#569) | ADR-MONO-014 (this) |
|---|---|---|---|---|
| Role | Console foundation (Model B, phases) | GAP registry on operator boundary | Console OIDC login + registry client | **Reconciles the operator-auth gap between BE-296 & FE-001** |
| Operator-auth | § D5 deferred ("skeleton") | required `token_type=admin` | assumed GAP OIDC token suffices | **decides: B token exchange** |

This ADR amends ADR-MONO-013 § D5 (the deferred bridge) and is a prerequisite for ADR-MONO-013 Phase 2–3.

## 6. Status Transition History

Append-only.

| Date | Transition | Decision | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-05-16 | created PROPOSED | B (RFC 8693 token exchange) | "B. Token Exchange" (resolution-direction answer) | this PR |

(ACCEPTED row reserved — appended when execution begins per § D6.)

## 7. Provenance

- HARDSTOP-06 #3 + HARDSTOP-09 #2 (`platform/hardstop-rules.md`) — mandate for an ADR + PAUSE-until-ACCEPTED on a conflicting-spec / undocumented cross-service architecture decision.
- Surfaced during the "다음 단계 시작" Phase 2 investigation (2026-05-16): `console-registry-api.md` § Authentication vs `console-integration-contract.md` § 2.1/§ 2.2.
- Decision direction B = user-explicit answer to the resolution-direction question (2026-05-16).

분석=Opus 4.7 / 구현 권장: ADR·계약 재정렬 + GAP exchange 엔드포인트 설계 = Opus (cross-service identity, RFC 8693, fail-closed mapping — interpretive); 콘솔 wiring + Phase 2 parity 화면 = Sonnet/Opus (보안 경로는 Opus).
