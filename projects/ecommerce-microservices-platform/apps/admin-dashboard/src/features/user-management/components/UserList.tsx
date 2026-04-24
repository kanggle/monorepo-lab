'use client';

import { useRouter } from 'next/navigation';
import { DataTable, StatusBadge, FilterBar, ListError } from '@/shared/ui';
import type { ColumnDef } from '@/shared/ui';
import { useUsers } from '../hooks/use-users';
import { USER_STATUS_OPTIONS } from '@/shared/lib/status-options';
import type { AdminUserSummary } from '@repo/types';

const columns: ColumnDef<AdminUserSummary>[] = [
  { key: 'email', header: '이메일', sortable: true },
  { key: 'name', header: '이름', sortable: true },
  { key: 'nickname', header: '닉네임', sortable: true },
  {
    key: 'status',
    header: '상태',
    sortable: true,
    render: (user: AdminUserSummary) => <StatusBadge status={user.status} />,
  },
  {
    key: 'createdAt',
    header: '가입일',
    sortable: true,
    render: (user: AdminUserSummary) =>
      new Date(user.createdAt).toLocaleDateString('ko-KR'),
  },
];

export function UserList() {
  const router = useRouter();
  const { data, isLoading, isError, refetch, pagination, filters } = useUsers();

  if (isError) {
    return <ListError message="사용자 목록을 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <div>
      <FilterBar
        searchPlaceholder="이메일 검색..."
        searchValue={filters.email ?? ''}
        onSearchChange={(value) => filters.setFilter('email', value || undefined)}
        statusOptions={USER_STATUS_OPTIONS}
        statusValue={filters.status}
        onStatusChange={(value) => filters.setFilter('status', value)}
      />
      <DataTable<AdminUserSummary>
        columns={columns}
        data={data?.content ?? []}
        pagination={pagination}
        isLoading={isLoading}
        emptyMessage="등록된 사용자가 없습니다."
        onRowClick={(item) => router.push(`/users/${item.userId}`)}
      />
    </div>
  );
}
