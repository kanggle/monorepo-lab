export function createQueryKeys(scope: string, resource: string) {
  const all = [scope, resource] as const;
  return {
    all,
    list: (params: Record<string, unknown>) => [...all, params] as const,
    detail: (id: string) => [...all, id] as const,
  };
}
