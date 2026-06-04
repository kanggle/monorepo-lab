import { NextResponse } from 'next/server';
import { listJobGrades, createJobGrade } from '@/features/erp-ops/api/erp-api';
import { CreateJobGradeBodySchema } from '@/features/erp-ops/api/types';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp job-grades LIST read proxy (GET) + CREATE proxy (POST —
 * TASK-PC-FE-048). Producer orders the list by `displayOrder` asc (forwarded
 * verbatim). Create carries an `Idempotency-Key`.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listJobGrades(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body: ReturnType<typeof CreateJobGradeBodySchema.parse>;
  try {
    body = CreateJobGradeBodySchema.parse(await req.json());
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'invalid create-job-grade body' },
      { status: 400 },
    );
  }
  try {
    const { idempotencyKey, ...input } = body;
    const result = await createJobGrade(input, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
