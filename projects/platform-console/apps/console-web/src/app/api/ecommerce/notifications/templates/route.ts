import { NextResponse } from 'next/server';
import {
  listTemplates,
  createTemplate,
} from '@/features/ecommerce-ops/api/notifications-api';
import {
  CreateTemplateBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce notifications templates proxy — list + create
 * (TASK-PC-FE-089 — ADR-031 Phase 5b):
 *   GET  /api/ecommerce/notifications/templates?page=&size= → list
 *   POST /api/ecommerce/notifications/templates             → create (201)
 *
 * Domain-facing IAM OIDC token attached server-side (NOT the operator token —
 * § 2.4.10.4); NO X-Tenant-Id; NO Idempotency-Key. Body Zod-validated before
 * reaching the upstream (defence-in-depth). mapEcommerceError imported from
 * the products _proxy (same EcommerceUnavailableError class, same mapper).
 * Base URL: ECOMMERCE_PUBLIC_BASE_URL + /notifications/templates (non-admin path).
 */

export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const page = Number(searchParams.get('page') ?? 0);
  const size = Number(searchParams.get('size') ?? 20);

  try {
    const result = await listTemplates({ page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();

  let body;
  try {
    body = tryParse(CreateTemplateBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await createTemplate(body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
