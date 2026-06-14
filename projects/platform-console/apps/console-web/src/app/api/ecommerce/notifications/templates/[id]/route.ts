import { NextResponse } from 'next/server';
import {
  getTemplate,
  updateTemplate,
} from '@/features/ecommerce-ops/api/notifications-api';
import {
  UpdateTemplateBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce notification template [id] proxy
 * (TASK-PC-FE-089 — ADR-031 Phase 5b):
 *   GET /api/ecommerce/notifications/templates/{id} → detail (full incl. body)
 *   PUT /api/ecommerce/notifications/templates/{id} → update (subject+body only)
 *
 * NO DELETE (producer defines none — § 2.4.10.4).
 * type/channel are IMMUTABLE after creation — the update body NEVER includes them.
 * Domain-facing IAM OIDC token server-side; Zod body validation; NO Idempotency-Key.
 */

export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getTemplate(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function PUT(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(UpdateTemplateBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await updateTemplate(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
