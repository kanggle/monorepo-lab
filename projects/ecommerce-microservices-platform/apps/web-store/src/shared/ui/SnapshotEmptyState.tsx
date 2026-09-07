/**
 * 0건을 **두 사실로 갈라서** 그린다 (ADR-MONO-070 § D3.2).
 *
 * 🔴🔴 «조건에 맞는 게 없다» 와 «저장본이 비어 있다» 는 다른 사실이고 다른 문구를 받아야
 *    한다. 하나로 합치면 화면이 «전체 카탈로그가 비었다» 는 **없는 주장**을 한다 — 저장본은
 *    카탈로그의 *범위* 일 뿐이고, 그 범위 밖을 «세상에 없음» 으로 번역하는 것이 이 컴포넌트가
 *    막는 실패다. 그래서 판정 축은 `corpusSize`(질의 **이전**의 모집단 크기)다:
 *
 *      corpusSize > 0   → 검색 결과 없음      (저장본에 상품은 있는데 조건에 안 맞았다)
 *      corpusSize === 0 → 저장본이 비어 있음  (아직 발행 전 · 이 화면의 범위가 비었다)
 *
 * 🔵 `@repo/ui` 의 `EmptyState` 를 안 쓰는 이유: 그쪽은 문구 한 줄만 받는다. 여기서 필요한
 *    것은 «두 번째 줄이 첫 줄의 범위를 제한하는» 모양이라 한 줄로는 표현이 안 된다.
 */
interface SnapshotEmptyStateProps {
  /**
   * 질의 이전의 저장본 모집단 크기.
   * 🔴 `undefined` 는 "0" 이 **아니라** "모른다" 다. 모를 때는 «검색 결과 없음» 쪽으로 붙는다
   *    — 모집단을 모르면서 «저장본이 비었다» 고 단정하는 것이 더 큰 거짓이기 때문이다.
   */
  corpusSize?: number;
  /** 검색어가 있었으면 문구에 그대로 인용한다. */
  query?: string;
}

export function SnapshotEmptyState({ corpusSize, query }: SnapshotEmptyStateProps) {
  const empty = corpusSize === 0;
  const scope = typeof corpusSize === 'number' ? `저장본 ${corpusSize.toLocaleString()}건 안에서` : '저장본 안에서';

  return (
    <div
      role="status"
      data-testid="snapshot-empty-state"
      data-corpus-size={corpusSize ?? 'unknown'}
      style={{ padding: '3rem', textAlign: 'center', color: 'var(--color-text-secondary)' }}
    >
      <p style={{ margin: 0, fontWeight: 'var(--font-weight-semibold)' }}>
        {empty ? '저장본이 비어 있음' : '검색 결과 없음'}
      </p>
      <p style={{ margin: 'var(--space-2) 0 0', fontSize: 'var(--font-size-sm)' }}>
        {empty
          ? '아직 발행된 상품 저장본이 없습니다. 전체 카탈로그가 비어 있다는 뜻은 아닙니다.'
          : `${scope}${query ? ` "${query}"에` : ''} 해당하는 상품을 찾지 못했습니다.`}
      </p>
    </div>
  );
}
