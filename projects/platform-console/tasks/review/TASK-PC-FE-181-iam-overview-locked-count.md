# TASK-PC-FE-181 — IAM overview 계정 카드에 잠금 현황(LOCKED) 카운트 추가

- **Status**: review
- **Type**: TASK-PC-FE (console-web)
- **Depends on**: **TASK-BE-475** (producer `status` filter on `GET /api/admin/accounts` — MUST merge + deploy before this) · TASK-PC-FE-180 (the live IAM overview this extends)
- **Analysis model**: Opus 4.8 · **Impl model recommendation**: Sonnet (mirrors the existing operators ACTIVE/SUSPENDED split-leg pattern)

## Goal

The IAM overview 계정 카드 (TASK-PC-FE-180) shows only a total — PC-FE-180 deferred a lock breakdown because the producer had no status filter. TASK-BE-475 adds that filter, so this task surfaces a **잠금(LOCKED) 현황** sub-count on the 계정 카드, mirroring the operators card's ACTIVE/SUSPENDED split.

## ⚠️ Merge ordering (hard dependency)

This MUST NOT merge/deploy before **TASK-BE-475** is live. The current (pre-BE-475) producer **ignores** an unknown `status` query param and returns the **full** list — so the 잠금 count would render as the **total** (wrong). BE-475 first, then this.

## Scope

Under `projects/platform-console/apps/console-web/src/`:

- **`features/accounts/api/types.ts`** — `AccountSearchParams` gains `status?: string` (list-branch-only lifecycle filter).
- **`features/accounts/api/accounts-api.ts`** — `searchAccounts` appends `?status=` on the LIST branch only, when present (absent ⇒ all statuses; the email single-lookup never sends it).
- **`features/iam-overview/api/overview-state.ts`** — `AccountsSummary` gains `locked: number | null`; the fan-out adds a `searchAccounts({ status: 'LOCKED', page: 0, size: 1 })` sub-leg (its own cell → own degrade/forbidden; the total leg stays the card gate). Map `locked = lockedCell.value?.totalElements ?? null`.
- **`features/iam-overview/components/IamOverviewScreen.tsx`** — the 계정 카드 renders `총계` + a `잠금` sub-count (`dl`, same shape as the operators ACTIVE/SUSPENDED split); a null `locked` renders `—` (never blanks the card).

Spec:
- **`specs/contracts/console-integration-contract.md` §2.4.3.1** — the 계정 counts line now documents the `?status=LOCKED&size=1` sub-leg (via the BE-475 filter), replacing the "no lock breakdown" note.

## Non-Goals

- No ACTIVE/DORMANT/DELETED breakdown on the card (LOCKED is the operator-relevant 현황; the filter supports the others if a later task wants them).
- No producer change (that is TASK-BE-475).
- No change to the `/accounts` screen itself (this is the overview card only).

## Acceptance Criteria

- **AC-1** The 계정 카드 renders `총계` + a `잠금` count, the latter from `searchAccounts({ status: 'LOCKED', size: 1 }).totalElements`.
- **AC-2** `searchAccounts` sends `?status=LOCKED` on the list branch only, and only when a status is passed (no `?status=` on the unfiltered total leg or the email lookup).
- **AC-3** The LOCKED sub-leg has its own cell resilience: a `403`/`503` on it → `잠금` shows `—` while the total still renders; the card gate stays the total leg. A `401` on any leg → whole-session redirect (unchanged).
- **AC-4** `pnpm lint` + `tsc --noEmit` + `vitest` green — `iam-overview-state` (locked mapping + independent sub-leg degrade), `IamOverviewScreen` (locked render), updated fixtures.

## Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.3.1 (IAM overview snapshot) / §2.4.1 (accounts surface).
- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/accounts (`status` param — TASK-BE-475, consumed read-only).

## Related Contracts

- Producer: `GET /api/admin/accounts?status=` (admin-service, TASK-BE-475) — consumed read-only, additive.

## Edge Cases

- Producer not yet deployed → the ⚠️ merge-ordering note; do not merge first.
- Empty tenant (0 accounts) → `총계 0` + `잠금 0` (both valid, not degraded).
- LOCKED sub-leg forbidden (a role with `account.read` on total but a producer quirk on the filtered call — unlikely, same permission) → `잠금 —`, total intact.

## Failure Scenarios

- Merge-before-producer → 잠금 shows the full total (wrong); prevented by the merge-ordering discipline (this task blocks on BE-475).
- Sub-leg 503 → `잠금 —`, card otherwise intact (per-cell resilience, §2.5).
