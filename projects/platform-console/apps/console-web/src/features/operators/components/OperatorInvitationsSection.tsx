'use client';

import { ApiError } from '@/shared/api/errors';
import { Button } from '@/shared/ui/Button';
import {
  isDeliveryFailed,
  type OperatorInvitation,
  type OperatorInvitationPage,
} from '../api/invitation-types';
import { deliveryLabel } from './invitation-copy';

/**
 * «대기 중인 초대» list (TASK-MONO-772 S5 — console-integration-contract
 * § 2.4.3 row 12). Presentational: the container owns the query + the
 * cancel / resend confirm flow; this renders the result for the ACTIVE
 * tenant (`PENDING`, the producer's default — an expired one comes back as
 * `PENDING` + `expired=true` and is offered only resend / cancel).
 *
 * Columns: email · roles · invited by · expires · delivery status · actions.
 * A FAILED_* delivery is a visible WARNING on the row (OD-4 — «발송 실패는
 * 화면에»), never an error state of the section.
 *
 * 🔴 No token, no link, no «링크 복사» — the producer never returns one.
 *
 * Section-only degrade: a 503 / timeout on the list read degrades THIS
 * section (the operators table above stays usable); a 403 renders an inline
 * «not permitted» note.
 */

export interface OperatorInvitationsSectionProps {
  tenantId: string;
  data: OperatorInvitationPage | undefined;
  isLoading: boolean;
  error: unknown;
  page: number;
  onPrevPage: () => void;
  onNextPage: () => void;
  onRetry: () => void;
  onCancel: (inv: OperatorInvitation) => void;
  onResend: (inv: OperatorInvitation) => void;
  /** operatorId → label, for the «초대한 운영자» column (best effort). */
  operatorLabel: (operatorId: string) => string;
  /** Row to visually mark (e.g. the «already pending» jump target). */
  highlightInvitationId?: string | null;
  /** Disables row actions while a cancel / resend is in flight. */
  busy?: boolean;
}

function formatInstant(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function OperatorInvitationsSection({
  tenantId,
  data,
  isLoading,
  error,
  page,
  onPrevPage,
  onNextPage,
  onRetry,
  onCancel,
  onResend,
  operatorLabel,
  highlightInvitationId = null,
  busy = false,
}: OperatorInvitationsSectionProps) {
  const apiError = error instanceof ApiError ? error : null;
  const forbidden = apiError?.status === 403;
  const degraded = !!error && !forbidden;
  const rows = data?.content ?? [];

  return (
    <section
      aria-labelledby="operator-invitations-heading"
      className="mb-8"
      data-testid="operator-invitations"
    >
      <h2
        id="operator-invitations-heading"
        className="mb-1 text-lg font-semibold text-foreground"
      >
        대기 중인 초대
      </h2>
      <p className="mb-3 text-xs text-muted-foreground">
        <strong>{tenantId}</strong> 테넌트로 보낸, 아직 수락되지 않은 초대입니다.
        수락되면 이 목록에서 사라지고 위 운영자 목록에 나타납니다.
      </p>

      {forbidden ? (
        <div
          role="status"
          data-testid="operator-invitations-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-4 text-sm text-muted-foreground"
        >
          이 테넌트의 초대 목록을 볼 권한이 없습니다.
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="operator-invitations-degraded"
          className="rounded-md border border-border bg-muted px-4 py-4 text-sm text-muted-foreground"
        >
          초대 목록을 일시적으로 불러올 수 없습니다. 운영자 관리의 다른 기능은
          계속 사용할 수 있습니다.{' '}
          <button
            type="button"
            onClick={onRetry}
            className="underline underline-offset-2"
            data-testid="operator-invitations-retry"
          >
            다시 불러오기
          </button>
        </div>
      ) : isLoading && !data ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="operator-invitations-loading"
        >
          초대 목록을 불러오는 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="rounded-md border border-dashed border-border px-4 py-4 text-sm text-muted-foreground"
          data-testid="operator-invitations-empty"
        >
          대기 중인 초대가 없습니다.
        </p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-md border border-border">
            <table
              className="w-full text-left text-sm"
              data-testid="operator-invitations-table"
            >
              <caption className="sr-only">대기 중인 운영자 초대</caption>
              <thead className="bg-muted text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="px-3 py-2">이메일</th>
                  <th scope="col" className="px-3 py-2">역할</th>
                  <th scope="col" className="px-3 py-2">초대한 운영자</th>
                  <th scope="col" className="px-3 py-2">만료</th>
                  <th scope="col" className="px-3 py-2">발송 상태</th>
                  <th scope="col" className="px-3 py-2">작업</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((inv) => {
                  const failed = isDeliveryFailed(inv);
                  const highlighted = inv.invitationId === highlightInvitationId;
                  return (
                    <tr
                      key={inv.invitationId}
                      data-testid={`invitation-row-${inv.invitationId}`}
                      data-highlighted={highlighted ? 'true' : undefined}
                      className={
                        highlighted
                          ? 'border-t border-border bg-primary/5'
                          : 'border-t border-border'
                      }
                    >
                      <td className="px-3 py-2">
                        <div className="text-foreground">{inv.email}</div>
                        <div className="text-xs text-muted-foreground">
                          {inv.displayName}
                        </div>
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex flex-wrap gap-1">
                          {/* Plain chips — NOT `OperatorRoleChips`, whose
                              per-role testids would collide with the
                              operators table's on the same page. */}
                          {inv.roles.map((r) => (
                            <span
                              key={r}
                              className="rounded bg-muted px-1.5 py-0.5 text-xs text-foreground"
                            >
                              {r}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-3 py-2 text-muted-foreground">
                        {inv.invitedBy ? operatorLabel(inv.invitedBy) : '—'}
                      </td>
                      <td className="px-3 py-2">
                        {inv.expired ? (
                          <span
                            className="font-medium text-destructive"
                            data-testid={`invitation-expired-${inv.invitationId}`}
                          >
                            만료됨
                          </span>
                        ) : (
                          <span className="text-muted-foreground">
                            {formatInstant(inv.expiresAt)}까지
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2">
                        <span
                          data-testid={`invitation-delivery-${inv.invitationId}`}
                          data-delivery-status={inv.delivery?.status ?? 'NONE'}
                          className={
                            failed
                              ? 'font-medium text-amber-700 dark:text-amber-400'
                              : 'text-muted-foreground'
                          }
                        >
                          {failed ? '⚠ ' : ''}
                          {deliveryLabel(inv)}
                        </span>
                      </td>
                      <td className="px-3 py-2">
                        <div className="flex gap-2">
                          <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            disabled={busy}
                            onClick={() => onResend(inv)}
                            data-testid={`invitation-resend-${inv.invitationId}`}
                          >
                            다시 보내기
                          </Button>
                          <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            disabled={busy}
                            onClick={() => onCancel(inv)}
                            data-testid={`invitation-cancel-${inv.invitationId}`}
                          >
                            취소
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {data && data.totalPages > 1 && (
            <div className="mt-2 flex items-center gap-3 text-sm">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={page <= 0}
                onClick={onPrevPage}
                data-testid="operator-invitations-prev"
              >
                이전
              </Button>
              <span data-testid="operator-invitations-pageinfo">
                {page + 1} / {data.totalPages} 페이지
              </span>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={page + 1 >= data.totalPages}
                onClick={onNextPage}
                data-testid="operator-invitations-next"
              >
                다음
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
