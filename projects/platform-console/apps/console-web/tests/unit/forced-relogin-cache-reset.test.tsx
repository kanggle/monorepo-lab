/**
 * `widgets/forced-relogin-cache-reset/ForcedReLoginCacheReset` (TASK-PC-FE-299 AC-5).
 */

import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ForcedReLoginCacheReset } from '@/widgets/forced-relogin-cache-reset/ForcedReLoginCacheReset';

describe('ForcedReLoginCacheReset', () => {
  it('🔴🔴 clears the QueryClient cache on mount', () => {
    const qc = new QueryClient();
    qc.setQueryData(['accounts', 'stale-operator'], { id: 'stale' });
    expect(qc.getQueryData(['accounts', 'stale-operator'])).toEqual({
      id: 'stale',
    });

    render(
      <QueryClientProvider client={qc}>
        <ForcedReLoginCacheReset />
      </QueryClientProvider>,
    );

    expect(qc.getQueryData(['accounts', 'stale-operator'])).toBeUndefined();
    expect(qc.getQueryCache().getAll()).toHaveLength(0);
  });

  it('renders nothing', () => {
    const qc = new QueryClient();
    const { container } = render(
      <QueryClientProvider client={qc}>
        <ForcedReLoginCacheReset />
      </QueryClientProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
