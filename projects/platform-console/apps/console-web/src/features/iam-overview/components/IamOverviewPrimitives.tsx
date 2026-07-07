import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import type { CellStatus } from '../api/overview-state';
import { cellPlaceholder, STATUS_DOT, STATUS_LABEL } from './overview-labels';

/**
 * Presentational primitives shared by the IAM overview count cards
 * (TASK-PC-FE-180 — extracted from {@link IamOverviewScreen}, TASK-PC-FE-212
 * presentational split). Server components — STRICTLY READ-ONLY, no
 * `'use client'`. Markup / testids are byte-verbatim from the former god-file.
 */

export function CardShell({
  href,
  testid,
  label,
  status,
  children,
}: {
  href: string;
  testid: string;
  label: string;
  status: CellStatus;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      data-testid={testid}
      className="flex min-w-[12rem] flex-1 flex-col gap-3 rounded-md border border-border bg-background px-4 py-4 transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <span
        className="flex items-center gap-1.5 text-sm text-muted-foreground"
        data-testid={`${testid}-status`}
        data-status={status}
      >
        <span
          className={cn('h-1.5 w-1.5 shrink-0 rounded-full', STATUS_DOT[status])}
          aria-hidden="true"
        />
        {label}
        <span className="sr-only">상태: {STATUS_LABEL[status]}</span>
      </span>
      {children}
    </Link>
  );
}

export function BigCount({ value, testid }: { value: number; testid: string }) {
  return (
    <span
      className="text-2xl font-semibold tabular-nums text-foreground"
      data-testid={testid}
    >
      {value.toLocaleString()}
    </span>
  );
}

export function Placeholder({
  status,
  testid,
}: {
  status: CellStatus;
  testid: string;
}) {
  return (
    <span
      className="text-sm font-medium text-muted-foreground"
      data-testid={testid}
    >
      {cellPlaceholder(status)}
    </span>
  );
}
