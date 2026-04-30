'use client';

import { useEffect } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Dialog, DialogFooter } from '@/shared/ui/dialog';
import { Button } from '@/shared/ui/button';
import { useToast } from '@/shared/ui/toast';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { Operator, OperatorRole } from '@/shared/api/admin-api';
import { PatchRolesFormSchema, type PatchRolesFormInput } from '../schemas';
import { usePatchOperatorRoles } from '../hooks/usePatchOperatorRoles';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  operator: Operator;
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
 * for the `X-Operator-Reason` header (matches CreateOperatorDialog pattern).
 */
const EDIT_ROLES_REASON = 'operator.roles.change';

export function EditRolesDialog({ open, onOpenChange, operator }: Props) {
  const form = useForm<PatchRolesFormInput>({
    resolver: zodResolver(PatchRolesFormSchema),
    defaultValues: { roles: operator.roles },
  });
  const patchRoles = usePatchOperatorRoles();
  const toast = useToast();

  // Sync form defaults when the operator prop changes (e.g. reopening for a different row).
  useEffect(() => {
    if (open) {
      form.reset({ roles: operator.roles });
    }
  }, [open, operator.operatorId, operator.roles, form]);

  async function onSubmit(values: PatchRolesFormInput) {
    try {
      await patchRoles.mutateAsync({
        operatorId: operator.operatorId,
        roles: values.roles,
        reason: EDIT_ROLES_REASON,
      });
      toast.show('역할을 변경했습니다.', 'success');
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
      title="역할 변경"
      description={`${operator.email} 의 역할 목록을 선택한 값으로 전체 교체합니다.`}
    >
      <form
        aria-label="edit-roles-form"
        onSubmit={form.handleSubmit(onSubmit)}
        className="flex flex-col gap-3"
        noValidate
      >
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

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button type="submit" variant="default" disabled={patchRoles.isPending}>
            {patchRoles.isPending ? '저장 중...' : '저장'}
          </Button>
        </DialogFooter>
      </form>
    </Dialog>
  );
}
