import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ParticipantManager } from '@/features/partnerships/components/ParticipantManager';

/**
 * TASK-PC-FE-334 AC-1 — the 참여자 배정/해제 panel's raw-UUID operator-id
 * input is replaced by the shared `<OperatorPicker>`. `onOpen` must still
 * receive the picked operator's raw `operatorId`; `label` carries a
 * name/email display string when the picker resolved the operator.
 */

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function operatorsPage(
  rows: { operatorId: string; email: string; displayName: string }[],
) {
  return {
    content: rows.map((r) => ({
      operatorId: r.operatorId,
      email: r.email,
      displayName: r.displayName,
      status: 'ACTIVE',
      roles: [],
      createdAt: '2026-01-01T00:00:00Z',
    })),
    totalElements: rows.length,
    page: 0,
    size: 100,
    totalPages: 1,
  };
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('ParticipantManager — operator picker (AC-1)', () => {
  it('배정: picking an operator sends its operatorId to onOpen, with a name/email label', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        operatorsPage([{ operatorId: 'op-9', email: 'alice@demo.co', displayName: 'Alice' }]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onOpen = vi.fn();
    render(<ParticipantManager partnershipId="pship-1" onOpen={onOpen} />);

    const input = screen.getByTestId('partnership-participant-operator-pship-1-input');
    await user.click(input);
    const option = await screen.findByTestId(
      'partnership-participant-operator-pship-1-option-op-9',
      {},
      { timeout: 2000 },
    );
    await user.click(option);

    await user.click(screen.getByTestId('partnership-participant-add-pship-1'));

    expect(onOpen).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'participant-add',
        partnershipId: 'pship-1',
        operatorId: 'op-9',
        label: 'Alice (alice@demo.co)',
      }),
    );
  });

  it('해제: picking an operator sends its operatorId to onOpen for participant-remove', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(
        operatorsPage([{ operatorId: 'op-7', email: 'bob@demo.co', displayName: 'Bob' }]),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    const onOpen = vi.fn();
    render(<ParticipantManager partnershipId="pship-2" onOpen={onOpen} />);

    const input = screen.getByTestId('partnership-participant-operator-pship-2-input');
    await user.click(input);
    const option = await screen.findByTestId(
      'partnership-participant-operator-pship-2-option-op-7',
      {},
      { timeout: 2000 },
    );
    await user.click(option);

    await user.click(screen.getByTestId('partnership-participant-remove-pship-2'));

    expect(onOpen).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'participant-remove',
        partnershipId: 'pship-2',
        operatorId: 'op-7',
        label: 'Bob (bob@demo.co)',
      }),
    );
  });

  it('the 배정/해제 buttons stay disabled until an operator is picked', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(operatorsPage([]))));
    render(<ParticipantManager partnershipId="pship-3" onOpen={vi.fn()} />);
    expect(screen.getByTestId('partnership-participant-add-pship-3')).toBeDisabled();
    expect(screen.getByTestId('partnership-participant-remove-pship-3')).toBeDisabled();
  });
});
