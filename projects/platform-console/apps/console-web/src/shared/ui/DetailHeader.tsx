import Link from 'next/link';
import type { ReactNode } from 'react';
import { Button } from '@/shared/ui/Button';

export interface DetailHeaderProps {
  /** `id` for the h1, referenced by the section's `aria-labelledby`. */
  headingId: string;
  /** Page title. Convention across detail pages: "OO 상세". */
  title: string;
  /** Route the back button returns to (a list, or a parent detail). */
  backHref: string;
  /** Stable test id for the back button. */
  backTestId: string;
  /**
   * Back button label. Defaults to "목록" (return to list); pass "상세" on an
   * edit page whose back button returns to the parent item's detail.
   */
  backLabel?: string;
  /**
   * Optional extra action buttons (수정 / 삭제 / 쿠폰 발급 …) rendered to the
   * left of the always-present back button.
   */
  actions?: ReactNode;
}

/**
 * Shared detail-page header (promoted 2026-07-10 from
 * `features/ecommerce-ops/components/DetailHeader.tsx` to `shared/ui` so the
 * org-hierarchy / IAM surfaces can reuse the same back-to-list affordance and
 * title convention — TASK-PC-FE-237 § F). Renders the page title on the left
 * and a right-aligned action row that always ends with an identical back
 * button. Behaviour is byte-identical to the pre-promotion source.
 */
export function DetailHeader({
  headingId,
  title,
  backHref,
  backTestId,
  backLabel = '목록',
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
            {backLabel}
          </Button>
        </Link>
      </div>
    </div>
  );
}
