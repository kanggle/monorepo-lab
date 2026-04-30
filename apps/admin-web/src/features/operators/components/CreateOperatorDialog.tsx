'use client';

import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { useToast } from '@/shared/ui/toast';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { OperatorRole } from '@/shared/api/admin-api';
import { CreateOperatorFormSchema, type CreateOperatorFormInput } from '../schemas';
import { useCreateOperator } from '../hooks/useCreateOperator';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const ALL_ROLES: OperatorRole[] = [
  'SUPER_ADMIN',
  'SUPPORT_READONLY',
  'SUPPORT_LOCK',
  'SECURITY_ANALYST',
];

/**
 * HTTP headers must be ByteString-safe (ASCII/Latin-1). Korean UI copy would
 * fail strict jsdom/undici Header validation, so we keep the reason ASCII
 * for the `X-Operator-Reason` header.
 */
const CREATE_REASON = 'operator.create';

const EMAIL_CONFLICT_MESSAGE = '이미 사용 중인 이메일입니다.';

export function CreateOperatorDialog({ open, onOpenChange }: Props) {
  const form = useForm<CreateOperatorFormInput>({
    resolver: zodResolver(CreateOperatorFormSchema),
    defaultValues: { email: '', displayName: '', password: '', roles: [] },
  });
  const createOperator = useCreateOperator();
  const toast = useToast();

  async function onSubmit(values: CreateOperatorFormInput) {
    try {
      await createOperator.mutateAsync({
        email: values.email,
        displayName: values.displayName,
        password: values.password,
        roles: values.roles,
        reason: CREATE_REASON,
      });
      toast.show('운영자를 생성했습니다.', 'success');
      form.reset();
      onOpenChange(false);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'OPERATOR_EMAIL_CONFLICT') {
        toast.show(EMAIL_CONFLICT_MESSAGE, 'error');
        return;
      }
      const msg =
        err instanceof ApiError ? messageForCode(err.code, err.message) : '작업에 실패했습니다.';
      toast.show(msg, 'error');
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="운영자 추가"
      description="신규 운영자 계정 정보와 초기 역할을 입력하세요."
    >
      <form
        aria-label="create-operator-form"
        onSubmit={form.handleSubmit(onSubmit)}
        className="flex flex-col gap-3"
        noValidate
      >
        <div className="flex flex-col gap-1">
          <Label htmlFor="op-email">이메일</Label>
          <Input id="op-email" type="email" autoComplete="off" {...form.register('email')} />
          {form.formState.errors.email ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.email.message}
            </p>
          ) : null}
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="op-displayName">표시 이름</Label>
          <Input id="op-displayName" {...form.register('displayName')} />
          {form.formState.errors.displayName ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.displayName.message}
            </p>
          ) : null}
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="op-password">초기 비밀번호</Label>
          <Input
            id="op-password"
            type="password"
            autoComplete="new-password"
            {...form.register('password')}
          />
          {form.formState.errors.password ? (
            <p role="alert" className="text-xs text-destructive">
              {form.formState.errors.password.message}
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">
              최소 10자, 영문·숫자·특수문자 각 1자 이상 포함
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <span className="text-sm font-medium text-foreground">역할 (선택 가능)</span>
          <Controller
            control={form.control}
            name="roles"
            render={({ field }) => (
              <div className="flex flex-col gap-1">
                {ALL_ROLES.map((role) => {
                  const checked = field.value.includes(role);
                  return (
                    <label key={role} className="inline-flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        value={role}
                        checked={checked}
                        onChange={(e) => {
                          const next = e.target.checked
                            ? [...field.value, role]
                            : field.value.filter((r) => r !== role);
                          field.onChange(next);
                        }}
                      />
                      <span>{role}</span>
                    </label>
                  );
                })}
              </div>
            )}
          />
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button type="submit" variant="default" disabled={createOperator.isPending}>
            {createOperator.isPending ? '생성 중...' : '생성'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
