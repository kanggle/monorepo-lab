import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MyProfileForm } from '@/features/operators/components/MyProfileForm';

/**
 * `MyProfileForm` — self update-profile UI (TASK-PC-FE-016).
 *
 *  - (a) initial render with a seeded value populates the input;
 *  - (b) edit value + Save → calls `onSubmit(<new>)`;
 *  - (c) Clear button → calls `onSubmit(null)` (NOT empty string —
 *    the producer rejects `""` as `400 INVALID_REQUEST`);
 *  - (d) whitespace-only Save → button disabled / `onSubmit` NOT called
 *    + inline client-side error surfaced.
 *
 *  Plus: the form is button-explicit (no auto-save on input change),
 *  and the producer's body shape `{ operatorContext: { defaultAccountId } }`
 *  is reconstructed at the call site (the form contract only carries the
 *  inner value).
 */

describe('MyProfileForm — initial render with seeded value (a)', () => {
  it('populates the input with the server-rendered initial', () => {
    render(
      <MyProfileForm initial="acc-uuid-7" onSubmit={() => undefined} />,
    );
    const input = screen.getByTestId(
      'my-profile-default-account-id',
    ) as HTMLInputElement;
    expect(input).toBeInTheDocument();
    expect(input.value).toBe('acc-uuid-7');
    // Save is disabled while the value is unchanged from the initial
    // (no-op submit would still write an audit row producer-side, but
    // the form contract is: button enables on dirty + valid).
    expect(screen.getByTestId('my-profile-save')).toBeDisabled();
    // Clear is enabled (the initial is set ⇒ a clear is meaningful).
    expect(screen.getByTestId('my-profile-clear')).not.toBeDisabled();
  });

  it('shows an empty input + disabled Save when the initial is null', () => {
    render(<MyProfileForm initial={null} onSubmit={() => undefined} />);
    const input = screen.getByTestId(
      'my-profile-default-account-id',
    ) as HTMLInputElement;
    expect(input.value).toBe('');
    expect(screen.getByTestId('my-profile-save')).toBeDisabled();
  });
});

describe('MyProfileForm — edit value + Save (b)', () => {
  it('calls onSubmit with the new (trimmed) value on Save', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<MyProfileForm initial={null} onSubmit={onSubmit} />);

    const input = screen.getByTestId('my-profile-default-account-id');
    await user.type(input, 'new-uuid-9');
    // Save is button-explicit — NOT auto-saved on typing.
    expect(onSubmit).not.toHaveBeenCalled();

    const save = screen.getByTestId('my-profile-save');
    expect(save).not.toBeDisabled();
    await user.click(save);

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith('new-uuid-9');
  });

  it('does not auto-save on input change (button-explicit only)', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<MyProfileForm initial="acc-uuid-7" onSubmit={onSubmit} />);

    const input = screen.getByTestId('my-profile-default-account-id');
    await user.clear(input);
    await user.type(input, 'changed');
    // 7+ keystrokes — none should have triggered onSubmit.
    expect(onSubmit).not.toHaveBeenCalled();
  });
});

describe('MyProfileForm — Clear sends explicit null (c)', () => {
  it('Clear → onSubmit(null) (NOT empty string — producer rejects ""’)', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(
      <MyProfileForm initial="acc-uuid-7" onSubmit={onSubmit} />,
    );

    const clear = screen.getByTestId('my-profile-clear');
    expect(clear).not.toBeDisabled();
    await user.click(clear);

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith(null);
    // After clear, the input is empty.
    const input = screen.getByTestId(
      'my-profile-default-account-id',
    ) as HTMLInputElement;
    expect(input.value).toBe('');
  });
});

describe('MyProfileForm — whitespace-only Save (d)', () => {
  it('Save is disabled when the user types only whitespace and surfaces a client error', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<MyProfileForm initial={null} onSubmit={onSubmit} />);

    const input = screen.getByTestId('my-profile-default-account-id');
    await user.type(input, '   ');

    const save = screen.getByTestId('my-profile-save');
    expect(save).toBeDisabled();
    // The button is the gate; even a forced click does NOT call onSubmit.
    await user.click(save);
    expect(onSubmit).not.toHaveBeenCalled();

    // Inline client-side error visible.
    expect(
      screen.getByTestId('my-profile-client-error'),
    ).toBeInTheDocument();
  });

  it('Save is disabled when the input contains internal whitespace', async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<MyProfileForm initial={null} onSubmit={onSubmit} />);

    const input = screen.getByTestId('my-profile-default-account-id');
    await user.type(input, 'acc 7');

    expect(screen.getByTestId('my-profile-save')).toBeDisabled();
    expect(
      screen.getByTestId('my-profile-client-error'),
    ).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});

describe('MyProfileForm — server error / success display', () => {
  it('renders an inline server error from the last attempt', () => {
    render(
      <MyProfileForm
        initial={null}
        onSubmit={() => undefined}
        serverError="동시 변경 충돌"
      />,
    );
    expect(screen.getByTestId('my-profile-server-error')).toHaveTextContent(
      '동시 변경 충돌',
    );
  });

  it('renders the success message after a successful submit', () => {
    render(
      <MyProfileForm
        initial="acc-uuid-7"
        onSubmit={() => undefined}
        succeeded
      />,
    );
    expect(screen.getByTestId('my-profile-success')).toHaveTextContent(
      '저장되었습니다',
    );
  });

  it('shows the pending state on the Save button while a call is in flight', () => {
    render(
      <MyProfileForm initial={null} onSubmit={() => undefined} pending />,
    );
    expect(screen.getByTestId('my-profile-save')).toHaveTextContent('처리 중');
  });
});
