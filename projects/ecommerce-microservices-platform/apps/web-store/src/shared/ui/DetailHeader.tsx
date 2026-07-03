import Link from 'next/link';
import type { ReactNode } from 'react';

interface DetailHeaderProps {
  /** Page title (e.g. "주문 상세", "알림 설정"). */
  title: string;
  /** List route the back button returns to. */
  backHref: string;
  /** Back button label (e.g. "주문내역", "알림 목록"). */
  backLabel: string;
  /**
   * Optional status/action element rendered to the left of the back button on
   * the same row (e.g. an order-status badge). Mirrors the console
   * `DetailHeader` `actions` slot.
   */
  actions?: ReactNode;
}

/**
 * Shared detail/settings-page header for the storefront. Renders the page
 * title on the left and a right-aligned row that always ends with a `ghost`
 * back-to-list button, so every "상세/설정 → 목록" affordance matches the
 * platform-console `DetailHeader` layout (title left, ghost 목록 button right).
 *
 * Replaces the earlier top-of-page `BackLink` ("← 목록") on the pages the
 * customer navigates back from a detail/settings view. The plain ghost button
 * carries the destination as its accessible name (no ← glyph) — same as the
 * console.
 */
export function DetailHeader({ title, backHref, backLabel, actions }: DetailHeaderProps) {
  return (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 'var(--space-3)',
        marginBottom: 'var(--space-8)',
      }}
    >
      <h1 className="page-title" style={{ margin: 0 }}>
        {title}
      </h1>
      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
        {actions}
        <Link href={backHref} className="btn btn-ghost btn-sm">
          {backLabel}
        </Link>
      </div>
    </div>
  );
}
