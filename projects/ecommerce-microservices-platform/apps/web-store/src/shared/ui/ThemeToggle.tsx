'use client';

import { useEffect, useState } from 'react';
import { applyTheme, readAppliedTheme, type Theme } from '@/shared/lib/theme';

/**
 * Header light/dark toggle. The initial theme is chosen pre-hydration by
 * {@link ThemeScript}; this button reads it after mount and flips it on click,
 * persisting the explicit choice. Until mounted it renders a neutral moon so
 * the server and first client render agree (no hydration mismatch on the icon).
 *
 * `className` lets a host (the header) supply its own control styling so the
 * toggle lines up with the cart/profile buttons; without it a self-contained
 * 36×36 outline circle is used.
 */
const STANDALONE_STYLE = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 36,
  height: 36,
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-full)',
  background: 'transparent',
  color: 'var(--color-text-secondary)',
  cursor: 'pointer',
  transition: 'color var(--transition-fast), border-color var(--transition-fast)',
} as const;

export function ThemeToggle({ className }: { className?: string }) {
  const [theme, setTheme] = useState<Theme>('light');
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setTheme(readAppliedTheme());
    setMounted(true);
  }, []);

  const isDark = mounted && theme === 'dark';

  const toggle = () => {
    const next: Theme = isDark ? 'light' : 'dark';
    applyTheme(next);
    setTheme(next);
  };

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={isDark ? '라이트 모드로 전환' : '다크 모드로 전환'}
      title="테마 전환"
      data-testid="theme-toggle"
      className={className}
      style={className ? undefined : STANDALONE_STYLE}
    >
      {/* Sun when dark (click → light); moon when light (click → dark). */}
      {isDark ? (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
        </svg>
      ) : (
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
        </svg>
      )}
    </button>
  );
}
