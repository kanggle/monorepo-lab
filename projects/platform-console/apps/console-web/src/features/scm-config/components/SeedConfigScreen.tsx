'use client';

import Link from 'next/link';
import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { PolicyForm } from './PolicyForm';
import { SupplierMapForm } from './SupplierMapForm';

/**
 * scm replenishment **seed/config** operator screen (TASK-PC-FE-080 — the
 * operator config arm of the ADR-MONO-027 wms→scm replenishment loop, the third
 * SCM drill-in tab 설정). SKU-code-driven: the operator enters a SKU code, the
 * screen GETs both the reorder policy + the SKU→supplier mapping, shows them (or
 * an actionable "not configured yet → create" empty state on 404), and lets them
 * upsert each via a confirm-gated PUT.
 *
 * Config-surface invariant surfaced (normative — § 2.4.6.2): editing the seed
 * rows affects FUTURE reorder-suggestion evaluation only — it does NOT mutate
 * existing suggestions or POs and does NOT dispatch anything. The screen issues
 * NO suggestion / PO / dispatch call (only policies + sku-supplier-map GET/PUT).
 *
 * No list route exists (per-SKU GET/PUT only) — nothing is fetched until a SKU
 * code is entered (the forms' read queries are `enabled` only then).
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login); 403 → inline "not scoped"; 429 → bounded backoff notice; 503/timeout
 * → only the affected sub-section degrades (the console shell + the 운영 + 보충
 * sections stay intact).
 */
export function SeedConfigScreen() {
  const lookupId = useId();
  const [draft, setDraft] = useState('');
  // The "active" SKU drives the two forms' read queries; it only changes on an
  // explicit lookup submit (so typing does not fire a fetch per keystroke into
  // the rate-limited scm gateway).
  const [activeSku, setActiveSku] = useState('');

  function submitLookup(e: React.FormEvent) {
    e.preventDefault();
    setActiveSku(draft.trim());
  }

  return (
    <section aria-labelledby="cfg-heading">
      <h1 id="cfg-heading" className="mb-2 text-2xl font-semibold">
        SCM 보충 설정 (재주문 정책 · 공급사 매핑)
      </h1>
      <p className="mb-2 text-sm text-muted-foreground">
        SKU 코드를 입력해 해당 SKU 의 재주문 정책과 공급사 매핑을 조회하고
        설정(upsert)합니다. 공급사 매핑이 없어 보충 추천 승인이
        실패(<code>SKU_SUPPLIER_UNMAPPED</code>)한 경우, 여기서 매핑을 추가한 뒤
        보충 화면으로 돌아가 승인할 수 있습니다.{' '}
        <Link
          href="/scm/replenishment"
          data-testid="cfg-replenishment-link"
          className="underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          보충 화면으로 이동
        </Link>
      </p>
      <p
        data-testid="cfg-future-only-note"
        className="mb-6 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
      >
        여기서 변경한 설정은 <strong>이후(미래) 보충 추천 평가에만</strong>{' '}
        반영됩니다. 기존 추천·발주(PO)를 변경하거나 발주를 발행하지 않습니다.
      </p>

      {/* ── SKU-code lookup ─────────────────────────────────────────────── */}
      <form
        onSubmit={submitLookup}
        className="mb-6 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="SKU 설정 조회"
      >
        <div className="grow">
          <label
            htmlFor={lookupId}
            className="block text-sm font-medium text-foreground"
          >
            SKU 코드
          </label>
          <input
            id={lookupId}
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            data-testid="cfg-sku-input"
            placeholder="예: SKU-APPLE-001"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button
          type="submit"
          data-testid="cfg-sku-lookup"
          disabled={draft.trim().length === 0}
        >
          조회
        </Button>
      </form>

      {activeSku ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <PolicyForm key={`policy-${activeSku}`} skuCode={activeSku} />
          <SupplierMapForm key={`map-${activeSku}`} skuCode={activeSku} />
        </div>
      ) : (
        <p className="text-sm text-muted-foreground" data-testid="cfg-no-sku">
          위에 SKU 코드를 입력하고 조회하면 재주문 정책과 공급사 매핑을 설정할 수
          있습니다.
        </p>
      )}
    </section>
  );
}
