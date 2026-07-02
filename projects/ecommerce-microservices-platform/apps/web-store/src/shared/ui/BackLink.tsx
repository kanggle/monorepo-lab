import Link from 'next/link';
import type { ReactNode } from 'react';

interface BackLinkProps {
  /** List route this control returns to. */
  href: string;
  /** Link label (e.g. "주문내역", "알림 목록"); a ← glyph is prepended. */
  children: ReactNode;
}

/**
 * Shared back-to-list control for storefront detail / settings pages
 * (order detail, notification detail, notification settings). Renders a
 * console-style `ghost` button — transparent, text-colored, subtle hover
 * fill — so every "상세 → 목록" affordance is visually identical. Extracted
 * 2026-07-02 to replace three per-page inline-styled text links.
 *
 * The ← glyph is `aria-hidden`, so the accessible link name stays the plain
 * label (e.g. "주문내역").
 */
export function BackLink({ href, children }: BackLinkProps) {
  return (
    <Link href={href} className="btn btn-ghost btn-sm back-link">
      <span aria-hidden="true">←</span>
      {children}
    </Link>
  );
}
