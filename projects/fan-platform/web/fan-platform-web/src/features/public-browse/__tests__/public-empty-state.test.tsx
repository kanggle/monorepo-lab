/**
 * 0건의 **두 뜻**을 화면이 구별하는가.
 *
 * 🔴 요구가 명시한 축이다: *"저장 범위 밖 결과를 '전체 데이터에 없음' 으로 오인시키지
 *    않는다."* 두 칸이 같은 문구를 받으면 이 파일은 아무 일도 안 하므로, 마지막 칸이
 *    **문구가 실제로 다른가**를 직접 단언한다(각 칸을 따로 보면 둘 다 통과한다).
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { queryArtists } from '@demo/public-data';
import type { PublicArtist } from '@demo/public-data';
import { emptyKind } from '../lib/empty-state';
import { PublicEmptyState } from '../ui/PublicEmptyState';

const ARTIST: PublicArtist = {
  id: 'a1',
  stageName: '루미',
  artistType: 'SOLO',
  agency: 'Aurora',
  debutDate: '2021-03-14',
  bio: null,
  profileImageUrl: null,
  searchText: '루미 aurora',
};

describe('emptyKind', () => {
  it('결과가 있으면 null — 호출자가 목록을 그린다', () => {
    expect(emptyKind({ totalElements: 3, corpusSize: 10 })).toBeNull();
  });

  it('🔴 저장본에는 있는데 질의에 안 걸림 → no-match', () => {
    expect(emptyKind({ totalElements: 0, corpusSize: 10 })).toBe('no-match');
  });

  it('🔴 저장본 자체가 빔 → empty-corpus', () => {
    expect(emptyKind({ totalElements: 0, corpusSize: 0 })).toBe('empty-corpus');
  });

  it('🔵 `queryArtists` 의 실제 결과와 물린다 — 두 칸이 진짜로 생긴다', () => {
    // 대조군을 손으로 만든 수가 아니라 **질의 함수의 출력**으로 만든다. 그래야
    // `corpusSize` 의 의미가 내 상상이 아니라 그 함수의 의미와 같다는 것이 시험된다.
    const noMatch = queryArtists([ARTIST], { q: '존재하지않는이름' });
    expect(noMatch.totalElements).toBe(0);
    expect(noMatch.corpusSize).toBe(1);
    expect(emptyKind(noMatch)).toBe('no-match');

    const emptyCorpus = queryArtists([], { q: '존재하지않는이름' });
    expect(emptyCorpus.corpusSize).toBe(0);
    expect(emptyKind(emptyCorpus)).toBe('empty-corpus');

    // 음성 대조군 — 걸리는 질의는 0건이 아니다(위 두 칸이 «항상 0건» 이라서 통과한 게 아님).
    expect(emptyKind(queryArtists([ARTIST], { q: '루미' }))).toBeNull();
  });
});

describe('PublicEmptyState', () => {
  it('no-match → "검색 결과가 없습니다" + 검색어를 되비춘다', () => {
    render(<PublicEmptyState kind="no-match" query="존재하지않는이름" noun="아티스트" />);
    expect(screen.getByText('검색 결과가 없습니다')).toBeInTheDocument();
    expect(screen.getByText(/존재하지않는이름/)).toBeInTheDocument();
  });

  it('empty-corpus → "저장본이 비어 있습니다" + 검색어를 바꿔도 소용없음을 말한다', () => {
    render(<PublicEmptyState kind="empty-corpus" noun="아티스트" />);
    expect(screen.getByText('저장본이 비어 있습니다')).toBeInTheDocument();
    expect(screen.getByText(/검색어를 바꿔도/)).toBeInTheDocument();
  });

  it('🔴🔴 두 칸의 제목이 **서로 다르다** — 같으면 이 파일 전체가 공허하다', () => {
    const { unmount } = render(<PublicEmptyState kind="no-match" noun="아티스트" />);
    const noMatchTitle = screen.getByText('검색 결과가 없습니다').textContent;
    unmount();

    render(<PublicEmptyState kind="empty-corpus" noun="아티스트" />);
    const emptyTitle = screen.getByText('저장본이 비어 있습니다').textContent;

    expect(noMatchTitle).not.toBe(emptyTitle);
  });
});
