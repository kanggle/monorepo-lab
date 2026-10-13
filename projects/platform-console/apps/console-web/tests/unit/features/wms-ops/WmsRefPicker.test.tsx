import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import {
  WmsRefPicker,
  type WmsRefPickerProps,
} from '@/features/wms-ops/components/WmsRefPicker';

/**
 * TASK-PC-FE-333 — the shared "코드 · 이름으로 고르는" combobox that replaces
 * the six raw-UUID text inputs on `/wms/inbound` · `/wms/inventory` ·
 * `/wms/outbound`:
 *   - debounced same-origin fetch, scope params (warehouseId/skuId + the
 *     REPEATED partnerType) travel on the wire (AC-2)
 *   - selecting a candidate sets its **id**, never the typed text
 *   - clearing resets both the displayed label and the parent's value
 *   - AC-4 — client-side substring re-filter (survives a server that still
 *     ignores `q` pre-AMI-rebake)
 *   - AC-5 — a UUID-shaped input offers "이 ID로 조회"
 *   - AC-6 — a fetch failure renders an inline error, not an empty list
 *
 * Client calls the same-origin `/api/wms/master/refs/{type}` proxy via
 * `fetch` (mocked) — never wms directly.
 */

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function refsPage(
  rows: {
    id: string;
    code: string;
    name?: string | null;
    status?: string;
    attributes?: Record<string, unknown>;
  }[],
) {
  return {
    content: rows,
    page: { number: 0, size: 20, totalElements: rows.length, totalPages: 1 },
  };
}

/** Controlled harness — mirrors how a filter screen owns `value`/`onChange`. */
function Harness({
  onChangeSpy,
  initial,
  ...rest
}: Omit<WmsRefPickerProps, 'value' | 'onChange'> & {
  onChangeSpy: (id: string | undefined) => void;
  initial?: string;
}) {
  const [value, setValue] = useState<string | undefined>(initial);
  return (
    <WmsRefPicker
      {...rest}
      value={value}
      onChange={(id) => {
        setValue(id);
        onChangeSpy(id);
      }}
    />
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('WmsRefPicker — fetch + scope params (AC-2)', () => {
  it('debounces the fetch and sends q + the REPEATED partnerType scope', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(refsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-partner"
        label="공급처"
        type="partners"
        testid="picker-partner"
        scope={{ partnerType: ['SUPPLIER', 'BOTH'] }}
        onChangeSpy={vi.fn()}
      />,
    );

    const input = screen.getByTestId('picker-partner-input');
    await user.click(input);
    await user.type(input, 'ACME');

    await waitFor(
      () =>
        expect(
          fetchMock.mock.calls.some(([u]) =>
            String(u).includes('/api/wms/master/refs/partners'),
          ),
        ).toBe(true),
      { timeout: 2000 },
    );
    const call = fetchMock.mock.calls.find(([u]) =>
      String(u).includes('/api/wms/master/refs/partners'),
    )!;
    const url = new URL(String(call[0]), 'http://console.local');
    expect(url.searchParams.get('q')).toBe('ACME');
    expect(url.searchParams.getAll('partnerType')).toEqual(['SUPPLIER', 'BOTH']);
  });

  it('scopes locations by warehouseId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(refsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-loc"
        label="위치"
        type="locations"
        testid="picker-loc"
        scope={{ warehouseId: 'wh-9' }}
        onChangeSpy={vi.fn()}
      />,
    );
    const input = screen.getByTestId('picker-loc-input');
    await user.click(input);
    await user.type(input, 'A-01');

    await waitFor(() => expect(fetchMock).toHaveBeenCalled(), { timeout: 2000 });
    const url = new URL(String(fetchMock.mock.calls[0][0]), 'http://console.local');
    expect(url.pathname).toBe('/api/wms/master/refs/locations');
    expect(url.searchParams.get('warehouseId')).toBe('wh-9');
  });

  it('scopes lots by skuId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(refsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-lot"
        label="로트"
        type="lots"
        testid="picker-lot"
        scope={{ skuId: 'sku-9' }}
        onChangeSpy={vi.fn()}
      />,
    );
    const input = screen.getByTestId('picker-lot-input');
    await user.click(input);
    await user.type(input, 'LOT-1');

    await waitFor(() => expect(fetchMock).toHaveBeenCalled(), { timeout: 2000 });
    const url = new URL(String(fetchMock.mock.calls[0][0]), 'http://console.local');
    expect(url.pathname).toBe('/api/wms/master/refs/lots');
    expect(url.searchParams.get('skuId')).toBe('sku-9');
  });
});

