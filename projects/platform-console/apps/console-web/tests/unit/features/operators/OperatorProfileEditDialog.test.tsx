import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OperatorProfileEditDialog } from '@/features/operators/components/OperatorProfileEditDialog';

/**
 * `OperatorProfileEditDialog` — admin-on-behalf-of profile-edit dialog
 * (TASK-PC-FE-017). Per the task § Decision authority + § Scope:
 *
 *   - (a) initial render — empty input, Clear unchecked, Save disabled
 *     (no current-value pre-population in v1; § Decision authority
 *     "Why no current-value pre-population in v1");
 *   - (b) typed value + reason → click Save → `onConfirm("<value>",
 *     "<reason>")` called once with the trimmed values;
 *   - (c) Clear toggle ON + reason → click Save → `onConfirm(null,
 *     "<reason>")` called once (NOT empty string — producer rejects
 *     `""` as `400 INVALID_REQUEST`; explicit `null` is the documented
 *     clear semantic);
 *   - (d) Save is disabled when the reason is empty (reason-required
 *     fail-safe — mirrors the per-endpoint header matrix obligation);
 *   - (e) Save is disabled when input is whitespace-only AND Clear is OFF
 *     (the producer + proxy reject; UI fail-fast).
 *
 *   Plus: the dialog is button-explicit (no auto-save on input change)
 *   and uses a SEPARATE component (not extending OperatorConfirmDialog)
 *   per the task's Decision authority.
 */

const HINT_TEXT =
  /현재 값은 보이지 않습니다.*입력값이 그대로 저장됩니다/;

describe('OperatorProfileEditDialog (a) — initial render', () => {
  it('opens empty with Clear OFF, Save disabled, and shows the "current value not visible" hint', () => {
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="target@example.com"
        onConfirm={() => undefined}
        onCancel={() => undefined}
      />,
    );

    const input = screen.getByTestId(
      'operator-profile-edit-value',
    ) as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.value).toBe('');
    expect(input).not.toBeDisabled();

    const clear = screen.getByTestId(
      'operator-profile-edit-clear',
    ) as HTMLInputElement;
    expect(clear.checked).toBe(false);

    const save = screen.getByTestId('operator-profile-edit-save');
    expect(save).toBeDisabled();

    // The v1 design hint is visible (no current-value pre-population).
    expect(
      screen.getByTestId('operator-profile-edit-hint'),
    ).toHaveTextContent(HINT_TEXT);

    // Title includes the operatorIdLabel for context.
    expect(
      screen.getByText(/프로파일 편집 — target@example.com/),
    ).toBeInTheDocument();
  });

  it('renders role="dialog" + aria-modal for accessibility', () => {
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="op-1"
        onConfirm={() => undefined}
        onCancel={() => undefined}
      />,
    );
    const dlg = screen.getByTestId('operator-profile-edit-dialog');
    expect(dlg).toHaveAttribute('role', 'dialog');
    expect(dlg).toHaveAttribute('aria-modal', 'true');
  });
});

describe('OperatorProfileEditDialog (b) — typed value + reason → onConfirm string', () => {
  it('calls onConfirm with the trimmed value and trimmed reason on Save', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="target@example.com"
        onConfirm={onConfirm}
        onCancel={() => undefined}
      />,
    );

    const input = screen.getByTestId('operator-profile-edit-value');
    await user.type(input, 'uuid-7');
    const reason = screen.getByTestId('operator-profile-edit-reason');
    await user.type(reason, '  policy realign  ');

    // Save is button-explicit — typing does NOT call onConfirm.
    expect(onConfirm).not.toHaveBeenCalled();

    const save = screen.getByTestId('operator-profile-edit-save');
    expect(save).not.toBeDisabled();
    await user.click(save);

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledWith('uuid-7', 'policy realign');
  });
});

