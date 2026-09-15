import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PostCard } from '@/features/post/ui/PostCard';
import type { FeedItem } from '@/entities/post';

const PHOTO_A = 'https://images.example.com/a.jpg';
const PHOTO_B = 'https://images.example.com/b.jpg';

const baseItem: FeedItem = {
  postId: 'p1',
  postType: 'ARTIST_POST',
  visibility: 'PUBLIC',
  authorAccountId: 'a1',
  title: '봄 컴백 D-1',
  bodyPreview: '드디어 내일이에요!',
  mediaRefs: [],
  commentCount: 3,
  reactionCount: 12,
  publishedAt: '2026-05-03T00:00:00Z',
  locked: false,
};

describe('PostCard', () => {
  it('renders ARTIST badge for ARTIST_POST type', () => {
    render(<PostCard item={baseItem} />);
    expect(screen.getByText('ARTIST')).toBeInTheDocument();
  });

  it('renders FAN badge for FAN_POST type', () => {
    render(<PostCard item={{ ...baseItem, postType: 'FAN_POST' }} />);
    expect(screen.getByText('FAN')).toBeInTheDocument();
  });

  it('renders title and body preview when not locked', () => {
    render(<PostCard item={baseItem} />);
    expect(screen.getByText('봄 컴백 D-1')).toBeInTheDocument();
    expect(screen.getByText('드디어 내일이에요!')).toBeInTheDocument();
  });

  it('renders subscribe gate when locked is true', () => {
    render(
      <PostCard
        item={{
          ...baseItem,
          visibility: 'MEMBERS_ONLY',
          title: null,
          bodyPreview: null,
          locked: true,
        }}
      />,
    );
    expect(screen.getByText(/멤버십이 필요한 포스트입니다/)).toBeInTheDocument();
    expect(screen.queryByText('봄 컴백 D-1')).not.toBeInTheDocument();
  });

  it('shows membership tier badge for MEMBERS_ONLY visibility', () => {
    render(<PostCard item={{ ...baseItem, visibility: 'MEMBERS_ONLY' }} />);
    expect(screen.getByText('멤버 전용')).toBeInTheDocument();
  });

  it('exposes a self-test selector for E2E', () => {
    render(<PostCard item={baseItem} />);
    expect(screen.getByTestId('post-card')).toBeInTheDocument();
  });
});

describe('PostCard — 사진 (TASK-MONO-679)', () => {
  it('🔵 열린 항목은 첫 장을 그리고, 여러 장이면 남은 수를 알린다', () => {
    const { container } = render(<PostCard item={{ ...baseItem, mediaRefs: [PHOTO_A, PHOTO_B] }} />);
    const imgs = container.querySelectorAll('img');
    expect(imgs).toHaveLength(1);
    expect(imgs[0].getAttribute('src')).toBe(PHOTO_A);
    expect(imgs[0].getAttribute('alt')).toBe('봄 컴백 D-1');
    expect(screen.getByText('+1')).toBeInTheDocument();
  });

  it('🔴🔴 잠긴 항목은 사진이 실려 와도 `<img>` 를 하나도 그리지 않는다 — 서버가 비우지 못한 날에도', () => {
    // 서버는 잠긴 항목의 mediaRefs 를 [] 로 보낸다. 그 계약이 깨진 **불가능한 입력**으로 화면 계층만 잰다
    // — 커밋된 계약만으로 단언하면 «그릴 사진이 없어서 안 그렸다» 와 구별되지 않는다.
    const { container } = render(
      <PostCard
        item={{ ...baseItem, visibility: 'MEMBERS_ONLY', title: null, bodyPreview: null, locked: true, mediaRefs: [PHOTO_A] }}
      />,
    );
    expect(container.querySelectorAll('img')).toHaveLength(0);
    expect(container.innerHTML).not.toContain(PHOTO_A);
  });

  it('🔴 사진 필드가 없는 옛 백엔드 응답도 깨지지 않는다 — 사진 자리 없이 예전 카드 그대로', () => {
    const legacy: FeedItem = { ...baseItem };
    delete legacy.mediaRefs; // 옛 응답에는 키 자체가 없다 — `undefined` 값이 아니라 부재다
    expect('mediaRefs' in legacy).toBe(false);
    const { container } = render(<PostCard item={legacy} />);
    expect(container.querySelectorAll('[data-testid="post-image-frame"]')).toHaveLength(0);
    expect(screen.getByText('드디어 내일이에요!')).toBeInTheDocument();
  });

  it('사진 0장이면 틀을 그리지 않는다', () => {
    const { container } = render(<PostCard item={baseItem} />);
    expect(container.querySelectorAll('[data-testid="post-image-frame"]')).toHaveLength(0);
  });
});

describe('PostCard — 카드 어디를 눌러도 상세로 (TASK-FAN-FE-022)', () => {
  // 🔵 jsdom 은 늘린 클릭 영역을 못 잰다 — 여기서는 «그렇게 짜였는가» 까지(티켓 AC-3), 실제 클릭은 라이브(AC-4).
  it('🔴 제목이 있는 열린 항목: 상세 링크는 제목 하나이고 카드 전체를 덮는다', () => {
    const { container } = render(<PostCard item={{ ...baseItem, mediaRefs: [PHOTO_A] }} />);
    const detail = container.querySelectorAll('a[href="/posts/p1"]');
    expect(detail).toHaveLength(1);
    expect(screen.getByRole('link', { name: '봄 컴백 D-1' })).toBe(detail[0]);
    for (const token of ['after:absolute', 'after:inset-0', 'after:z-[1]']) {
      expect(detail[0].getAttribute('class') ?? '').toContain(token);
    }
    expect(screen.getByTestId('post-card').className).toContain('relative');
  });

  it('🔴 잠긴 항목(`title: null`)도 상세 링크가 하나이고 **이름이 비지 않는다**', () => {
    const { container } = render(
      <PostCard item={{ ...baseItem, visibility: 'MEMBERS_ONLY', title: null, bodyPreview: null, locked: true }} />,
    );
    expect(container.querySelectorAll('a[href="/posts/p1"]')).toHaveLength(1);
    expect(screen.getByRole('link', { name: '멤버십이 필요한 포스트' })).toHaveAttribute('href', '/posts/p1');
  });

  it('제목이 없는 열린 항목도 이름 있는 링크 하나', () => {
    const { container } = render(<PostCard item={{ ...baseItem, title: null }} />);
    expect(container.querySelectorAll('a[href="/posts/p1"]')).toHaveLength(1);
    expect(screen.getByRole('link', { name: '포스트 보기' })).toBeInTheDocument();
  });

  it('🔴 중첩 링크 0 · 「자세히 보기」 글자 0 · 댓글·반응 수는 그대로', () => {
    const { container } = render(<PostCard item={baseItem} />);
    expect(container.querySelector('a a')).toBeNull();
    expect(container.textContent ?? '').not.toContain('자세히 보기');
    expect(screen.getByText('댓글 3')).toBeInTheDocument();
    expect(screen.getByText('반응 12')).toBeInTheDocument();
  });
});
