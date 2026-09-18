import { SAMPLE_LABEL_SUFFIX } from './codes';

/**
 * R2ⓐ «(샘플)» labelling rule — the predicate, shared by the guard test and by
 * the domain fixture tickets (TASK-PC-FE-283…288).
 *
 * A fixture string is classified by the KEY it sits under:
 *
 *   - human-readable (names, titles, descriptions, memos, notes, warnings)
 *     → MUST end with {@link SAMPLE_LABEL_SUFFIX};
 *   - machine-read (ids, codes, enums, dates, amounts, routes, currency)
 *     → MUST NOT contain «(샘플)» — parsers and `StatusBadge` interpret them,
 *       and a suffix would break the screen (ADR-MONO-074 R2ⓐ);
 *   - any other string key → a violation of its own: the key must be
 *     classified before a fixture may use it. 🔴 An unknown key failing is the
 *     point — "unclassified passes" would let a new human-readable field ship
 *     without the suffix and nobody would notice.
 *
 * Strings inside arrays inherit the key of the array.
 */

const HUMAN_READABLE_KEYS = new Set([
  'displayName',
  'name',
  'title',
  'body',
  'description',
  'memo',
  'note',
  'warning',
  'label',
  // TASK-PC-FE-283 — the IAM accounts/operators surfaces have NO OTHER
  // human-readable field (`AccountSummarySchema` is `{id,email,status,
  // createdAt}` — no name/title/description). `email` is both the value an
  // operator reads AND the value shown as the table's own record; classifying
  // it human-readable is what lets AC-7's synthetic-identity requirement and
  // AC-6's literal «(샘플)»-string-on-`/accounts` requirement both land on
  // the SAME field, deliberately, rather than inventing a display-only field
  // the real screen never has. `AccountSummarySchema`/`OperatorSummarySchema`
  // model `email` as `z.string()` (no format constraint), so a suffixed value
  // parses; nothing in the app re-validates it as an RFC address.
  'email',
  // TASK-PC-FE-284 — ecommerce domain fixtures. Each is a free-prose /
  // display-only string a person reads (not interpreted by a parser or
  // `StatusBadge`): product-variant option name (`optionName`, e.g. "M"),
  // order line + summary denormalized product name (`productName` /
  // `firstItemName`), user nickname, notification-template `subject`, and the
  // order shipping-address free-text fields (`recipient` / `address1` /
  // `address2`).
  'optionName',
  'productName',
  'firstItemName',
  'nickname',
  'subject',
  'recipient',
  'address1',
  'address2',
  // TASK-PC-FE-285 — erp domain fixtures. `title` is the approval request's
  // free-text title (a person types it in `ApprovalCreateDialog`); every
  // other human-readable erp value already resolves to `name` (department /
  // employee / job-grade / cost-center / business-partner / department-path
  // node / cost-center-ref / job-grade-ref all share that key).
  'title',
  // TASK-PC-FE-287 — wms domain fixtures. `customerName`/`supplierName` are
  // the admin read-model's OWN denormalized display names (OrderSummary §8 /
  // AsnSummary §6 — "Denormalized [from PartnerRef] at projection time"), a
  // person reads them the same way IAM's `email` or ERP's `name` is read.
  // `reasonNote` is the free-text note on an inventory adjustment
  // (`AdjustmentAudit.reason_note` — a person typed it, unlike the
  // machine-coded sibling `reasonCode`). `message` is the alert log's
  // human-readable description (`AlertLog` has no other display field).
  'customerName',
  'supplierName',
  'reasonNote',
  'message',
]);

