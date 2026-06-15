/**
 * The console read-query refetch policy: surface projection lag, never
 * background-refetch. Spread into every read `useQuery` (NOT mutations, NOT the
 * QueryClient global default — applying it per-read keeps intent explicit and
 * avoids changing behavior for any query that wants different defaults).
 */
export const READ_QUERY_REFETCH = {
  refetchOnWindowFocus: false,
  refetchInterval: false,
} as const;
