'use client';

import { usePushSubscription } from '../model/use-push-subscription';

/**
 * "Receive push on this browser" opt-in (TASK-FE-083). Distinct from the server-side
 * push preference toggle: this manages whether THIS browser holds a Web Push
 * subscription. Reflects the browser permission state (default/granted/denied) and
 * degrades to a support notice where Web Push is unavailable.
 */
export function PushOptIn() {
  const { supported, permission, subscribed, isBusy, error, subscribe, unsubscribe } =
    usePushSubscription();

  const note = (text: string) => (
    <p
      style={{
        margin: 'var(--space-1) 0 0',
        fontSize: 'var(--font-size-xs)',
        color: 'var(--color-text-secondary)',
      }}
    >
      {text}
    </p>
  );

  return (
    <section
      data-testid="push-optin"
      style={{ marginTop: 'var(--space-6)', paddingTop: 'var(--space-4)', borderTop: '1px solid var(--color-border-light)' }}
    >
      <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)' }}>
        이 브라우저에서 푸시 받기
      </p>

      {!supported && note('이 브라우저는 푸시 알림을 지원하지 않습니다.')}

      {supported && permission === 'denied' && note(
        '알림 권한이 차단되어 있습니다. 브라우저 설정에서 이 사이트의 알림을 허용해주세요.',
      )}

      {supported && permission !== 'denied' && (
        <div style={{ marginTop: 'var(--space-3)' }}>
          {note(
            subscribed
              ? '이 브라우저에서 푸시 알림을 받고 있습니다.'
              : '허용하면 주문·배송 알림을 이 브라우저에서 실시간으로 받습니다.',
          )}
          <button
            type="button"
            data-testid="push-optin-button"
            disabled={isBusy}
            onClick={() => (subscribed ? unsubscribe() : subscribe())}
            style={{
              marginTop: 'var(--space-3)',
              padding: 'var(--space-2) var(--space-4)',
              fontSize: 'var(--font-size-sm)',
              cursor: isBusy ? 'not-allowed' : 'pointer',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              backgroundColor: subscribed ? 'var(--color-surface)' : 'var(--color-primary)',
              color: subscribed ? 'var(--color-text-primary)' : 'var(--color-white)',
            }}
          >
            {isBusy ? '처리 중…' : subscribed ? '이 브라우저 구독 해지' : '이 브라우저에서 푸시 받기'}
          </button>
        </div>
      )}

      {error && (
        <p
          data-testid="push-optin-error"
          role="alert"
          style={{ marginTop: 'var(--space-3)', fontSize: 'var(--font-size-sm)', color: 'var(--color-error)' }}
        >
          {error}
        </p>
      )}
    </section>
  );
}