const MACHINE_KEYS = new Set([
  'id',
  'productKey',
  'baseRoute',
  'tenants',
  'domain',
  'status',
  'reason',
  'type',
  'sourceType',
  'sourceDomain',
  'deepLink',
  'currency',
  'amount',
  'code',
  'degradedDomains',
  // TASK-PC-FE-283 — IAM domain fixtures (accounts/audit/operators/rbac/
  // tenants/org-nodes/groups/partnerships). Each is an enum/discriminant/
  // catalog-key value a parser or `StatusBadge`-like element interprets, or
  // an array of such values (arrays inherit their key) — never free prose.
  'source', // audit discriminant ('admin' | 'login_history' | 'suspicious')
  'actionCode', // admin_actions action code, e.g. 'ACCOUNT_LOCK'
  'outcome', // login/suspicious outcome enum
  'ipMasked', // producer-masked IP — a machine-formatted value
  'geoCountry', // ISO country code
  'roles', // operator role-name array (enum-like codes, e.g. 'SUPER_ADMIN')
  'scope', // rbac catalog scope ('global')
  'permissions', // permission-key array (e.g. 'operator.manage')
  'tenantType', // tenant type enum
  'roleName', // group/org-node admin grant role name
  'mode', // org-node ceiling discriminant ('UNBOUNDED' | 'BOUNDED')
  'domains', // org-node ceiling / partnership scope domain-key array
  'tenantIds', // org-node subtree tenant id array
  'myRole', // partnership side discriminant ('host' | 'partner')
  'grantableRoles', // operators grantable-roles endpoint — role-name array (label-guard document key only; not a wire field of any schema)
  // TASK-PC-FE-284 — ecommerce domain fixtures. All enum/code/formatted values
  // a parser, `StatusBadge`-like element or a copy-paste search key reads —
  // never free prose.
  'discountType', // promotion enum ('FIXED' | 'PERCENTAGE')
  'channel', // notification-template enum ('EMAIL' | 'SMS' | 'PUSH')
  'trackingNumber', // shipping carrier tracking code
  'carrier', // shipping carrier code/name (formatted value, not free prose)
  'payoutReference', // settlement payout reference code
  'phone', // formatted phone number (order shipping address / user profile)
  'zipCode', // postal code
  'startDate', // promotion window bound (ISO instant)
  'endDate', // promotion window bound (ISO instant)
  'from', // settlement period window bound (ISO instant)
  'to', // settlement period window bound (ISO instant)
  'objectKey', // product-image storage key
  'url', // product-image URL
  'thumbnailUrl', // product thumbnail URL (always null in this fixture set — AC-7)
  // TASK-PC-FE-285 — erp domain fixtures (masters · read-model · approval ·
  // delegation). Every value below is an id/code/enum/date/actor-reference a
  // parser, `StatusBadge`, `EffectivePeriodBadge` or `masterRefLabel` reads —
  // never free prose (ADR-MONO-050 D9 — cross-service identifiers are codes;
  // `master-ref-label.ts` never suffixes a `code`, only the `name` beside it).
  'code', // department/employee/job-grade/cost-center/business-partner CODE
  'employeeNumber', // employee business identifier (사번), e.g. 'E-0001'
  'partnerType', // business-partner enum (CUSTOMER | SUPPLIER | BOTH)
  'employmentStatus', // employee enum (EMPLOYED | ON_LEAVE | SEPARATED)
  'subjectType', // approval enum (DEPARTMENT | EMPLOYEE)
  'effectiveFrom', // E2 EffectivePeriod bound (ISO-8601 DATE)
  'effectiveTo', // E2 EffectivePeriod bound (ISO-8601 DATE, nullable)
  'validFrom', // delegation grant/fact period bound (ISO-8601)
  'validTo', // delegation grant/fact period bound (ISO-8601, NON_NULL-absent)
  'createdBy', // audit envelope actor id
  'updatedBy', // audit envelope actor id
  'revokedBy', // delegation grant actor id (who revoked)
  'scope', // delegation-fact enum (GLOBAL | REQUEST)
  'method', // business-partner paymentTerms enum (e.g. 'BANK_TRANSFER')
  'transition', // approval history discriminant ('submit' | 'approve' | …)
  'actor', // approval history actor id
  'at', // approval history entry timestamp (ISO-8601)
  // TASK-PC-FE-286 — finance + ledger domain fixtures. Neither
  // `AccountSchema`/`BalanceSchema`/`TransactionSchema` (finance) nor any
  // ledger-types schema (trial balance / period / journal / account /
  // reconciliation / fx) has a name/title/description/memo field — every
  // string on these two surfaces is an id/code/enum/date/money value a
  // parser, `StatusBadge` or `formatMoney` reads, never free prose typed or
  // read by a person (confirmed by reading every schema under
  // `shared/api/finance-accounts-types.ts` and `shared/api/ledger-types/`).
  'kycLevel', // finance account enum (NONE | BASIC | FULL)
  'ledger', // finance Balance minor-units string (F5 — like 'amount')
  'available', // finance Balance minor-units string (F5)
  'held', // finance Balance minor-units string (F5)
  'ledgerAccountCode', // ledger chart-of-accounts CODE (ADR-MONO-050 D9)
  'normalSide', // AccountBalance enum (DEBIT | CREDIT)
  'balanceSide', // AccountBalance enum (DEBIT | CREDIT)
  'direction', // JournalLine / AccountEntryLine enum (DEBIT | CREDIT)
  'exchangeRate', // JournalLine F5 decimal-string provenance factor
  'resolutionType', // reconciliation resolve enum (MATCHED_MANUALLY | WRITTEN_OFF | ACCEPTED)
  // 🔵 NOT adding 'note' here — it is ALREADY in `HUMAN_READABLE_KEYS` above
  // (the base/foundation set), which wins first in `findLabelViolations`'s
  // check order regardless — so the reconciliation resolution's `note` takes
  // the «(샘플)» suffix like any other human-readable field (`fixtures/ledger.ts`).
  'closedBy', // accounting period actor id (who closed it)
  'resolvedBy', // reconciliation resolution actor id
  'statementDate', // reconciliation statement date (ISO-8601 DATE)
  'externalRef', // reconciliation discrepancy/statement-match external system reference code
  'statementLineExternalRef', // reconciliation statement match external system line reference code
  'rate', // FX rate F5 decimal-string (never Number/parseFloat/parseInt)
  'baseCurrency', // FX rate ISO-4217 code
  // TASK-PC-FE-295 — the operator-overview cards now carry the PRODUCER's own
  // response body (see `shared/sample/fixtures/dashboards.ts`), so two keys
  // that only ever existed inside a producer envelope reach this rule for the
  // first time. Neither is prose: `sort` is the wms read-model's sort spec
  // ('lastEventAt,desc') that `PageResponse` echoes back, and `timestamp` is
  // the ISO-8601 stamp scm's / finance's `ApiEnvelope` puts in `meta`.
  'sort', // wms PageResponse sort spec (field,direction)
  'timestamp', // ApiEnvelope meta stamp (ISO-8601)
  'foreignCurrency', // FX rate ISO-4217 code
  'base', // FX rate-history pair ISO-4217 code
  'foreign', // FX rate-history pair ISO-4217 code
  'originalForeignMinor', // FX position lot F5 minor-units string
  'remainingForeignMinor', // FX position lot F5 minor-units string
  'originalBaseMinor', // FX position lot F5 minor-units string
  'carryingBaseMinor', // FX position lot F5 minor-units string
  'totalRemainingForeignMinor', // FX position summary F5 minor-units string
  'totalCarryingBaseMinor', // FX position summary F5 minor-units string
  'expectedMinor', // reconciliation discrepancy F5 minor-units string
  'actualMinor', // reconciliation discrepancy F5 minor-units string
  // TASK-PC-FE-288 — scm domain fixtures (procurement PO · inventory-visibility ·
  // demand-planning suggestions · demand-planning seed/config). Every value
  // below is an id/code/enum/date/money-decimal value a parser or
  // `masterRefLabel`/`StatusBadge`-like element reads — never free prose
  // (ADR-MONO-050 D9 — cross-service identifiers are codes; `supplierCode` is
  // the master's own `code`, mirrored from erp/wms's identical `code` rule).
  'supplierCode', // PurchaseOrderResponse resolved supplier CODE (TASK-MONO-677)
  'poNumber', // PurchaseOrderResponse business identifier (mirrors wms's orderNo/asnNo)
  'totalAmount', // PurchaseOrderResponse F5 decimal-string amount
  'sku', // InventoryVisibility SnapshotRow / SkuBreakdown SKU code (bare — distinct literal key from wms's `skuCode`)
  'supplierSku', // PoLine supplier-side SKU code
  'quantity', // PoLine / SnapshotRow F5 decimal-string or number quantity
  'unitPrice', // PoLine F5 decimal-string unit price
  'receivedQuantity', // PoLine F5 decimal-string received quantity
  'staleness', // SnapshotRow / cross-node meta enum (FRESH | STALE | UNREACHABLE)
  'stalenessStatus', // StalenessRow enum (FRESH | STALE | UNREACHABLE)
  'nodeType', // NodeRow enum (WMS_WAREHOUSE | SUPPLIER | THIRD_PARTY_LOGISTICS | IN_TRANSIT)
  // TASK-PC-FE-287 — wms domain fixtures (admin read-model · outbound-service ·
  // logistics dispatch). Every value below is an id/code/enum/date a parser or
  // `StatusBadge`-like element reads — never free prose (mirrors ADR-MONO-050
  // D9 "cross-service identifiers are codes", already applied to erp/ecommerce).
  'locationCode', // InventorySnapshot/LocationRef denormalized CODE (TASK-MONO-675 AC-3 field)
  'skuCode', // InventorySnapshot/SkuRef/OutboundOrderLine denormalized CODE (TASK-MONO-675 AC-3 field)
  'lotNo', // InventorySnapshot/LotRef denormalized lot number (TASK-MONO-675 AC-3 field)
  'warehouseCode', // InventorySnapshot/AsnSummary/WarehouseRef denormalized CODE (TASK-MONO-675 AC-3 field)
  'zoneCode', // ZoneRef CODE
  'zoneType', // ZoneRef enum
  'locationType', // LocationRef enum
  'baseUom', // SkuRef enum (unit of measure)
  'trackingType', // SkuRef enum (LOT | NONE)
  'partnerCode', // PartnerRef CODE
  'timezone', // WarehouseRef IANA timezone id
  'alertType', // AlertLog enum (LOW_STOCK | ANOMALY)
  'bucket', // AdjustmentAudit enum (which stock bucket changed)
  'reasonCode', // AdjustmentAudit enum (machine reason code, sibling of human `reasonNote`)
  'acknowledgedBy', // AlertLog actor id (who acknowledged) — mirrors erp's `revokedBy`/`resolvedBy`
  'orderNo', // OrderSummary/OutboundOrder business identifier
  'shipmentNo', // ShipmentSummary business identifier
  'asnNo', // AsnSummary business identifier
  'trackingNo', // ShipmentSummary/DispatchRef carrier tracking code
  'carrierCode', // ShipmentSummary/DispatchRef carrier code
  'sagaState', // OrderSummary/OutboundOrder enum (mirrors erp's read-model enums)
  'state', // OutboundSaga enum (REQUESTED | RESERVED | … — the saga's own status key)
  'requiredShipDate', // OrderSummary/OutboundOrder window bound (ISO date)
  'expectedArriveDate', // AsnSummary window bound (ISO date)
  'expiryDate', // LotRef window bound (ISO date)
  'date', // ThroughputDaily day bucket (ISO date)
  'topic', // projection-status Kafka topic name
  'consumerGroup', // projection-status Kafka consumer-group id
  'key', // Setting's own dot-notation key (machine-formatted, not a display label)
]);

