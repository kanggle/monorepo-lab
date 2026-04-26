import { useQuery } from '@tanstack/react-query';
import { getTemplates } from '../api/notification-api';
import { useListParams } from '@/shared/hooks';
import { templateKeys } from './query-keys';

export function useTemplates() {
  const { page, buildPagination } = useListParams();

  const query = useQuery({
    queryKey: templateKeys.list({ page }),
    queryFn: () => getTemplates({ page }),
  });

  return {
    ...query,
    pagination: buildPagination(query.data),
  };
}
