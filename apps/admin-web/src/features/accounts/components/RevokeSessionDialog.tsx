'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { ReasonSchema, type ReasonInput } from '../schemas';
import { useRevokeSession } from '../hooks/useRevokeSession';
import { useToast } from '@/shared/ui/toast';
import { messageForCode, ApiError } from '@/shared/api/errors';

interface RevokeSessionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  accountId: string;
}

export function RevokeSessionDialog({ open, onOpenChange, accountId }: RevokeSessionDialogProps) {
  const form = useForm<ReasonInput>({
    resolver: zodResolver(ReasonSchema),
    defaultValues: { reason: '' },
  });
  const revoke = useRevokeSession();
  const toast = useToast();
  const [revokedCount, setRevokedCount] = useState<number | null>(null);

  async function onSubmit(values: ReasonInput) {
    try {
      const result = await revoke.mutateAsync({ accountId, reason: values.reason });
      setRevokedCount(result.revokedSessionCount);
      const countMsg = result.revokedSessionCount > 0
        ? `refresh token ${result.revokedSessionCount}개 만료, `
        : '';
      toast.show(`세션 강제 종료 완료 — ${countMsg}현재 접속 즉시 차단됨`, 'success');
      form.reset();
      onOpenChange(false);
      setRevokedCount(null);
    } catch (err) {
      const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="세션 강제 종료"
      description="해당 계정의 모든 세션을 즉시 차단합니다. 현재 유효한 access token도 즉시 무효화됩니다. 사유는 감사 로그에 기록됩니다."
    >
      <form aria-label="revoke-session-form" onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3" noValidate>
        <div className="flex flex-col gap-1">
          <Label htmlFor="revoke-reason">사유 (필수)</Label>
          <Input id="revoke-reason" {...form.register('reason')} />
          {form.formState.errors.reason ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.reason.message}
            </p>
          ) : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button type="submit" variant="default" disabled={revoke.isPending}>
            {revoke.isPending ? '처리 중...' : '세션 강제 종료'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
