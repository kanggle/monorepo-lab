'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { useToast } from '@/shared/ui/toast';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { Operator, OperatorStatus } from '@/shared/api/admin-api';
import { ChangeStatusFormSchema, type ChangeStatusFormInput } from '../schemas';
import { usePatchOperatorStatus } from '../hooks/usePatchOperatorStatus';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  operator: Operator;
  /** Target status after confirmation. */
  nextStatus: OperatorStatus;
}

/**
 * HTTP headers must be ByteString-safe (ASCII/Latin-1). The user-entered
 * reason may contain Korean text, which would fail strict jsdom/undici Header
 * validation. We therefore send a fixed ASCII constant as the
 * `X-Operator-Reason` header (matches CreateOperatorDialog pattern). The
 * user's free-text reason is still required by the form for UX, but the
 * PATCH /api/admin/operators/{id}/status contract (see
 * specs/contracts/http/admin-api.md) only carries `{ status }` in the body.
 */
const CHANGE_STATUS_REASON = 'operator.status.change';

export function ChangeStatusDialog({ open, onOpenChange, operator, nextStatus }: Props) {
  const form = useForm<ChangeStatusFormInput>({
    resolver: zodResolver(ChangeStatusFormSchema),
    defaultValues: { reason: '' },
  });
  const patchStatus = usePatchOperatorStatus();
  const toast = useToast();

  const actionLabel = nextStatus === 'SUSPENDED' ? '정지' : '활성화';

  async function onSubmit(_values: ChangeStatusFormInput) {
    try {
      await patchStatus.mutateAsync({
        operatorId: operator.operatorId,
        status: nextStatus,
        reason: CHANGE_STATUS_REASON,
      });
      toast.show(`운영자를 ${actionLabel}했습니다.`, 'success');
      form.reset();
      onOpenChange(false);
    } catch (err) {
      const msg =
        err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={`운영자 ${actionLabel}`}
      description={`${operator.email} 의 상태를 ${nextStatus} 로 변경합니다. 사유는 감사 로그에 기록됩니다.`}
    >
      <form
        aria-label="change-status-form"
        onSubmit={form.handleSubmit(onSubmit)}
        className="flex flex-col gap-3"
        noValidate
      >
        <div className="flex flex-col gap-1">
          <Label htmlFor="status-reason">사유 (필수)</Label>
          <Input id="status-reason" {...form.register('reason')} />
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
          <Button type="submit" variant="default" disabled={patchStatus.isPending}>
            {patchStatus.isPending ? '처리 중...' : actionLabel}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
