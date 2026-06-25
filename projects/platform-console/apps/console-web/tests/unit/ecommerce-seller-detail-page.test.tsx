import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';

/**
 * TASK-PC-FE-133 — the `/ecommerce/sellers/[id]` detail route must decode the
 * dynamic segment before handing it to `getSellerDetailSectionState`.
 *
 * Root cause (runtime diagnosis 2026-06-26): seller ids registered via the
 * in-console form can carry spaces / non-ASCII (e.g. `셀러 1`). Next.js delivers
 * the `[id]` segment percent-encoded (`%EC%85%80%EB%9F%AC%201`); `getSeller()`
 * re-encodes it for the upstream path, producing a double-encoded `%25EC..`
 * path. Spring StrictHttpFirewall rejects the `%25`-bearing path with `400`
 * (verified against the live gateway: `%25EC..` → 400, `%EC..` → 401/200), and
 * the section maps any non-404 error to a degrade → the operator sees a
 * spurious "ecommerce 셀러 정보를 일시적으로 불러올 수 없습니다." note.
 *
 * The fix decodes the segment at the page boundary so `getSeller()` encodes
 * exactly once. ASCII ids are unaffected (decode is a no-op).
 */

const { notFoundMock } = vi.hoisted(() => ({ notFoundMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  notFound: () => {
    notFoundMock();
    throw new Error('NOT_FOUND');
  },
}));
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const { resolveEligibilityMock } = vi.hoisted(() => ({
  resolveEligibilityMock: vi.fn(),
}));
vi.mock('@/app/(console)/ecommerce/products/_eligibility', () => ({
  resolveEcommerceEligibility: resolveEligibilityMock,
}));

const { getSellerDetailSectionStateMock } = vi.hoisted(() => ({
  getSellerDetailSectionStateMock: vi.fn(),
}));
vi.mock('@/features/ecommerce-ops', () => ({
  getSellerDetailSectionState: getSellerDetailSectionStateMock,
  // Happy-path render component — a thin stub is enough for these assertions.
  SellerDetail: ({ seller }: { seller: { sellerId: string } }) => (
    <div data-testid="seller-detail">{seller.sellerId}</div>
  ),
}));

import SellerDetailPage from '@/app/(console)/ecommerce/sellers/[id]/page';

const DETAIL_EMPTY = {
  detail: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

beforeEach(() => {
  vi.clearAllMocks();
  cleanup();
  resolveEligibilityMock.mockResolvedValue({
    eligible: true,
    registryDegraded: false,
  });
});

async function renderPage(id: string) {
  render(await SellerDetailPage({ params: Promise.resolve({ id }) }));
}

describe('SellerDetailPage (TASK-PC-FE-133 — decode before upstream encode)', () => {
  it('decodes a percent-encoded non-ASCII segment before reading the section state', async () => {
    getSellerDetailSectionStateMock.mockResolvedValue({
      ...DETAIL_EMPTY,
      detail: { sellerId: '셀러 1' },
    });

    // What Next.js delivers for a `셀러 1` seller link.
    await renderPage('%EC%85%80%EB%9F%AC%201');

    // The decisive assertion: the upstream read receives the DECODED id, so
    // getSeller() (downstream) encodes exactly once — no double-encode / 400.
    expect(getSellerDetailSectionStateMock).toHaveBeenCalledWith(true, '셀러 1');
    expect(screen.getByTestId('seller-detail')).toHaveTextContent('셀러 1');
  });

  it('decodes a space-bearing ascii segment (%20) to a raw id', async () => {
    getSellerDetailSectionStateMock.mockResolvedValue({
      ...DETAIL_EMPTY,
      detail: { sellerId: 'shop a' },
    });

    await renderPage('shop%20a');

    expect(getSellerDetailSectionStateMock).toHaveBeenCalledWith(true, 'shop a');
  });

  it('passes a plain ascii id through unchanged (decode is a no-op)', async () => {
    getSellerDetailSectionStateMock.mockResolvedValue({
      ...DETAIL_EMPTY,
      detail: { sellerId: 'default' },
    });

    await renderPage('default');

    expect(getSellerDetailSectionStateMock).toHaveBeenCalledWith(true, 'default');
  });

  it('falls back to the raw segment when the encoding is malformed (no throw)', async () => {
    getSellerDetailSectionStateMock.mockResolvedValue({
      ...DETAIL_EMPTY,
      detail: { sellerId: 'a%b' },
    });

    // A lone `%` is not valid percent-encoding — decodeURIComponent throws; the
    // page must not crash, it falls back to the raw segment.
    await renderPage('a%b');

    expect(getSellerDetailSectionStateMock).toHaveBeenCalledWith(true, 'a%b');
  });

  it('still renders the degraded note when the (decoded) read degrades', async () => {
    getSellerDetailSectionStateMock.mockResolvedValue({
      ...DETAIL_EMPTY,
      degraded: true,
    });

    await renderPage('%EC%85%80%EB%9F%AC%201');

    expect(getSellerDetailSectionStateMock).toHaveBeenCalledWith(true, '셀러 1');
    expect(screen.getByTestId('seller-degraded')).toBeInTheDocument();
  });

  it('registry degraded short-circuits before any upstream read', async () => {
    resolveEligibilityMock.mockResolvedValue({
      eligible: false,
      registryDegraded: true,
    });

    await renderPage('%EC%85%80%EB%9F%AC%201');

    expect(getSellerDetailSectionStateMock).not.toHaveBeenCalled();
    expect(screen.getByTestId('seller-degraded')).toBeInTheDocument();
  });
});
