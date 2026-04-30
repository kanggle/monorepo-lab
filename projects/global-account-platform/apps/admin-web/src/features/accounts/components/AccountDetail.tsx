'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAccountDetail } from '../hooks/useAccountDetail';
import { useExportAccount } from '../hooks/useExportAccount';
import { LockDialog } from './LockDialog';
import { UnlockDialog } from './UnlockDialog';
import { RevokeSessionDialog } from './RevokeSessionDialog';
import { GdprDeleteDialog } from './GdprDeleteDialog';
import { Button } from '@/shared/ui/button';
import { Badge } from '@/shared/ui/badge';
import { RoleGuard } from '@/shared/ui/RoleGuard';
import { useToast } from '@/shared/ui/toast';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/date';
import type { OperatorRole } from '@/shared/api/admin-api';

interface Props {
  accountId: string;
  roles: OperatorRole[];
}

export function AccountDetail({ accountId, roles }: Props) {
  const router = useRouter();
  const [lockOpen, setLockOpen] = useState(false);
  const [unlockOpen, setUnlockOpen] = useState(false);
  const [revokeOpen, setRevokeOpen] = useState(false);
  const [gdprOpen, setGdprOpen] = useState(false);
  const { data, isLoading, isError } = useAccountDetail(accountId);
  const exportMutation = useExportAccount();
  const toast = useToast();
  const revokeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (revokeTimerRef.current !== null) {
        clearTimeout(revokeTimerRef.current);
      }
    };
  }, []);

  async function handleExport() {
    try {
      const result = await exportMutation.mutateAsync({ accountId });
      const blob = new Blob([JSON.stringify(result, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const date = new Date().toISOString().slice(0, 10).replace(/-/g, '');
      a.href = url;
      a.download = `export-${accountId}-${date}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      revokeTimerRef.current = setTimeout(() => URL.revokeObjectURL(url), 100);
      toast.show('데이터를 내보냈습니다.', 'success');
    } catch (err) {
      const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  if (isLoading) return <p>로딩 중...</p>;
  if (isError || !data) return <p role="alert" className="text-destructive">계정 정보를 불러오지 못했습니다.</p>;

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-center gap-4">
        <h1 className="text-xl font-semibold">{data.email}</h1>
        <Badge>{data.status}</Badge>
        <Button variant="outline" size="sm" className="ml-auto" onClick={() => router.push('/accounts')}>
          ← 목록
        </Button>
      </header>

      <dl className="grid grid-cols-2 gap-2 text-sm">
        <dt className="text-muted-foreground">ID</dt>
        <dd>{data.id}</dd>
        <dt className="text-muted-foreground">가입일</dt>
        <dd>{formatDateTime(data.createdAt)}</dd>
        <dt className="text-muted-foreground">최근 로그인</dt>
        <dd>{formatDateTime(data.lastLoginAt)}</dd>
      </dl>

      <section>
        <h2 className="mb-2 text-sm font-semibold">최근 로그인 이력</h2>
        <ul className="text-sm">
          {data.recentLogins.length === 0 ? <li>이력 없음</li> : null}
          {data.recentLogins.map((h) => (
            <li key={h.eventId}>
              {formatDateTime(h.occurredAt)} — {h.outcome} ({h.ipMasked ?? '-'}, {h.geoCountry ?? '-'})
            </li>
          ))}
        </ul>
      </section>

      <RoleGuard roles={roles} allow={['SUPER_ADMIN', 'SUPPORT_LOCK']}>
        <div className="flex gap-2">
          <Button variant="default" onClick={() => setLockOpen(true)} disabled={data.status === 'LOCKED'}>
            잠금
          </Button>
          <Button variant="outline" onClick={() => setUnlockOpen(true)} disabled={data.status !== 'LOCKED'}>
            해제
          </Button>
          <Button variant="default" onClick={() => setRevokeOpen(true)}>
            세션 강제 종료
          </Button>
        </div>
      </RoleGuard>

      <RoleGuard roles={roles} allow={['SUPER_ADMIN', 'SUPPORT_LOCK']}>
        <div className="flex gap-2 border-t pt-4">
          <Button
            variant="default"
            onClick={() => setGdprOpen(true)}
            disabled={data.status === 'DELETED'}
          >
            GDPR 삭제
          </Button>
        </div>
      </RoleGuard>

      <RoleGuard roles={roles} allow={['SUPER_ADMIN', 'SUPPORT_READONLY', 'SECURITY_ANALYST']}>
        <div className="flex gap-2 border-t pt-4">
          <Button
            variant="outline"
            onClick={handleExport}
            disabled={exportMutation.isPending}
          >
            {exportMutation.isPending ? '처리 중...' : '데이터 내보내기'}
          </Button>
        </div>
      </RoleGuard>

      <LockDialog open={lockOpen} onOpenChange={setLockOpen} accountId={accountId} />
      <UnlockDialog open={unlockOpen} onOpenChange={setUnlockOpen} accountId={accountId} />
      <RevokeSessionDialog open={revokeOpen} onOpenChange={setRevokeOpen} accountId={accountId} />
      <GdprDeleteDialog open={gdprOpen} onOpenChange={setGdprOpen} accountId={accountId} />
    </div>
  );
}