/** `*Id` (sourceId, accountId, nodeId, …) and `*At` (createdAt, asOf-like). */
function isMachineKey(key: string): boolean {
  return (
    MACHINE_KEYS.has(key) ||
    /Id$/.test(key) ||
    /At$/.test(key) ||
    key === 'asOf'
  );
}

export interface LabelViolation {
  path: string;
  value: string;
  problem: 'missing-suffix' | 'suffix-on-machine-value' | 'unclassified-key';
}

export function findLabelViolations(
  value: unknown,
  path = '$',
  key: string | null = null,
): LabelViolation[] {
  if (typeof value === 'string') {
    if (key === null) {
      return [{ path, value, problem: 'unclassified-key' }];
    }
    if (HUMAN_READABLE_KEYS.has(key)) {
      return value.endsWith(SAMPLE_LABEL_SUFFIX)
        ? []
        : [{ path, value, problem: 'missing-suffix' }];
    }
    if (isMachineKey(key)) {
      return value.includes('(샘플)')
        ? [{ path, value, problem: 'suffix-on-machine-value' }]
        : [];
    }
    return [{ path, value, problem: 'unclassified-key' }];
  }
  if (Array.isArray(value)) {
    return value.flatMap((item, i) => findLabelViolations(item, `${path}[${i}]`, key));
  }
  if (value !== null && typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).flatMap(([k, v]) =>
      findLabelViolations(v, `${path}.${k}`, k),
    );
  }
  return [];
}
