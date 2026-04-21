import { useMutation, useQueryClient, type QueryKey, type UseMutationOptions } from '@tanstack/react-query';
import { alertError } from '@/shared/lib/alert-error';

type InvalidateKeys<TVariables> = QueryKey[] | ((variables: TVariables) => QueryKey[]);

interface Options<TData, TVariables, TError> {
  mutationFn: (variables: TVariables) => Promise<TData>;
  invalidate: InvalidateKeys<TVariables>;
  errorMessage?: string;
  onError?: (error: TError) => void;
  onSuccess?: UseMutationOptions<TData, TError, TVariables>['onSuccess'];
}

export function useInvalidatingMutation<TData, TVariables = void, TError = Error>({
  mutationFn,
  invalidate,
  errorMessage,
  onError,
  onSuccess,
}: Options<TData, TVariables, TError>) {
  const queryClient = useQueryClient();

  return useMutation<TData, TError, TVariables>({
    mutationFn,
    onSuccess: (...args) => {
      const variables = args[1];
      const keys = typeof invalidate === 'function' ? invalidate(variables) : invalidate;
      for (const key of keys) {
        queryClient.invalidateQueries({ queryKey: key });
      }
      onSuccess?.(...args);
    },
    onError: onError ?? (errorMessage ? (error: TError) => alertError(error, errorMessage) : undefined),
  });
}
