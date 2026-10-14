# Task ID

TASK-PC-FE-340

# Title

Org-node admin grant: show Korean copy for the new 422 `ORG_ADMIN_GRANT_TARGET_NOT_MEMBER` (iam `TASK-BE-636` ③) instead of the raw server message

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

> **Analysis model:** Opus 5.5 / **Recommended implementation model:** Sonnet (one message-map entry)
> Source: iam `TASK-BE-636` (merged #4357 `2d3a0661d`) added the code. That PR did not touch the console, which was a gap found after the merge.

# Dependency Markers

- **Prerequisite**: iam `TASK-BE-636` (contract `admin-api.md` § POST /api/admin/org-nodes/{orgNodeId}/admins — 422 `ORG_ADMIN_GRANT_TARGET_NOT_MEMBER`). The server side goes live only with the next AMI bake. The console deploys on its own (Vercel), so it should be live **before** that window (`TASK-MONO-672` Order step 5, BE-636 ③).

# Goal

`OrgNodeDetail.errText` maps an `ApiError` through `messageForCode(code, err.message)`. For a code that is not in the map, it shows the server's message. For this code, that message is English and carries the raw operator id and tenant id. Add a Korean entry so the node admin panel shows actionable copy.

# Scope

## In Scope

- `src/shared/api/errors.ts`: a `MESSAGES` entry for `ORG_ADMIN_GRANT_TARGET_NOT_MEMBER`, plus the list in the org-node error docblock.
- `OrgAdminPanel.test.tsx`: renders the Korean copy and does not render the raw server message.

## Out of Scope

- Pre-filtering such operators out of the picker. The server stays the authority, and the picker has no membership-versus-home signal.

# Acceptance Criteria

- [x] **AC-1**: with `grantError = messageForCode('ORG_ADMIN_GRANT_TARGET_NOT_MEMBER', <server message>)`, the panel shows the Korean copy and no raw operator id. The test fails without the map entry, which bites: the fallback shows the server message.
- [x] **AC-2**: console-web unit tests and lint/build green in CI.

# Related Specs

- `projects/iam-platform/specs/contracts/http/admin-api.md` § POST /api/admin/org-nodes/{orgNodeId}/admins

# Related Contracts

- Same as above. No console BFF contract change: the proxy passes the code through `mapError` unchanged.

# Edge Cases

- An older server, before the bake, never sends this code, so the entry is inert until then.

# Failure Scenarios

1. The entry is added under a misspelt key. The fallback silently keeps showing the English message, so the test asserts the absence of the raw id, not just the presence of some Korean text.

---

# Close (2026-10-14 UTC)

- #4360 → `14629a187` (squash, merged 15:07:16Z). `origin/main` tip = that commit. 0 failing checks at merge (12 pass · 54 skipping; the four required checks passed).
- Measurement order: first commit (test only, no map entry) → «Frontend unit tests» **1 failed / 384 passed** — only the new test, at `toHaveTextContent('홈 회사의 소속이 아니어서')` (the fallback showed the server's English message). The map-entry commit `7ea8db762` → `OrgAdminPanel.test.tsx` 5 ✓ · 385 files passed (job 115925223716, read from the log).
- Vercel Production deployment of `14629a187`: **success** (deployment 7069203676), so it is live before the re-bake window. The server sends this code only after the bake (`TASK-MONO-672` Order step 5, BE-636 ③).
