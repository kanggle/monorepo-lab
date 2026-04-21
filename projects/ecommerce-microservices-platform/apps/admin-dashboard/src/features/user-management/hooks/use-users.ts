import { useQuery } from '@tanstack/react-query';
import { getUsers } from '../api/user-api';
import { useListParams } from '@/shared/hooks';
import { toValidStatus } from '@/shared/lib/to-valid-status';
import { VALID_USER_STATUSES } from '@/shared/lib/status-options';
import { userKeys } from './query-keys';

export function useUsers() {
  const { page, getParam, setFilter, buildPagination } = useListParams();

  const status = toValidStatus(getParam('status'), VALID_USER_STATUSES);
  const email = getParam('email') || undefined;

  const query = useQuery({
    queryKey: userKeys.list({ page, status, email }),
    queryFn: () => getUsers({ page, status, email }),
  });

  return {
    ...query,
    pagination: buildPagination(query.data),
    filters: { status, email, setFilter },
  };
}
