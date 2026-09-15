import { SAMPLE_TENANT_ID } from '../codes';

/**
 * Sample IAM product/tenant registry (`GET /api/admin/console/registry`) —
 * hand-authored synthetic data (ADR-MONO-074 A4: there is no extraction path
 * from any backend).
 *
 * AC-12 (implementer's choice, recorded in TASK-PC-FE-282): every product is
 * `available` and lists the single sample tenant, so every screen's registry
 * eligibility pre-flight passes — a sample visitor never sees a
 * "not permitted" screen. Permission-denied states are not reproduced in
 * sample mode.
 *
 * R2ⓐ: `displayName` is read by people → «(샘플)». `productKey`, `baseRoute`
 * and tenant ids are parsed → no suffix.
 */
export const SAMPLE_REGISTRY = {
  products: [
    {
      productKey: 'iam',
      displayName: 'IAM (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/iam',
    },
    {
      productKey: 'wms',
      displayName: 'WMS (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/wms',
    },
    {
      productKey: 'scm',
      displayName: 'SCM (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/scm',
    },
    {
      productKey: 'erp',
      displayName: 'ERP (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/erp',
    },
    {
      productKey: 'finance',
      displayName: 'Finance (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/finance',
    },
    {
      productKey: 'ecommerce',
      displayName: 'E-Commerce (샘플)',
      available: true,
      tenants: [SAMPLE_TENANT_ID],
      baseRoute: '/ecommerce',
    },
  ],
} as const;
