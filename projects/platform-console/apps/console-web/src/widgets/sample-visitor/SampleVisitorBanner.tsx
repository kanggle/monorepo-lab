import { SampleRefusalNotice } from './SampleRefusalNotice';

/**
 * The persistent sample banner (ADR-MONO-074 A6 / A7).
 *
 * 🔴🔴 Rendered by the `(console)` LAYOUT, never by a page. A per-page banner is
 *    a banner some page forgets; the layout wraps all of them. (The same
 *    argument the `(demo)` group's `SampleDataBanner` made.)
 *
 * It also hosts the write-refusal notice: when any same-origin write is
 * refused with `SAMPLE_READ_ONLY`, the shell says so here, from the one
 * code → copy mapping — whatever the screen's own error renderer does.
 */
export function SampleVisitorBanner() {
  return (
    <div
      role="region"
      aria-label="샘플 데이터 안내"
      data-testid="sample-visitor-banner"
      className="border-b border-border bg-muted px-4 py-2 text-center text-sm text-foreground"
    >
      <p>샘플 데이터로 보는 실제 콘솔 화면입니다 · 로그인하면 실제 데이터</p>
      <SampleRefusalNotice />
    </div>
  );
}
