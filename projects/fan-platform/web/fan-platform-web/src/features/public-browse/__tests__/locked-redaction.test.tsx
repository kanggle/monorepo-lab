/**
 * 잠긴 글의 **본문이 렌더된 출력 어디에도 없는가**.
 *
 * ═════════════════════════════════════════════════════════════════════════
 * 🔴🔴 이 파일의 절반은 **일부러 계약을 어긴 입력**으로 돈다
 * ═════════════════════════════════════════════════════════════════════════
 * 커밋된 시드의 잠긴 글은 `body: null` 이다(봉투 계약이 강제한다). 그 데이터만으로
 * "본문이 안 보인다" 를 단언하면 그 단언은 **공허하다** — 애초에 그릴 본문이 없으니
 * 컴포넌트를 통째로 지워도 초록이다. 이 저장소가 여러 번 데인 모양이라 여기서는
 * 반대로 간다: `locked: true` 인데 `body` 에 값이 든 **불가능한 입력**을 만들어,
 * 화면 계층이 **독립적으로** 리댁션하는지를 잰다.
 *
 * 두 겹이 각각 시험된다:
 *   ① 봉투 계약  — 커밋된 시드가 실제로 그 성질을 갖는가(아래 첫 describe).
 *   ② 화면 계층  — 계약이 깨져도 화면이 본문을 안 그리는가(아래 둘째·셋째 describe).
 *
 * ①만 있으면 화면의 결함이 안 보이고, ②만 있으면 시드가 썩는 것이 안 보인다.
 * ═════════════════════════════════════════════════════════════════════════
 */

import { describe, it, expect } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import type { PublicPost } from '@demo/public-data';
import bundled from '@demo/public-data/snapshots/fan.json';
import { PublicPostCard } from '../ui/PublicPostCard';
import { PublicPostDetail } from '../ui/PublicPostDetail';
import { PublicFeedList } from '../ui/PublicFeedList';
import { PublicPostImage } from '../ui/PublicPostImage';

const posts = bundled.data.posts as unknown as PublicPost[];
const lockedPosts = posts.filter((p) => p.locked);

/** 계약을 어긴 «있을 수 없는» 글 — 화면 계층만 남겨 놓고 재기 위한 주입. */
const CONTRABAND = '초-비밀-미공개-데모-가사-절대-노출-금지';
const CONTRABAND_PREVIEW = '초-비밀-미리보기-절대-노출-금지';
/** 잠긴 글에 실려 온 사진 주소 — 경로도 본문이다(TASK-MONO-678). */
const CONTRABAND_IMAGE = 'leak.jpg';
const violating = {
  id: 'violating-1',
  artistId: 'a1',
  artistStageName: '루미',
  title: '[멤버십] 미공개 데모 트랙 이야기',
  body: CONTRABAND,
  visibility: 'MEMBERS_ONLY',
  locked: true,
  publishedAt: '2026-02-09T05:00:00Z',
  imageUrls: ['https://example.invalid/leak.jpg'],
  // 공개 계약에 **없는** 필드. 백엔드 `FeedItem` 에는 있어서, 누군가 타입을 넓히는 날
  // 조용히 흘러 들어올 수 있는 자리다.
  bodyPreview: CONTRABAND_PREVIEW,
} as unknown as PublicPost;

describe('① 커밋된 시드 — 잠긴 글은 본문을 **들고 있지 않다**', () => {
  it('🔵 모집단이 비어 있지 않다 (이 describe 가 공허하지 않다는 증거)', () => {
    expect(posts.length).toBeGreaterThan(0);
    expect(lockedPosts.length).toBeGreaterThan(0);
  });

  it('🔴 잠긴 글의 body 는 전부 null 이고 imageUrls 는 전부 비었다', () => {
    for (const p of lockedPosts) {
      expect(p.body).toBeNull();
      expect(p.imageUrls).toEqual([]);
    }
  });

  it('🔴 어떤 글에도 `bodyPreview` 키가 없다 — 공개 계약에 없는 필드다', () => {
    const withPreview = posts.filter((p) => 'bodyPreview' in p);
    expect(withPreview).toEqual([]);
  });
});

