'use client';

import { useUser } from '../hooks/use-ecommerce-users';
import type { UserDetail as UserDetailType } from '../api/user-types';
import { DetailHeader } from './DetailHeader';
import { formatDateTime } from '@/shared/lib/datetime';

/**
 * ecommerce user detail section (TASK-PC-FE-084 — § 2.4.10 users). The console
 * user detail screen: profile header + extended fields.
 *
 * Server-seeded detail is passed in; the client query keeps it fresh on
 * window focus. READ-ONLY — no status-transition actions, no mutation area.
 *
 * notFound → "사용자를 찾을 수 없습니다" empty state (rendered by the page
 * waterfall, not here; this component is only rendered on the happy path).
 */

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-800',
  SUSPENDED: 'bg-yellow-100 text-yellow-800',
  WITHDRAWN: 'bg-gray-100 text-gray-600',
};

function statusBadgeClass(status: string): string {
  return STATUS_BADGE[status] ?? 'bg-muted text-muted-foreground';
}

export interface UserDetailProps {
  user: UserDetailType;
}

export function UserDetail({ user }: UserDetailProps) {
  const detailQ = useUser(user.userId, user);
  const data = detailQ.data ?? user;

  return (
    <section
      aria-labelledby="user-detail-heading"
      data-testid="user-detail"
    >
      <DetailHeader
        headingId="user-detail-heading"
        title="사용자 상세"
        backHref="/ecommerce/users"
        backTestId="user-detail-back"
      />

      {/* User profile header */}
      <dl className="mb-6 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">이름</dt>
          <dd data-testid="user-detail-name">{data.name ?? '-'}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd data-testid="user-detail-status">
            <span
              className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${statusBadgeClass(data.status)}`}
            >
              {data.status}
            </span>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">이메일</dt>
          <dd
            className="break-all text-xs"
            data-testid="user-detail-email"
          >
            {data.email ?? '-'}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">닉네임</dt>
          <dd data-testid="user-detail-nickname">
            {data.nickname ?? '-'}
          </dd>
        </div>
        {data.phone != null && data.phone !== '' && (
          <div>
            <dt className="text-muted-foreground">전화번호</dt>
            <dd data-testid="user-detail-phone">{data.phone}</dd>
          </div>
        )}
        {data.profileImageUrl != null && data.profileImageUrl !== '' && (
          <div className="col-span-2 sm:col-span-3">
            <dt className="text-muted-foreground">프로필 이미지</dt>
            <dd>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={data.profileImageUrl}
                alt="프로필 이미지"
                data-testid="user-detail-profile-image"
                className="mt-1 h-16 w-16 rounded-full object-cover"
              />
            </dd>
          </div>
        )}
        <div>
          <dt className="text-muted-foreground">가입일</dt>
          <dd className="text-xs" data-testid="user-detail-created-at">
            {formatDateTime(data.createdAt)}
          </dd>
        </div>
        {data.updatedAt && (
          <div>
            <dt className="text-muted-foreground">수정일</dt>
            <dd className="text-xs" data-testid="user-detail-updated-at">
              {formatDateTime(data.updatedAt)}
            </dd>
          </div>
        )}
      </dl>
    </section>
  );
}
