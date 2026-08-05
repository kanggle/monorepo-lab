import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

/**
 * TASK-FAN-FE-016 — the compose screen.
 *
 * Two of these are about what the screen must NOT do: no visibility control (AC-2) and no
 * compose entry point for an anonymous visitor (AC-3). Both are absences, and an absence
 * nobody asserts is one refactor away from coming back.
 */

const { publishFanPost, push } = vi.hoisted(() => ({
  publishFanPost: vi.fn(),
  push: vi.fn(),
}));

// Mocked wholesale rather than with importActual: the real module reaches
// `@/shared/auth/session`, which imports `server-only` and cannot load in jsdom. The
// action's own behaviour is covered separately in fan-post-publish-action.test.ts.
vi.mock('@/features/post/api/actions', () => ({ publishFanPost }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push }) }));

import { ComposeForm } from '@/features/post/ui/ComposeForm';

beforeEach(() => {
  vi.clearAllMocks();
});

describe('ComposeForm', () => {
  it('본문을 입력하고 게시하면 작성 액션이 호출된다', async () => {
    publishFanPost.mockResolvedValue({ ok: true, postId: 'p-1' });
    render(<ComposeForm />);

    fireEvent.change(screen.getByTestId('compose-title'), { target: { value: '첫 글' } });
    fireEvent.change(screen.getByTestId('compose-body'), { target: { value: '안녕하세요' } });
    fireEvent.submit(screen.getByTestId('compose-form'));

    await waitFor(() => expect(publishFanPost).toHaveBeenCalledWith('첫 글', '안녕하세요'));
  });

  it('🔴 성공하면 작성한 글의 상세로 이동한다 — "저장됨" 으로 끝나지 않는다', async () => {
    publishFanPost.mockResolvedValue({ ok: true, postId: 'p-42' });
    render(<ComposeForm />);

    fireEvent.change(screen.getByTestId('compose-body'), { target: { value: '본문' } });
    fireEvent.submit(screen.getByTestId('compose-form'));

    // 피드는 팔로우 기반이라 자기 글이 자기 피드에 뜨지 않는다. 이동이 없으면 글이
    // 사라진 것처럼 보인다 — 티켓이 Goal 에 "다시 볼 수 있다" 를 넣은 이유다.
    await waitFor(() => expect(push).toHaveBeenCalledWith('/posts/p-42'));
  });

  it('실패하면 이동하지 않고 메시지를 화면에 남긴다', async () => {
    publishFanPost.mockResolvedValue({ ok: false, message: '저장 실패' });
    render(<ComposeForm />);

    fireEvent.change(screen.getByTestId('compose-body'), { target: { value: '본문' } });
    fireEvent.submit(screen.getByTestId('compose-form'));

    await waitFor(() => expect(screen.getByTestId('compose-error')).toHaveTextContent('저장 실패'));
    expect(push).not.toHaveBeenCalled();
  });

  it('본문이 비어 있으면 게시할 수 없다', () => {
    render(<ComposeForm />);
    expect(screen.getByRole('button', { name: '게시하기' })).toBeDisabled();
  });

  it('🔴 AC-2: 가시성 선택 컨트롤이 화면에 없다', () => {
    render(<ComposeForm />);

    // 팬의 유료 글은 읽는 사람도, 수익도 없다(멤버십은 플랫폼 스코프다). 백엔드는
    // 아직 이것을 허용하므로 — `PublishPostUseCase` 는 postType 만 검사한다 — 화면이
    // 유일한 방벽이고, 그래서 그 부재를 단언한다.
    expect(screen.queryByText(/멤버.*전용/)).toBeNull();
    expect(screen.queryByText('PREMIUM')).toBeNull();
    expect(screen.queryByRole('combobox')).toBeNull();
    expect(screen.queryByRole('radio')).toBeNull();
    // 대신 무엇이 되는지는 사용자에게 말한다.
    expect(screen.getByText('작성한 글은 전체 공개됩니다.')).toBeInTheDocument();
  });
});

describe('AC-3 음성 대조 — 진입점은 인증된 사용자에게만', () => {
  it('Header 소스에서 글쓰기/내 글 링크가 authed 분기 안에만 있다', async () => {
    const { readFileSync } = await import('node:fs');
    const { join } = await import('node:path');
    const src = readFileSync(join(process.cwd(), 'src/widgets/header/Header.tsx'), 'utf8');

    // 비어 있지 않은지 먼저 확인한다 — 못 읽은 파일은 어떤 정규식도 통과시킨다.
    expect(src.length).toBeGreaterThan(0);
    expect(src).toContain('{authed ? (');

    const start = src.indexOf('{authed ? (');
    const end = src.indexOf('</>', start);
    expect(end).toBeGreaterThan(start);
    const authedBranch = src.slice(start, end);

    expect(authedBranch).toContain('href="/compose"');
    expect(authedBranch).toContain('href="/me/posts"');

    // 그리고 그 밖 어디에도 없다. 익명 사용자에게 보이는 두 번째 링크가 생기면 잡힌다.
    const outside = src.slice(0, start) + src.slice(end);
    expect(outside).not.toContain('href="/compose"');
    expect(outside).not.toContain('href="/me/posts"');
  });
});
