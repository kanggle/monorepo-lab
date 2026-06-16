'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  JobGradeListResponseSchema,
  type JobGradeListResponse,
  JobGradeSchema,
  type JobGrade,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateJobGradeInput,
  type UpdateJobGradeInput,
} from '../../api/types';
import { jobGradeDetailKey, jobGradesListKey } from '../../api/erp-keys';
import {
  clampSize,
  buildListQs,
  buildDetailQs,
  useThreadedAsOf,
  invalidateMaster,
} from '../use-erp-shared';

/**
 * erp-ops masters — job-grades hooks (TASK-PC-FE-107 split). List (producer
 * orders by displayOrder asc) + detail reads + create/update/retire mutations
 * (TASK-PC-FE-048). Behavior-preserving; re-exported through the masters barrel.
 */

// ---------------------------------------------------------------------------
// job-grades — list (producer orders by displayOrder asc) + detail
// ---------------------------------------------------------------------------

async function fetchJobGradesList(
  params: ErpListQueryParams,
): Promise<JobGradeListResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/job-grades?${buildListQs(params)}`,
  );
  return JobGradeListResponseSchema.parse(raw);
}

export function useJobGrades(
  paramsIn: ErpListQueryParams = {},
  initial?: JobGradeListResponse,
) {
  const asOf = useThreadedAsOf(paramsIn.asOf);
  const params: ErpListQueryParams = { ...paramsIn, asOf };
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.filters &&
    params.active === undefined;
  return useQuery({
    queryKey: jobGradesListKey(asOf, params.page ?? 0, clampSize(params.size)),
    queryFn: () => fetchJobGradesList(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 15_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

async function fetchJobGradeDetail(
  id: string,
  params: ErpDetailQueryParams,
): Promise<JobGrade> {
  const raw = await apiClient.get<unknown>(
    `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}${buildDetailQs(params)}`,
  );
  return JobGradeSchema.parse(raw);
}

export function useJobGrade(id: string | null, asOfExplicit?: string | null) {
  const asOf = useThreadedAsOf(asOfExplicit);
  return useQuery({
    queryKey: jobGradeDetailKey(id ?? '', asOf),
    queryFn: () => fetchJobGradeDetail(id as string, { asOf }),
    enabled: Boolean(id && id.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// ---------------------------------------------------------------------------
// job-grades — WRITE mutations (TASK-PC-FE-048).
// ---------------------------------------------------------------------------

export function useCreateJobGrade() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      input: CreateJobGradeInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        '/api/erp/masterdata/job-grades',
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return JobGradeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'job-grades'),
  });
}
export function useUpdateJobGrade() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      input: UpdateJobGradeInput;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/job-grades/${encodeURIComponent(args.id)}`,
        { ...args.input, idempotencyKey: args.idempotencyKey },
      );
      return JobGradeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'job-grades'),
  });
}
export function useRetireJobGrade() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: string;
      reason: string;
      idempotencyKey: string;
    }) => {
      const raw = await apiClient.post<unknown>(
        `/api/erp/masterdata/job-grades/${encodeURIComponent(args.id)}/retire`,
        { reason: args.reason, idempotencyKey: args.idempotencyKey },
      );
      return JobGradeSchema.parse(raw);
    },
    onSuccess: () => invalidateMaster(qc, 'job-grades'),
  });
}
