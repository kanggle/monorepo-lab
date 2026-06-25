import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ShipFormDialog } from '@/features/ecommerce-ops';

/**
 * TASK-MONO-305 — the PREPARING → SHIPPED ship form's WMS-deduct toggle
 * (ADR-MONO-022 D4 v2(c)). The "WMS 재고 차감" checkbox is rendered ONLY when
 * the row is `wmsRouted`; on confirm a checked box puts `deductWmsInventory: true`
 * in the onConfirm payload, otherwise it is `false`.
 *
 * Existing ship-form behaviour (carrier + trackingNumber required gate) is
 * unchanged — those fields still gate the confirm button.
 */

describe('ShipFormDialog — WMS-deduct toggle (wmsRouted gate)', () => {
  it('renders the WMS 재고 차감 checkbox when wmsRouted=true', () => {
    render(
      <ShipFormDialog
        open
        shippingId="ship-1"
        wmsRouted
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByTestId('ship-form-deduct-wms')).toBeInTheDocument();
    expect(screen.getByText('WMS 재고 차감')).toBeInTheDocument();
  });

  it('does NOT render the checkbox when wmsRouted=false', () => {
    render(
      <ShipFormDialog
        open
        shippingId="ship-1"
        wmsRouted={false}
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.queryByTestId('ship-form-deduct-wms')).not.toBeInTheDocument();
  });

  it('does NOT render the checkbox when wmsRouted is omitted (default false)', () => {
    render(
      <ShipFormDialog
        open
        shippingId="ship-1"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.queryByTestId('ship-form-deduct-wms')).not.toBeInTheDocument();
  });

  it('checking the box → onConfirm payload carries deductWmsInventory=true', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <ShipFormDialog
        open
        shippingId="ship-1"
        wmsRouted
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    await user.type(screen.getByTestId('ship-form-carrier'), 'CJ대한통운');
    await user.type(screen.getByTestId('ship-form-tracking-number'), 'TRK-001');
    await user.click(screen.getByTestId('ship-form-deduct-wms'));
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledWith({
      carrier: 'CJ대한통운',
      trackingNumber: 'TRK-001',
      deductWmsInventory: true,
    });
  });

  it('leaving the box unchecked → onConfirm payload carries deductWmsInventory=false', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <ShipFormDialog
        open
        shippingId="ship-1"
        wmsRouted
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    await user.type(screen.getByTestId('ship-form-carrier'), 'CJ대한통운');
    await user.type(screen.getByTestId('ship-form-tracking-number'), 'TRK-001');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));

    expect(onConfirm).toHaveBeenCalledWith({
      carrier: 'CJ대한통운',
      trackingNumber: 'TRK-001',
      deductWmsInventory: false,
    });
  });

  it('a non-wmsRouted row never carries a true flag (no toggle to set)', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <ShipFormDialog
        open
        shippingId="ship-1"
        wmsRouted={false}
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    await user.type(screen.getByTestId('ship-form-carrier'), 'CJ대한통운');
    await user.type(screen.getByTestId('ship-form-tracking-number'), 'TRK-001');
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));

    expect(onConfirm).toHaveBeenCalledWith({
      carrier: 'CJ대한통운',
      trackingNumber: 'TRK-001',
      deductWmsInventory: false,
    });
  });

  it('confirm stays gated until both carrier + trackingNumber are filled', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(
      <ShipFormDialog
        open
        shippingId="ship-1"
        wmsRouted
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    // Only carrier filled — still disabled.
    await user.type(screen.getByTestId('ship-form-carrier'), 'CJ대한통운');
    expect(screen.getByTestId('ecommerce-confirm-confirm')).toBeDisabled();
    await user.click(screen.getByTestId('ecommerce-confirm-confirm'));
    expect(onConfirm).not.toHaveBeenCalled();

    await user.type(screen.getByTestId('ship-form-tracking-number'), 'TRK-001');
    expect(screen.getByTestId('ecommerce-confirm-confirm')).toBeEnabled();
  });
});