describe('② PublicPostCard — 계약이 깨져도 본문을 안 그린다', () => {
  it('🔴🔴 body/bodyPreview 가 실려 와도 렌더된 HTML 에 **없다**', () => {
    const { container } = render(<PublicPostCard post={violating} />);
    expect(container.innerHTML).not.toContain(CONTRABAND);
    expect(container.innerHTML).not.toContain(CONTRABAND_PREVIEW);
    // textContent 로도 한 번 더 — 속성에 숨는 경우와 텍스트로 나오는 경우가 다르다.
    expect(container.textContent ?? '').not.toContain(CONTRABAND);
  });

  it('🔵 대조군: 잠기지 **않은** 글이면 같은 본문이 그려진다', () => {
    // 이 칸이 없으면 «본문을 아무 때도 안 그린다»(=컴포넌트가 고장남)도 위 칸을 통과한다.
    const open = { ...violating, locked: false, visibility: 'PUBLIC' } as PublicPost;
    const { container } = render(<PublicPostCard post={open} />);
    expect(container.textContent ?? '').toContain(CONTRABAND);
  });

  it('🔴🔴 사진이 실려 와도 잠긴 카드에는 `<img>` 가 하나도 없다 (TASK-MONO-678)', () => {
    const { container } = render(<PublicPostCard post={violating} />);
    expect(container.querySelectorAll('img')).toHaveLength(0);
    // 속성 어디에도 — `srcset`·`data-*` 로 숨는 경우까지.
    expect(container.innerHTML).not.toContain(CONTRABAND_IMAGE);
  });

  it('🔵 대조군: 잠기지 않은 카드는 첫 장을 그리고, 여러 장이면 남은 수를 알린다', () => {
    const one = { ...violating, locked: false, visibility: 'PUBLIC' } as PublicPost;
    const single = render(<PublicPostCard post={one} />);
    const imgs = single.container.querySelectorAll('img');
    expect(imgs).toHaveLength(1);
    expect(imgs[0].getAttribute('src')).toContain(CONTRABAND_IMAGE);
    expect(imgs[0].getAttribute('alt')).toBeTruthy();
    expect(single.container.textContent ?? '').not.toMatch(/\+\d/);
    single.unmount();

    const three = {
      ...one,
      imageUrls: ['https://example.invalid/a.jpg', 'https://example.invalid/b.jpg', 'https://example.invalid/c.jpg'],
    } as PublicPost;
    const multi = render(<PublicPostCard post={three} />);
    // 🔴 카드는 **한 장만** 그린다 — 피드가 사진 벽이 되지 않게.
    expect(multi.container.querySelectorAll('img')).toHaveLength(1);
    expect(multi.container.textContent ?? '').toContain('+2');
  });

  it('🔵 사진 0장인 공개 글은 사진 자리 없이 예전 카드 그대로다', () => {
    const none = { ...violating, locked: false, visibility: 'PUBLIC', imageUrls: [] } as unknown as PublicPost;
    const { container } = render(<PublicPostCard post={none} />);
    expect(container.querySelectorAll('[data-testid="public-post-image-frame"]')).toHaveLength(0);
    expect(container.textContent ?? '').toContain(CONTRABAND);
  });

  it('잠긴 글은 «멤버십 전용» 과 제목, /membership 링크를 낸다', () => {
    const { container, getByText } = render(<PublicPostCard post={violating} />);
    expect(getByText('멤버십 전용')).toBeInTheDocument();
    expect(getByText('[멤버십] 미공개 데모 트랙 이야기')).toBeInTheDocument();
    expect(container.querySelector('a[href="/membership"]')).not.toBeNull();
  });
});

