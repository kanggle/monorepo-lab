import type { ProfileFieldErrors } from '../model/types';
import { ProfileFormField } from './ProfileFormField';

interface EditFieldsSectionProps {
  nickname: string;
  phone: string;
  fieldErrors: ProfileFieldErrors;
  onNicknameChange: (value: string) => void;
  onPhoneChange: (value: string) => void;
}

export function EditFieldsSection({
  nickname,
  phone,
  fieldErrors,
  onNicknameChange,
  onPhoneChange,
}: EditFieldsSectionProps) {
  return (
    <section
      className="card"
      style={{ padding: 'var(--space-6)', marginBottom: 'var(--space-6)' }}
    >
      <h2 className="section-title">프로필 수정</h2>
      <div
        style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}
      >
        <ProfileFormField
          id="nickname"
          label="닉네임"
          type="text"
          value={nickname}
          onChange={onNicknameChange}
          error={fieldErrors.nickname}
        />
        <ProfileFormField
          id="phone"
          label="전화번호"
          type="tel"
          value={phone}
          onChange={onPhoneChange}
          placeholder="010-0000-0000"
          error={fieldErrors.phone}
        />
      </div>
    </section>
  );
}
