'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { Table, THead, TBody, TR, TH, TD } from '@/shared/ui/table';
import { Badge } from '@/shared/ui/badge';
import { useBulkLock } from '../hooks/useBulkLock';
import { useToast } from '@/shared/ui/toast';
import { messageForCode, ApiError } from '@/shared/api/errors';
import type { BulkLockResultItem } from '@/shared/api/admin-api';

const BulkLockFormSchema = z.object({
  accountIdsText: z.string().min(1, { message: '계정 ID를 입력하세요.' }),
  reason: z.string().min(8, { message: '사유는 8자 이상이어야 합니다.' }),
  ticketId: z.string().optional(),
});
type BulkLockFormInput = z.infer<typeof BulkLockFormSchema>;

interface BulkLockDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function parseAccountIds(text: string): string[] {
  return text
    .split(/[\n,]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

const OUTCOME_LABELS: Record<string, string> = {
  LOCKED: '잠금 완료',
  NOT_FOUND: '계정 없음',
  ALREADY_LOCKED: '이미 잠김',
  FAILURE: '실패',
};

export function BulkLockDialog({ open, onOpenChange }: BulkLockDialogProps) {
  const form = useForm<BulkLockFormInput>({
    resolver: zodResolver(BulkLockFormSchema),
    defaultValues: { accountIdsText: '', reason: '', ticketId: '' },
  });
  const bulkLock = useBulkLock();
  const toast = useToast();
  const [results, setResults] = useState<BulkLockResultItem[] | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  async function onSubmit(values: BulkLockFormInput) {
    setValidationError(null);
    const accountIds = parseAccountIds(values.accountIdsText);

    if (accountIds.length === 0) {
      setValidationError('유효한 계정 ID가 없습니다.');
      return;
    }
    if (accountIds.length > 100) {
      setValidationError(`최대 100개까지 가능합니다. (현재 ${accountIds.length}개)`);
      return;
    }

    try {
      const result = await bulkLock.mutateAsync({
        accountIds,
        reason: values.reason,
        ticketId: values.ticketId || undefined,
      });
      setResults(result.results);
      const locked = result.results.filter((r) => r.outcome === 'LOCKED').length;
      toast.show(`${locked}/${result.results.length}개 계정을 잠갔습니다.`, 'success');
    } catch (err) {
      const msg = err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  function handleClose(v: boolean) {
    if (!v) {
      form.reset();
      setResults(null);
      setValidationError(null);
    }
    onOpenChange(v);
  }

  return (
    <Dialog
      open={open}
      onOpenChange={handleClose}
      title="일괄 잠금"
      description="여러 계정을 한 번에 잠급니다. 계정 ID를 줄바꿈 또는 쉼표로 구분하세요. (최대 100개)"
    >
      {results ? (
        <div className="flex flex-col gap-3">
          <Table>
            <THead>
              <TR>
                <TH>계정 ID</TH>
                <TH>결과</TH>
                <TH>오류</TH>
              </TR>
            </THead>
            <TBody>
              {results.map((r) => (
                <TR key={r.accountId}>
                  <TD className="font-mono text-xs">{r.accountId}</TD>
                  <TD>
                    <Badge>{OUTCOME_LABELS[r.outcome] ?? r.outcome}</Badge>
                  </TD>
                  <TD className="text-xs text-muted-foreground">{r.error ?? '-'}</TD>
                </TR>
              ))}
            </TBody>
          </Table>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleClose(false)}>
              닫기
            </Button>
          </DialogFooter>
        </div>
      ) : (
        <form aria-label="bulk-lock-form" onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3" noValidate>
          <div className="flex flex-col gap-1">
            <Label htmlFor="bulk-account-ids">계정 ID 목록 (필수)</Label>
            <textarea
              id="bulk-account-ids"
              className="min-h-[100px] w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              placeholder="계정 ID를 줄바꿈 또는 쉼표로 구분"
              {...form.register('accountIdsText')}
            />
            {form.formState.errors.accountIdsText ? (
              <p role="alert" className="text-xs text-destructive">
                {form.formState.errors.accountIdsText.message}
              </p>
            ) : null}
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="bulk-reason">사유 (필수, 8자 이상)</Label>
            <Input id="bulk-reason" {...form.register('reason')} />
            {form.formState.errors.reason ? (
              <p role="alert" className="text-xs text-destructive">
                {form.formState.errors.reason.message}
              </p>
            ) : null}
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="bulk-ticket">티켓 번호 (선택)</Label>
            <Input id="bulk-ticket" {...form.register('ticketId')} />
          </div>
          {validationError ? (
            <p role="alert" className="text-sm text-destructive">
              {validationError}
            </p>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleClose(false)}>
              취소
            </Button>
            <Button type="submit" variant="default" disabled={bulkLock.isPending}>
              {bulkLock.isPending ? '처리 중...' : '일괄 잠금'}
            </Button>
          </DialogFooter>
        </form>
      )}
    </Dialog>
  );
}
