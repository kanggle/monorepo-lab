import { NextResponse } from 'next/server';
import {
  getJobGradeById,
  updateJobGrade,
} from '@/features/erp-ops/api/erp-api';
import { UpdateJobGradeBodySchema } from '@/features/erp-ops/api/types';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp job-grade DETAIL read proxy (GET) + UPDATE proxy (POST →
 * upstream PATCH — TASK-PC-FE-048). E3 `?asOf=` threaded through on read.
 * Update carries an `Idempotency-Key`.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getJobGradeById(id, detailParams);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  let body: ReturnType<typeof UpdateJobGradeBodySchema.parse>;
  try {
    body = UpdateJobGradeBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid update-job-grade body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await updateJobGrade(id, input, idempotencyKey);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
