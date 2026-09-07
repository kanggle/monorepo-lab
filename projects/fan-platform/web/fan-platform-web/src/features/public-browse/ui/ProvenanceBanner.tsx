import { provenanceOf, type ProvenanceInput } from '../lib/provenance';

/**
 * 데이터 출처 표시 — 작고, 눈에 거슬리지 않고, **반드시 있다**.
 *
 * 🔵 서버 컴포넌트다(`'use client'` 가 없다). 판정 입력이 봉투이고, 봉투는 서버에서만
 *    읽히기 때문이다. 클라이언트로 내려보낼 이유가 없고, 안 내려보내면 봉투의 어떤 필드도
 *    RSC 페이로드에 실리지 않는다 — `text` 문자열 하나만 HTML 에 남는다.
 *
 * 🔴 문구를 여기서 만들지 않는다. 판정은 `lib/provenance.ts` 가 하고 이 파일은 그린다.
 *    그래야 "무엇을 말하는가" 를 렌더 없이 시험할 수 있다.
 */
export function ProvenanceBanner({ source, generatedAt, degraded }: ProvenanceInput) {
  const { kind, text } = provenanceOf({ source, generatedAt, degraded });
  return (
    <p
      data-testid="provenance-banner"
      data-provenance-kind={kind}
      className="mt-8 text-xs text-ink-400"
    >
      {text}
    </p>
  );
}
