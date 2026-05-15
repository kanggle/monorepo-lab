import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import './globals.css';

export const metadata: Metadata = {
  title: 'Platform Console',
  description: 'Unified operations console for the enterprise suite (gap · wms · scm · erp · finance)',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-full flex flex-col">
        {/* App shell header */}
        <header className="border-b border-border bg-primary text-primary-foreground">
          <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
            <div className="flex h-14 items-center justify-between">
              <span className="text-lg font-semibold tracking-tight">
                Platform Console
              </span>
              {/*
               * TODO(TASK-PC-FE-001): replace this placeholder with:
               *   - GAP OIDC Auth Code + PKCE login button / user menu
               *   - Tenant switcher (reads tenant list from CONSOLE_REGISTRY_URL)
               */}
              <span className="text-xs opacity-60">Phase 1 skeleton</span>
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1">
          {children}
        </main>
      </body>
    </html>
  );
}
