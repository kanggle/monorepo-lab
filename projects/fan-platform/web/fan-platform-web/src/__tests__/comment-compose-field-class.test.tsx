import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * TASK-FAN-FE-034 — the comment composer and the fan post composer's two
 * fields use `FORM_FIELD_CLASS` (TASK-FAN-FE-026), so an OS dark scheme does
 * not paint a dark native background under their `ink-900` text.
 *
 * Wiring only: jsdom computes no `color-scheme`, so the computed colour is not
 * observable here. The two tokens asserted are the two the defect lacked —
 * the background, and `[color-scheme:light]` for the native parts.
 */

const { addComment, deleteComment, loadMoreComments, publishFanPost, push } = vi.hoisted(() => ({
  addComment: vi.fn(),
  deleteComment: vi.fn(),
  loadMoreComments: vi.fn(),
  publishFanPost: vi.fn(),
  push: vi.fn(),
}));
vi.mock('@/features/post/api/comments', () => ({ addComment, deleteComment, loadMoreComments }));
vi.mock('@/features/post/api/actions', () => ({ publishFanPost }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push }) }));

import { CommentPanel } from '@/features/post/ui/CommentPanel';
import { ComposeForm } from '@/features/post/ui/ComposeForm';
import { FORM_FIELD_CLASS } from '@/shared/ui/formField';

function expectLightField(el: HTMLElement) {
  const classes = el.className.split(/\s+/);
  expect(classes).toContain('bg-white');
  expect(classes).toContain('[color-scheme:light]');
  expect(el.className).toBe(FORM_FIELD_CLASS);
}

describe('TASK-FAN-FE-034 — composer fields use FORM_FIELD_CLASS', () => {
  it('comment input', () => {
    render(
      <CommentPanel
        postId="p-1"
        initialComments={[]}
        initialPage={0}
        initialHasNext={false}
        viewerAccountId="acc-me"
      />,
    );
    expectLightField(screen.getByTestId('comment-input'));
  });

  it('fan post title and body', () => {
    render(<ComposeForm />);
    expectLightField(screen.getByTestId('compose-title'));
    expectLightField(screen.getByTestId('compose-body'));
  });
});
