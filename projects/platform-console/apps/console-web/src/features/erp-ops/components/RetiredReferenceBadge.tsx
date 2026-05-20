/**
 * `<RetiredReferenceBadge>` — surfaces a broken or retired
 * cross-reference HONESTLY (TASK-PC-FE-010 / § 2.4.8 / E1
 * obligation).
 *
 * When an employee references a department that is currently
 * retired (or any master references a retired peer), the consumer
 * MUST surface the retired-reference state — silently sanitizing
 * the broken link is the E1 honesty defect to avoid.
 *
 * Renders next to the referenced id / name; the badge itself
 * never carries the referenced record's PII.
 */
export interface RetiredReferenceBadgeProps {
  /** Short reason — typically "retired" or "missing". Free string
   *  so a future producer surfacing can supply more context (e.g.
   *  "retired 2025-12-31"). */
  reason?: string;
}

export function RetiredReferenceBadge({
  reason = 'retired reference',
}: RetiredReferenceBadgeProps) {
  return (
    <span
      className="ml-1 rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive"
      data-testid="erp-retired-reference"
      role="status"
      title="참조 대상이 retired/missing 상태입니다 (E1 reference integrity)"
    >
      ⚠ {reason}
    </span>
  );
}