describe('WmsRefPicker — selection sets the id (AC-1 / AC-3)', () => {
  it('selecting a candidate sets its id (not the typed text) and shows code · name', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        refsPage([
          { id: 'wh-1-id', code: 'WH01', name: '서울창고' },
          { id: 'wh-2-id', code: 'WH02', name: '부산창고' },
        ]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={onChangeSpy}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await user.type(input, 'WH01');

    const option = await screen.findByTestId(
      'picker-wh-option-wh-1-id',
      {},
      { timeout: 2000 },
    );
    await user.click(option);

    expect(onChangeSpy).toHaveBeenLastCalledWith('wh-1-id');
    expect(input).toHaveValue('WH01 · 서울창고');
  });

  it('clearing resets onChange(undefined) and the displayed label', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(refsPage([{ id: 'wh-1-id', code: 'WH01', name: '서울창고' }])),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={onChangeSpy}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await user.type(input, 'WH01');
    const option = await screen.findByTestId(
      'picker-wh-option-wh-1-id',
      {},
      { timeout: 2000 },
    );
    await user.click(option);
    expect(input).toHaveValue('WH01 · 서울창고');

    await user.click(screen.getByTestId('picker-wh-clear'));
    expect(onChangeSpy).toHaveBeenLastCalledWith(undefined);
    expect(input).toHaveValue('');
  });

  it('externally clearing `value` (parent reset, e.g. 창고 변경 → 위치 해제) also clears the displayed label', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(refsPage([{ id: 'loc-1-id', code: 'A-01', name: null }])),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const { rerender } = render(
      <WmsRefPicker
        id="picker-loc"
        label="위치"
        type="locations"
        testid="picker-loc"
        value="loc-1-id"
        onChange={() => {}}
      />,
    );
    // Parent already holds a selection — select it once to populate the
    // component's own `selectedLabel`, mirroring how a screen would have
    // reached this state via an earlier pick.
    const input = screen.getByTestId('picker-loc-input');
    await user.click(input);
    const option = await screen.findByTestId(
      'picker-loc-option-loc-1-id',
      {},
      { timeout: 2000 },
    );
    await user.click(option);
    expect(input).toHaveValue('A-01');

    // Parent clears the value out-of-band (not via this component's onChange).
    rerender(
      <WmsRefPicker
        id="picker-loc"
        label="위치"
        type="locations"
        testid="picker-loc"
        value={undefined}
        onChange={() => {}}
      />,
    );
    expect(input).toHaveValue('');
  });
});

describe('WmsRefPicker — AC-5 UUID escape hatch', () => {
  it('a UUID-shaped input offers "이 ID로 조회" and selecting it sets that id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(refsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    const uuid = '0199de70-0000-7000-8000-00000000ad03';
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={onChangeSpy}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await user.type(input, uuid);

    const option = await screen.findByTestId('picker-wh-option-uuid', {}, { timeout: 2000 });
    expect(option.textContent).toContain(uuid);
    await user.click(option);
    expect(onChangeSpy).toHaveBeenLastCalledWith(uuid);
    expect(input).toHaveValue(uuid);
  });

  it('a non-UUID string does NOT offer the escape hatch', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(refsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={vi.fn()}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await user.type(input, 'not-a-uuid');

    await waitFor(() => expect(screen.getByTestId('picker-wh-empty')).toBeInTheDocument(), {
      timeout: 2000,
    });
    expect(screen.queryByTestId('picker-wh-option-uuid')).not.toBeInTheDocument();
  });
});

describe('WmsRefPicker — AC-4 client-side re-filter', () => {
  it('hides candidates the server returned that do not match the typed text (pre-AMI-rebake backend)', async () => {
    // Simulates TASK-BE-630 not yet baked into the demo AMI: the server
    // ignores `q` and returns its unfiltered first page regardless.
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        refsPage([
          { id: 'wh-1-id', code: 'WH01', name: '서울창고' },
          { id: 'wh-2-id', code: 'WH02', name: '부산창고' },
        ]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={vi.fn()}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await user.type(input, 'WH01');

    await screen.findByTestId('picker-wh-option-wh-1-id', {}, { timeout: 2000 });
    expect(screen.queryByTestId('picker-wh-option-wh-2-id')).not.toBeInTheDocument();
  });

  it('matches on name too (not only code)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        refsPage([
          { id: 'wh-1-id', code: 'WH01', name: '서울창고' },
          { id: 'wh-2-id', code: 'WH02', name: '부산창고' },
        ]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={vi.fn()}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await user.type(input, '부산');

    await screen.findByTestId('picker-wh-option-wh-2-id', {}, { timeout: 2000 });
    expect(screen.queryByTestId('picker-wh-option-wh-1-id')).not.toBeInTheDocument();
  });

  it('also applies the SCOPE client-side — a pre-rebake server ignores partnerType/warehouseId too', async () => {
    // The server ignores every filter: it returns a customer among the suppliers.
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        refsPage([
          { id: 'p-sup', code: 'SUP-1', name: '공급사', attributes: { partnerType: 'SUPPLIER' } },
          { id: 'p-both', code: 'BTH-1', name: '양쪽', attributes: { partnerType: 'BOTH' } },
          { id: 'p-cus', code: 'CUS-1', name: '고객사', attributes: { partnerType: 'CUSTOMER' } },
          // tolerant: a row without the attribute is kept (server is the authority)
          { id: 'p-unk', code: 'UNK-1', name: '미상' },
        ]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-partner"
        label="공급처"
        type="partners"
        testid="picker-partner"
        scope={{ partnerType: ['SUPPLIER', 'BOTH'] }}
        onChangeSpy={vi.fn()}
      />,
    );
    await user.click(screen.getByTestId('picker-partner-input'));

    await screen.findByTestId('picker-partner-option-p-sup', {}, { timeout: 2000 });
    expect(screen.getByTestId('picker-partner-option-p-both')).toBeInTheDocument();
    expect(screen.getByTestId('picker-partner-option-p-unk')).toBeInTheDocument();
    expect(screen.queryByTestId('picker-partner-option-p-cus')).not.toBeInTheDocument();
  });
});

