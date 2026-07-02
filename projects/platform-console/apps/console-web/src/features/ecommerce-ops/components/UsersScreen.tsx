'use client';

import { useId, useState } from 'react';
import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useUsers } from '../hooks/use-ecommerce-users';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  USER_DEFAULT_PAGE_SIZE,
  USER_STATUS_VALUES,
  type UserList,
  type UserListParams,
} from '../api/user-types';

/**
 * ecommerce user operations list section (TASK-PC-FE-084 — § 2.4.10 users).
 * The console user list screen.
 *
 * Server-rendered initial page is passed in; client re-query handles
 * filter / pagination. Per-row action: 상세(drill).
 *
 * READ-ONLY: no mutation actions. Filters: email search + status dropdown.
 *
 * Resilience (§ 2.5): 401 handled by the server route (whole-session
 * re-login); 403 → inline actionable; 503/timeout → this section degrades
 * only.
 */

export interface UsersScreenProps {
  users: UserList;
}

const STATUS_FILTER_OPTIONS = ['', ...USER_STATUS_VALUES] as const;

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-800',
  SUSPENDED: 'bg-yellow-100 text-yellow-800',
  WITHDRAWN: 'bg-gray-100 text-gray-600',
};

function statusBadgeClass(status: string): string {
  return (
    STATUS_BADGE[status] ??
    'bg-muted text-muted-foreground'
  );
}

export function UsersScreen({ users }: UsersScreenProps) {
  const statusFid = useId();
  const emailFid = useId();

  const [statusFilter, setStatusFilter] = useState('');
  const [emailFilter, setEmailFilter] = useState('');
  const [query, setQuery] = useState<UserListParams>({
    page: 0,
    size: users.size || USER_DEFAULT_PAGE_SIZE,
  });

  const seeded =
    (query.page ?? 0) === 0 && !query.status && !query.email;
  const listQ = useUsers(query, seeded ? users : undefined);
  // Only the seeded (page 0, no filter) query may fall back to the server-rendered
  // `users` seed. For a filtered/paginated query, falling back to the seed would
  // flash the full unfiltered list while the new query is still in flight — instead
  // we render a loading placeholder until the real result lands.
  const data = seeded ? listQ.data ?? users : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter || undefined,
      email: emailFilter || undefined,
      page: 0,
      size: users.size || USER_DEFAULT_PAGE_SIZE,
    });
  }

  const rows = data?.content ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

  return (
    <section aria-labelledby="ecommerce-users-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1 id="ecommerce-users-heading" className="text-2xl font-semibold">
          E-Commerce 사용자
        </h1>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        사용자 목록 · 상세(프로필).
      </p>

      <form
        onSubmit={submitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="사용자 필터"
      >
        <div>
          <label
            htmlFor={emailFid}
            className="block text-sm font-medium text-foreground"
          >
            이메일
          </label>
          <input
            id={emailFid}
            type="text"
            value={emailFilter}
            onChange={(e) => setEmailFilter(e.target.value)}
            placeholder="이메일 검색"
            data-testid="user-email-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            data-testid="user-status-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_FILTER_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s || '전체'}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="user-filter-submit">
          조회
        </Button>
      </form>

      {forbidden ? (
        <div
          role="status"
          data-testid="user-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="user-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          ecommerce 사용자 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p className="text-sm text-muted-foreground" data-testid="user-loading">
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="user-empty">
          표시할 사용자가 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="user-table">
            <caption className="sr-only">사용자 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  이메일
                </th>
                <th scope="col" className="p-2">
                  이름
                </th>
                <th scope="col" className="p-2">
                  닉네임
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  가입일
                </th>
                <th scope="col" className="p-2">
                  작업
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((u, i) => (
                <tr
                  key={u.userId}
                  data-testid={`user-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2 text-xs break-all">{u.email ?? '-'}</td>
                  <td className="p-2">{u.name ?? '-'}</td>
                  <td className="p-2 text-muted-foreground">
                    {u.nickname ?? '-'}
                  </td>
                  <td className="p-2" data-testid={`user-row-status-${i}`}>
                    <span
                      className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${statusBadgeClass(u.status)}`}
                    >
                      {u.status}
                    </span>
                  </td>
                  <td className="p-2 text-sm text-muted-foreground">
                    {formatDateTime(u.createdAt)}
                  </td>
                  <td className="p-2">
                    <Link href={`/ecommerce/users/${u.userId}`}>
                      <Button
                        variant="secondary"
                        size="sm"
                        data-testid={`user-detail-${i}`}
                      >
                        상세
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="flex items-center justify-between"
            aria-label="사용자 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((q) => ({
                  ...q,
                  page: Math.max(0, (q.page ?? 0) - 1),
                }))
              }
              data-testid="user-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="user-pageinfo"
            >
              {`${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`}
            </span>
            <Button
              variant="secondary"
              disabled={(data?.page ?? 0) + 1 >= totalPages}
              onClick={() =>
                setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
              }
              data-testid="user-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