describe('OperatorProfileEditDialog (c) — Clear toggle → onConfirm(null, reason)', () => {
  it('Clear ON + reason → Save calls onConfirm(null, "<reason>") (NOT empty string)', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="target@example.com"
        onConfirm={onConfirm}
        onCancel={() => undefined}
      />,
    );

    const clear = screen.getByTestId(
      'operator-profile-edit-clear',
    ) as HTMLInputElement;
    await user.click(clear);
    expect(clear.checked).toBe(true);

    // The text input is disabled while Clear is ON — the value is ignored.
    const input = screen.getByTestId(
      'operator-profile-edit-value',
    ) as HTMLInputElement;
    expect(input).toBeDisabled();

    const reason = screen.getByTestId('operator-profile-edit-reason');
    await user.type(reason, 'clearing per ticket #99');

    const save = screen.getByTestId('operator-profile-edit-save');
    expect(save).not.toBeDisabled();
    await user.click(save);

    expect(onConfirm).toHaveBeenCalledTimes(1);
    // Explicit null (NOT empty string — the producer rejects "").
    expect(onConfirm).toHaveBeenCalledWith(null, 'clearing per ticket #99');
  });
});

describe('OperatorProfileEditDialog (d) — Save disabled when reason empty', () => {
  it('typed value but empty reason → Save disabled; forced click is a no-op', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="target@example.com"
        onConfirm={onConfirm}
        onCancel={() => undefined}
      />,
    );

    const input = screen.getByTestId('operator-profile-edit-value');
    await user.type(input, 'uuid-7');
    // No reason typed.

    const save = screen.getByTestId('operator-profile-edit-save');
    expect(save).toBeDisabled();
    expect(
      screen.getByTestId('operator-profile-edit-reason-required'),
    ).toBeInTheDocument();

    // Forced click is a no-op (disabled button).
    await user.click(save);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('Clear ON but reason empty → Save still disabled (reason-required is universal)', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="target@example.com"
        onConfirm={onConfirm}
        onCancel={() => undefined}
      />,
    );

    const clear = screen.getByTestId('operator-profile-edit-clear');
    await user.click(clear);

    const save = screen.getByTestId('operator-profile-edit-save');
    expect(save).toBeDisabled();

    await user.click(save);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});

describe('OperatorProfileEditDialog (e) — Save disabled on whitespace-only + Clear OFF', () => {
  it('whitespace-only value with reason → Save disabled (Clear OFF)', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="target@example.com"
        onConfirm={onConfirm}
        onCancel={() => undefined}
      />,
    );

    const input = screen.getByTestId('operator-profile-edit-value');
    await user.type(input, '   ');
    const reason = screen.getByTestId('operator-profile-edit-reason');
    await user.type(reason, 'policy realign');

    const save = screen.getByTestId('operator-profile-edit-save');
    expect(save).toBeDisabled();

    await user.click(save);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('internal whitespace value with reason → Save disabled (Clear OFF) + client error visible', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="target@example.com"
        onConfirm={onConfirm}
        onCancel={() => undefined}
      />,
    );

    const input = screen.getByTestId('operator-profile-edit-value');
    await user.type(input, 'acc 7');
    const reason = screen.getByTestId('operator-profile-edit-reason');
    await user.type(reason, 'r');

    expect(
      screen.getByTestId('operator-profile-edit-value-error'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('operator-profile-edit-save')).toBeDisabled();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});

describe('OperatorProfileEditDialog — error / pending / cancel', () => {
  it('renders an inline server error from the last attempt', () => {
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="op-1"
        errorMessage="동시 변경 충돌 — 다시 시도하세요"
        onConfirm={() => undefined}
        onCancel={() => undefined}
      />,
    );
    expect(
      screen.getByTestId('operator-profile-edit-error'),
    ).toHaveTextContent('동시 변경 충돌');
  });

  it('shows the pending state on the Save button while a call is in flight', () => {
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="op-1"
        pending
        onConfirm={() => undefined}
        onCancel={() => undefined}
      />,
    );
    expect(
      screen.getByTestId('operator-profile-edit-save'),
    ).toHaveTextContent('처리 중');
  });

  it('Cancel calls onCancel', async () => {
    const onCancel = vi.fn();
    const user = userEvent.setup();
    render(
      <OperatorProfileEditDialog
        open
        operatorIdLabel="op-1"
        onConfirm={() => undefined}
        onCancel={onCancel}
      />,
    );
    await user.click(screen.getByTestId('operator-profile-edit-cancel'));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
