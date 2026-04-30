'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { ReasonSchema, type ReasonInput } from '../schemas';
import { useUnlockAccount } from '../hooks/useLockAccount';
import { useToast } from '@/shared/ui/toast';
import { messageForCode, ApiError } from '@/shared/api/errors';

interface UnlockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  accountId: string;
}

export function UnlockDialog({ open, onOpenChange, accountId }: UnlockDialogProps) {
  const form = useForm<ReasonInput>({ resolver: zodResolver(ReasonSchema), defaultValues: { reason: '', ticketId: '' } });
  const unlock = useUnlockAccount();
  const toast = useToast();

  async function onSubmit(values: ReasonInput) {
    try {
      await unlock.mutateAsync({ accountId, reason: values.reason, ticketId: values.ticketId });
      toast.show('계정 잠금을 해제했습니다.', 'success');
      form.reset();
      onOpenChange(false);
    } catch (err) {
      const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="계정 잠금 해제" description="해제 사유는 감사 로그에 기록됩니다.">
      <form aria-label="unlock-form" onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3" noValidate>
        <div className="flex flex-col gap-1">
          <Label htmlFor="unlock-reason">사유 (필수)</Label>
          <Input id="unlock-reason" {...form.register('reason')} />
          {form.formState.errors.reason ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.reason.message}
            </p>
          ) : null}
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="unlock-ticket">티켓 번호 (선택)</Label>
          <Input id="unlock-ticket" {...form.register('ticketId')} />
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>취소</Button>
          <Button type="submit" disabled={unlock.isPending}>{unlock.isPending ? '처리 중...' : '해제'}</Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
