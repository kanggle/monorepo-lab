export function toValidStatus<T extends string>(
  value: string | null,
  validStatuses: readonly T[],
): T | undefined {
  if (!value) return undefined;
  return (validStatuses as readonly string[]).includes(value) ? (value as T) : undefined;
}
