'use client';

import Image from 'next/image';
import { PageLayout, StatusBadge, DescriptionList, Section } from '@/shared/ui';
import { ErrorMessage } from '@repo/ui';
import { useUser } from '../hooks/use-user';

interface Props {
  userId: string;
}

export function UserDetail({ userId }: Props) {
  const { data: user, isLoading, isError, error, refetch } = useUser(userId);

  if (isError) {
    const is404 =
      error && typeof error === 'object' && 'code' in error && error.code === 'USER_PROFILE_NOT_FOUND';

    return (
      <ErrorMessage
        message={is404 ? '사용자를 찾을 수 없습니다.' : '사용자 정보를 불러오는데 실패했습니다.'}
        onRetry={is404 ? undefined : () => refetch()}
      />
    );
  }

  if (isLoading || !user) {
    return <PageLayout.Skeleton />;
  }

  return (
    <PageLayout title={user.name} actions={[{ label: '← 사용자 관리', href: '/users', variant: 'secondary' as const }]}>
      <Section title="기본 정보">
        <DescriptionList
          items={[
            { label: '상태', value: <StatusBadge status={user.status} /> },
            { label: '이메일', value: <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{user.email}</span> },
            { label: '이름', value: user.name },
            { label: '닉네임', value: <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{user.nickname ?? '-'}</span> },
            { label: '연락처', value: user.phone ?? '-' },
            {
              label: '프로필 이미지',
              value: user.profileImageUrl ? (
                <Image
                  src={user.profileImageUrl}
                  alt={`${user.name} 프로필`}
                  width={64}
                  height={64}
                  // Arbitrary user-uploaded profile URL — skip the Next.js
                  // optimizer to avoid per-host remotePatterns config.
                  unoptimized
                  style={{ width: '64px', height: '64px', borderRadius: '50%', objectFit: 'cover' }}
                />
              ) : (
                '-'
              ),
            },
            { label: '가입일', value: new Date(user.createdAt).toLocaleString('ko-KR') },
            { label: '수정일', value: new Date(user.updatedAt).toLocaleString('ko-KR') },
          ]}
        />
      </Section>
    </PageLayout>
  );
}
