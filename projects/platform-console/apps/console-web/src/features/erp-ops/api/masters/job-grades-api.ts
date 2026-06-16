import {
  JobGradeListResponseSchema,
  type JobGradeListResponse,
  JobGradeDetailResponseSchema,
  type JobGrade,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type CreateJobGradeInput,
  type UpdateJobGradeInput,
} from '../types';
import { callErp } from '../erp-client';
import { listQs, detailQs, compact } from './masters-qs';

/**
 * erp masters — job-grades api (TASK-PC-FE-108 split). List (producer orders
 * by displayOrder asc) + detail reads + create/update/retire mutations
 * (TASK-PC-FE-048). Behavior-preserving; re-exported through the barrel.
 */

// ---------------------------------------------------------------------------
// job-grades — list (producer orders by displayOrder asc) + detail
//   GET /api/erp/masterdata/job-grades
//   GET /api/erp/masterdata/job-grades/{id}
// ---------------------------------------------------------------------------

export async function listJobGrades(
  params: ErpListQueryParams = {},
): Promise<JobGradeListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades?${listQs(params)}`,
      logPath: '/api/erp/masterdata/job-grades',
    },
    (json) => JobGradeListResponseSchema.parse(json),
  );
}

export async function getJobGradeById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/job-grades/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return JobGradeDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// job-grades — WRITE (TASK-PC-FE-048).
// ---------------------------------------------------------------------------

function parseJobGradeData(json: unknown): JobGrade {
  const env = (json ?? {}) as { data?: unknown };
  return JobGradeDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

export async function createJobGrade(
  input: CreateJobGradeInput,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: '/api/erp/masterdata/job-grades',
      logPath: '/api/erp/masterdata/job-grades',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseJobGradeData,
  );
}
export async function updateJobGrade(
  id: string,
  input: UpdateJobGradeInput,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/job-grades/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseJobGradeData,
  );
}
export async function retireJobGrade(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/job-grades/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseJobGradeData,
  );
}
