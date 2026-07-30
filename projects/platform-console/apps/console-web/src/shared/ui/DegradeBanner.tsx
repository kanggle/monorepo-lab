import type { ReactNode } from 'react';

/**
 * Shared "all cards down" banner shell (TASK-PC-FE-263).
 *
 * `features/domain-health`'s `DegradeBanner` and `features/operator-overview`'s
 * `OverviewDegradeBanner` reimplemented the SAME banner markup (`role="status"`,
 * identical Tailwind classes, two-paragraph copy structure, embedded retry
 * button) — differing only in their "all down" predicate, testid, and copy
 * text. Per `platform/refactoring-policy.md` § Prioritization duplication
 * outranks naming, so the shell is promoted here (the `shared/ui/ConfirmDialog`
 * precedent, TASK-PC-FE-262); each feature keeps its own predicate function
 * (`isAllDegraded` / `isAllDown` — deliberately NOT unified, the two features'
 * card-status unions differ: `domain-health` never emits `forbidden` per its
 * own § 2.4.9.2 doc comment) and delegates rendering to this shell.
 *
 * Presentational-only — the caller computes `show` and supplies its own
 * `retry` element (the feature-local `RetryButton` wrapper).
 */

export interface DegradeBannerProps {
  /** Whether the banner should render (caller's predicate result). */
  show: boolean;
  /** Fully-resolved testid string. */
  testid: string;
  /** Bold first line. */
  heading: string;
  /** Second-line explanatory copy. */
  description: string;
  /** The embedded retry affordance (feature-local `RetryButton`). */
  retry: ReactNode;
}

export function DegradeBanner({ show, testid, heading, description, retry }: DegradeBannerProps) {
  if (!show) return null;
  return (
    <div
      role="status"
      data-testid={testid}
      className="mb-6 flex items-start justify-between gap-4 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
    >
      <div>
        <p className="font-medium text-foreground">{heading}</p>
        <p className="mt-1">{description}</p>
      </div>
      {retry}
    </div>
  );
}
