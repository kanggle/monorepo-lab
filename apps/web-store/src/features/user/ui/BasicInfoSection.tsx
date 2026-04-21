import type { UserProfile } from '@repo/types';
import { ProfileImageSection } from './ProfileImageSection';

interface BasicInfoSectionProps {
  profile: UserProfile;
  profileImageUrl: string;
  onImageChange: (url: string) => void;
}

export function BasicInfoSection({
  profile,
  profileImageUrl,
  onImageChange,
}: BasicInfoSectionProps) {
  return (
    <section
      className="card"
      style={{ padding: 'var(--space-6)', marginBottom: 'var(--space-6)' }}
    >
      <h2 className="section-title">기본 정보</h2>
      <div
        style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-6)' }}
      >
        <ProfileImageSection
          profileImageUrl={profileImageUrl}
          profileName={profile.name}
          onImageChange={onImageChange}
        />
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 'var(--space-3)',
          }}
        >
          <div>
            <span
              style={{
                fontWeight: 'var(--font-weight-semibold)',
                fontSize: 'var(--font-size-sm)',
                color: 'var(--color-text-secondary)',
              }}
            >
              이메일
            </span>
            <p style={{ margin: 'var(--space-1) 0 0', fontSize: 'var(--font-size-sm)' }}>
              {profile.email}
            </p>
          </div>
          <div>
            <span
              style={{
                fontWeight: 'var(--font-weight-semibold)',
                fontSize: 'var(--font-size-sm)',
                color: 'var(--color-text-secondary)',
              }}
            >
              이름
            </span>
            <p style={{ margin: 'var(--space-1) 0 0', fontSize: 'var(--font-size-sm)' }}>
              {profile.name}
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
