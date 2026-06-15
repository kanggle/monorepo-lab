/** Clamp a requested page size to [1, max], defaulting when unset. */
export function clampPageSize(
  size: number | undefined,
  defaultSize: number,
  maxSize: number,
): number {
  return Math.min(maxSize, Math.max(1, size ?? defaultSize));
}
