'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { ReasonSchema, type ReasonInput } from '../schemas';
import { useGdprDelete } from '../hooks/useGdprDelete';
import { useToast } from '@/shared/ui/toast';
import { messageForCode, ApiError } from '@/shared/api/errors';

interface GdprDeleteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  accountId: string;
}

export function GdprDeleteDialog({ open, onOpenChange, accountId }: GdprDeleteDialogProps) {
  const router = useRouter();
  const form = useForm<ReasonInput>({
    resolver: zodResolver(ReasonSchema),
    defaultValues: { reason: '', ticketId: '' },
  });
  const gdprDelete = useGdprDelete();
  const toast = useToast();

  async function onSubmit(values: ReasonInput) {
    try {
      await gdprDelete.mutateAsync({ accountId, reason: values.reason, ticketId: values.ticketId });
      toast.show('계정이 삭제(마스킹)되었습니다.', 'success');
      form.reset();
      onOpenChange(false);
      router.push('/accounts');
    } catch (err) {
      const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="GDPR 삭제"
      description="계정 상태가 DELETED로 전환되고 개인정보가 즉시 마스킹됩니다. 이 작업은 되돌릴 수 없습니다."
    >
      <form aria-label="gdpr-delete-form" onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3" noValidate>
        <div className="flex flex-col gap-1">
          <Label htmlFor="gdpr-reason">사유 (필수)</Label>
          <Input id="gdpr-reason" {...form.register('reason')} />
          {form.formState.errors.reason ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.reason.message}
            </p>
          ) : null}
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="gdpr-ticket">티켓 번호 (선택)</Label>
          <Input id="gdpr-ticket" {...form.register('ticketId')} />
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button type="submit" variant="default" disabled={gdprDelete.isPending}>
            {gdprDelete.isPending ? '처리 중...' : '영구 삭제'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
