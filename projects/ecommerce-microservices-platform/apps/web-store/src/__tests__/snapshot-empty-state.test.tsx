/**
 * `SnapshotEmptyState` — 0건을 **두 사실로** 갈라 그리는 자리 (ADR-MONO-070 § D3.2).
 *
 * 🔴🔴 이 스위트가 잡는 결함: 저장 범위 밖 결과를 «전체 카탈로그에 없음» 으로 오인시키는 것.
 *    그래서 판정은 "문구가 뜬다" 가 아니라 **"두 문구가 서로 다르고, 빈 저장본 쪽이 전체
 *    카탈로그를 대신 말하지 않는다"** 이다.
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SnapshotEmptyState } from '@/shared/ui/SnapshotEmptyState';

describe('SnapshotEmptyState', () => {
  it('corpusSize > 0 → "검색 결과 없음"', () => {
    render(<SnapshotEmptyState corpusSize={8} query="노트북" />);

    const el = screen.getByTestId('snapshot-empty-state');
    expect(el).toHaveTextContent('검색 결과 없음');
    expect(el).not.toHaveTextContent('저장본이 비어 있음');
    expect(el).toHaveTextContent('저장본 8건 안에서');
    expect(el).toHaveTextContent('"노트북"에');
  });

  it('corpusSize === 0 → "저장본이 비어 있음"', () => {
    render(<SnapshotEmptyState corpusSize={0} />);

    const el = screen.getByTestId('snapshot-empty-state');
    expect(el).toHaveTextContent('저장본이 비어 있음');
    expect(el).not.toHaveTextContent('검색 결과 없음');
  });

  it('빈 저장본 문구는 «전체 카탈로그가 비었다» 를 명시적으로 부정한다', () => {
    render(<SnapshotEmptyState corpusSize={0} />);

    expect(screen.getByTestId('snapshot-empty-state')).toHaveTextContent(
      '전체 카탈로그가 비어 있다는 뜻은 아닙니다',
    );
  });

  it('두 상태의 문구는 서로 다르다 (한 문구로 합치면 이 단언이 죽는다)', () => {
    const { unmount } = render(<SnapshotEmptyState corpusSize={0} />);
    const emptyText = screen.getByTestId('snapshot-empty-state').textContent;
    unmount();

    render(<SnapshotEmptyState corpusSize={5} />);
    const missText = screen.getByTestId('snapshot-empty-state').textContent;

    expect(emptyText).not.toBe(missText);
  });

  it('corpusSize 를 모르면(undefined) «저장본이 비었다» 고 단정하지 않는다', () => {
    render(<SnapshotEmptyState />);

    const el = screen.getByTestId('snapshot-empty-state');
    expect(el).toHaveTextContent('검색 결과 없음');
    expect(el).not.toHaveTextContent('저장본이 비어 있음');
    expect(el).toHaveAttribute('data-corpus-size', 'unknown');
  });
});
