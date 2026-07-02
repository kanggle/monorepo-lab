import type { ReactNode } from 'react';

/**
 * Shared status pill (TASK-PC-FE-158). The single home for the console's
 * status-badge markup + colour palette, so every domain/menu renders status
 * chips consistently (previously each feature copy-pasted the
 * `inline-block rounded px-2 py-0.5 text-xs font-medium …` markup and its own
 * ad-hoc colour map — ecommerce users/sellers, wms outbound, erp approvals).
 *
 * The palette is SEMANTIC, not per-status: a domain maps its own status enum to
 * one of the five tones (its status vocabulary stays domain-local — a single
 * global status→colour map is impossible because `ACTIVE` and `SHIPPED` are
 * different enums), and this component owns the actual Tailwind classes +
 * dark-mode variants. Add a new domain by writing a `status → StatusTone` map;
 * never re-hardcode colours at the call site.
 */

export type StatusTone =
  | 'success' // terminal-good / active (green)
  | 'progress' // in-flight / mid-lifecycle (blue)
  | 'warning' // attention / pending / paused (amber)
  | 'danger' // terminal-bad / failed / cancelled (red)
  | 'neutral'; // unknown / inactive / withdrawn (muted)

const TONE_CLASS: Record<StatusTone, string> = {
  success:
    'bg-green-100 text-green-800 dark:bg-green-950/60 dark:text-green-100',
  progress: 'bg-blue-100 text-blue-800 dark:bg-blue-950/60 dark:text-blue-100',
  warning:
    'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-100',
  danger: 'bg-red-100 text-red-800 dark:bg-red-950/60 dark:text-red-100',
  neutral: 'bg-muted text-muted-foreground',
};

/** The pill classes for a tone — for the rare call site that needs the raw
 *  className (e.g. a non-`<span>` element). Prefer the `<StatusBadge>`. */
export function statusToneClass(tone: StatusTone): string {
  return `inline-block rounded px-2 py-0.5 text-xs font-medium ${TONE_CLASS[tone]}`;
}

export interface StatusBadgeProps {
  /** Semantic tone → colour. Defaults to `neutral` (safe for unknown status). */
  tone?: StatusTone;
  /** The label (usually the raw status string, kept verbatim for a11y + text
   *  assertions + parity with the producer enum). */
  children: ReactNode;
  /** Optional extra classes appended after the tone classes. */
  className?: string;
  /** Optional test id — forwarded to the rendered `<span>`. Lets a migrating
   *  call site keep its existing `*-status` testid on the badge itself (the
   *  status element), rather than hoisting it to a wrapping cell. */
  'data-testid'?: string;
}

/**
 * Renders a status chip. Text content is the raw label so screen readers and
 * status-text assertions keep working; colour comes from the semantic tone.
 */
export function StatusBadge({
  tone = 'neutral',
  children,
  className,
  'data-testid': testId,
}: StatusBadgeProps) {
  return (
    <span
      className={`${statusToneClass(tone)}${className ? ` ${className}` : ''}`}
      data-testid={testId}
    >
      {children}
    </span>
  );
}
