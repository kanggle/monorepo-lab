/**
 * 공개 피드 카드 — **카드 어디를 눌러도 상세로** (TASK-FAN-FE-022).
 *
 * 🔴 jsdom 은 CSS 로 늘린 클릭 영역(`::after` · `z-index`)을 **재지 못한다.** 그래서 이 파일이 재는 것은
 *    «그렇게 되도록 짜였는가» 까지다: 상세 링크가 정확히 하나, 중첩 링크 0, 덮개 클래스, 안쪽 링크가
 *    덮개 위에 있다는 표시. «사진을 눌렀더니 상세로 갔다» 는 티켓 AC-4 의 **라이브 브라우저**가 잰다.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { PublicPost } from '@demo/public-data';
import { PublicPostCard } from '../ui/PublicPostCard';

const open = {
  id: 'p-open',
  artistId: 'a-1',
  artistStageName: '루미',
  title: '새 싱글 발매 안내',
  body: '공개 본문',
  visibility: 'PUBLIC',
  locked: false,
  publishedAt: '2026-02-10T08:00:00Z',
  imageUrls: ['https://images.example.com/a.jpg'],
} as PublicPost;

const locked = {
  ...open,
  id: 'p-locked',
  title: '멤버십 전용 — 작업실 이야기',
  body: null,
  visibility: 'MEMBERS_ONLY',
  locked: true,
  imageUrls: [],
} as unknown as PublicPost;

describe('PublicPostCard — 카드 어디를 눌러도 상세로 (TASK-FAN-FE-022)', () => {
  it('🔴 상세로 가는 링크는 정확히 하나이고, 이름은 글 제목이며, 카드 전체를 덮는다', () => {
    const { container } = render(<PublicPostCard post={open} />);
    const detail = container.querySelectorAll('a[href="/posts/p-open"]');
    expect(detail).toHaveLength(1);
    expect(screen.getByRole('link', { name: '새 싱글 발매 안내' })).toBe(detail[0]);

    const cls = detail[0].getAttribute('class') ?? '';
    for (const token of ['after:absolute', 'after:inset-0', 'after:z-[1]']) expect(cls).toContain(token);
    // 덮개가 붙을 기준 — 카드 루트가 `relative` 가 아니면 `inset-0` 은 페이지 전체를 덮는다.
    expect(container.querySelector('[data-testid="public-post-card"]')?.className).toContain('relative');
  });

  it('🔴 `<a>` 안의 `<a>` 가 없다 · 「자세히 보기」 글자가 없다', () => {
    for (const post of [open, locked]) {
      const { container, unmount } = render(<PublicPostCard post={post} />);
      expect(container.querySelector('a a')).toBeNull();
      expect(container.textContent ?? '').not.toContain('자세히 보기');
      unmount();
    }
  });

  it('🔴 대조군: 아티스트 배지는 자기 목적지로 가고 덮개 위에 있다', () => {
    const { container } = render(<PublicPostCard post={open} />);
    const badge = container.querySelector('a[href="/artists/a-1"]');
    expect(badge).not.toBeNull();
    expect(badge?.className).toContain('relative');
    expect(badge?.className).toContain('z-10');
  });

  it('🔴 잠긴 카드: 상세 링크 하나 + 「멤버십 안내 보기」는 /membership 이고 덮개 위에 있다', () => {
    const { container } = render(<PublicPostCard post={locked} />);
    expect(container.querySelectorAll('a[href="/posts/p-locked"]')).toHaveLength(1);
    const membership = container.querySelector('a[href="/membership"]');
    expect(membership?.textContent).toContain('멤버십 안내 보기');
    expect(membership?.className).toContain('z-10');
  });
});
