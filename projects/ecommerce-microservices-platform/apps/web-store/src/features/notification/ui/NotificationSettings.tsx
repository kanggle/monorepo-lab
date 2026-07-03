'use client';

import { useState, useEffect } from 'react';
import { ErrorMessage } from '@repo/ui';
import { getErrorMessage } from '@repo/types/guards';
import { Skeleton } from '@/shared/ui/Skeleton';
import { useNotificationPreferences } from '../model/use-notification-preferences';
import { useUpdatePreferences } from '../model/use-update-preferences';
import { SettingToggle } from './SettingToggle';
import { PushOptIn } from './PushOptIn';
import { PushDeviceList } from './PushDeviceList';
import { DetailHeader } from '@/shared/ui';

type PreferenceField = 'emailEnabled' | 'smsEnabled' | 'pushEnabled';

function NotificationSettingsSkeleton() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 'var(--space-4) 0' }}>
          <div>
            <Skeleton width="80px" height="14px" />
            <div style={{ marginTop: 'var(--space-1)' }}>
              <Skeleton width="160px" height="12px" />
            </div>
          </div>
          <Skeleton width="44px" height="24px" borderRadius="12px" />
        </div>
      ))}
    </div>
  );
}

export function NotificationSettings() {
  const { data: preferences, isLoading, isError, refetch } = useNotificationPreferences();
  const updatePreferences = useUpdatePreferences();

  const [emailEnabled, setEmailEnabled] = useState(true);
  const [smsEnabled, setSmsEnabled] = useState(false);
  const [pushEnabled, setPushEnabled] = useState(true);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    if (preferences) {
      setEmailEnabled(preferences.emailEnabled);
      setSmsEnabled(preferences.smsEnabled);
      setPushEnabled(preferences.pushEnabled);
    }
  }, [preferences]);

  async function handleToggle(field: PreferenceField, value: boolean) {
    const updatedState = { emailEnabled, smsEnabled, pushEnabled, [field]: value };

    const setters: Record<PreferenceField, (v: boolean) => void> = {
      emailEnabled: setEmailEnabled,
      smsEnabled: setSmsEnabled,
      pushEnabled: setPushEnabled,
    };

    setters[field](value);
    setSaveSuccess(false);

    try {
      await updatePreferences.mutateAsync(updatedState);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } catch {
      // Rollback on failure
      setters[field](!value);
    }
  }

  const error = isError ? '알림 설정을 불러오는데 실패했습니다.' : '';

  return (
    <div>
      <DetailHeader title="알림 설정" backHref="/my/notifications" backLabel="알림 목록" />

      {isLoading && <NotificationSettingsSkeleton />}
      {error && <ErrorMessage message={error} onRetry={() => refetch()} />}

      {!isLoading && !error && (
        <div>
          <SettingToggle
            label="이메일"
            description="이메일로 알림을 받습니다"
            checked={emailEnabled}
            disabled={updatePreferences.isPending}
            onChange={(v) => handleToggle('emailEnabled', v)}
          />
          <SettingToggle
            label="SMS"
            description="문자 메시지로 알림을 받습니다"
            checked={smsEnabled}
            disabled={updatePreferences.isPending}
            onChange={(v) => handleToggle('smsEnabled', v)}
          />
          <SettingToggle
            label="푸시"
            description="푸시 알림을 받습니다"
            checked={pushEnabled}
            disabled={updatePreferences.isPending}
            onChange={(v) => handleToggle('pushEnabled', v)}
            // When the push area opens below, drop the toggle's bottom divider so the
            // opt-in button sits inside the area with no line directly above it.
            divider={!pushEnabled}
          />

          {/* Push channel area: when push is enabled, this browser's opt-in and the
              registered-device list are grouped here, directly under the 푸시 toggle. */}
          {pushEnabled && (
            <div data-testid="push-area">
              <PushOptIn />
              <PushDeviceList />
            </div>
          )}

          {saveSuccess && (
            <p
              data-testid="save-success"
              style={{
                marginTop: 'var(--space-4)',
                fontSize: 'var(--font-size-sm)',
                color: 'var(--color-success)',
              }}
            >
              설정이 저장되었습니다.
            </p>
          )}
          {updatePreferences.isError && (
            <p
              data-testid="save-error"
              role="alert"
              style={{
                marginTop: 'var(--space-4)',
                fontSize: 'var(--font-size-sm)',
                color: 'var(--color-error)',
              }}
            >
              {getErrorMessage(updatePreferences.error, '알림 설정 변경에 실패했습니다.')}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
