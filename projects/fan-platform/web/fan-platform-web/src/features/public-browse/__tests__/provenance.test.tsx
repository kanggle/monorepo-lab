/**
 * 출처 표기 — **화면이 «지금 살아 있는 데이터» 를 주장하지 않는가**.
 *
 * 🔴 판정 축이 둘이다(`source` × `degraded`). `source` 하나로 갈리지 않는다는 것이
 *    이 파일이 지키는 명제이고, 그래서 `bundled + degraded=false` 와 `bundled +
 *    degraded=true` 가 **서로 다른 문구**를 받는지를 나란히 단언한다. 한 칸만 있으면
 *    순서를 뒤집는 리팩터(=degraded 칸이 영영 안 그려지는 결함)가 초록으로 통과한다.
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { provenanceOf } from '../lib/provenance';
import { ProvenanceBanner } from '../ui/ProvenanceBanner';

const AT = '2026-09-07T04:05:00.000Z';

describe('provenanceOf', () => {
  it('bundled + 정상 → "샘플 데이터 (아직 발행 전)"', () => {
    expect(provenanceOf({ source: 'bundled', generatedAt: AT, degraded: false })).toEqual({
      kind: 'bundled',
      text: '샘플 데이터 (아직 발행 전)',
    });
  });

  it('🔴 degraded → "저장본을 읽지 못해 샘플을 표시 중" — bundled 문구와 **다르다**', () => {
    const degraded = provenanceOf({ source: 'bundled', generatedAt: AT, degraded: true });
    const normal = provenanceOf({ source: 'bundled', generatedAt: AT, degraded: false });

    expect(degraded).toEqual({ kind: 'degraded', text: '저장본을 읽지 못해 샘플을 표시 중' });
    // 🔴 이 단언이 요점이다. 두 상태가 같은 문구를 받으면 «아직 발행 전» 이라는 정상
    //    상태와 «저장소가 죽었다» 는 사건이 같은 얼굴로 온다.
    expect(degraded.text).not.toBe(normal.text);
  });

  it('🔴 degraded 는 source 를 **이긴다** — 폴백된 뒤의 source 는 언제나 bundled 다', () => {
    // 판정 순서가 뒤집히면 degraded 칸은 영원히 안 그려진다.
    expect(provenanceOf({ source: 'backend', generatedAt: AT, degraded: true }).kind).toBe(
      'degraded',
    );
  });

  it('backend → generatedAt 을 "YYYY-MM-DD HH:mm UTC 기준 저장본" 으로 말한다', () => {
    expect(provenanceOf({ source: 'backend', generatedAt: AT, degraded: false })).toEqual({
      kind: 'snapshot',
      text: '2026-09-07 04:05 UTC 기준 저장본',
    });
  });

  it('🔴🔴 시각은 **UTC 로** 포매팅한다 — 호스트 TZ(KST)가 값을 바꾸면 안 된다', () => {
    // 이 저장소의 개발 호스트는 UTC+9 이고 CI 는 UTC 다. 로컬 시간대로 포매팅했다면
    // 같은 봉투가 두 곳에서 다른 문자열을 내고, 그러면서도 문구는 계속 "UTC" 라고 말한다.
    // 09:00 KST == 00:00 UTC 인 시각을 골라 두 축을 갈라 놓는다.
    const midnightUtc = provenanceOf({
      source: 'backend',
      generatedAt: '2026-09-07T00:00:00.000Z',
      degraded: false,
    });
    expect(midnightUtc.text).toBe('2026-09-07 00:00 UTC 기준 저장본');
    // KST 로 읽었다면 "2026-09-07 09:00" 이 됐을 것이다.
    expect(midnightUtc.text).not.toContain('09:00');
  });

  it('🔴 생성 시각을 못 읽어도 «실시간» 쪽으로 넘어가지 않는다', () => {
    const bad = provenanceOf({ source: 'backend', generatedAt: 'not-a-date', degraded: false });
    expect(bad.kind).toBe('unknown-time');
    expect(bad.text).toContain('저장본');
  });

  it('authored → bundled 문구를 재활용하지 않는다 (다른 사실이다)', () => {
    const authored = provenanceOf({ source: 'authored', generatedAt: AT, degraded: false });
    expect(authored.kind).toBe('authored');
    expect(authored.text).not.toBe('샘플 데이터 (아직 발행 전)');
  });

  it('🔴🔴 어떤 칸도 «실시간/현재» 를 주장하지 않는다', () => {
    const cells = [
      provenanceOf({ source: 'bundled', generatedAt: AT, degraded: false }),
      provenanceOf({ source: 'bundled', generatedAt: AT, degraded: true }),
      provenanceOf({ source: 'backend', generatedAt: AT, degraded: false }),
      provenanceOf({ source: 'authored', generatedAt: AT, degraded: false }),
      provenanceOf({ source: 'backend', generatedAt: 'x', degraded: false }),
    ];
    // 모집단이 비어 있으면 이 단언은 공허하다 — 먼저 센다.
    expect(cells).toHaveLength(5);
    for (const cell of cells) {
      expect(cell.text).not.toMatch(/실시간|현재 데이터|라이브/);
    }
  });
});

describe('ProvenanceBanner', () => {
  it('렌더된 DOM 에 문구가 실제로 나온다', () => {
    render(<ProvenanceBanner source="bundled" generatedAt={AT} degraded={false} />);
    const banner = screen.getByTestId('provenance-banner');
    expect(banner).toHaveTextContent('샘플 데이터 (아직 발행 전)');
    expect(banner).toHaveAttribute('data-provenance-kind', 'bundled');
  });

  it('degraded 를 DOM 에서도 구별할 수 있다', () => {
    render(<ProvenanceBanner source="bundled" generatedAt={AT} degraded />);
    expect(screen.getByTestId('provenance-banner')).toHaveAttribute(
      'data-provenance-kind',
      'degraded',
    );
  });
});
