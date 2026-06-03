'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { performLogout } from '@/features/auth';
import { cn } from '@/shared/lib/cn';

interface Props {
  /** The signed-in operator's display identity (email / username / sub),
   *  decoded verification-free server-side and passed down — display only,
   *  never an authorization input (TASK-PC-FE-041 / § 2.1). */
  accountLabel: string;
}

/**
 * TASK-PC-FE-041 — Vercel-style top-bar account control. Replaces the bare
 * 로그아웃 button with a kebab (⋮) trigger that opens a dropdown menu holding
 * the operator's **아이디** (read-only), **계정 설정** (→ `/account`), and
 * **로그아웃** (the existing RP-initiated OIDC logout, `performLogout`, with its
 * `data-testid="logout-button"` preserved). Closes on outside-click, Escape, or
 * item activation. Client component (the open/close + logout interaction).
 */
export function AccountMenu({ accountLabel }: Props) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const onLogout = async () => {
    setBusy(true);
    setOpen(false);
    await performLogout();
  };

  return (
    <div className="relative" ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        aria-label="계정 메뉴"
        title="계정"
        aria-haspopup="menu"
        aria-expanded={open}
        data-testid="account-menu-trigger"
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'inline-flex h-8 w-8 items-center justify-center rounded-md border border-border text-muted-foreground',
          'transition-colors hover:bg-accent hover:text-foreground',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background',
        )}
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
        >
          <circle cx="12" cy="5" r="1.6" />
          <circle cx="12" cy="12" r="1.6" />
          <circle cx="12" cy="19" r="1.6" />
        </svg>
      </button>

      {open && (
        <div
          role="menu"
          aria-label="계정 메뉴"
          data-testid="account-menu"
          className={cn(
            'absolute right-0 z-50 mt-2 w-56 overflow-hidden rounded-md border border-border bg-background py-1 shadow-md',
          )}
        >
          <div
            data-testid="account-menu-id"
            className="px-3 py-2"
            role="presentation"
          >
            <p className="text-xs text-muted-foreground">아이디</p>
            <p className="truncate text-sm font-medium text-foreground" title={accountLabel}>
              {accountLabel}
            </p>
          </div>
          <div className="my-1 border-t border-border" role="separator" />
          <Link
            href="/account"
            role="menuitem"
            data-testid="account-menu-settings"
            onClick={() => setOpen(false)}
            className="block px-3 py-2 text-sm text-foreground transition-colors hover:bg-accent focus-visible:bg-accent focus-visible:outline-none"
          >
            계정 설정
          </Link>
          <button
            type="button"
            role="menuitem"
            data-testid="logout-button"
            disabled={busy}
            onClick={onLogout}
            className="block w-full px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-accent focus-visible:bg-accent focus-visible:outline-none disabled:opacity-50"
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
