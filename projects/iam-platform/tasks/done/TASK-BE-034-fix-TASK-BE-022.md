# Task ID

TASK-BE-034

# Title

Fix TASK-BE-022 spec issues — access-token device_id claim, IP masking format, data-model classification gap

# Status

ready

# Owner

backend

# Task Tags

- spec
- fix

# depends_on

- TASK-BE-022

---

# Goal

Fix issues found during review of TASK-BE-022 (device/session specs). Resolves two Critical and one Warning finding that would block TASK-BE-023 implementation if left unaddressed.

---

# Scope

## In Scope

### Fix 1 (Critical) — Access token JWT claim: `device_id`

`GET /sessions/current` and `DELETE /sessions` (bulk) in `specs/contracts/http/auth-api.md` reference `"토큰 claim의 device_id"` as the mechanism to identify the current session. However, the Access Token JWT claims table in the same file does not include a `device_id` claim.

**Required action**: Decide and document **one** of the following approaches in `auth-api.md`:

**Option A** — Add `device_id` as a JWT access token claim:
- Add `device_id` row to the Access Token claims table.
- Clarify that this claim is populated at login time from `device_sessions.device_id`.

**Option B** — Use refresh token JTI → device session lookup (no new JWT claim):
- Update the endpoint descriptions to clarify that the server resolves the current device session via the refresh token body field or a separate stateful session cookie, not a JWT claim.
- Remove all references to `"토큰 claim의 device_id"`.

**Recommended**: Option A. The existing `current: bool` field in the session list response already implies the server knows which session the caller is using, and a `device_id` JWT claim is the simplest, stateless way to carry this. It does not change the security surface materially since `device_id` is an opaque server-issued UUID, not a fingerprint.

### Fix 2 (Critical) — IP masking format inconsistency

`auth-api.md` line (Response 필드 노트 for `GET /sessions`): states "마지막 두 옥텟 마스킹" but the example `"192.168.1.***"` only masks one octet (last).

`device-session.md` mentions "응답 시 마스킹" without defining the format. The cross-reference to device-session.md (in auth-api.md note) points to a definition that does not exist there.

**Required actions**:
1. In `specs/services/auth-service/device-session.md`, add an explicit "IP Masking Format" subsection under the Data Model section. Define the exact format (e.g., last two octets for IPv4: `192.168.*.*`, last 80 bits for IPv6: `2001:db8::*****`).
2. In `specs/contracts/http/auth-api.md`, align the example value with the definition — if two-octet, the example must be `"192.168.*.*"` (not `"192.168.1.***"`). If one-octet, remove "두" from the note.
3. Apply consistently to both `GET /sessions` and `GET /sessions/current` response examples, and also verify `auth.login.attempted` and `auth.login.succeeded` event payloads use the same format string.

### Fix 3 (Warning) — `device_sessions` classification missing from `data-model.md`

`specs/services/auth-service/data-model.md` Data Classification Summary only covers `credentials` and `refresh_tokens`. The new `device_sessions` table has inline classification in `device-session.md` but the canonical classification document (`data-model.md`) is not updated.

Per `rules/traits/regulated.md` R1, classification must be documented in `specs/services/<service>/data-model.md` (or a dedicated `data-classification.md`). Missing the `device_sessions` table from the summary is a compliance gap.

**Required action**: Add `device_sessions` table rows to the Data Classification Summary table in `data-model.md`:

| 등급 | 컬럼 |
|---|---|
| **confidential** | `device_sessions.device_fingerprint`, `device_sessions.ip_last` |
| **internal** | all other `device_sessions` columns |

Also add a reference note that `device_sessions` DDL is declared in `device-session.md`.

## Out of Scope

- Implementation code changes (TASK-BE-023)
- Changing the concurrent-session policy or eviction algorithm
- Changing the `auth.login.succeeded` payload to include `deviceId` (this is a separate concern for a future security-service migration task)

---

# Acceptance Criteria

- [ ] `auth-api.md` Access Token claims table includes `device_id` (or the alternative lookup mechanism is clearly documented with no remaining references to "토큰 claim의 device_id")
- [ ] `device-session.md` contains an explicit IP masking format definition
- [ ] `auth-api.md` IP masking example and description text are consistent with each other and with `device-session.md`
- [ ] `data-model.md` Data Classification Summary includes `device_sessions` columns
- [ ] No new ambiguity introduced that would block TASK-BE-023

---

# Related Specs

- `specs/services/auth-service/device-session.md`
- `specs/services/auth-service/data-model.md`
- `specs/contracts/http/auth-api.md`

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/auth-service`

---

# Edge Cases

- IPv6 masking format must be defined alongside IPv4 (the DB column `ip_last VARCHAR(45)` already accommodates IPv6)
- If Option A is chosen for Fix 1, the JWT claim must be documented in the Refresh Token spec as well, since a new access token is issued during rotation and the `device_id` must remain consistent

---

# Failure Scenarios

- Fix 1 Option A: if `device_id` is added to the access token claim but the login response in `POST /api/auth/login` (200 response schema) does not reflect this, TASK-BE-023 will have a mismatched contract — verify login response does not need updating (access token is returned opaquely as a JWT string, so no JSON field change needed, just the claim spec)

---

# Test Requirements

- Spec review only — verify no remaining ambiguity in the three files before TASK-BE-023 moves to ready

---

# Definition of Done

- [ ] Three spec files updated with fixes
- [ ] No Critical or Warning findings remain
- [ ] TASK-BE-023 can proceed to implementation without ambiguity
