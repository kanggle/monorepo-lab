import Link from 'next/link';
import type { ReactNode } from 'react';
import type { CardStatus } from '../api/types';

/**
 * Per-source overview card shell for the IAM composed operator overview
 * (TASK-PC-FE-005 — extracted from {@link OperatorOverviewScreen},
 * TASK-PC-FE-212 presentational split).
 *
 * Per-source isolation (§ 2.4.4 / ADR-015 D3): the card renders its `ok`
 * children OR its OWN degraded / "not available to your role" placeholder;
 * one source down never blanks the overview or the console shell. The
 * markup / testids / status-badge copy are byte-verbatim from the former
 * god-file.
 */

function statusBadge(status: CardStatus): ReactNode {
  if (status === 'ok') return null;
  const label =
    status === 'forbidden' ? '권한 없음' : '일시적으로 불러올 수 없음';
  return (
    <span
      className="ml-2 rounded-full border border-border bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground"
      data-testid={`card-status-${status}`}
    >
      {label}
    </span>
  );
}

interface CardProps {
  id: string;
  testid: string;
  title: string;
  status: CardStatus;
  quickLinkHref: string;
  quickLinkLabel: string;
  /** Rendered only when `status === 'ok'`. */
  children: ReactNode;
  /** The "not available to your role" copy for a `forbidden` card. */
  forbiddenCopy: string;
}

export function OverviewCard({
  id,
  testid,
  title,
  status,
  quickLinkHref,
  quickLinkLabel,
  children,
  forbiddenCopy,
}: CardProps) {
  return (
    <section
      aria-labelledby={`${id}-heading`}
      data-testid={testid}
      data-status={status}
      className="flex flex-col rounded-lg border border-border bg-background p-5"
    >
      <h2
        id={`${id}-heading`}
        className="mb-3 flex items-center text-lg font-semibold text-foreground"
      >
        {title}
        {statusBadge(status)}
      </h2>

      <div className="flex-1">
        {status === 'ok' && children}

        {status === 'forbidden' && (
          <p
            role="status"
            data-testid={`${testid}-forbidden`}
            className="text-sm text-muted-foreground"
          >
            {forbiddenCopy}
          </p>
        )}

        {status === 'degraded' && (
          <p
            role="status"
            data-testid={`${testid}-degraded`}
            className="text-sm text-muted-foreground"
          >
            이 항목을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
            계속 사용할 수 있습니다. 아래에서 다시 시도하거나 잠시 후
            새로고침하세요.
          </p>
        )}
      </div>

      <div className="mt-4 border-t border-border pt-3">
        <Link
          href={quickLinkHref}
          data-testid={`${testid}-quicklink`}
          className="text-sm font-medium underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          {quickLinkLabel}
        </Link>
      </div>
    </section>
  );
}
