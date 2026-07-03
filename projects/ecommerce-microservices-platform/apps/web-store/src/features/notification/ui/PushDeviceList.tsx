'use client';

import { ErrorMessage } from '@repo/ui';
import { formatDate } from '@/shared/lib/datetime';
import { usePushDevices } from '../model/use-push-devices';
import { deviceLabelFromUserAgent } from '../lib/device-label';

/**
 * Lists the browsers/devices this account has registered for Web Push
 * (TASK-FE-085). Each row shows a device label (parsed from the stored
 * User-Agent), its registration date, a "이 기기" badge for the current browser,
 * and a per-device opt-out. Distinct from `PushOptIn`, which only toggles THIS
 * browser's subscription.
 */
export function PushDeviceList() {
  const {
    devices,
    currentEndpoint,
    isLoading,
    isError,
    refetch,
    removeDevice,
    removingEndpoint,
  } = usePushDevices();

  const heading = (
    <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)' }}>
      푸시 수신 기기
    </p>
  );

  const note = (text: string) => (
    <p style={{ margin: 'var(--space-2) 0 0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
      {text}
    </p>
  );

  return (
    <section
      data-testid="push-device-list"
      style={{ marginTop: 'var(--space-4)' }}
    >
      {heading}

      {isLoading && note('기기 목록을 불러오는 중…')}

      {isError && (
        <div style={{ marginTop: 'var(--space-2)' }}>
          <ErrorMessage message="기기 목록을 불러오지 못했습니다." onRetry={() => refetch()} />
        </div>
      )}

      {!isLoading && !isError && devices.length === 0 && (
        note('이 계정으로 푸시를 받도록 등록된 기기가 없습니다.')
      )}

      {!isLoading && !isError && devices.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 'var(--space-3) 0 0', padding: 0 }}>
          {devices.map((device) => {
            const isCurrent = currentEndpoint !== null && device.endpoint === currentEndpoint;
            const isRemoving = removingEndpoint === device.endpoint;
            return (
              <li
                key={device.id}
                data-testid="push-device-item"
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: 'var(--space-3)',
                  padding: 'var(--space-3) 0',
                  borderBottom: '1px solid var(--color-border-light)',
                }}
              >
                <div style={{ minWidth: 0 }}>
                  <p style={{
                    margin: 0,
                    fontSize: 'var(--font-size-sm)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}>
                    {deviceLabelFromUserAgent(device.userAgent)}
                    {isCurrent && (
                      <span
                        data-testid="current-device-badge"
                        style={{
                          marginLeft: 'var(--space-2)',
                          padding: '0 var(--space-2)',
                          fontSize: 'var(--font-size-xs)',
                          fontWeight: 'var(--font-weight-semibold)',
                          color: 'var(--color-primary)',
                          border: '1px solid var(--color-primary)',
                          borderRadius: 'var(--radius-sm)',
                        }}
                      >
                        이 기기
                      </span>
                    )}
                  </p>
                  <p style={{ margin: 'var(--space-1) 0 0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                    {formatDate(device.createdAt)} 등록
                  </p>
                </div>
                <button
                  type="button"
                  aria-label="이 기기 푸시 해지"
                  disabled={isRemoving}
                  onClick={() => removeDevice(device.endpoint)}
                  style={{
                    flexShrink: 0,
                    padding: 'var(--space-1) var(--space-3)',
                    fontSize: 'var(--font-size-xs)',
                    cursor: isRemoving ? 'not-allowed' : 'pointer',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    backgroundColor: 'var(--color-surface)',
                    color: 'var(--color-text-primary)',
                  }}
                >
                  {isRemoving ? '해지 중…' : '해지'}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
