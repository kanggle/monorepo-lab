import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * TASK-FAN-FE-033 — a viewer who already reacted could not take the reaction
 * back: `ReactionBar.onClick` PUT the same type again (an idempotent no-op on
 * the server) and `removeReaction` (DELETE) had no caller. Style copied from
 * `follow-button-optimistic.test.tsx` (deferred promise so assertions run
 * strictly before the server action settles) and `reaction-restore.test.tsx`
 * (server-only modules mocked wholesale).
 */

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const { setReaction, removeReaction } = vi.hoisted(() => ({
  setReaction: vi.fn(),
  removeReaction: vi.fn(),
}));
vi.mock('@/features/post/api/reactions', () => ({ setReaction, removeReaction }));

import { ReactionBar } from '@/features/post/ui/ReactionBar';

const pressed = (label: string) => screen.getByLabelText(label).getAttribute('aria-pressed');

describe('ReactionBar — 눌린 반응을 다시 누르면 취소 (TASK-FAN-FE-033)', () => {
  beforeEach(() => {
    setReaction.mockReset();
    removeReaction.mockReset();
  });

  it('🔴 AC-1: 눌린 LIKE 를 다시 누르면 removeReaction 1회 · setReaction 미호출 · 즉시 aria-pressed=false', async () => {
    const { promise } = deferred<void>();
    removeReaction.mockReturnValue(promise); // still pending when we assert

    const user = userEvent.setup();
    render(<ReactionBar postId="p-1" totalReactions={3} initialReaction="LIKE" />);
    expect(pressed('좋아요')).toBe('true');

    await user.click(screen.getByLabelText('좋아요'));

    expect(removeReaction).toHaveBeenCalledTimes(1);
    expect(removeReaction).toHaveBeenCalledWith('p-1');
    expect(setReaction).not.toHaveBeenCalled();
    expect(pressed('좋아요')).toBe('false');
  });

  it('AC-2: 눌린 LIKE 에서 LOVE 클릭 → setReaction(LOVE) 1회 · removeReaction 미호출 · LOVE 만 true', async () => {
    const { promise } = deferred<void>();
    setReaction.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<ReactionBar postId="p-1" totalReactions={3} initialReaction="LIKE" />);

    await user.click(screen.getByLabelText('사랑해요'));

    expect(setReaction).toHaveBeenCalledTimes(1);
    expect(setReaction).toHaveBeenCalledWith('p-1', 'LOVE');
    expect(removeReaction).not.toHaveBeenCalled();
    expect(pressed('사랑해요')).toBe('true');
    expect(pressed('좋아요')).toBe('false');
    expect(pressed('대박')).toBe('false');
    expect(pressed('슬퍼요')).toBe('false');
  });

  it('AC-3 대조군: 반응이 없을 때 클릭 → setReaction 1회 · removeReaction 미호출 · 눌림', async () => {
    const { promise } = deferred<void>();
    setReaction.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<ReactionBar postId="p-1" totalReactions={0} initialReaction={null} />);

    await user.click(screen.getByLabelText('좋아요'));

    expect(setReaction).toHaveBeenCalledTimes(1);
    expect(setReaction).toHaveBeenCalledWith('p-1', 'LIKE');
    expect(removeReaction).not.toHaveBeenCalled();
    expect(pressed('좋아요')).toBe('true');
  });

  it('🔴 AC-4 롤백: removeReaction 이 reject 하면 눌림이 복원된다', async () => {
    const { promise, reject } = deferred<void>();
    removeReaction.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<ReactionBar postId="p-1" totalReactions={3} initialReaction="LIKE" />);

    await user.click(screen.getByLabelText('좋아요'));
    expect(pressed('좋아요')).toBe('false'); // optimistic clear happened

    reject(new Error('network error'));

    await vi.waitFor(() => expect(pressed('좋아요')).toBe('true'));
  });

  it('롤백 짝: 전환(LIKE→LOVE) 이 실패하면 LIKE 로 되돌아온다', async () => {
    const { promise, reject } = deferred<void>();
    setReaction.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<ReactionBar postId="p-1" totalReactions={3} initialReaction="LIKE" />);

    await user.click(screen.getByLabelText('사랑해요'));
    expect(pressed('사랑해요')).toBe('true');

    reject(new Error('network error'));

    await vi.waitFor(() => expect(pressed('좋아요')).toBe('true'));
    expect(pressed('사랑해요')).toBe('false');
  });

  it('회귀: 연타는 isPending 동안 disabled 로 막혀 DELETE 가 한 번만 나간다', async () => {
    const { promise } = deferred<void>();
    removeReaction.mockReturnValue(promise);

    const user = userEvent.setup();
    render(<ReactionBar postId="p-1" totalReactions={3} initialReaction="LIKE" />);
    const button = screen.getByLabelText('좋아요');

    await user.click(button);
    expect(button).toBeDisabled();
    await user.click(button);

    expect(removeReaction).toHaveBeenCalledTimes(1);
  });
});
