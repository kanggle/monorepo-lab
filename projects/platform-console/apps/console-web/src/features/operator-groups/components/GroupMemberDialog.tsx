'use client';

import { useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import type { OperatorSummary } from '@/shared/api/iam-operators-types';
import { useGroupMemberCandidates } from '../hooks/use-group-member-candidates';
import { GroupReasonDialog } from './GroupReasonDialog';

/**
 * Add-member dialog (TASK-PC-FE-250 raw-UUID input → TASK-PC-FE-317 picker).
 *
 * Lists the GROUP's `tenantId`'s operators (NEVER the console's active
 * tenant — a SUPER_ADMIN may be viewing a group outside their active tenant;
 * task Failure Scenario / AC-1) via `useGroupMemberCandidates`, filters them
 * client-side by email/displayName (the producer's list has no search param),
 * excludes/disables operators who are already members (AC-3), and shows the
 * chosen operator's name + email in the reason-confirm step.
 *
 * AC-0 producer-mismatch finding (see the task's Implementation Record for
 * the code citation): the list API scopes by `tenantId` HOME **∪**
 * assignment, but `GroupAdminUseCase.addMember` only accepts a member whose
 * HOME tenant (`AdminOperatorJpaEntity.tenantId`) equals the group's tenant —
 * narrower. The list response does NOT expose each operator's home tenantId
 * (`OperatorSummarySchema` has no `tenantId` field), so this picker cannot
 * filter out an assignment-only (non-home) candidate client-side without a
 * producer change (out of scope — task Out of Scope explicitly bars a
 * producer change). An assignment-only pick still surfaces the existing
 * `422 GROUP_MEMBER_TENANT_MISMATCH` verbatim via `error`, exactly as the
 * pre-317 raw-UUID path already did — this is a pre-existing contract gap,
 * not a regression introduced here.
 *
 * SUSPENDED operators (AC-0(b): the producer's `addMember` has no status
 * check) are shown and ARE selectable — the picker does not invent a
 * restriction the producer doesn't enforce.
 *
 * Fallback (AC-5): when the candidate list fails to load (403/503/network —
 * `useGroupMemberCandidates().failed`), the dialog falls back to the original
 * raw operator-ID text input so the add-member flow never becomes unusable.
 */
export interface GroupMemberDialogProps {
  groupName: string;
  /** The GROUP's tenant — the picker queries THIS, not the active tenant. */
  groupTenantId: string;
  /** operatorIds already members of this group — excluded from selection (AC-3). */
  existingMemberIds: string[];
  pending: boolean;
  error: string | null;
  onConfirm: (operatorId: string, reason: string) => void;
  onCancel: () => void;
}

function matchesSearch(op: OperatorSummary, query: string): boolean {
  if (query === '') return true;
  return (
    op.email.toLowerCase().includes(query) ||
    op.displayName.toLowerCase().includes(query)
  );
}

export function GroupMemberDialog({
  groupName,
  groupTenantId,
  existingMemberIds,
  pending,
  error,
  onConfirm,
  onCancel,
}: GroupMemberDialogProps) {
  const candidates = useGroupMemberCandidates(groupTenantId);
  const fallback = candidates.failed;

  const [search, setSearch] = useState('');
  const [selectedOperator, setSelectedOperator] =
    useState<OperatorSummary | null>(null);
  const [manualOperatorId, setManualOperatorId] = useState('');
  const [confirming, setConfirming] = useState(false);

  const existingSet = useMemo(
    () => new Set(existingMemberIds),
    [existingMemberIds],
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return candidates.items.filter((op) => matchesSearch(op, q));
  }, [candidates.items, search]);

  const submitOk = fallback
    ? manualOperatorId.trim().length > 0
    : selectedOperator !== null;

  const confirmDescription = fallback
    ? `「${groupName}」 그룹에 운영자(${manualOperatorId.trim()})를 추가합니다.`
    : `「${groupName}」 그룹에 운영자 ${selectedOperator?.displayName ?? ''} (${
        selectedOperator?.email ?? ''
      })를 추가합니다.`;

  function handleConfirm(reason: string) {
    const operatorId = fallback
      ? manualOperatorId.trim()
      : (selectedOperator?.operatorId ?? '');
    onConfirm(operatorId, reason);
    setConfirming(false);
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="group-member-add-title"
    >
      <div className="w-full max-w-md space-y-3 rounded-lg border border-border bg-background p-6 shadow-lg">
        <h2
          id="group-member-add-title"
          className="text-lg font-semibold text-foreground"
        >
          멤버 추가
        </h2>
        <p className="text-sm text-muted-foreground">
          「{groupName}」 그룹에 운영자를 추가합니다. 그룹의 현행 grant 가 새
          멤버에게 fan-out 됩니다 (본인 스코프 밖 grant 는 서버가 거부).
        </p>

        {fallback ? (
          <>
            <p
              data-testid="group-member-picker-fallback-notice"
              className="text-sm text-muted-foreground"
            >
              운영자 목록을 불러오지 못했습니다. 운영자 ID 를 직접 입력하세요.
            </p>
            <div className="flex flex-col gap-1">
              <label
                htmlFor="group-member-operator"
                className="text-sm font-medium text-foreground"
              >
                운영자 ID
              </label>
              <input
                id="group-member-operator"
                value={manualOperatorId}
                onChange={(e) => setManualOperatorId(e.target.value)}
                data-testid="group-member-operator-input"
                className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                placeholder="operator-uuid"
              />
            </div>
          </>
        ) : (
          <>
            <div className="flex flex-col gap-1">
              <label
                htmlFor="group-member-search"
                className="text-sm font-medium text-foreground"
              >
                운영자 검색
              </label>
              <input
                id="group-member-search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                data-testid="group-member-search-input"
                className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                placeholder="이메일 또는 이름으로 검색"
              />
            </div>

            {candidates.loading ? (
              <p className="text-sm text-muted-foreground">불러오는 중…</p>
            ) : filtered.length === 0 ? (
              <p
                data-testid="group-member-candidates-empty"
                className="text-sm text-muted-foreground"
              >
                일치하는 운영자 없음 — 운영자 관리에서 먼저 등록
              </p>
            ) : (
              <ul
                data-testid="group-member-candidates-list"
                className="max-h-56 divide-y divide-border overflow-y-auto rounded-md border border-border"
              >
                {filtered.map((op) => {
                  const isMember = existingSet.has(op.operatorId);
                  const isSelected = selectedOperator?.operatorId === op.operatorId;
                  return (
                    <li key={op.operatorId}>
                      <button
                        type="button"
                        onClick={() => setSelectedOperator(op)}
                        disabled={isMember}
                        aria-pressed={isSelected}
                        data-testid={`group-member-candidate-${op.operatorId}`}
                        className={`flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50 ${
                          isSelected ? 'bg-accent' : 'hover:bg-accent'
                        }`}
                      >
                        <span className="min-w-0 truncate">
                          <span className="font-medium text-foreground">
                            {op.displayName}
                          </span>{' '}
                          <span className="text-muted-foreground">
                            · {op.email} · {op.status}
                          </span>
                        </span>
                        {isMember && (
                          <span className="text-xs text-muted-foreground">
                            이미 멤버
                          </span>
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}

            {candidates.hasMore && (
              <div className="flex justify-center">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={candidates.loadMore}
                  disabled={candidates.loadingMore}
                  data-testid="group-member-load-more"
                >
                  {candidates.loadingMore ? '불러오는 중…' : '더 보기'}
                </Button>
              </div>
            )}
          </>
        )}

        {error && (
          <p
            role="alert"
            data-testid="group-member-error"
            className="text-sm text-destructive"
          >
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={pending}>
            취소
          </Button>
          <Button
            onClick={() => setConfirming(true)}
            disabled={!submitOk || pending}
            data-testid="group-member-next"
          >
            다음
          </Button>
        </div>
      </div>

      {confirming && (
        <GroupReasonDialog
          title="멤버 추가"
          description={confirmDescription}
          confirmLabel="추가"
          pending={pending}
          error={error}
          onConfirm={handleConfirm}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  );
}
