import { describe, it, expect } from 'vitest';
import {
  RegistryResponseSchema,
  RegistryProductSchema,
} from '@/shared/api/registry-types';

/**
 * Consumer-side contract test for the TASK-BE-304 `operatorContext`
 * extension on the IAM registry item shape (consumer obligation:
 * TASK-PC-FE-014 / `console-integration-contract.md § 2.2`).
 *
 * Asserts:
 *   - the optional `operatorContext.defaultAccountId` parses on the
 *     finance item when set (set-case happy path);
 *   - the field is fully optional: absent on any item parses (the v1
 *     producer omits `operatorContext` for unprovisioned operators +
 *     for the 4 non-finance items always — Jackson `@JsonInclude(NON_NULL)`);
 *   - `operatorContext` may appear on a non-finance item without
 *     breaking the parse (schema is shape-tolerant per the extensible
 *     carrier rule — future tasks may begin populating wms / scm / erp
 *     items without a consumer schema change).
 *
 * STRENGTHEN-ONLY: the existing `registry-contract.test.ts` (verbatim
 * producer 200 example, no `operatorContext`) continues to parse — this
 * file is additive coverage for the new optional field.
 */

const REGISTRY_WITH_FINANCE_DEFAULT_ACCOUNT_ID = {
  products: [
    {
      productKey: 'iam',
      displayName: 'IAM',
      available: true,
      tenants: ['wms'],
      baseRoute: '/iam',
    },
    {
      productKey: 'wms',
      displayName: 'WMS',
      available: true,
      tenants: ['wms'],
      baseRoute: '/wms',
    },
    {
      productKey: 'scm',
      displayName: 'SCM',
      available: true,
      tenants: ['scm'],
      baseRoute: '/scm',
    },
    {
      productKey: 'erp',
      displayName: 'ERP',
      available: true,
      tenants: ['erp'],
      baseRoute: '/erp',
    },
    {
      productKey: 'finance',
      displayName: 'Finance',
      available: true,
      tenants: ['finance'],
      baseRoute: '/finance',
      operatorContext: {
        defaultAccountId: '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
      },
    },
  ],
};

const REGISTRY_NO_OPERATOR_CONTEXT_ANYWHERE = {
  products: [
    {
      productKey: 'iam',
      displayName: 'IAM',
      available: true,
      tenants: ['wms'],
      baseRoute: '/iam',
    },
    {
      productKey: 'finance',
      displayName: 'Finance',
      available: false,
      tenants: [],
      baseRoute: '/finance',
    },
  ],
};

describe('registry operatorContext — finance default account id (TASK-PC-FE-014)', () => {
  it('parses operatorContext.defaultAccountId on the finance item (set-case)', () => {
    const parsed = RegistryResponseSchema.parse(
      REGISTRY_WITH_FINANCE_DEFAULT_ACCOUNT_ID,
    );
    const finance = parsed.products.find((p) => p.productKey === 'finance')!;
    expect(finance.operatorContext).toBeDefined();
    expect(finance.operatorContext?.defaultAccountId).toBe(
      '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    );
  });

  it('parses cleanly when operatorContext is omitted on every item (unprovisioned operator)', () => {
    const parsed = RegistryResponseSchema.parse(
      REGISTRY_NO_OPERATOR_CONTEXT_ANYWHERE,
    );
    for (const product of parsed.products) {
      expect(product.operatorContext).toBeUndefined();
    }
  });

  it('parses when operatorContext.defaultAccountId is absent (other future attributes only)', () => {
    // The carrier is extensible per `console-registry-api.md § Per-operator
    // profile attributes`: `operatorContext` may be present with attributes
    // we don't yet read. The parse MUST not throw on a present-but-empty
    // shape.
    const parsed = RegistryProductSchema.parse({
      productKey: 'finance',
      displayName: 'Finance',
      available: true,
      tenants: ['finance'],
      baseRoute: '/finance',
      operatorContext: {},
    });
    expect(parsed.operatorContext).toBeDefined();
    expect(parsed.operatorContext?.defaultAccountId).toBeUndefined();
  });

  it('parses when operatorContext appears on a non-finance item (extensibility, v1 producer never emits this but consumer must be tolerant)', () => {
    const parsed = RegistryProductSchema.parse({
      productKey: 'wms',
      displayName: 'WMS',
      available: true,
      tenants: ['wms'],
      baseRoute: '/wms',
      // v1 producer never emits this on wms; future-extension tolerance.
      operatorContext: { defaultAccountId: 'some-wms-attr' },
    });
    expect(parsed.operatorContext?.defaultAccountId).toBe('some-wms-attr');
  });

  it('rejects operatorContext.defaultAccountId of the wrong type (defensive)', () => {
    expect(() =>
      RegistryProductSchema.parse({
        productKey: 'finance',
        displayName: 'Finance',
        available: true,
        tenants: ['finance'],
        baseRoute: '/finance',
        operatorContext: { defaultAccountId: 12345 },
      }),
    ).toThrow();
  });
});
