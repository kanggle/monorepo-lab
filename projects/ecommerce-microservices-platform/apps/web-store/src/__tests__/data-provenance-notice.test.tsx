/**
 * `DataProvenanceNotice` — 화면이 «어디서 온 데이터인가» 를 말하는 자리 (ADR-MONO-070).
 *
 * 🔴🔴 이 스위트가 잡는 결함은 **`source` 만 보고 문구를 정하는 것**이다. `bundled` 는
 *    «아직 발행 전»(정상)과 «저장본을 못 읽어 떨어짐»(장애) 두 경우에 다 참이라, source 만
 *    보면 장애를 정상으로 그린다. 그래서 degraded 칸이 이 표에서 제일 중요하다.
 * 🔴 그리고 어느 칸에서도 «실시간»·«최신» 을 말하면 안 된다 — 마지막 단언이 그것이다.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const mockProvenance = vi.hoisted(() => vi.fn());

vi.mock('@/shared/public-data/store-snapshot', async () => {
  const actual = await vi.importActual<typeof import('@/shared/public-data/store-snapshot')>(
    '@/shared/public-data/store-snapshot',
  );
  return { ...actual, readStoreProvenance: mockProvenance, readStoreSnapshot: vi.fn() };
});

import { DataProvenanceNotice } from '@/widgets/data-provenance/DataProvenanceNotice';

async function renderNotice() {
  render(await DataProvenanceNotice());
  return screen.getByTestId('store-data-provenance');
}

describe('DataProvenanceNotice', () => {
  beforeEach(() => vi.clearAllMocks());

  it('bundled + !degraded → "샘플 데이터 (아직 발행 전)"', async () => {
    mockProvenance.mockResolvedValue({
      source: 'bundled',
      generatedAt: '2026-09-07T00:00:00.000Z',
      degraded: false,
    });

    expect(await renderNotice()).toHaveTextContent('샘플 데이터 (아직 발행 전)');
  });

  it('degraded → "저장본을 읽지 못해 샘플을 표시 중" (source 가 bundled 여도 이쪽이 이긴다)', async () => {
    mockProvenance.mockResolvedValue({
      source: 'bundled',
      generatedAt: '2026-09-07T00:00:00.000Z',
      degraded: true,
    });

    const el = await renderNotice();
    expect(el).toHaveTextContent('저장본을 읽지 못해 샘플을 표시 중');
    expect(el).not.toHaveTextContent('아직 발행 전');
  });

  it('backend → "<generatedAt> UTC 기준 저장본"', async () => {
    mockProvenance.mockResolvedValue({
      source: 'backend',
      generatedAt: '2026-09-07T04:31:09.000Z',
      degraded: false,
    });

    expect(await renderNotice()).toHaveTextContent('2026-09-07 04:31 UTC 기준 저장본');
  });

  it('backend 인데 degraded 면 장애 문구가 이긴다', async () => {
    mockProvenance.mockResolvedValue({
      source: 'backend',
      generatedAt: '2026-09-07T04:31:09.000Z',
      degraded: true,
    });

    expect(await renderNotice()).toHaveTextContent('저장본을 읽지 못해 샘플을 표시 중');
  });

  it('authored 는 «아직 발행 전» 쪽으로 묶인다 (어느 쪽이든 실데이터가 아니다)', async () => {
    mockProvenance.mockResolvedValue({
      source: 'authored',
      generatedAt: '2026-09-07T00:00:00.000Z',
      degraded: false,
    });

    expect(await renderNotice()).toHaveTextContent('샘플 데이터 (아직 발행 전)');
  });

  it.each(['bundled', 'backend', 'authored'] as const)(
    '%s — 어떤 경우에도 «실시간/최신» 을 주장하지 않는다',
    async (source) => {
      mockProvenance.mockResolvedValue({
        source,
        generatedAt: '2026-09-07T00:00:00.000Z',
        degraded: false,
      });

      const text = (await renderNotice()).textContent ?? '';
      expect(text).not.toMatch(/실시간|라이브|최신 데이터/);
    },
  );

  it('generatedAt 모양이 다르면 원문 그대로 둔다 — 없는 시각을 꾸며내지 않는다', async () => {
    mockProvenance.mockResolvedValue({
      source: 'backend',
      generatedAt: 'not-a-timestamp',
      degraded: false,
    });

    expect(await renderNotice()).toHaveTextContent('not-a-timestamp UTC 기준 저장본');
  });
});