describe('WmsRefPicker — scope change drops cached candidates', () => {
  it('after the warehouse scope changes, reopening refetches with the new scope and never shows the old rows', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse(refsPage([{ id: 'loc-a', code: 'A-01', attributes: { warehouseId: 'wh-a' } }])),
      )
      .mockResolvedValueOnce(
        jsonResponse(refsPage([{ id: 'loc-b', code: 'B-01', attributes: { warehouseId: 'wh-b' } }])),
      );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();

    function ScopeHarness() {
      const [wh, setWh] = useState('wh-a');
      return (
        <>
          <button type="button" onClick={() => setWh('wh-b')}>
            switch
          </button>
          <Harness
            id="picker-loc"
            label="위치"
            type="locations"
            testid="picker-loc"
            scope={{ warehouseId: wh }}
            onChangeSpy={vi.fn()}
          />
        </>
      );
    }
    render(<ScopeHarness />);

    await user.click(screen.getByTestId('picker-loc-input'));
    await screen.findByTestId('picker-loc-option-loc-a', {}, { timeout: 2000 });

    await user.click(screen.getByRole('button', { name: 'switch' })); // blurs → closes
    await user.click(screen.getByTestId('picker-loc-input'));

    await screen.findByTestId('picker-loc-option-loc-b', {}, { timeout: 2000 });
    expect(screen.queryByTestId('picker-loc-option-loc-a')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(String(fetchMock.mock.calls[1][0])).toContain('warehouseId=wh-b');
  });
});

describe('WmsRefPicker — empty + error states (Edge Case / AC-6)', () => {
  it('shows "일치하는 항목 없음" when there are zero candidates', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(refsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={vi.fn()}
      />,
    );
    await user.click(screen.getByTestId('picker-wh-input'));
    await waitFor(() => expect(screen.getByTestId('picker-wh-empty')).toBeInTheDocument(), {
      timeout: 2000,
    });
  });

  it('AC-6 — a fetch failure shows an inline error, not a silently-empty list', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'x' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={vi.fn()}
      />,
    );
    await user.click(screen.getByTestId('picker-wh-input'));
    await waitFor(() => expect(screen.getByTestId('picker-wh-error')).toBeInTheDocument(), {
      timeout: 2000,
    });
    expect(screen.queryByTestId('picker-wh-empty')).not.toBeInTheDocument();
  });
});

describe('WmsRefPicker — keyboard operability', () => {
  it('ArrowDown highlights candidates and Enter selects the highlighted one', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        refsPage([
          { id: 'wh-1-id', code: 'WH01', name: '서울창고' },
          { id: 'wh-2-id', code: 'WH02', name: '부산창고' },
        ]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={onChangeSpy}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await waitFor(
      () => expect(screen.getByTestId('picker-wh-option-wh-1-id')).toBeInTheDocument(),
      { timeout: 2000 },
    );

    await user.keyboard('{ArrowDown}{ArrowDown}{Enter}');
    expect(onChangeSpy).toHaveBeenLastCalledWith('wh-2-id');
  });

  it('Escape closes the dropdown without selecting', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(refsPage([{ id: 'wh-1-id', code: 'WH01', name: '서울창고' }])),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(
      <Harness
        id="picker-wh"
        label="창고"
        type="warehouses"
        testid="picker-wh"
        onChangeSpy={onChangeSpy}
      />,
    );
    const input = screen.getByTestId('picker-wh-input');
    await user.click(input);
    await waitFor(() => expect(screen.getByTestId('picker-wh-listbox')).toBeInTheDocument(), {
      timeout: 2000,
    });

    await user.keyboard('{Escape}');
    expect(screen.queryByTestId('picker-wh-listbox')).not.toBeInTheDocument();
    expect(onChangeSpy).not.toHaveBeenCalled();
  });
});
