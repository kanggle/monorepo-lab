import {
  PRODUCT_STATUS_OPTIONS,
  ORDER_STATUS_OPTIONS,
  USER_STATUS_OPTIONS,
  SHIPPING_STATUS_OPTIONS,
  VALID_PRODUCT_STATUSES,
  VALID_ORDER_STATUSES,
  VALID_USER_STATUSES,
  VALID_SHIPPING_STATUSES,
} from '@/shared/lib/status-options';

describe('status-options', () => {
  describe('PRODUCT_STATUS_OPTIONS', () => {
    it('ON_SALE, SOLD_OUT, HIDDEN 상태를 포함한다', () => {
      const values = PRODUCT_STATUS_OPTIONS.map((o) => o.value);
      expect(values).toEqual(['ON_SALE', 'SOLD_OUT', 'HIDDEN']);
    });

    it('각 옵션에 한국어 라벨이 있다', () => {
      PRODUCT_STATUS_OPTIONS.forEach((o) => {
        expect(o.label).toBeTruthy();
      });
    });
  });

  describe('ORDER_STATUS_OPTIONS', () => {
    it('PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED 상태를 포함한다', () => {
      const values = ORDER_STATUS_OPTIONS.map((o) => o.value);
      expect(values).toEqual(['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED']);
    });
  });

  describe('USER_STATUS_OPTIONS', () => {
    it('ACTIVE, SUSPENDED, WITHDRAWN 상태를 포함한다', () => {
      const values = USER_STATUS_OPTIONS.map((o) => o.value);
      expect(values).toEqual(['ACTIVE', 'SUSPENDED', 'WITHDRAWN']);
    });
  });

  describe('SHIPPING_STATUS_OPTIONS', () => {
    it('PREPARING, SHIPPED, IN_TRANSIT, DELIVERED 상태를 포함한다', () => {
      const values = SHIPPING_STATUS_OPTIONS.map((o) => o.value);
      expect(values).toEqual(['PREPARING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED']);
    });

    it('각 옵션에 한국어 라벨이 있다', () => {
      SHIPPING_STATUS_OPTIONS.forEach((o) => {
        expect(o.label).toBeTruthy();
      });
    });
  });

  describe('VALID_*_STATUSES', () => {
    it('VALID_PRODUCT_STATUSES는 옵션의 value 배열과 동일하다', () => {
      expect(VALID_PRODUCT_STATUSES).toEqual(PRODUCT_STATUS_OPTIONS.map((o) => o.value));
    });

    it('VALID_ORDER_STATUSES는 옵션의 value 배열과 동일하다', () => {
      expect(VALID_ORDER_STATUSES).toEqual(ORDER_STATUS_OPTIONS.map((o) => o.value));
    });

    it('VALID_USER_STATUSES는 옵션의 value 배열과 동일하다', () => {
      expect(VALID_USER_STATUSES).toEqual(USER_STATUS_OPTIONS.map((o) => o.value));
    });

    it('VALID_SHIPPING_STATUSES는 옵션의 value 배열과 동일하다', () => {
      expect(VALID_SHIPPING_STATUSES).toEqual(SHIPPING_STATUS_OPTIONS.map((o) => o.value));
    });
  });
});
