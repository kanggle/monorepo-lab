import { describe, expect, it, vi, beforeEach } from 'vitest';

const apiGet = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: { get: (...a: unknown[]) => apiGet(...a) },
}));

import { queryGrantableSets } from '@/shared/api/grantable-domain-sets';

const item = (setId: string, systemKey: string | null) => ({
  setId,
  name: setId,
  systemKey,
  readOnly: false,
  domains: ['wms'],
});

describe('queryGrantableSets', () => {
  beforeEach(() => apiGet.mockReset());

  it('calls the slice-a list route', async () => {
    apiGet.mockResolvedValue({ items: [] });
    await queryGrantableSets();
    expect(apiGet).toHaveBeenCalledWith('/api/domain-permission-sets');
  });

  it('excludes the legacy-compat set and keeps the rest (incl. systemKey null)', async () => {
    apiGet.mockResolvedValue({
      items: [
        item('a', 'legacy-compat'),
        item('b', null),
        item('c', 'wms-viewer'),
      ],
    });
    const out = await queryGrantableSets();
    expect(out.map((s) => s.setId)).toEqual(['b', 'c']);
  });

  it('rejects a malformed response', async () => {
    apiGet.mockResolvedValue({ items: [{ setId: 1 }] });
    await expect(queryGrantableSets()).rejects.toThrow();
  });
});
