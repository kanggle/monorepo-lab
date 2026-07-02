import Link from 'next/link';
import type { ReactNode } from 'react';
import { Button } from '@/shared/ui/Button';

export interface DetailHeaderProps {
  /** `id` for the h1, referenced by the section's `aria-labelledby`. */
  headingId: string;
  /** Page title. Convention across ecommerce-ops detail pages: "OO 상세". */
  title: string;
  /** List route the "목록" button returns to. */
  backHref: string;
  /** Stable test id for the "목록" back button. */
  backTestId: string;
  /**
   * Optional extra action buttons (수정 / 삭제 / 쿠폰 발급 …) rendered to the
   * left of the always-present "목록" button.
   */
  actions?: ReactNode;
}

/**
 * Shared detail-page header for ecommerce-ops (product / order / user / seller /
 * promotion). Renders the page title on the left and a right-aligned action row
 * that always ends with an identical "목록" back button, so every detail page
 * presents the same back-to-list affordance and title convention. Extracted
 * 2026-07-02 to remove per-page drift in the header layout.
 */
export function DetailHeader({
  headingId,
  title,
  backHref,
  backTestId,
  actions,
}: DetailHeaderProps) {
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h1 id={headingId} className="text-2xl font-semibold">
        {title}
      </h1>
      <div className="flex gap-2">
        {actions}
        <Link href={backHref}>
          <Button variant="ghost" data-testid={backTestId}>
            목록
          </Button>
        </Link>
      </div>
    </div>
  );
}
