import { z } from 'zod';

export const LoginSchema = z.object({
  operatorId: z.string().min(1, { message: '운영자 ID를 입력하세요.' }),
  password: z.string().min(8, { message: '비밀번호는 8자 이상이어야 합니다.' }),
  totpCode: z.string().optional(),
});
export type LoginInput = z.infer<typeof LoginSchema>;
