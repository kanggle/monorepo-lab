import { z } from 'zod';

export const AccountSearchSchema = z.object({
  email: z.string().email({ message: '올바른 이메일을 입력하세요.' }),
});
export type AccountSearchInput = z.infer<typeof AccountSearchSchema>;

export const ReasonSchema = z.object({
  reason: z.string().trim().min(3, { message: '사유는 3자 이상이어야 합니다.' }),
  ticketId: z.string().optional(),
});
export type ReasonInput = z.infer<typeof ReasonSchema>;
