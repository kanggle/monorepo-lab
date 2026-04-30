import type { ReactNode } from 'react';
import Link from 'next/link';
import { requireOperatorSession } from './session';
import { OperatorDropdown } from './OperatorDropdown';

export default async function ConsoleLayout({ children }: { children: ReactNode }) {
  const session = await requireOperatorSession('/accounts');

  return (
    <div className="flex min-h-screen">
      <aside className="w-56 shrink-0 border-r border-border bg-muted/30 p-4">
        <div className="mb-6 text-sm font-semibold">Admin Console</div>
        <nav className="flex flex-col gap-2 text-sm">
          <Link href="/accounts">계정</Link>
          <Link href="/audit">감사 로그</Link>
          <Link href="/dashboards">대시보드</Link>
        </nav>
      </aside>
      <div className="flex flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-border px-6 py-3 text-sm">
          <span className="font-medium text-muted-foreground">Admin Console</span>
          <OperatorDropdown session={session} />
        </header>
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  );
}
