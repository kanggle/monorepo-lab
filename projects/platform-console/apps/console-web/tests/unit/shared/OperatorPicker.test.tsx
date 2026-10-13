import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import {
  OperatorPicker,
  type OperatorPickerProps,
} from '@/shared/ui/OperatorPicker';

/**
 * TASK-PC-FE-334 — the shared "이메일 · 이름으로 고르는" operator combobox
 * replacing raw-UUID operator-id inputs (org-hierarchy 노드 관리자 배정,
 * partnerships 참여자 배정/해제). Mirrors `WmsRefPicker.test.tsx`'s harness
 * shape; data source is the same-origin `/api/operators` proxy (mocked via
 * global `fetch`, never IAM directly).
 */

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function operatorsPage(
  rows: { operatorId: string; email: string; displayName: string; status?: string }[],
  totalPages = 1,
) {
  return {
    content: rows.map((r) => ({
      operatorId: r.operatorId,
      email: r.email,
      displayName: r.displayName,
      status: r.status ?? 'ACTIVE',
      roles: [],
      createdAt: '2026-01-01T00:00:00Z',
    })),
    totalElements: rows.length,
    page: 0,
    size: 100,
    totalPages,
  };
}

/** Controlled harness — mirrors how a screen owns `value`/`onChange`. */
function Harness({
  onChangeSpy,
  ...rest
}: Omit<OperatorPickerProps, 'value' | 'onChange'> & {
  onChangeSpy: OperatorPickerProps['onChange'];
}) {
  const [value, setValue] = useState<string | undefined>(undefined);
  return (
    <OperatorPicker
      {...rest}
      value={value}
      onChange={(...args) => {
        setValue(args[0]);
        onChangeSpy(...args);
      }}
    />
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('OperatorPicker — filter + select (AC-1)', () => {
  it('filters loaded rows by email/displayName and selecting sets the operatorId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        operatorsPage([
          { operatorId: 'op-1', email: 'alice@demo.co', displayName: 'Alice' },
          { operatorId: 'op-2', email: 'bob@demo.co', displayName: 'Bob' },
        ]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(
      <Harness id="op-picker" label="운영자" testid="op-picker" onChangeSpy={onChangeSpy} />,
    );

    const input = screen.getByTestId('op-picker-input');
    await user.click(input);
    await waitFor(() => expect(screen.getByTestId('op-picker-option-op-1')).toBeInTheDocument(), {
      timeout: 2000,
    });

    await user.type(input, 'alice');
    expect(screen.getByTestId('op-picker-option-op-1')).toBeInTheDocument();
    expect(screen.queryByTestId('op-picker-option-op-2')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('op-picker-option-op-1'));
    expect(onChangeSpy).toHaveBeenLastCalledWith(
      'op-1',
      expect.objectContaining({ operatorId: 'op-1', email: 'alice@demo.co' }),
    );
    expect(input).toHaveValue('Alice (alice@demo.co)');
  });

  it('clearing resets onChange(undefined) and the displayed label', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(operatorsPage([{ operatorId: 'op-1', email: 'a@x.co', displayName: 'A' }])),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(
      <Harness id="op-picker" label="운영자" testid="op-picker" onChangeSpy={onChangeSpy} />,
    );
    const input = screen.getByTestId('op-picker-input');
    await user.click(input);
    await user.click(await screen.findByTestId('op-picker-option-op-1'));
    expect(input).toHaveValue('A (a@x.co)');

    await user.click(screen.getByTestId('op-picker-clear'));
    expect(onChangeSpy).toHaveBeenLastCalledWith(undefined);
    expect(input).toHaveValue('');
  });
});

describe('OperatorPicker — AC-3 UUID escape hatch', () => {
  it('a UUID-shaped input offers "이 ID로 사용" and selecting it sets that id (no operator)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(operatorsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    const uuid = '0199de70-0000-7000-8000-00000000ad03';
    render(
      <Harness id="op-picker" label="운영자" testid="op-picker" onChangeSpy={onChangeSpy} />,
    );
    const input = screen.getByTestId('op-picker-input');
    await user.click(input);
    await user.type(input, uuid);

    const option = await screen.findByTestId('op-picker-option-uuid', {}, { timeout: 2000 });
    expect(option.textContent).toContain(uuid);
    await user.click(option);
    expect(onChangeSpy).toHaveBeenLastCalledWith(uuid, undefined);
    expect(input).toHaveValue(uuid);
  });

  it('a non-UUID string does NOT offer the escape hatch', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(operatorsPage([])));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<Harness id="op-picker" label="운영자" testid="op-picker" onChangeSpy={vi.fn()} />);
    const input = screen.getByTestId('op-picker-input');
    await user.click(input);
    await user.type(input, 'not-a-uuid');

    await waitFor(() => expect(screen.getByTestId('op-picker-empty')).toBeInTheDocument(), {
      timeout: 2000,
    });
    expect(screen.queryByTestId('op-picker-option-uuid')).not.toBeInTheDocument();
  });
});

describe('OperatorPicker — AC-2 fetch-failure fallback', () => {
  it('a 403/503 on the first page falls back to a plain ID text input under the SAME input testid', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'PERMISSION_DENIED', message: 'x' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    const onChangeSpy = vi.fn();
    render(<Harness id="op-picker" label="운영자" testid="op-picker" onChangeSpy={onChangeSpy} />);
    const input = screen.getByTestId('op-picker-input');
    await user.click(input);

    await waitFor(
      () => expect(screen.getByTestId('op-picker-fallback-notice')).toBeInTheDocument(),
      { timeout: 2000 },
    );
    const manualInput = screen.getByTestId('op-picker-input');
    await user.type(manualInput, 'operator-raw-uuid');
    expect(onChangeSpy).toHaveBeenLastCalledWith('operator-raw-uuid');
  });
});

describe('OperatorPicker — load more (Edge Case: >100 운영자)', () => {
  it('shows a load-more affordance + limitation note when more pages exist, and accumulates rows', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse(
          operatorsPage([{ operatorId: 'op-1', email: 'a@x.co', displayName: 'A' }], 2),
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          { ...operatorsPage([{ operatorId: 'op-2', email: 'b@x.co', displayName: 'B' }], 2), page: 1 },
        ),
      );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<Harness id="op-picker" label="운영자" testid="op-picker" onChangeSpy={vi.fn()} />);
    const input = screen.getByTestId('op-picker-input');
    await user.click(input);

    await waitFor(() => expect(screen.getByTestId('op-picker-load-more')).toBeInTheDocument(), {
      timeout: 2000,
    });
    expect(screen.getByTestId('op-picker-load-more-note')).toBeInTheDocument();

    await user.click(screen.getByTestId('op-picker-load-more'));
    await waitFor(() => expect(screen.getByTestId('op-picker-option-op-2')).toBeInTheDocument(), {
      timeout: 2000,
    });
    expect(screen.getByTestId('op-picker-option-op-1')).toBeInTheDocument();
  });
});
