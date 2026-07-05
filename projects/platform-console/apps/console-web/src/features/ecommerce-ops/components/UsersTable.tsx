'use client';

import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { formatDateTime } from '@/shared/lib/datetime';
import { userStatusTone, type UserList } from '../api/user-types';

interface UsersTableProps {
  rows: UserList['content'];
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * User list table + pagination (TASK-PC-FE-199 — extracted from
 * {@link UsersScreen}, presentational only). READ-ONLY per-row action:
 * 상세(drill). Query/filter state stays owned by `UsersScreen`; all
 * `data-testid`s are unchanged.
 */
export function UsersTable({ rows, pagination }: UsersTableProps) {
  return (
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
                <StatusBadge tone={userStatusTone(u.status)}>
                  {u.status}
                </StatusBadge>
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
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="user-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="user-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="user-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
