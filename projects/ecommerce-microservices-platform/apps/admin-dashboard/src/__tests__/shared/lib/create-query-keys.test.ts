import { createQueryKeys } from '@/shared/lib/create-query-keys';

describe('createQueryKeys', () => {
  const keys = createQueryKeys('admin', 'products');

  it('all 키는 [scope, resource] 형태를 반환한다', () => {
    expect(keys.all).toEqual(['admin', 'products']);
  });

  it('list 키는 all에 params를 추가한 형태를 반환한다', () => {
    const params = { page: 0, status: 'ON_SALE' };
    expect(keys.list(params)).toEqual(['admin', 'products', params]);
  });

  it('detail 키는 all에 id를 추가한 형태를 반환한다', () => {
    expect(keys.detail('abc-123')).toEqual(['admin', 'products', 'abc-123']);
  });

  it('서로 다른 scope/resource 조합은 다른 키를 생성한다', () => {
    const orderKeys = createQueryKeys('admin', 'orders');
    expect(orderKeys.all).not.toEqual(keys.all);
    expect(orderKeys.detail('1')).not.toEqual(keys.detail('1'));
  });
});
