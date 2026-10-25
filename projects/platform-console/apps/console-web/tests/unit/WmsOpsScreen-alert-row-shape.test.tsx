import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { WmsOpsScreen } from '@/features/wms-ops';
import { AlertPageSchema } from '@/features/wms-ops/api/types';

/**
 * TASK-PC-FE-373 AC-3/4/5 on the screen - rows are the PRODUCER shape (`id`, no `alertId`,
 * `message`, `acknowledged`), parsed through the real `AlertPageSchema` exactly as the server
 * seed / client refetch do.
 */

const ID_OPEN = '3f2b1c9e-0000-4000-8000-000000000001';
const ID_ACKED = '3f2b1c9e-0000-4000-8000-000000000002';

const base = {
  alertType: 'LOW_STOCK',
  warehouseId: null,
  locationId: '3f2b1c9e-0000-4000-8000-0000000000a1',
  skuId: '3f2b1c9e-0000-4000-8000-0000000000b1',
  lotId: null,
  thresholdQty: 10,
  actualQty: 3,
  detectedAt: '2026-10-25T01:00:00Z',
  projectedAt: '2026-10-25T01:00:01Z',
};
const OPEN_ROW = { ...base, id: ID_OPEN, acknowledgedAt: null, acknowledgedBy: null };
const ACKED_ROW = {
  ...base,
  id: ID_ACKED,
  acknowledgedAt: '2026-10-25T02:00:00Z',
  acknowledgedBy: null, // acknowledgedAt set, acknowledgedBy null -> still acknowledged
};
const META = { number: 0, size: 20, totalElements: 2, totalPages: 1 };

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}
function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function renderWith(rows: unknown[]) {
  const alerts = AlertPageSchema.parse({ content: rows, page: META });
  return render(<WmsOpsScreen alerts={alerts} lagSeconds={null} />, { wrapper: wrapper() });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('TASK-PC-FE-373 AC-3 - ack posts to the row id, never `undefined`', () => {
  it('ack button -> /api/wms/alerts/<id>/acknowledge, and the producer-shaped ack response parses', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/acknowledge')
          ? jsonResponse({ ...OPEN_ROW, acknowledgedAt: '2026-10-25T03:00:00Z', acknowledgedBy: 'op' })
          : jsonResponse({ content: [OPEN_ROW], page: META }),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderWith([OPEN_ROW]);

    await user.click(screen.getByTestId('wms-alert-ack-0'));
    await user.click(screen.getByTestId('wms-ack-confirm'));

    await waitFor(() =>
      expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('/acknowledge'))).toBe(true),
    );
    const ackUrl = String(
      fetchMock.mock.calls.find((c) => String(c[0]).includes('/acknowledge'))![0],
    );
    expect(ackUrl).toBe(`/api/wms/alerts/${ID_OPEN}/acknowledge`);
    expect(ackUrl).not.toContain('undefined');
    // A successful ack must close the dialog (a response parse failure would show an error).
    await waitFor(() =>
      expect(screen.queryByTestId('wms-ack-dialog')).not.toBeInTheDocument(),
    );
    expect(screen.queryByTestId('wms-ack-error')).not.toBeInTheDocument();
  });
});

describe('TASK-PC-FE-373 AC-4 - acknowledged state derives from acknowledgedAt', () => {
  it('acknowledgedAt set -> acknowledged badge and no button; unset -> button', () => {
    renderWith([OPEN_ROW, ACKED_ROW]);
    expect(screen.getByTestId('wms-alert-ack-0')).toBeInTheDocument();
    expect(screen.queryByTestId('wms-alert-ack-1')).toBeNull();
    expect(screen.getByTestId('wms-alert-acked-1')).toHaveTextContent('확인됨');
    expect(screen.queryByTestId('wms-alert-acked-0')).toBeNull();
  });
});

describe('TASK-PC-FE-373 AC-5 - the table renders the good row beside an unparseable one', () => {
  it('good row shown, bad row excluded with a count note', () => {
    renderWith([OPEN_ROW, { ...base, acknowledgedAt: null }]);
    expect(screen.getByTestId('wms-alerts-table')).toBeInTheDocument();
    expect(screen.getByTestId('wms-alert-row-0')).toBeInTheDocument();
    expect(screen.queryByTestId('wms-alert-row-1')).toBeNull();
    expect(screen.getByTestId('wms-alerts-excluded-note')).toHaveTextContent('1');
  });
});
