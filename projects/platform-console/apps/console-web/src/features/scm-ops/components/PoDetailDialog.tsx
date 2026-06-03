'use client';

import { useEffect, useId, useRef } from 'react';
import { Button } from '@/shared/ui/Button';
import type { PurchaseOrder } from '../api/types';

/**
 * Read-only PO detail dialog (console-integration-contract § 2.4.6).
 *
 * STRICTLY READ-ONLY — there is NO mutation affordance of any kind: no
 * submit / confirm / cancel button, no reason capture, no idempotency, no
 * confirm-to-mutate gate. PO write actions are buyer/business mutations,
 * explicitly out of console scope. This dialog only DISPLAYS the PO.
 *
 * Keyboard-operable + WCAG AA: focus moves into the dialog on open,
 * `Escape` closes, focus is trapped, `role="dialog"` + `aria-modal` +
 * labelled. axe-clean.
 */
export interface PoDetailDialogProps {
  open: boolean;
  po: PurchaseOrder | null;
  onClose: () => void;
}

export function PoDetailDialog({ open, po, onClose }: PoDetailDialogProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (open) {
      const t = setTimeout(() => closeRef.current?.focus(), 0);
      return () => clearTimeout(t);
    }
  }, [open]);

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      }
      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open || !po) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      data-testid="scm-po-overlay"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        data-testid="scm-po-dialog"
        className="max-h-[85vh] w-full max-w-2xl overflow-auto rounded-lg border border-border bg-background p-6 shadow-lg"
      >
        <div className="flex items-start justify-between">
          <h2
            id={titleId}
            className="text-lg font-semibold text-foreground"
          >
            발주(PO) 상세 — {po.poNumber ?? po.id}
          </h2>
          <Button
            ref={closeRef}
            variant="secondary"
            onClick={onClose}
            data-testid="scm-po-close"
          >
            닫기
          </Button>
        </div>

        <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-muted-foreground">상태</dt>
            <dd className="text-foreground" data-testid="scm-po-status">
              {po.status ?? '—'}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">공급사 ID</dt>
            <dd className="text-foreground">{po.supplierId ?? '—'}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">총액</dt>
            <dd className="text-foreground">
              {po.totalAmount ?? '—'} {po.currency ?? ''}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">생성 (UTC)</dt>
            <dd className="text-foreground">{po.createdAt ?? '—'}</dd>
          </div>
        </dl>

        <h3 className="mt-6 mb-2 text-sm font-medium text-foreground">
          라인
        </h3>
        {!po.lines || po.lines.length === 0 ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="scm-po-lines-empty"
          >
            표시할 라인이 없습니다.
          </p>
        ) : (
          <table
            className="data-table"
            data-testid="scm-po-lines"
          >
            <caption className="sr-only">PO 라인</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  #
                </th>
                <th scope="col" className="p-2">
                  SKU
                </th>
                <th scope="col" className="p-2">
                  수량
                </th>
                <th scope="col" className="p-2">
                  단가
                </th>
                <th scope="col" className="p-2">
                  입고
                </th>
              </tr>
            </thead>
            <tbody>
              {po.lines.map((l, i) => (
                <tr
                  key={l.id ?? `${l.lineNo ?? i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{l.lineNo ?? i + 1}</td>
                  <td className="p-2">{l.sku ?? '—'}</td>
                  <td className="p-2">{l.quantity ?? '—'}</td>
                  <td className="p-2">{l.unitPrice ?? '—'}</td>
                  <td className="p-2">{l.receivedQuantity ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
