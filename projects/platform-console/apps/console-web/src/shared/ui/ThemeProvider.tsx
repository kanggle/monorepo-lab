'use client';

import { ThemeProvider as NextThemesProvider } from 'next-themes';
import type { ReactNode } from 'react';

/**
 * TASK-PC-FE-038 — Vercel-style dark/light theming.
 *
 * next-themes drives a `.dark` class on <html> (matching tailwind
 * `darkMode: 'class'`), defaulting to the OS preference (`system`) and
 * honouring a manual toggle (see {@link ThemeToggle}). It injects a
 * pre-hydration script so the correct theme paints with no flash; the root
 * <html> carries `suppressHydrationWarning` for the class it sets.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      {children}
    </NextThemesProvider>
  );
}
