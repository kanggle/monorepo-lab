import Link from 'next/link';

/**
 * Shared erp route notice (TASK-PC-FE-076 drill-in split). The four
 * `(console)/erp/**` routes render IDENTICAL eligibility / degrade
 * states; this component is the single source for them (copy + testids
 * preserved verbatim from the original single `(console)/erp/page.tsx`):
 *
 *   - `registryDegraded` / `degraded` → `erp-degraded` (same copy — the
 *     registry-down and producer-down paths are indistinguishable to the
 *     operator and recover the same way).
 *   - `notEligible`  → `erp-not-eligible` (+ a "카탈로그로 이동" link).
 *   - `forbidden`    → `erp-forbidden`.
 *
 * The `heading` is route-specific (e.g. "ERP 마스터") so the notice keeps
 * the operator oriented to the section they navigated into. Pure
 * presentational server component — no hooks, no data.
 */
export type ErpNoticeKind =
  | 'registryDegraded'
  | 'notEligible'
  | 'forbidden'
  | 'degraded';

export function ErpSectionNotice({
  kind,
  heading,
}: {
  kind: ErpNoticeKind;
  heading: string;
}) {
  return (
    <section aria-labelledby="erp-heading">
      <h1 id="erp-heading" className="mb-6 text-2xl font-semibold">
        {heading}
      </h1>

      {kind === 'notEligible' ? (
        <div
          role="status"
          data-testid="erp-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            erp 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 erp 테넌트 스코프가 부여되어 있지
            않습니다. 접근이 필요하면 운영자 관리자에게 문의하세요.
          </p>
          <Link
            href="/console"
            className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            카탈로그로 이동
          </Link>
        </div>
      ) : kind === 'forbidden' ? (
        <div
          role="status"
          data-testid="erp-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          erp 운영 화면을 조회할 권한이 없습니다. (테넌트 스코프 / 역할 /
          데이터 스코프 확인이 필요합니다.)
        </div>
      ) : (
        <div
          role="status"
          data-testid="erp-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          erp 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      )}
    </section>
  );
}
