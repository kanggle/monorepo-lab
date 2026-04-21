import { useQuery } from '@tanstack/react-query';
import { getTemplate } from '../api/notification-api';
import { templateKeys } from './query-keys';

export function useTemplate(templateId: string) {
  return useQuery({
    queryKey: templateKeys.detail(templateId),
    queryFn: () => getTemplate(templateId),
    enabled: !!templateId,
  });
}
