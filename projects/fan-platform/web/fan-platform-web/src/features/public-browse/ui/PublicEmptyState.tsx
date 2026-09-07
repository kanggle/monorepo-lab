import { EmptyState } from '@/shared/ui/EmptyState';
import type { EmptyKind } from '../lib/empty-state';

/**
 * 0건 화면 — **두 가지 0 을 다른 문구로** 그린다(§ `lib/empty-state.ts`).
 *
 * 🔴 두 칸의 차이는 «방문자가 할 수 있는 일이 있는가» 다. `no-match` 는 검색어를 바꾸면
 *    되고, `empty-corpus` 는 바꿔도 소용없다. 같은 문구를 쓰면 두 번째 방문자가 헤매다가
 *    "이 서비스엔 아무것도 없다" 로 읽는다.
 */
export function PublicEmptyState({
  kind,
  query,
  noun,
}: {
  kind: EmptyKind;
  /** 검색어. `no-match` 일 때만 문구에 들어간다. */
  query?: string;
  /** "아티스트" / "포스트" — 문구에 넣을 대상 이름. */
  noun: string;
}) {
  if (kind === 'empty-corpus') {
    return (
      <EmptyState
        title="저장본이 비어 있습니다"
        description={`아직 공개된 ${noun} 저장본이 없습니다. 검색어를 바꿔도 결과는 같습니다.`}
      />
    );
  }

  return (
    <EmptyState
      title="검색 결과가 없습니다"
      description={
        query
          ? `"${query}" 와 일치하는 ${noun}를 저장본에서 찾지 못했습니다.`
          : `조건에 맞는 ${noun}를 저장본에서 찾지 못했습니다.`
      }
    />
  );
}
