'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { ApiError } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import { useAgencies } from '../hooks/use-fan-directory';
import { agencyStatusTone } from '../lib/fan-messages';
import { FAN_DEFAULT_PAGE_SIZE, type Agency, type Paged } from '../api/fan-types';
import { FanNote } from './fan-ui';

/**
 * Fan agencies list (TASK-MONO-751 — ADR-MONO-079 D1/D4-A). Server-seeded page 0, client
 * pagination. ARCHIVED agencies stay listed (the producer includes them — `status` says so).
 */
export function AgenciesScreen({ agencies }: { agencies: Paged<Agency> }) {
  const [page, setPage] = useState(0);
  const q = useAgencies(
    { page, size: agencies.size || FAN_DEFAULT_PAGE_SIZE },
    page === 0 ? agencies : undefined,
  );
  const data = page === 0 ? q.data ?? agencies : q.data;
  const apiError = q.error instanceof ApiError ? q.error : null;
  const forbidden = apiError?.status === 403;
  const degraded = q.isError && !forbidden;
  const rows = data?.content ?? [];
  const totalPages = Math.max(1, data?.totalPages ?? 1);

  return (
    <section aria-labelledby="fan-agencies-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1 id="fan-agencies-heading" className="text-2xl font-semibold">
          소속사
        </h1>
        <Link href="/fan/agencies/new" data-testid="fan-agency-new-link">
          <Button>소속사 등록</Button>
        </Link>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        팬 플랫폼의 소속사 디렉터리입니다. 아티스트·그룹의 소속은 각 상세 화면에서 바꿉니다.
        소속사는 삭제하지 않고 보관합니다(보관해도 기존 소속은 유지됩니다).
      </p>

      {forbidden ? (
        <FanNote testId="fan-agency-forbidden">
          이 화면은 fan-platform 테넌트로 전환한 플랫폼 운영자만 쓸 수 있습니다.
        </FanNote>
      ) : degraded ? (
        <FanNote testId="fan-agency-degraded">
          소속사 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은 계속 사용할 수 있습니다.
        </FanNote>
      ) : data === undefined ? (
        <p className="text-sm text-muted-foreground" data-testid="fan-agency-loading">
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="fan-agency-empty">
          등록된 소속사가 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="fan-agency-table">
            <caption className="sr-only">소속사 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">이름</th>
                <th scope="col" className="p-2">상태</th>
                <th scope="col" className="p-2">스토어 셀러</th>
                <th scope="col" className="p-2">등록일</th>
                <th scope="col" className="p-2">작업</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((a, i) => (
                <tr key={a.id} data-testid={`fan-agency-row-${i}`} className="border-b border-border">
                  <td className="p-2">{a.name}</td>
                  <td className="p-2">
                    <StatusBadge tone={agencyStatusTone(a.status)}>{a.status}</StatusBadge>
                  </td>
                  <td className="p-2 font-mono text-xs">{a.storeSellerId ?? '—'}</td>
                  <td className="p-2 text-sm text-muted-foreground">
                    {a.createdAt ? formatDateTime(a.createdAt) : '—'}
                  </td>
                  <td className="p-2">
                    <Link href={`/fan/agencies/${encodeURIComponent(a.id)}`}>
                      <Button variant="secondary" size="sm" data-testid={`fan-agency-detail-${i}`}>
                        상세
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav className="flex items-center justify-between" aria-label="소속사 페이지 이동">
            <Button
              variant="secondary"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              data-testid="fan-agency-prev"
            >
              이전
            </Button>
            <span className="text-sm text-muted-foreground" data-testid="fan-agency-pageinfo">
              {(data.page ?? 0) + 1} / {totalPages} 페이지 · 총 {data.totalElements}건
            </span>
            <Button
              variant="secondary"
              disabled={(data.page ?? 0) + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
              data-testid="fan-agency-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
