import type { ReceivedInvitation, ReceivedInvitationsState } from '../api/types';

/**
 * «받은 초대» — the first block of the non-operator empty state on `/new-tenant` (TASK-MONO-773 S6 —
 * console-integration-contract § 2.6.4 «Empty state» item 1; 773 Edge Case 2 «받은 초대 먼저»).
 *
 * Presentational only (the page reads the data server-side and passes `state`): no hooks, no network.
 *
 * 🔴🔴 READ-ONLY (owner decision 2026-10-15 UTC, 773 S1 Q6): no link and no accept button among the invitations.
 *      Acceptance stays the TASK-MONO-772 email-link flow at the IdP — the mailbox is the proof (IAM R4). The
 *      producer does not even send a link (`onboarding-api.md` «숨기는 것»).
 *
 * States:
 *   - `VERIFIED` → one row per invitation (company · roles · «첫 관리자» badge · expiry) and the instruction to
 *     accept through the email link; none → «받은 초대가 없습니다».
 *   - `EMAIL_NOT_VERIFIED` → «이메일을 인증하면 받은 초대와 회사 만들기가 열립니다» + the verification link
 *     (that link goes to the IdP's verification page — it is not an invitation link).
 *   - `NOT_ELIGIBLE` (or any other value) → the not-eligible copy (§ 2.6.4 error table).
 *   - `unavailable` → «받은 초대를 불러오지 못했습니다» — never rendered as «초대 없음».
 */
export interface ReceivedInvitationsProps {
  state: ReceivedInvitationsState;
  emailVerificationUrl: string | null;
}

const NOT_ELIGIBLE_COPY =
  '이 계정으로는 받은 초대를 볼 수 없습니다. 사이트나 회사용 계정이라면 개인 계정으로 로그인해 만드세요.';

/** `2026-10-22T10:00:00Z` → `2026-10-22` (the date is what the reader acts on; UTC, as the producer writes it). */
function dateOf(iso: string): string {
  return iso.slice(0, 10);
}

function InvitationRow({ item }: { item: ReceivedInvitation }) {
  return (
    <li
      data-testid={`received-invitation-${item.invitationId}`}
      className="rounded-md border border-border px-3 py-2"
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-foreground">{item.tenantDisplayName ?? item.tenantId}</span>
        {item.firstAdmin && (
          <span className="rounded-sm bg-primary/10 px-1.5 py-0.5 text-[11px] font-medium text-primary">
            첫 관리자
          </span>
        )}
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        역할: {item.roles.length > 0 ? item.roles.join(' · ') : '—'} · 만료: {dateOf(item.expiresAt)}
      </p>
    </li>
  );
}

export function ReceivedInvitations({ state, emailVerificationUrl }: ReceivedInvitationsProps) {
  let body: React.ReactNode;
  if (state.kind === 'unavailable') {
    body = (
      <p role="status" className="text-sm text-muted-foreground">
        받은 초대를 불러오지 못했습니다. 잠시 후 이 화면을 다시 열어 주세요.
      </p>
    );
  } else if (state.accountState === 'EMAIL_NOT_VERIFIED') {
    body = (
      <div className="space-y-1 text-sm text-muted-foreground">
        <p>이메일을 인증하면 받은 초대와 회사 만들기가 열립니다.</p>
        {emailVerificationUrl && (
          <a
            href={emailVerificationUrl}
            data-testid="received-invitations-verify-link"
            className="font-medium text-foreground underline underline-offset-2"
          >
            이메일 인증하러 가기
          </a>
        )}
      </div>
    );
  } else if (state.accountState !== 'VERIFIED') {
    body = <p className="text-sm text-muted-foreground">{NOT_ELIGIBLE_COPY}</p>;
  } else if (state.items.length === 0) {
    body = <p className="text-sm text-muted-foreground">받은 초대가 없습니다.</p>;
  } else {
    body = (
      <div className="space-y-2">
        <ul className="space-y-2">
          {state.items.map((item) => (
            <InvitationRow key={item.invitationId} item={item} />
          ))}
        </ul>
        <p className="text-xs text-muted-foreground">
          초대 메일의 링크로 수락하세요 — 메일이 없다면 초대한 분께 다시 보내 달라고 요청하세요.
        </p>
      </div>
    );
  }

  return (
    <section
      data-testid="received-invitations"
      aria-labelledby="received-invitations-heading"
      className="rounded-lg border border-border bg-background p-4"
    >
      <h2 id="received-invitations-heading" className="mb-2 text-sm font-semibold text-foreground">
        받은 초대
      </h2>
      {body}
    </section>
  );
}
