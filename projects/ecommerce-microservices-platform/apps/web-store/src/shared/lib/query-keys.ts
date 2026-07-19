/**
 * TanStack Query key factory for the recurring `all / lists / list(params)`
 * hierarchy. Features that also need extra keys (details, summaries, …) spread
 * the returned object and append their own — the returned tuples are typed with
 * `as const` so cache matching stays precise.
 *
 *   const base = createListQueryKeys('orders');
 *   export const orderKeys = {
 *     ...base,
 *     details: () => [...base.all, 'detail'] as const,
 *   };
 */
export function createListQueryKeys<const S extends string>(scope: S) {
  const all = [scope] as const;
  const lists = () => [...all, 'list'] as const;
  const list = (params: Record<string, unknown>) => [...lists(), params] as const;
  return { all, lists, list };
}
