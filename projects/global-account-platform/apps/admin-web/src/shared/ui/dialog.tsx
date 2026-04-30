'use client';

import * as React from 'react';
import { cn } from '@/shared/lib/cn';

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: React.ReactNode;
  title?: string;
  description?: string;
  labelledBy?: string;
}

/**
 * Minimal accessible dialog primitive (shadcn/ui-style).
 * Backdrop + role=dialog + aria-modal. Kept dependency-free so the scaffold
 * compiles without Radix UI; swap in `@radix-ui/react-dialog` later if desired.
 */
export function Dialog({ open, onOpenChange, children, title, description, labelledBy }: DialogProps) {
  React.useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onOpenChange(false);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onOpenChange]);

  if (!open) return null;
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onClick={() => onOpenChange(false)}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy ?? (title ? 'dialog-title' : undefined)}
        aria-describedby={description ? 'dialog-desc' : undefined}
        className={cn('w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-lg')}
        onClick={(e) => e.stopPropagation()}
      >
        {title ? <h2 id="dialog-title" className="text-lg font-semibold">{title}</h2> : null}
        {description ? <p id="dialog-desc" className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}

export function DialogFooter({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn('mt-6 flex justify-end gap-2', className)}>{children}</div>;
}
