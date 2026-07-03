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
      style={{ marginTop: 'var(--space-4)' }}
    >
      {!supported && note('이 브라우저는 푸시 알림을 지원하지 않습니다.')}

      {supported && permission === 'denied' && note(
        '알림 권한이 차단되어 있습니다. 브라우저 설정에서 이 사이트의 알림을 허용해주세요.',
      )}

      {supported && permission !== 'denied' && (
        <button
          type="button"
          data-testid="push-optin-button"
          disabled={isBusy}
          onClick={() => (subscribed ? unsubscribe() : subscribe())}
          style={{
            padding: 'var(--space-2) var(--space-4)',
            fontSize: 'var(--font-size-sm)',
            cursor: isBusy ? 'not-allowed' : 'pointer',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)',
            backgroundColor: subscribed ? 'var(--color-surface)' : 'var(--color-primary)',
            // "이 브라우저에서 푸시 받기" text color matches the selected sidebar menu
            // item (my/layout.tsx active color = var(--color-on-primary)).
            color: subscribed ? 'var(--color-text-primary)' : 'var(--color-on-primary)',
          }}
        >
          {isBusy ? '처리 중…' : subscribed ? '이 브라우저 구독 해지' : '이 브라우저에서 푸시 받기'}
        </button>
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
