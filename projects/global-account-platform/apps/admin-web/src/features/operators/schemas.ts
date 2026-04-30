import { z } from 'zod';
import { OperatorRoleSchema } from '@/shared/api/admin-api';

/**
 * Password policy: minimum 10 chars, must contain at least one letter, one digit,
 * and one special character (matching admin-api.md `/api/admin/operators` spec).
 */
const PASSWORD_POLICY = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,}$/;
const PASSWORD_MESSAGE = '최소 10자, 영문·숫자·특수문자 각 1자 이상 포함';

export const CreateOperatorFormSchema = z.object({
  email: z.string().email({ message: '올바른 이메일을 입력하세요.' }),
  displayName: z
    .string()
    .min(1, { message: '표시 이름을 입력하세요.' })
    .max(64, { message: '표시 이름은 최대 64자입니다.' }),
  password: z.string().regex(PASSWORD_POLICY, { message: PASSWORD_MESSAGE }),
  roles: z.array(OperatorRoleSchema),
});
export type CreateOperatorFormInput = z.infer<typeof CreateOperatorFormSchema>;

export const PatchRolesFormSchema = z.object({
  roles: z.array(OperatorRoleSchema),
});
export type PatchRolesFormInput = z.infer<typeof PatchRolesFormSchema>;

export const ChangeStatusFormSchema = z.object({
  reason: z.string().min(3, { message: '사유는 3자 이상이어야 합니다.' }),
});
export type ChangeStatusFormInput = z.infer<typeof ChangeStatusFormSchema>;