describe('③ PublicPostDetail — 상세에서도 마찬가지다', () => {
  it('🔴🔴 상세가 목록보다 «더 보여 주지» 않는다', () => {
    const { container } = render(<PublicPostDetail post={violating} />);
    expect(container.innerHTML).not.toContain(CONTRABAND);
    expect(container.innerHTML).not.toContain(CONTRABAND_PREVIEW);
    expect(container.querySelector('[data-locked="true"]')).not.toBeNull();
    expect(container.textContent ?? '').toContain('멤버십 전용');
  });

  it('🔵 대조군: 공개 글의 상세는 본문을 그린다', () => {
    const open = { ...violating, locked: false, visibility: 'PUBLIC' } as PublicPost;
    const { container } = render(<PublicPostDetail post={open} />);
    expect(container.textContent ?? '').toContain(CONTRABAND);
  });

  it('🔴🔴 사진이 실려 와도 잠긴 상세에는 `<img>` 가 하나도 없다 (TASK-MONO-678)', () => {
    const { container } = render(<PublicPostDetail post={violating} />);
    expect(container.querySelectorAll('img')).toHaveLength(0);
    expect(container.innerHTML).not.toContain(CONTRABAND_IMAGE);
  });

  it('🔵 대조군: 공개 글의 상세는 사진을 **전부** 그리고, 장마다 다른 alt 를 준다', () => {
    const urls = ['https://example.invalid/a.jpg', 'https://example.invalid/b.jpg', 'https://example.invalid/c.jpg'];
    const open = { ...violating, locked: false, visibility: 'PUBLIC', imageUrls: urls } as PublicPost;
    const { container } = render(<PublicPostDetail post={open} />);
    const imgs = Array.from(container.querySelectorAll('img'));
    expect(imgs.map((i) => i.getAttribute('src'))).toEqual(urls);
    expect(new Set(imgs.map((i) => i.getAttribute('alt'))).size).toBe(urls.length);
  });
});

describe('④ 실제 시드로 그린 피드 — 잠긴 글이 티저로 나온다', () => {
  it('잠긴 글의 제목은 보이고, 상태는 «멤버십 전용» 이다', () => {
    const { container } = render(<PublicFeedList posts={posts} />);
    for (const p of lockedPosts) {
      expect(container.textContent ?? '').toContain(p.title);
    }
    expect(container.querySelectorAll('[data-locked="true"]')).toHaveLength(lockedPosts.length);
    // 🔵 음성 대조군: 공개 글도 같이 그려졌다(피드가 통째로 잠김 상태인 게 아니다).
    expect(container.querySelectorAll('[data-locked="false"]').length).toBe(
      posts.length - lockedPosts.length,
    );
  });

  it('🔴 사진 `<img>` 수 = 사진이 있는 공개 글 수 — 그리고 전부 공개 카드 안에 있다 (TASK-MONO-678)', () => {
    // 🔴 TASK-MONO-641 의 모양(«데이터는 채웠는데 아무도 안 그린다»)을 실제 시드로 문다.
    const withPhotos = posts.filter((p) => !p.locked && p.imageUrls.length > 0);
    expect(withPhotos.length).toBeGreaterThan(0); // 비공허성 — 0 이면 아래가 «0 = 0» 으로 통과한다
    const { container } = render(<PublicFeedList posts={posts} />);
    const imgs = container.querySelectorAll('img');
    expect(imgs).toHaveLength(withPhotos.length);
    for (const img of Array.from(imgs)) {
      expect(img.closest('[data-locked="false"]')).not.toBeNull();
    }
  });
});

describe('⑤ PublicPostImage — 사진이 죽어도 글은 산다 (TASK-MONO-678 AC-4)', () => {
  it('🔴 로드 실패 시 틀(frame)까지 사라진다 — 깨진 이미지 상자를 남기지 않는다', () => {
    const { container } = render(
      <PublicPostImage src="https://example.invalid/404.jpg" alt="사진" frameClassName="aspect-video" />,
    );
    const img = container.querySelector('img');
    expect(img).not.toBeNull(); // 대조군 — 실패 전에는 그려져 있다
    fireEvent.error(img!);
    expect(container.querySelector('[data-testid="public-post-image-frame"]')).toBeNull();
    expect(container.querySelector('img')).toBeNull();
  });

  it('🔴 카드 안에서 사진이 죽어도 제목·본문은 그대로다', () => {
    const open = { ...violating, locked: false, visibility: 'PUBLIC' } as PublicPost;
    const { container } = render(<PublicPostCard post={open} />);
    fireEvent.error(container.querySelector('img')!);
    expect(container.querySelector('img')).toBeNull();
    expect(container.textContent ?? '').toContain(CONTRABAND);
    expect(container.textContent ?? '').toContain(open.title);
  });
});
