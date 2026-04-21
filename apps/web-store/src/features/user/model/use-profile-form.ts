'use client';

import { useState, useCallback } from 'react';
import type { UserProfile } from '@repo/types';
import { isApiError, ERROR_MESSAGES } from '@repo/types/guards';
import type { ProfileFieldErrors } from './types';
import { useUpdateProfile } from './use-update-profile';
import { useProfileImage } from '@/shared/context/ProfileImageContext';

function validateFields(nickname: string, phone: string): ProfileFieldErrors {
  const errors: ProfileFieldErrors = {};

  if (nickname.length > 0 && nickname.trim().length === 0) {
    errors.nickname = '닉네임을 올바르게 입력해주세요.';
  }

  if (phone.length > 0 && !/^[\d-]+$/.test(phone)) {
    errors.phone = '전화번호 형식이 올바르지 않습니다.';
  }

  return errors;
}

export interface ProfileFormState {
  nickname: string;
  phone: string;
  profileImageUrl: string;
  fieldErrors: ProfileFieldErrors;
  toast: { message: string; type: 'success' | 'error' } | null;
  isSubmitting: boolean;
  hasChanges: boolean;
  setNickname: (v: string) => void;
  setPhone: (v: string) => void;
  setProfileImageUrl: (v: string) => void;
  setFieldErrors: React.Dispatch<React.SetStateAction<ProfileFieldErrors>>;
  clearToast: () => void;
  handleSubmit: (e: React.FormEvent) => Promise<void>;
}

export function useProfileForm(
  profile: UserProfile,
  onUpdated: () => void,
): ProfileFormState {
  const [nickname, setNickname] = useState(profile.nickname ?? '');
  const [phone, setPhone] = useState(profile.phone ?? '');
  const [profileImageUrl, setProfileImageUrl] = useState(
    profile.profileImageUrl ?? '',
  );
  const [fieldErrors, setFieldErrors] = useState<ProfileFieldErrors>({});
  const [toast, setToast] = useState<{
    message: string;
    type: 'success' | 'error';
  } | null>(null);

  const { setImageUrl: setGlobalProfileImage } = useProfileImage();
  const updateMutation = useUpdateProfile();
  const isSubmitting = updateMutation.isPending;

  const clearToast = useCallback(() => setToast(null), []);

  const hasChanges =
    nickname !== (profile.nickname ?? '') ||
    phone !== (profile.phone ?? '') ||
    profileImageUrl !== (profile.profileImageUrl ?? '');

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (isSubmitting || !hasChanges) return;

    const errors = validateFields(nickname, phone);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setToast(null);

    const data: Record<string, string> = {};
    if (nickname !== (profile.nickname ?? '')) {
      data.nickname = nickname || '';
    }
    if (phone !== (profile.phone ?? '')) {
      data.phone = phone || '';
    }
    if (profileImageUrl !== (profile.profileImageUrl ?? '')) {
      data.profileImageUrl = profileImageUrl || '';
    }

    updateMutation.mutate(data, {
      onSuccess: (updated) => {
        setToast({ message: '프로필이 수정되었습니다.', type: 'success' });
        setGlobalProfileImage(updated.profileImageUrl ?? '');
        onUpdated();
      },
      onError: (err) => {
        if (isApiError(err)) {
          setToast({
            message:
              ERROR_MESSAGES[err.code] ??
              err.message ??
              '프로필 수정에 실패했습니다.',
            type: 'error',
          });
        } else {
          setToast({ message: '프로필 수정에 실패했습니다.', type: 'error' });
        }
      },
    });
  }

  return {
    nickname,
    phone,
    profileImageUrl,
    fieldErrors,
    toast,
    isSubmitting,
    hasChanges,
    setNickname,
    setPhone,
    setProfileImageUrl,
    setFieldErrors,
    clearToast,
    handleSubmit,
  };
}
