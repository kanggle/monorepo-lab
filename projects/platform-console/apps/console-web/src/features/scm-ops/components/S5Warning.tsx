/**
 * S5 visibility-warning banner (console-integration-contract § 2.4.6 —
 * NORMATIVE contract obligation, not a UX nicety).
 *
 * Every inventory-visibility view MUST render the producer
 * `meta.warning: "Not for procurement decisions (S5)"` PROMINENTLY. The
 * console MUST NOT strip, hide, or de-emphasise it. This component is the
 * single prominent surface for that obligation — it is `role="alert"`,
 * high-contrast, and placed at the top of every inventory-visibility view.
 *
 * It is intentionally NOT collapsible / dismissible — the obligation is to
 * keep it visible whenever inventory-visibility data is shown.
 */
export function S5Warning({ warning }: { warning: string }) {
  return (
    <div
      role="alert"
      data-testid="scm-s5-warning"
      className="mb-4 flex items-start gap-2 rounded-md border-2 border-amber-500 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900 dark:border-amber-500 dark:bg-amber-950/60 dark:text-amber-100"
    >
      <span aria-hidden="true" className="text-base leading-none">
        ⚠
      </span>
      <span>
        <span className="font-semibold">S5: </span>
        {warning}
        <span className="mt-1 block font-normal">
          이 재고 가시성 데이터는 조달(구매) 의사결정의 근거로 사용해서는 안
          됩니다. 최종 일관성 읽기 모델입니다.
        </span>
      </span>
    </div>
  );
}
