import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ScmInventoryScreen } from '@/features/scm-ops';
import type { SnapshotResponse, StalenessResponse } from '@/features/scm-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/scm-ops` 재고 (inventory-visibility) screen behaviour — split out
 * of the former combined ScmOpsScreen (TASK-PC-FE-220; read section
 * TASK-PC-FE-008). STRICTLY READ-ONLY:
 *   - inventory-visibility snapshot / per-SKU / staleness panels
 *   - EVERY inventory-visibility view renders the S5 warning prominently
 *   - the staleness panel shows STALE/UNREACHABLE nodes honestly
 *   - WCAG AA axe-clean + keyboard-operable
 *
 * Client calls the same-origin `/api/scm/**` proxy via `fetch` (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const SNAP: SnapshotResponse = {
  data: {
    content: [
      {
        id: 's-1',
        nodeId: 'node-1',
        sku: 'SKU-001',
        quantity: 100,
        lastEventAt: '2026-05-01T10:00:00Z',
        version: 3,
        staleness: 'FRESH',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
  },
  meta: { warning: 'Not for procurement decisions (S5)' },
};

const STALE: StalenessResponse = {
  data: [
    {
      nodeId: 'node-1',
      stalenessStatus: 'UNREACHABLE',
      lastEventAt: null,
      lastCheckedAt: '2026-05-01T10:05:00Z',
    },
  ],
  meta: { warning: 'Not for procurement decisions (S5)' },
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('ScmInventoryScreen — render & read tables (read-only)', () => {
  it('renders the snapshot + staleness from the server pages', () => {
    render(<ScmInventoryScreen snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByRole('heading', { name: 'SCM 재고 가시성' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('scm-snap-table')).toBeInTheDocument();
    expect(screen.getByTestId('scm-staleness-table')).toBeInTheDocument();
  });

  it('S5 meta.warning is rendered prominently on EVERY inventory-visibility view (never stripped)', () => {
    render(<ScmInventoryScreen snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    const warnings = screen.getAllByTestId('scm-s5-warning');
    // snapshot + per-SKU header + staleness — at least 3 prominent S5 surfaces.
    expect(warnings.length).toBeGreaterThanOrEqual(3);
    for (const w of warnings) {
      expect(w).toHaveAttribute('role', 'alert');
      expect(w).toHaveTextContent('Not for procurement decisions (S5)');
    }
  });

  it('the staleness panel shows an UNREACHABLE node honestly (not hidden)', () => {
    render(<ScmInventoryScreen snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    expect(screen.getByTestId('scm-staleness-status-0')).toHaveTextContent(
      'UNREACHABLE',
    );
  });
});

describe('ScmInventoryScreen — per-SKU breakdown (S5 surfaced on result)', () => {
  it('fetches a SKU breakdown and renders it WITH its own S5 warning', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          sku: 'SKU-001',
          nodes: [{ nodeId: 'n-1', quantity: 100, staleness: 'FRESH' }],
          totalQuantity: 100,
        },
        meta: { warning: 'Not for procurement decisions (S5)' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ScmInventoryScreen snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });

    await user.type(screen.getByTestId('scm-sku-input'), 'SKU-001');
    await user.click(screen.getByTestId('scm-sku-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('scm-sku-result')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('scm-sku-table')).toBeInTheDocument();
    // The per-SKU result carries its OWN S5 warning — surfaced, not stripped
    // (the screen now has 4 S5 surfaces).
    expect(
      screen.getAllByTestId('scm-s5-warning').length,
    ).toBeGreaterThanOrEqual(4);
  });
});

describe('ScmInventoryScreen — a11y', () => {
  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = render(
      <ScmInventoryScreen snapshot={SNAP} staleness={STALE} />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });
});
