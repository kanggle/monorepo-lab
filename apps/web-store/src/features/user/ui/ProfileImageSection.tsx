import { useRef } from 'react';
import type { UserProfile } from '@repo/types';

interface ProfileImageSectionProps {
  profileImageUrl: string;
  profileName: UserProfile['name'];
  onImageChange: (imageDataUrl: string) => void;
}

export function ProfileImageSection({
  profileImageUrl,
  profileName,
  onImageChange,
}: ProfileImageSectionProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handleFileInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      onImageChange(reader.result as string);
    };
    reader.readAsDataURL(file);
  }

  return (
    <div style={{ flexShrink: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-2)' }}>
      {profileImageUrl ? (
        <img
          src={profileImageUrl}
          alt="프로필"
          style={{ width: 80, height: 80, borderRadius: 'var(--radius-full)', objectFit: 'cover', border: '1px solid var(--color-border-light)' }}
        />
      ) : (
        <div
          style={{ width: 80, height: 80, borderRadius: 'var(--radius-full)', background: 'var(--color-primary)', color: 'var(--color-white)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)' }}
        >
          {profileName?.charAt(0).toUpperCase()}
        </div>
      )}
      <input ref={fileInputRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handleFileInputChange} />
      <div style={{ display: 'flex', gap: 'var(--space-1)' }}>
        <button
          type="button"
          className="btn"
          style={{ fontSize: 'var(--font-size-xs)', padding: 'var(--space-1) var(--space-2)' }}
          onClick={() => fileInputRef.current?.click()}
        >
          선택
        </button>
        {profileImageUrl && (
          <button
            type="button"
            className="btn"
            style={{ fontSize: 'var(--font-size-xs)', padding: 'var(--space-1) var(--space-2)', color: 'var(--color-text-muted)' }}
            onClick={() => onImageChange('')}
          >
            삭제
          </button>
        )}
      </div>
    </div>
  );
}
