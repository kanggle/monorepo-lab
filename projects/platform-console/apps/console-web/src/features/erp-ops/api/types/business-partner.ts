import { z } from 'zod';
import { EffectivePeriodSchema, AuditSchema, ErpMetaSchema } from './common';

/**
 * erp masters — BusinessPartner types (TASK-PC-FE-109 split; CUSTOMER /
 * SUPPLIER / BOTH; confidential financial details — `paymentTerms`). Read
 * schemas + responses + create/update inputs / body schemas (TASK-PC-FE-048).
 * `paymentTerms` is surfaced to the operator UI but NEVER logged. Re-exported
 * via the `types` barrel. 0 behavior change.
 *
 *   GET /api/erp/masterdata/business-partners (?asOf=&active=&partnerType=&page=&size=)
 *   GET /api/erp/masterdata/business-partners/{id} (?asOf=)
 */

export const BusinessPartnerSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    partnerType: z.string(),
    // confidential — paymentTerms surfaced to the operator UI but
    // NEVER logged. Tolerant-parser passthrough — the inner shape
    // is producer-owned and may grow.
    paymentTerms: z.unknown().optional(),
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type BusinessPartner = z.infer<typeof BusinessPartnerSchema>;

export const BusinessPartnerListResponseSchema = z.object({
  data: z.array(BusinessPartnerSchema),
  meta: ErpMetaSchema,
});
export type BusinessPartnerListResponse = z.infer<
  typeof BusinessPartnerListResponseSchema
>;

export const BusinessPartnerDetailResponseSchema = z.object({
  data: BusinessPartnerSchema,
  meta: ErpMetaSchema,
});
export type BusinessPartnerDetailResponse = z.infer<
  typeof BusinessPartnerDetailResponseSchema
>;

// ---------------------------------------------------------------------------
// BusinessPartner WRITE inputs (TASK-PC-FE-048). retire is the shared
// `{ reason }` (ErpRetireBodySchema in ./common).
// ---------------------------------------------------------------------------

/** Optional payment-terms sub-object (BusinessPartner — confidential). */
const PaymentTermsSchema = z
  .object({
    termDays: z.number().int().nonnegative().optional(),
    method: z.string().optional(),
  })
  .passthrough();

export interface CreateBusinessPartnerInput {
  code: string;
  name: string;
  partnerType: string;
  paymentTerms?: { termDays?: number; method?: string } | null;
  effectiveFrom?: string;
}
export interface UpdateBusinessPartnerInput {
  name?: string;
  partnerType?: string;
  paymentTerms?: { termDays?: number; method?: string } | null;
  effectiveFrom?: string;
}
export const CreateBusinessPartnerBodySchema = z.object({
  code: z.string().min(1).max(64),
  name: z.string().min(1).max(256),
  partnerType: z.string().min(1),
  paymentTerms: PaymentTermsSchema.nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
export const UpdateBusinessPartnerBodySchema = z.object({
  name: z.string().min(1).max(256).optional(),
  partnerType: z.string().min(1).optional(),
  paymentTerms: PaymentTermsSchema.nullable().optional(),
  effectiveFrom: z.string().min(1).optional(),
  idempotencyKey: z.string().min(1),
});
