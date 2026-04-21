'use client';

import { useEffect } from 'react';
import { ErrorMessage } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { ProfileForm } from './ProfileForm';
import { useProfile } from '../model/use-profile';
import { useProfileImage } from '@/shared/context/ProfileImageContext';

export function ProfileLoader() {
  const { data: profile, isLoading, isError, error: queryError, refetch } = useProfile();
  const { setImageUrl: setGlobalProfileImage } = useProfileImage();

  useEffect(() => {
    if (profile) {
      setGlobalProfileImage(profile.profileImageUrl ?? '');
    }
  }, [profile, setGlobalProfileImage]);

  const error = isError
    ? (queryError as { code?: string })?.code === 'USER_PROFILE_NOT_FOUND'
      ? '프로필을 찾을 수 없습니다.'
      : '프로필을 불러오는데 실패했습니다.'
    : '';

  return (
    <div>
      <h1 className="page-title">내 프로필</h1>

      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
            <Skeleton width="80px" height="80px" borderRadius="var(--radius-full)" />
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              <Skeleton width="40%" height="16px" />
              <Skeleton width="60%" height="14px" />
            </div>
          </div>
          <Skeleton width="100%" height="40px" />
          <Skeleton width="100%" height="40px" />
          <Skeleton width="100%" height="40px" />
        </div>
      )}
      {error && <ErrorMessage message={error} onRetry={() => refetch()} />}
      {!isLoading && !error && profile && (
        <ProfileForm
          profile={profile}
          onUpdated={() => refetch()}
        />
      )}
    </div>
  );
}
