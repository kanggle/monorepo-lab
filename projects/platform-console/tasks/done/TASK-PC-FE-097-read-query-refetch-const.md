# TASK-PC-FE-097 — extract the duplicated read-query "no auto-refetch" option pair to a shared const

**Status:** done
**Area:** platform-console / console-web · **Refactor only** (Reduce Duplication — 0 behavior change, 0 contract change)
**Parent:** continuation of the console-web dedup sweep (PC-FE-094/095/096).
**Closure:** PR #1686 squash `9fd5c03c3`, 3-dim verified (state=MERGED · origin/main tip match · pre-merge all checks pass incl. Frontend unit tests/lint/build/E2E). 61 sites → `...READ_QUERY_REFETCH`; 2 `refetchOnReconnect` sites correctly left. (Task doc re-authored in `done/` directly: the impl worktree was mis-created nested in the main checkout due to a relative-path `git worktree add` under a drifted cwd, and the original ready/ task file was written to a stray sibling path that was removed during cleanup — the merged code is unaffected.)

## Goal

Every console **read** `useQuery` ended with the same two options expressing the
console policy "reads never background-refetch — projection lag is surfaced, not
polled-around":

```ts
    refetchOnWindowFocus: false,
    refetchInterval: false,
```

This adjacent pair appeared at **61 query sites across 20 hook files**, byte-identical.
Extracted to a single shared const and spread in. **No behavior change** (same two
keys/values; object key order is irrelevant for these booleans).

The **lead-in** options (`staleTime`, `refetchOnMount`, `initialData`, `enabled`,
`retry`) are genuinely per-query (6+ `staleTime` variants, 4 `refetchOnMount`
variants) and were **left untouched** — only the invariant pair was extracted.

## Delivered
1. **New** `src/shared/api/query-options.ts` — `READ_QUERY_REFETCH` const
   (`{ refetchOnWindowFocus: false, refetchInterval: false } as const`) with a doc
   comment on the policy and why it is spread per-read (not a QueryClient global default).
2. 61 sites across 20 `features/**/hooks/use-*.ts` → `...READ_QUERY_REFETCH,` + one import per file.
3. **Left untouched** (correct): `use-domain-health.ts` + `use-operator-overview.ts`
   each have `refetchOnWindowFocus: false` followed by `refetchOnReconnect: false`
   (not `refetchInterval`) — a different intentional pair.

## Acceptance Criteria (met)
- One shared `READ_QUERY_REFETCH`; 0 remaining adjacent `refetchInterval: false` pairs.
- `npx vitest run` green (CI authoritative); `tsc --noEmit` 0; `next lint` clean.
- 0 behavior / contract / test change. QueryClient global defaults untouched.

## Related
- `platform/refactoring-policy.md` (Reduce Duplication)
- `specs/services/console-web/architecture.md` — "공유 가치는 `shared/` 로 승격"; `shared/api/` is the query/client layer.
