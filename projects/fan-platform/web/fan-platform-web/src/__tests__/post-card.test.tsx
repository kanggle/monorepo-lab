import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PostCard } from '@/features/post/ui/PostCard';
import type { FeedItem } from '@/entities/post';

const baseItem: FeedItem = {
  postId: 'p1',
  postType: 'ARTIST_POST',
  visibility: 'PUBLIC',
  authorAccountId: 'a1',
  title: '봄 컴백 D-1',
  bodyPreview: '드디어 내일이에요!',
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
