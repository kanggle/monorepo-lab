import Link from 'next/link';

/**
 * TASK-PC-FE-346 — the «조회 전용» wording shared by every E-commerce screen whose mutation buttons
 * are hidden for lack of the area's `*_WRITE` business role (contract § 2.4.3.4 «Read-only sets hide
 * write buttons»). Same shape as the SCM / Finance notes (`cfg-read-only-note`, `repl-read-only-note`,
 * `ledger-recon-read-only-note`): `role="note"`, `data-testid="<screen>-read-only-note"`.
 *
 * `action` = what the hidden buttons do, `roleLabel` = the missing permission in operator words
 * («상품 변경»). The server stays the final gate; this only keeps a button from leading to a `403`.
 */
export interface ReadOnlyNoteProps {
  testId: string;
  action: string;
  roleLabel: string;
}

export function ReadOnlyNote({ testId, action, roleLabel }: ReadOnlyNoteProps) {
  return (
    <p role="note" data-testid={testId} className="mb-4 text-xs text-muted-foreground">
      조회 전용 — {action}에 필요한 업무 권한({roleLabel})이 이 테넌트에서 배정되지 않아 해당
      버튼을 숨겼습니다.
    </p>
  );
}

/**
 * The `new` / `edit` routes: a non-writer who navigates here directly gets this notice INSTEAD of
 * the form (ticket Failure Scenario 2 — hiding only the entry button would let them fill a whole
 * form and meet a `403` on submit).
 */
export interface ReadOnlyRouteNoticeProps {
  heading: string;
  testId: string;
  action: string;
  roleLabel: string;
  backHref: string;
  backLabel: string;
}

export function ReadOnlyRouteNotice({
  heading,
  testId,
  action,
  roleLabel,
  backHref,
  backLabel,
}: ReadOnlyRouteNoticeProps) {
  return (
    <section>
      <h1 className="mb-6 text-2xl font-semibold">{heading}</h1>
      <div
        role="note"
        data-testid={testId}
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        <p>
          조회 전용 — {action}에 필요한 업무 권한({roleLabel})이 이 테넌트에서 배정되지 않아 이
          화면을 열지 않습니다. 권한이 필요하면 운영자 관리자에게 문의하세요.
        </p>
        <Link href={backHref} className="mt-4 inline-block text-sm underline">
          {backLabel}
        </Link>
      </div>
    </section>
  );
}
