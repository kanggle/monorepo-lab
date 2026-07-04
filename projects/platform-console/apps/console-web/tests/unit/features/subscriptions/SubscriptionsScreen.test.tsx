import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SubscriptionsScreen } from '@/features/subscriptions';
import { ApiError } from '@/shared/api/errors';

/**
 * `SubscriptionsScreen` — the tenant-owner domain subscription surface
 * (TASK-PC-FE-183). `apiClient` + `useRouter` are mocked so mutations + the
 * post-success refresh are asserted without a real backend / navigation.
 */

const post = vi.fn();
const patch = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    post: (...a: unknown[]) => post(...a),
    patch: (...a: unknown[]) => patch(...a),
  },
}));

const refresh = vi.fn();
vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh }) }));

const ROWS = [
  { key: 'wms' as const, label: 'WMS', state: 'ACTIVE' as const },
  { key: 'scm' as const, label: 'SCM', state: 'NOT_SUBSCRIBED' as const },
];

beforeEach(() => {
  post.mockReset();
  patch.mockReset();
  refresh.mockReset();
});

describe('SubscriptionsScreen — render', () => {
  it('shows 구독 중 + suspend/cancel for ACTIVE, 미구독 + subscribe for NOT_SUBSCRIBED', () => {
    render(<SubscriptionsScreen activeTenant="acme-corp" rows={ROWS} />);
    expect(screen.getByTestId('subscription-status-wms')).toHaveTextContent('구독 중');
    expect(screen.getByTestId('subscription-suspend-wms')).toBeInTheDocument();
    expect(screen.getByTestId('subscription-cancel-wms')).toBeInTheDocument();
    expect(screen.getByTestId('subscription-status-scm')).toHaveTextContent('미구독');
    expect(screen.getByTestId('subscription-subscribe-scm')).toBeInTheDocument();
  });
});

describe('SubscriptionsScreen — subscribe', () => {
  it('requires a reason, then POSTs {domainKey, reason} and refreshes', async () => {
    post.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<SubscriptionsScreen activeTenant="acme-corp" rows={ROWS} />);

    await user.click(screen.getByTestId('subscription-subscribe-scm'));
    const submit = screen.getByTestId('subscription-confirm-submit');
    // reason empty → gated
    expect(submit).toBeDisabled();

    await user.type(screen.getByTestId('subscription-confirm-reason'), 'SCM 도입');
    expect(submit).not.toBeDisabled();
    await user.click(submit);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/subscriptions', {
        domainKey: 'scm',
        reason: 'SCM 도입',
      }),
    );
    expect(refresh).toHaveBeenCalled();
  });

  it('on 409 ALREADY_EXISTS, switches the dialog to 재개 and shows the actionable message', async () => {
    post.mockRejectedValue(
      new ApiError(409, 'SUBSCRIPTION_ALREADY_EXISTS', 'exists'),
    );
    const user = userEvent.setup();
    render(<SubscriptionsScreen activeTenant="acme-corp" rows={ROWS} />);

    await user.click(screen.getByTestId('subscription-subscribe-scm'));
    await user.type(screen.getByTestId('subscription-confirm-reason'), 'SCM 도입');
    await user.click(screen.getByTestId('subscription-confirm-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('subscription-confirm-error')).toHaveTextContent(
        '이미 구독 이력',
      ),
    );
    // Dialog stays open, transformed to a resume action.
    expect(screen.getByTestId('subscription-confirm-submit')).toHaveTextContent('재개');
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe('SubscriptionsScreen — suspend', () => {
  it('PATCHes status SUSPENDED for an ACTIVE domain', async () => {
    patch.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<SubscriptionsScreen activeTenant="acme-corp" rows={ROWS} />);

    await user.click(screen.getByTestId('subscription-suspend-wms'));
    await user.type(screen.getByTestId('subscription-confirm-reason'), '점검');
    await user.click(screen.getByTestId('subscription-confirm-submit'));

    await waitFor(() =>
      expect(patch).toHaveBeenCalledWith('/api/subscriptions/wms/status', {
        status: 'SUSPENDED',
        reason: '점검',
      }),
    );
    expect(refresh).toHaveBeenCalled();
  });

  it('surfaces an inline error on a producer failure without navigating', async () => {
    patch.mockRejectedValue(
      new ApiError(409, 'SUBSCRIPTION_TRANSITION_INVALID', 'bad'),
    );
    const user = userEvent.setup();
    render(<SubscriptionsScreen activeTenant="acme-corp" rows={ROWS} />);

    await user.click(screen.getByTestId('subscription-cancel-wms'));
    await user.type(screen.getByTestId('subscription-confirm-reason'), '해지');
    await user.click(screen.getByTestId('subscription-confirm-submit'));

    expect(await screen.findByTestId('subscription-confirm-error')).toBeInTheDocument();
    expect(refresh).not.toHaveBeenCalled();
  });
});
