import type { ReactNode } from 'react';

/** Shared form classes for the fan-directory screens (same tokens as the ecommerce forms). */
export const inputCls =
  'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
export const labelCls = 'block text-sm font-medium text-foreground';

/** A muted status note (degraded / forbidden / empty) — same look as the sibling screens. */
export function FanNote({ testId, children }: { testId: string; children: ReactNode }) {
  return (
    <div
      role="status"
      data-testid={testId}
      className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
    >
      {children}
    </div>
  );
}

/** An inline error line for a failed mutation. */
export function FanError({ testId, message }: { testId: string; message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="text-sm text-destructive" data-testid={testId}>
      {message}
    </p>
  );
}

/** A label / value row in a detail card. */
export function FanField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[8rem_1fr] gap-2 py-1 text-sm">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 break-words text-foreground">{children}</dd>
    </div>
  );
}
