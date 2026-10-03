'use client';

import { useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useAuth } from '../model/auth-context';

/**
 * GAP-backed login surface for web-store. After TASK-FE-067, web-store no
 * longer collects email/password directly — credentials are entered on the
 * GAP authorize page and exchanged via the OIDC `authorization_code` + PKCE
 * flow.
 *
 * Error surfaces (consumer-integration-guide § Phase 4.5 F5 — standard error
 * vocabulary). Legacy + NextAuth-native codes are normalized to the shared
 * vocabulary so every front consumer uses the same keys/meanings:
 *   - `account_type_mismatch` / NextAuth `AccessDenied` → `role_denied`: the
 *     `signIn` guard rejected a non-`CUSTOMER` (operator) account. Tell them to
 *     use the operator console.
 *   - `Configuration` → `config_error`: NextAuth client/discovery setup error
 *     (most commonly "discovery doc fetch failed" when IAM is down).
 *   - `access_denied`: the IdP itself denied (user declined consent).
 *   - NextAuth `OAuthCallbackError` → `provider_error`: the IdP answered the
 *     callback with an error. MEASURED (iam TASK-BE-619 AC-2, nightly
 *     full-stack `e2e/consent-decline.spec.ts`, run 37094305963): declining
 *     IAM's first-visit consent screen (IAM → `error=access_denied` on our
 *     callback) reaches THIS page as `?error=OAuthCallbackError` — NextAuth v5
 *     does not pass the provider's own code through. Before 619 it fell to the
 *     generic fallback, which told a person who had just said «no» to «잠시 후
 *     다시 시도» as if something had broken.
 *   - any unrecognized code → generic fallback (F5: no silent failure).
 *
 * Note: NextAuth v5 surfaces its own `AccessDenied` when a `signIn` callback
 * returns false/redirects; our `signIn` callback redirects to
 * `?error=account_type_mismatch` explicitly, so both must map to `role_denied`.
 */

function resolveCallbackUrl(raw: string | null): string {
  if (!raw) return '/';
  try {
    const decoded = decodeURIComponent(raw);
    if (decoded.startsWith('/') && !decoded.startsWith('//')) return decoded;
  } catch {
    // ignore malformed
  }
  return '/';
}

/**
 * Normalize legacy web-store + NextAuth-native error codes to the Phase 4.5 F5
 * standard vocabulary. Unknown codes pass through unchanged so the message
 * lookup falls to the generic fallback (never silent).
 */
function normalizeErrorCode(code: string): string {
  switch (code) {
    case 'account_type_mismatch':
    case 'AccessDenied': // NextAuth-native: signIn callback denied
      return 'role_denied';
    case 'Configuration': // NextAuth-native: client/discovery config error
      return 'config_error';
    case 'OAuthCallbackError': // NextAuth-native: the IdP returned an error on the callback (TASK-BE-619)
      return 'provider_error';
    default:
      return code;
  }
}

const STANDARD_ERROR_MESSAGES: Record<string, string> = {
  role_denied:
    'operator 계정으로는 web-store 에 접근할 수 없습니다. 운영자 콘솔을 이용해 주세요.',
  config_error: '인증 서버 설정에 문제가 있습니다. 잠시 후 다시 시도해 주세요.',
  access_denied: '로그인이 거부되었습니다. 권한을 확인해 주세요.',
  // TASK-BE-619: the measured case is a declined first-visit consent — say what
  // happened and how to continue, not «try again later» (nothing is broken).
  provider_error:
    'IAM 로그인이 끝나지 않았습니다. 이 사이트 이용에 동의하지 않으셨다면, 다시 «IAM 로그인»을 눌러 동의하면 계속할 수 있습니다.',
  invalid_state: '로그인 세션이 만료되었습니다. 다시 시도해 주세요.',
  state_mismatch: '로그인 요청을 검증하지 못했습니다. 다시 시도해 주세요.',
  token_exchange_failed: '인증 토큰 발급에 실패했습니다. 잠시 후 다시 시도해 주세요.',
};

function describeError(code: string | null): string | null {
  if (!code) return null;
  const standard = normalizeErrorCode(code);
  // F5: unknown code → generic fallback (never silent).
  return (
    STANDARD_ERROR_MESSAGES[standard] ??
    '로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
  );
}

export function LoginForm() {
  const searchParams = useSearchParams();
  const { login, isLoading } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const callbackUrl = useMemo(
    () => resolveCallbackUrl(searchParams?.get('from') ?? searchParams?.get('redirect') ?? null),
    [searchParams],
  );
  const errorMessage = useMemo(
    () => describeError(searchParams?.get('error') ?? null),
    [searchParams],
  );

  async function handleClick() {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await login(callbackUrl);
    } finally {
      // Note: signIn() typically redirects, so we won't actually reach here.
      setIsSubmitting(false);
    }
  }

  const disabled = isSubmitting || isLoading;

  return (
    <div>
      <h1 className="auth-title">로그인</h1>

      {errorMessage && (
        <div role="alert" className="alert-error">
          {errorMessage}
        </div>
      )}

      {/* IAM ADR-007 § 값 변경 (2026-10-01): one account name across fan and store — «IAM».
          The store keeps its own wording and colour; only the account name is shared. */}
      <p style={{ color: 'var(--color-text-secondary)', marginBottom: 'var(--space-6)' }}>
        쇼핑을 계속하려면 IAM 계정으로 로그인하세요.
      </p>

      <button
        type="button"
        onClick={handleClick}
        className="btn btn-primary btn-lg"
        style={{ width: '100%' }}
        disabled={disabled}
      >
        {disabled ? '이동 중...' : 'IAM 로그인'}
      </button>

      <p className="auth-footer" style={{ marginTop: 'var(--space-6)' }}>
        계정이 없으신가요?{' '}
        <a href="/signup">회원가입</a>
      </p>
    </div>
  );
}
