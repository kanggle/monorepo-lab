import type { ReactNode } from 'react';
import Link from 'next/link';
import {
  noTenantNoticeKind,
  type NoTenantNoticeKind,
} from '@/shared/lib/active-tenant-default';

export interface NoTenantNoticeBodyProps {
  /** Already-resolved judgement ({@link noTenantNoticeKind}) — for a caller
   *  that is itself a sync/presentational component and cannot await inside
   *  its own render (e.g. `IamOverviewScreen`, tested with the client-side
   *  `@testing-library/react` renderer, which cannot render an async
   *  Server Component). The owning `*-state.ts` / `page.tsx` resolves the
   *  kind server-side and threads it down as a plain prop. */
  kind: NoTenantNoticeKind;
  /** The screen's existing `data-testid` — kept stable so this refactor does
   *  not move any render-test's selector (TASK-PC-FE-301). */
  testId: string;
  /** The screen-specific second sentence, shown only in the 'select' case
   *  (≥2 selectable tenants, none chosen — today's unchanged copy). */
  description: ReactNode;
  /** Where "카탈로그로 이동" points. Defaults to `/console`; the tenant-detail
   *  route points back to its own list instead. */
  linkHref?: string;
  /** Defaults to "카탈로그로 이동". */
  linkLabel?: string;
}

/**
 * The single RENDER site for "no tenant is currently active" (TASK-PC-FE-301)
 * — pure/sync, so it can be used from either an async Server Component
 * ({@link NoTenantNotice} below) or a sync presentational component that
 * already resolved {@link noTenantNoticeKind} itself. Every gated screen
 * renders THIS (directly or via the wrapper) INSTEAD of hand-writing the two
 * possible paragraphs, so the copy can never drift between screens (task
 * Failure Scenario 2: "화면마다 문구를 고친다 → 한 곳이 빠진다").
 *
 * Owner decision ⓒ (2026-09-26 UTC):
 *  - `'zero'` — the operator has NO selectable tenant at all (0 roles → 0
 *    available products → 0 selectable tenants, the `viewer@demo.com` shape
 *    measured in the `TASK-MONO-730` AC-1 window). A permission-denied-toned
 *    notice with no switcher pointer — there is nothing to switch to.
 *  - `'select'` — ≥2 selectable tenants and none chosen yet, OR the registry
 *    could not be read (Edge Case: 없음 ≠ 못 읽음 — see {@link
 *    noTenantNoticeKind}). Renders the ORIGINAL «테넌트를 먼저 선택하세요» plus
 *    the caller's own `description` and a link back to the catalog/switcher,
 *    byte-identical to before this task.
 *
 * A single selectable tenant is never rendered here at all — it auto-selects
 * at login/idle-refresh (`chooseDefaultTenant`, TASK-PC-FE-292) before any
 * gated screen's `noTenant` flag can even become true.
 */
export function NoTenantNoticeBody({
  kind,
  testId,
  description,
  linkHref = '/console',
  linkLabel = '카탈로그로 이동',
}: NoTenantNoticeBodyProps) {
  if (kind === 'zero') {
    return (
      <div
        role="status"
        data-testid={testId}
        data-tenant-notice="zero"
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        <p className="mb-2 font-medium text-foreground">
          이 계정에는 접근 가능한 테넌트가 없습니다.
        </p>
        <p>권한이 필요합니다 — 관리자에게 요청하세요.</p>
      </div>
    );
  }

  return (
    <div
      role="status"
      data-testid={testId}
      data-tenant-notice="select"
      className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
    >
      <p className="mb-2 font-medium text-foreground">
        테넌트를 먼저 선택하세요.
      </p>
      <p>{description}</p>
      <Link
        href={linkHref}
        className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        {linkLabel}
      </Link>
    </div>
  );
}

export type NoTenantNoticeProps = Omit<NoTenantNoticeBodyProps, 'kind'>;

/**
 * Async Server Component wrapper — resolves {@link noTenantNoticeKind} itself
 * and delegates to {@link NoTenantNoticeBody}. This is what every `page.tsx` /
 * `DomainTenantGate` gate renders directly (they do not otherwise need the
 * registry). A caller that already has the kind from its own state object
 * (`IamOverviewScreen`) renders {@link NoTenantNoticeBody} directly instead.
 */
export async function NoTenantNotice(props: NoTenantNoticeProps) {
  const kind = await noTenantNoticeKind();
  return <NoTenantNoticeBody kind={kind} {...props} />;
}
