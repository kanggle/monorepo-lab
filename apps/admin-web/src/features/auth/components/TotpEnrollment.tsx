'use client';

import { useState, useEffect, useRef } from 'react';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { apiClient } from '@/shared/api/client';
import { TotpEnrollResponseSchema, type TotpEnrollResponse } from '@/shared/api/admin-api';
import { ApiError, messageForCode } from '@/shared/api/errors';

interface TotpEnrollmentProps {
  bootstrapToken: string;
  onComplete: () => void;
}

type Step = 'loading' | 'show-qr' | 'verify';

export function TotpEnrollment({ bootstrapToken, onComplete }: TotpEnrollmentProps) {
  const [step, setStep] = useState<Step>('loading');
  const [enrollData, setEnrollData] = useState<TotpEnrollResponse | null>(null);
  const [totpCode, setTotpCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [copied, setCopied] = useState(false);

  const enrolledRef = useRef(false);

  useEffect(() => {
    if (enrolledRef.current) return;
    enrolledRef.current = true;
    (async () => {
      try {
        const data = await apiClient.post<unknown>(
          '/api/auth/2fa/enroll',
          undefined,
          {
            skipAuthRetry: true,
            headers: { Authorization: `Bearer ${bootstrapToken}` },
          },
        );
        const parsed = TotpEnrollResponseSchema.parse(data);
        setEnrollData(parsed);
        setStep('show-qr');
      } catch (err) {
        const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : '2FA 등록 요청에 실패했습니다.';
        setError(msg);
      }
    })();
  }, [bootstrapToken]);

  async function handleVerify() {
    if (!enrollData) return;
    setError(null);
    setVerifying(true);
    try {
      await apiClient.post(
        '/api/auth/2fa/verify',
        { totpCode },
        {
          skipAuthRetry: true,
          headers: { Authorization: `Bearer ${enrollData.bootstrapToken}` },
        },
      );
      // Verify success — now auto-login by calling the login proxy again
      // The parent will handle re-login
      onComplete();
    } catch (err) {
      const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : 'TOTP 검증에 실패했습니다.';
      setError(msg);
    } finally {
      setVerifying(false);
    }
  }

  function extractSecret(uri: string): string | null {
    try {
      const url = new URL(uri);
      return url.searchParams.get('secret');
    } catch {
      return null;
    }
  }

  async function copyRecoveryCodes() {
    if (!enrollData) return;
    try {
      await navigator.clipboard.writeText(enrollData.recoveryCodes.join('\n'));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // fallback: do nothing
    }
  }

  if (error && step === 'loading') {
    return (
      <div className="flex w-full max-w-sm flex-col gap-4">
        <h2 className="text-lg font-semibold">2FA 등록</h2>
        <p role="alert" className="text-sm text-destructive">{error}</p>
      </div>
    );
  }

  if (step === 'loading') {
    return (
      <div className="flex w-full max-w-sm flex-col gap-4">
        <h2 className="text-lg font-semibold">2FA 등록</h2>
        <p className="text-sm text-muted-foreground">2FA 등록 정보를 불러오는 중...</p>
      </div>
    );
  }

  if (!enrollData) return null;

  const secret = extractSecret(enrollData.otpauthUri);

  return (
    <div className="flex w-full max-w-sm flex-col gap-4">
      <h2 className="text-lg font-semibold">2FA 등록</h2>
      <p className="text-sm text-muted-foreground">
        인증 앱(Google Authenticator, Authy 등)에 아래 정보를 등록하세요.
      </p>

      <div className="flex flex-col gap-2">
        <Label>OTP URI</Label>
        <div className="break-all rounded-md border border-border bg-muted/30 p-2 text-xs font-mono select-all">
          {enrollData.otpauthUri}
        </div>
        {secret ? (
          <div className="flex flex-col gap-1">
            <Label>시크릿 키</Label>
            <div className="break-all rounded-md border border-border bg-muted/30 p-2 text-xs font-mono select-all">
              {secret}
            </div>
          </div>
        ) : null}
      </div>

      <div className="flex flex-col gap-2">
        <Label>복구 코드</Label>
        <p className="text-xs text-muted-foreground">
          아래 복구 코드를 안전한 곳에 보관하세요. 인증 앱 분실 시 사용됩니다.
        </p>
        <div className="rounded-md border border-border bg-muted/30 p-2">
          <ul className="grid grid-cols-2 gap-1 text-xs font-mono">
            {enrollData.recoveryCodes.map((code, i) => (
              <li key={i}>{code}</li>
            ))}
          </ul>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={copyRecoveryCodes}>
          {copied ? '복사됨' : '복구 코드 복사'}
        </Button>
      </div>

      <div className="flex flex-col gap-1">
        <Label htmlFor="totp-code">TOTP 코드 입력</Label>
        <Input
          id="totp-code"
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          placeholder="6자리 코드"
          value={totpCode}
          onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        />
      </div>

      {error ? (
        <p role="alert" className="text-sm text-destructive">{error}</p>
      ) : null}

      <Button
        type="button"
        disabled={totpCode.length !== 6 || verifying}
        onClick={handleVerify}
      >
        {verifying ? '검증 중...' : '확인'}
      </Button>
    </div>
  );
}
