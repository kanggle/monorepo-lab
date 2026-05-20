'use client';

import {
  KNOWN_MASTER_STATUSES,
  KNOWN_PARTNER_TYPES,
  labelForUnknownEnum,
  type BusinessPartner,
} from '../api/types';
import { useBusinessPartner } from '../hooks/use-erp-ops';
import { EffectivePeriodBadge } from './EffectivePeriodBadge';

/**
 * Business-partner detail (TASK-PC-FE-010 / § 2.4.8).
 *
 * Confidential financial: `paymentTerms` is rendered as a redacted
 * summary (true to the operator UI semantics — the operator can
 * see WHICH terms are configured but the api module logs NOTHING
 * about them; test asserts the log-spy invariant). Cross-domain
 * references from procurement / finance are read-only per E5 and
 * not enumerated here.
 */
export interface BusinessPartnerDetailProps {
  id: string;
  initial?: BusinessPartner;
}

export function BusinessPartnerDetail({
  id,
  initial,
}: BusinessPartnerDetailProps) {
  const q = useBusinessPartner(id);
  const p = q.data ?? initial ?? null;
  if (!p) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="erp-businesspartner-detail-loading"
      >
        거래처 정보를 불러오는 중…
      </p>
    );
  }
  // paymentTerms is producer-typed (unknown) — surface a redacted
  // "configured / not configured" summary, NOT the inner values.
  const hasPaymentTerms =
    p.paymentTerms !== null && p.paymentTerms !== undefined;
  return (
    <section
      aria-labelledby="erp-businesspartner-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="erp-businesspartner-detail"
    >
      <h2
        id="erp-businesspartner-detail-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        거래처 상세
      </h2>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">코드</dt>
          <dd className="text-foreground">{p.code}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">이름</dt>
          <dd className="text-foreground">{p.name}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">유형</dt>
          <dd className="text-foreground">
            <span
              data-testid="erp-businesspartner-type"
              className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
            >
              {labelForUnknownEnum(p.partnerType, KNOWN_PARTNER_TYPES)}
            </span>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd className="text-foreground">
            <span
              data-testid="erp-businesspartner-status"
              className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
            >
              {labelForUnknownEnum(p.status, KNOWN_MASTER_STATUSES)}
            </span>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">유효기간</dt>
          <dd className="text-foreground">
            <EffectivePeriodBadge period={p.effectivePeriod} />
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">결제 조건 (paymentTerms)</dt>
          <dd
            className="text-foreground"
            data-testid="erp-businesspartner-paymentterms"
          >
            {hasPaymentTerms ? '구성됨 (상세는 운영자 권한 필요)' : '— (미구성)'}
          </dd>
        </div>
      </dl>
    </section>
  );
}
