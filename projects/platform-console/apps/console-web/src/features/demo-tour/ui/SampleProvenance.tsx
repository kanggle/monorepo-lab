import type { PublicDataEnvelope } from '@demo/public-data';
import { formatDateTime } from '@/shared/lib/datetime';

/**
 * 이 화면의 숫자가 **무엇인지** 말한다 (ADR-MONO-070 봉투 § source / generatedAt).
 *
 * 🔴🔴 `source` 를 한국어로 옮길 때 **없는 사실을 만들지 않는다.** `console-sample` 의
 *    `source` 는 항상 `authored` 이고, 그 뜻은 «저장소가 손으로 쓴 합성값» 이지
 *    «백엔드에서 뽑아 저장해 둔 값» 이 아니다. 그래서 `backend` / `bundled` 와 **다른
 *    문구**를 쓴다 — 셋을 한 문장으로 합치면 둘 중 하나는 반드시 거짓이 된다.
 *
 * 🔴 `generatedAt` 은 «지금» 이 아니라 «이 봉투가 만들어진 시각» 이다. 그렇게 적는다.
 *    콘솔 규약대로 `formatDateTime`(`Asia/Seoul` 고정)만 쓴다 — 호출부에서
 *    `toLocale*` 을 직접 부르면 SSR/하이드레이션이 갈린다(frontend-ui.md § 1).
 */
const SOURCE_LABEL: Record<string, string> = {
  authored: '샘플(합성) 데이터',
  bundled: '저장소에 커밋된 스냅샷',
  backend: '백엔드에서 수집한 스냅샷',
};

export function SampleProvenance({
  envelope,
}: {
  envelope: PublicDataEnvelope<unknown>;
}) {
  // 🔴 모르는 `source` 값을 «샘플» 로 접지 않는다. 계약이 늘어나 새 값이 오면 그 값을
  //    그대로 보여 주는 편이 낫다 — 틀린 한국어 문구보다 낯선 영어 한 단어가 정직하다.
  const label = SOURCE_LABEL[envelope.source] ?? envelope.source;

  return (
    <p
      data-testid="demo-provenance"
      className="text-xs text-muted-foreground"
    >
      출처: {label} · 생성 시각 {formatDateTime(envelope.generatedAt)} · 데이터
      세대 <span className="font-mono">{envelope.dataVersion}</span>
    </p>
  );
}
