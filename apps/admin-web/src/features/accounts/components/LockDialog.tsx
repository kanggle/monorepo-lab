'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { ReasonSchema, type ReasonInput } from '../schemas';
import { useLockAccount } from '../hooks/useLockAccount';
import { useToast } from '@/shared/ui/toast';
import { messageForCode, ApiError } from '@/shared/api/errors';

interface LockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  accountId: string;
}

export function LockDialog({ open, onOpenChange, accountId }: LockDialogProps) {
  const form = useForm<ReasonInput>({
    resolver: zodResolver(ReasonSchema),
    defaultValues: { reason: '', ticketId: '' },
  });
  const lock = useLockAccount();
  const toast = useToast();

  async function onSubmit(values: ReasonInput) {
    try {
      await lock.mutateAsync({ accountId, reason: values.reason, ticketId: values.ticketId });
      toast.show('계정을 잠갔습니다.', 'success');
      form.reset();
      onOpenChange(false);
    } catch (err) {
      const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="계정 잠금"
      description="잠금 사유는 감사 로그에 기록됩니다."
    >
      <form aria-label="lock-form" onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3" noValidate>
        <div className="flex flex-col gap-1">
          <Label htmlFor="lock-reason">사유 (필수)</Label>
          <Input id="lock-reason" {...form.register('reason')} />
          {form.formState.errors.reason ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.reason.message}
            </p>
          ) : null}
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="lock-ticket">티켓 번호 (선택)</Label>
          <Input id="lock-ticket" {...form.register('ticketId')} />
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button type="submit" variant="default" disabled={lock.isPending}>
            {lock.isPending ? '처리 중...' : '잠금'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
