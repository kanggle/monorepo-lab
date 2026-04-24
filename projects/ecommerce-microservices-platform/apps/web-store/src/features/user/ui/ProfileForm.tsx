'use client';

import type { UserProfile } from '@repo/types';
import { useProfileForm } from '../model/use-profile-form';
import { Toast } from '@/shared/ui';
import { BasicInfoSection } from './BasicInfoSection';
import { EditFieldsSection } from './EditFieldsSection';

interface ProfileFormProps {
  profile: UserProfile;
  onUpdated: () => void;
}

export function ProfileForm({ profile, onUpdated }: ProfileFormProps) {
  const {
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
  } = useProfileForm(profile, onUpdated);

  return (
    <form onSubmit={handleSubmit} noValidate>
      <BasicInfoSection
        profile={profile}
        profileImageUrl={profileImageUrl}
        onImageChange={setProfileImageUrl}
      />

      {toast && (
        <Toast message={toast.message} type={toast.type} onClose={clearToast} />
      )}

      <EditFieldsSection
        nickname={nickname}
        phone={phone}
        fieldErrors={fieldErrors}
        onNicknameChange={(value) => {
          setNickname(value);
          setFieldErrors((prev) => ({ ...prev, nickname: undefined }));
        }}
        onPhoneChange={(value) => {
          setPhone(value);
          setFieldErrors((prev) => ({ ...prev, phone: undefined }));
        }}
      />

      <button
        type="submit"
        disabled={!hasChanges || isSubmitting}
        className="btn btn-primary btn-lg"
        style={{ width: '100%' }}
      >
        {isSubmitting ? '수정 중...' : '프로필 수정'}
      </button>
    </form>
  );
}
