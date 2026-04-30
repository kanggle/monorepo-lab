'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter, useSearchParams } from 'next/navigation';
import { LoginSchema, type LoginInput } from '../schemas';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { apiClient } from '@/shared/api/client';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { TotpEnrollment } from './TotpEnrollment';

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const initialErrorCode = params?.get('error') ?? null;
  const [submitError, setSubmitError] = useState<string | null>(
    initialErrorCode ? messageForCode(initialErrorCode) : null,
  );
  const [enrollmentToken, setEnrollmentToken] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<LoginInput | null>(null);
  const [needsTotp, setNeedsTotp] = useState(false);

  const form = useForm<LoginInput>({
    resolver: zodResolver(LoginSchema),
    defaultValues: { operatorId: '', password: '', totpCode: '' },
    mode: 'onBlur',
  });

  async function onSubmit(values: LoginInput) {
    setSubmitError(null);
    const body: Record<string, string> = {
      operatorId: values.operatorId,
      password: values.password,
    };
    if (values.totpCode && values.totpCode.length === 6) {
      body.totpCode = values.totpCode;
    }
    try {
      await apiClient.post('/api/auth/login', body, { skipAuthRetry: true });
      const redirect = params?.get('redirect') ?? '/accounts';
      router.push(redirect);
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === 'ENROLLMENT_REQUIRED' && err.extra.bootstrapToken) {
          setCredentials(values);
          setEnrollmentToken(err.extra.bootstrapToken as string);
          return;
        }
        if (err.code === 'BAD_REQUEST' && !needsTotp) {
          setNeedsTotp(true);
          return;
        }
        setSubmitError(messageForCode(err.code, err.message));
      } else {
        setSubmitError('네트워크 오류가 발생했습니다.');
      }
    }
  }

  async function handleEnrollmentComplete() {
    if (!credentials) return;
    setEnrollmentToken(null);
    setCredentials(null);
    setNeedsTotp(true);
    setSubmitError('2FA 등록이 완료되었습니다. TOTP 코드와 함께 다시 로그인해주세요.');
  }

  if (enrollmentToken) {
    return (
      <TotpEnrollment
        bootstrapToken={enrollmentToken}
        onComplete={handleEnrollmentComplete}
      />
    );
  }

  return (
    <form
      aria-label="operator-login"
      onSubmit={form.handleSubmit(onSubmit)}
      className="flex w-full max-w-sm flex-col gap-4"
      noValidate
    >
      <div className="flex flex-col gap-1">
        <Label htmlFor="operatorId">운영자 ID</Label>
        <Input id="operatorId" type="text" autoComplete="username" {...form.register('operatorId')} />
        {form.formState.errors.operatorId ? (
          <p role="alert" className="text-xs text-destructive">
            {form.formState.errors.operatorId.message}
          </p>
        ) : null}
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="password">비밀번호</Label>
        <Input id="password" type="password" autoComplete="current-password" {...form.register('password')} />
        {form.formState.errors.password ? (
          <p role="alert" className="text-xs text-destructive">
            {form.formState.errors.password.message}
          </p>
        ) : null}
      </div>
      {needsTotp ? (
        <div className="flex flex-col gap-1">
          <Label htmlFor="totpCode">TOTP 코드 (6자리)</Label>
          <Input
            id="totpCode"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="인증 앱의 6자리 코드"
            {...form.register('totpCode')}
          />
        </div>
      ) : null}
      {submitError ? (
        <p role="alert" className="text-sm text-destructive">
          {submitError}
        </p>
      ) : null}
      <Button type="submit" disabled={form.formState.isSubmitting}>
        {form.formState.isSubmitting ? '로그인 중...' : '로그인'}
      </Button>
    </form>
  );
}
