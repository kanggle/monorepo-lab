'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useScmSkuBreakdown } from '../hooks/use-scm-ops';
import type { SnapshotResponse, StalenessResponse } from '../api/types';
import { snapshotRows } from './scm-ops-helpers';
import { ScmSnapshotTable } from './ScmSnapshotTable';
import { ScmSkuBreakdown } from './ScmSkuBreakdown';
import { ScmStalenessTable } from './ScmStalenessTable';

/**
 * scm 재고 (inventory-visibility) screen — split out of the former
 * combined ScmOpsScreen (TASK-PC-FE-220; the read section was
 * TASK-PC-FE-008, the god-file split TASK-PC-FE-144). STRICTLY READ-ONLY.
 *
 * Renders the three inventory-visibility read regions:
 *   - the cross-node snapshot table;
 *   - the on-demand per-SKU breakdown (with the X-Cache freshness);
 *   - the node staleness panel.
 *
 * S5 (§ 2.4.6, NORMATIVE): EVERY inventory-visibility view renders the
 * producer `meta.warning` PROMINENTLY via <S5Warning> (inside each child).
 * It is never stripped / hidden / de-emphasised — it is a required,
 * surfaced field of each view-model.
 *
 * This container owns the SKU input/query state + the snapshot row
 * normaliser (`snapshotRows`); the read regions are rendered by the
 * prop-driven `ScmSnapshotTable` / `ScmSkuBreakdown` / `ScmStalenessTable`
 * presentational children.
 */
export interface ScmInventoryScreenProps {
  snapshot: SnapshotResponse;
  staleness: StalenessResponse;
}

export function ScmInventoryScreen({
  snapshot,
  staleness,
}: ScmInventoryScreenProps) {
  const skuFid = useId();

  // ── inventory-visibility per-SKU breakdown (on demand) ──────────────
  const [skuInput, setSkuInput] = useState('');
  const [skuQuery, setSkuQuery] = useState<string | null>(null);
  const skuQ = useScmSkuBreakdown(skuQuery);
  const skuApiError =
    skuQ.error instanceof ApiError ? (skuQ.error as ApiError) : null;

  const snapRows = useMemo(() => snapshotRows(snapshot), [snapshot]);
  const stalenessRows = staleness.data;

  return (
    <section aria-labelledby="scm-inventory-heading">
      <h1
        id="scm-inventory-heading"
        className="mb-2 text-2xl font-semibold"
      >
        SCM 재고 가시성
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        재고 가시성 (읽기 전용) — 노드별 스냅샷 · SKU 분포 · 노드 신선도.
      </p>

      <ScmSnapshotTable warning={snapshot.meta.warning} rows={snapRows} />

      <ScmSkuBreakdown
        headerWarning={snapshot.meta.warning}
        skuFid={skuFid}
        skuInput={skuInput}
        onSkuInputChange={setSkuInput}
        onSubmit={(e) => {
          e.preventDefault();
          setSkuQuery(skuInput.trim() || null);
        }}
        apiError={skuApiError}
        result={skuQ.data}
      />

      <ScmStalenessTable
        warning={staleness.meta.warning}
        rows={stalenessRows}
      />
    </section>
  );
}
