import type { PublicDataSource } from '@demo/public-data';

/**
 * 화면이 방문자에게 말해야 하는 것 — **이 데이터가 어디서 왔는가**.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴🔴 이 화면은 «지금 살아 있는 데이터» 를 절대 주장하지 않는다
 * ─────────────────────────────────────────────────────────────────────────
 * 공개 화면은 백엔드를 안 부른다. 그러므로 여기 그려지는 것은 **어느 시점의 저장본**이고,
 * 그 사실을 말하지 않으면 방문자는 그것을 실시간으로 읽는다 — 없는 사실을 주장하는
 * 화면이 된다. 그래서 문구에 "실시간"·"현재"에 해당하는 말이 하나도 없고, `backend`
 * 칸조차 **"저장본"** 이라고 못박은 뒤 그 저장본이 언제 만들어졌는지를 붙인다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 축이 **둘**이다 — `source` 하나로 갈리지 않는다
 * ─────────────────────────────────────────────────────────────────────────
 * `envelope.source === 'bundled'` 와 `degraded === true` 는 **다른 사실**이다
 * (`@demo/public-data` 의 `PublicDataResult.degraded` 주석이 같은 말을 한다):
 *
 *   · bundled + degraded=false — 저장소 시드를 그대로 쓰는 중. 아직 발행한 적이 없거나
 *                                발행본이 곧 시드다. **정상 상태이고 결함이 아니다.**
 *   · degraded=true            — 영속 저장본을 **시도했는데 못 읽었다.** 설정이 있는데
 *                                실패한 것이므로 운영자가 알아야 한다.
 *
 * 두 상태를 한 문구로 합치면, 「아직 발행 전」이라는 정상 상태와 「저장소가 죽었다」는
 * 사건이 같은 얼굴로 온다. `degraded` 를 **먼저** 본다: 그때 `source` 는 언제나 폴백된
 * `bundled` 이므로, 순서를 뒤집으면 degraded 칸이 영원히 안 그려진다.
 * ─────────────────────────────────────────────────────────────────────────
 */

export type ProvenanceKind = 'degraded' | 'bundled' | 'authored' | 'snapshot' | 'unknown-time';

export interface Provenance {
  kind: ProvenanceKind;
  text: string;
}

export interface ProvenanceInput {
  source: PublicDataSource;
  generatedAt: string;
  degraded: boolean;
}

/**
 * ISO 문자열 → `YYYY-MM-DD HH:mm UTC`.
 *
 * 🔴🔴 **UTC 로 읽는다(`getUTC*`).** 로컬 시간대로 포매팅하면 이 저장소의 개발 호스트
 *    (KST, UTC+9)와 CI(UTC)가 **같은 봉투에 다른 시각**을 그리고, 스냅샷 단언은 호스트가
 *    바뀌는 날 조용히 빨개진다. 그리고 더 나쁜 쪽: 방문자가 보는 시각이 서버가 어디
 *    있느냐에 따라 달라지는데 문구는 계속 "UTC" 라고 말한다. 문구와 값이 갈리면 문구가
 *    거짓이 된다 ⇒ 값 쪽을 문구에 맞춘다.
 */
function formatUtc(iso: string): string | null {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const p = (n: number) => String(n).padStart(2, '0');
  return (
    `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())} ` +
    `${p(d.getUTCHours())}:${p(d.getUTCMinutes())} UTC`
  );
}

export function provenanceOf({ source, generatedAt, degraded }: ProvenanceInput): Provenance {
  // 🔴 순서 고정 — 위 § 참조. degraded 일 때 source 는 항상 폴백된 값이다.
  if (degraded) {
    return { kind: 'degraded', text: '저장본을 읽지 못해 샘플을 표시 중' };
  }

  if (source === 'bundled') {
    return { kind: 'bundled', text: '샘플 데이터 (아직 발행 전)' };
  }

  if (source === 'authored') {
    // 🔵 요구 문구가 지정한 세 칸(bundled / degraded / backend)에 없는 네 번째 값이다.
    //    `bundled` 문구를 재활용하지 않는다 — authored 는 «아직 발행 전» 이 아니라
    //    «저장소가 직접 쓴 안내» 이고, 둘을 같은 말로 덮으면 그 문장이 거짓이 된다.
    return { kind: 'authored', text: '저장소가 작성한 안내 데이터' };
  }

  const at = formatUtc(generatedAt);
  if (at === null) {
    // 🔴 시각을 못 읽었다고 «실시간» 쪽으로 넘어가지 않는다. 모르면 모른다고 말한다.
    return { kind: 'unknown-time', text: '저장본 (생성 시각 미상)' };
  }
  return { kind: 'snapshot', text: `${at} 기준 저장본` };
}
