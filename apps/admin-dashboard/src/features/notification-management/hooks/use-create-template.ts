import { createTemplate } from '../api/notification-api';
import { templateKeys } from './query-keys';
import { alertError } from '@/shared/lib/alert-error';
import { useInvalidatingMutation } from '@/shared/hooks';
import { isApiError } from '@repo/types/guards';

const DUPLICATE_MESSAGE = '동일한 유형/채널 조합의 템플릿이 이미 존재합니다';

export function useCreateTemplate() {
  return useInvalidatingMutation({
    mutationFn: createTemplate,
    invalidate: [templateKeys.all],
    onError: (error: unknown) => {
      if (isApiError(error) && error.code === 'TEMPLATE_ALREADY_EXISTS') {
        alertError({ ...error, message: DUPLICATE_MESSAGE }, DUPLICATE_MESSAGE);
        return;
      }
      alertError(error, '알림 템플릿 생성에 실패했습니다.');
    },
  });
}
