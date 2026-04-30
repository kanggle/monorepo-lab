'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { useToast } from '@/shared/ui/toast';
import { ApiError } from '@/shared/api/errors';
import { useChangePassword } from '../hooks/useChangePassword';

const ChangePasswordFormSchema = z
  .object({
    currentPassword: z.string().min(1, '현재 비밀번호를 입력하세요'),
    newPassword: z
      .string()
      .min(8, '최소 8자 이상 입력하세요')
      .max(128, '최대 128자까지 입력 가능합니다')
      .refine((v) => {
        let categories = 0;
        if (/[A-Z]/.test(v)) categories++;
        if (/[a-z]/.test(v)) categories++;
        if (/[0-9]/.test(v)) categories++;
        if (/[^A-Za-z0-9]/.test(v)) categories++;
        return categories >= 3;
      }, '대문자·소문자·숫자·특수문자 중 3종 이상 포함하세요'),
    confirmPassword: z.string().min(1, '비밀번호 확인을 입력하세요'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: '비밀번호가 일치하지 않습니다',
    path: ['confirmPassword'],
  });

type ChangePasswordFormInput = z.infer<typeof ChangePasswordFormSchema>;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ChangePasswordDialog({ open, onOpenChange }: Props) {
  const form = useForm<ChangePasswordFormInput>({
    resolver: zodResolver(ChangePasswordFormSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });
  const changePassword = useChangePassword();
  const toast = useToast();

  async function onSubmit(values: ChangePasswordFormInput) {
    try {
      await changePassword.mutateAsync({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      toast.show('비밀번호가 변경되었습니다.', 'success');
      form.reset();
      onOpenChange(false);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'CURRENT_PASSWORD_MISMATCH') {
        toast.show('현재 비밀번호가 올바르지 않습니다.', 'error');
        return;
      }
      if (err instanceof ApiError && err.status >= 500) {
        toast.show('서버 오류가 발생했습니다.', 'error');
        return;
      }
      const msg = err instanceof ApiError ? err.message : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o) form.reset();
        onOpenChange(o);
      }}
      title="비밀번호 변경"
      description="현재 비밀번호를 확인 후 새 비밀번호로 변경합니다."
    >
      <form
        aria-label="change-password-form"
        onSubmit={form.handleSubmit(onSubmit)}
        className="flex flex-col gap-3"
        noValidate
      >
        <div className="flex flex-col gap-1">
          <Label htmlFor="cp-current">현재 비밀번호</Label>
          <Input
            id="cp-current"
            type="password"
            autoComplete="current-password"
            {...form.register('currentPassword')}
          />
          {form.formState.errors.currentPassword && (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.currentPassword.message}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="cp-new">새 비밀번호</Label>
          <Input
            id="cp-new"
            type="password"
            autoComplete="new-password"
            {...form.register('newPassword')}
          />
          {form.formState.errors.newPassword ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.newPassword.message}
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">
              8자 이상, 대·소·숫·특 문자 중 3종 이상
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="cp-confirm">비밀번호 확인</Label>
          <Input
            id="cp-confirm"
            type="password"
            autoComplete="new-password"
            {...form.register('confirmPassword')}
          />
          {form.formState.errors.confirmPassword && (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.confirmPassword.message}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button type="submit" variant="default" disabled={changePassword.isPending}>
            {changePassword.isPending ? '변경 중...' : '변경'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
