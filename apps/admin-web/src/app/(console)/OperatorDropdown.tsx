'use client';

import { useState, useRef, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { ChangePasswordDialog } from '@/features/operators/components/ChangePasswordDialog';
import type { OperatorSession } from '@/shared/api/admin-api';

export function OperatorDropdown({ session }: { session: OperatorSession }) {
  const [open, setOpen] = useState(false);
  const [pwDialogOpen, setPwDialogOpen] = useState(false);
  const router = useRouter();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  async function handleLogout() {
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    router.push('/login');
    router.refresh();
  }

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 text-sm hover:text-foreground transition-colors cursor-pointer"
      >
        <span>{session.email}</span>
        <span className="text-muted-foreground text-xs">{session.roles.join(', ')}</span>
        <span className="text-muted-foreground text-xs">{open ? '▲' : '▼'}</span>
      </button>
      {open && (
        <div className="absolute right-0 top-full mt-2 w-64 rounded-md border border-border bg-background shadow-lg z-50 p-4 flex flex-col gap-3">
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
            <dt className="text-muted-foreground">운영자 ID</dt>
            <dd className="font-mono text-xs">{session.operatorId}</dd>
            <dt className="text-muted-foreground">이메일</dt>
            <dd className="text-xs">{session.email}</dd>
            <dt className="text-muted-foreground">역할</dt>
            <dd className="flex flex-wrap gap-1">
              {session.roles.map((r) => (
                <Badge key={r}>{r}</Badge>
              ))}
            </dd>
          </dl>
          <Button
            variant="ghost"
            size="sm"
            className="w-full"
            onClick={() => { setOpen(false); setPwDialogOpen(true); }}
          >
            비밀번호 변경
          </Button>
          <Button variant="ghost" size="sm" className="w-full" onClick={handleLogout}>
            로그아웃
          </Button>
        </div>
      )}
      <ChangePasswordDialog open={pwDialogOpen} onOpenChange={setPwDialogOpen} />
    </div>
  );
}
