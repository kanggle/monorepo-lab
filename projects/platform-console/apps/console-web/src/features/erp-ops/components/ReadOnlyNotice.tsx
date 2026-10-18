/**
 * TASK-PC-FE-347 — the «조회 전용» wording shared by the ERP screens whose mutation buttons are
 * hidden for lack of the area's `*_WRITE` business role (contract § 2.4.3.4 «Read-only sets hide
 * write buttons»). Same shape as the SCM / Finance / E-commerce notes: `role="note"`,
 * `data-testid="<screen>-read-only-note"`.
 *
 * `action` = what the hidden buttons do, `roleLabel` = the missing permission in operator words
 * («기준정보 변경»). The server stays the final gate; this only keeps a button from leading to a
 * `403`. (Own copy: `features/*` may not import each other, so the E-commerce one is not shared.)
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
