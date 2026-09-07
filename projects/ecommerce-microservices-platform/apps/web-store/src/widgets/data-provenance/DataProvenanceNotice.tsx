// DEMO-PUBLIC-DATA-CONSUMER: web-store
import { formatSnapshotInstantUtc, readStoreProvenance } from '@/shared/public-data/store-snapshot';

/**
 * 이 화면의 데이터가 **어디서 왔는지** 말한다 (ADR-MONO-070 § D3).
 *
 * 🔴🔴 «실시간» 이라고 절대 말하지 않는다. 공개 카탈로그는 저장본을 읽고, 저장본은 발행
 *    시점의 사진이다. 아무 말도 안 하면 방문자는 번들 시드를 **「지금 백엔드에서 뽑은 것」**
 *    으로 읽는다 — 없는 사실을 주장하는 화면이 된다(read.ts 가 `source` 를 싣는 이유).
 *
 * 세 문구는 **세 가지 다른 사실**이고, 갈리는 축이 둘이다:
 *
 *   degraded === true          → "저장본을 읽지 못해 샘플을 표시 중"  (읽기 실패)
 *   source === 'backend'       → "<generatedAt> UTC 기준 저장본"      (발행된 실데이터)
 *   그 외(bundled/authored)    → "샘플 데이터 (아직 발행 전)"          (정상적인 미발행 상태)
 *
 * 🔴 `degraded` 를 먼저 본다. `source === 'bundled'` 는 «아직 발행 전» 과 «저장본을 못 읽어
 *    번들로 떨어짐» 두 경우에 다 참이므로, source 만 보면 **장애를 정상으로 그린다**.
 * 🔵 `authored` 는 store 데이터셋에 오지 않지만(발행자가 백엔드에서 뽑는다) 값 집합에는
 *    있으므로 «아직 발행 전» 쪽으로 묶는다 — 어느 쪽이든 "실데이터가 아니다" 가 참이다.
 *
 * 🔵 서버 컴포넌트다. 판정이 저장본 판독자(서버 전용)에 달려 있고, 브라우저는 서버가 렌더한
 *    결과만 보면 된다.
 */
export async function DataProvenanceNotice() {
  const { source, generatedAt, degraded } = await readStoreProvenance();

  const label = degraded
    ? '저장본을 읽지 못해 샘플을 표시 중'
    : source === 'backend'
      ? `${formatSnapshotInstantUtc(generatedAt)} UTC 기준 저장본`
      : '샘플 데이터 (아직 발행 전)';

  return (
    <p
      data-testid="store-data-provenance"
      data-source={source}
      data-degraded={degraded ? '1' : '0'}
      style={{
        margin: 0,
        color: 'var(--color-text-muted)',
        fontSize: 'var(--font-size-xs)',
      }}
    >
      {label}
    </p>
  );
}
