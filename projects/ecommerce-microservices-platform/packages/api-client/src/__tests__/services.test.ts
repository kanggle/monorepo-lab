import { describe, it, expect, vi } from 'vitest';
import { createAuthApi } from '../services/auth-api';
import { createProductApi } from '../services/product-api';
import { createOrderApi } from '../services/order-api';
import { createSearchApi } from '../services/search-api';
import { createPaymentApi } from '../services/payment-api';
import type { ApiClient } from '../client';

function createMockClient(): ApiClient {
  return {
    get: vi.fn().mockResolvedValue({}),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    patch: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({}),
  } as unknown as ApiClient;
}

describe('Auth API', () => {
  it('signup은 POST /api/auth/signup을 호출한다', async () => {
    const client = createMockClient();
    const api = createAuthApi(client);
    const data = { email: 'test@test.com', password: 'pass123', name: 'Test' };

    await api.signup(data);

    expect(client.post).toHaveBeenCalledWith('/api/auth/signup', data);
  });

  it('login은 POST /api/auth/login을 호출한다', async () => {
    const client = createMockClient();
    const api = createAuthApi(client);
    const data = { email: 'test@test.com', password: 'pass123' };

    await api.login(data);

    expect(client.post).toHaveBeenCalledWith('/api/auth/login', data);
  });

  it('refresh는 POST /api/auth/refresh를 호출한다', async () => {
    const client = createMockClient();
    const api = createAuthApi(client);
    const data = { refreshToken: 'token123' };

    await api.refresh(data);

    expect(client.post).toHaveBeenCalledWith('/api/auth/refresh', data);
  });

  it('logout은 POST /api/auth/logout을 호출한다', async () => {
    const client = createMockClient();
    const api = createAuthApi(client);
    const data = { refreshToken: 'token123' };

    await api.logout(data);

    expect(client.post).toHaveBeenCalledWith('/api/auth/logout', data);
  });
});

describe('Product API', () => {
  it('getProducts는 GET /api/products를 호출한다', async () => {
    const client = createMockClient();
    const api = createProductApi(client);

    await api.getProducts({ page: 0, size: 20 });

    expect(client.get).toHaveBeenCalledWith('/api/products', {
      params: { page: 0, size: 20 },
    });
  });

  it('getProducts는 파라미터 없이 호출할 수 있다', async () => {
    const client = createMockClient();
    const api = createProductApi(client);

    await api.getProducts();

    expect(client.get).toHaveBeenCalledWith('/api/products', {
      params: undefined,
    });
  });

  it('getProduct는 GET /api/products/{productId}를 호출한다', async () => {
    const client = createMockClient();
    const api = createProductApi(client);

    await api.getProduct('prod-123');

    expect(client.get).toHaveBeenCalledWith('/api/products/prod-123');
  });

  it('createProduct는 POST /api/admin/products를 호출한다', async () => {
    const client = createMockClient();
    const api = createProductApi(client);
    const data = {
      name: 'Test Product',
      description: 'A test product',
      price: 10000,
      categoryId: 'cat-1',
      variants: [{ optionName: 'Default', stock: 100, additionalPrice: 0 }],
    };

    await api.createProduct(data);

    expect(client.post).toHaveBeenCalledWith('/api/admin/products', data);
  });

  it('updateProduct는 PATCH /api/admin/products/{productId}를 호출한다', async () => {
    const client = createMockClient();
    const api = createProductApi(client);
    const data = { name: 'Updated Product' };

    await api.updateProduct('prod-123', data);

    expect(client.patch).toHaveBeenCalledWith('/api/admin/products/prod-123', data);
  });

  it('adjustStock은 PATCH /api/admin/products/{productId}/stock을 호출한다', async () => {
    const client = createMockClient();
    const api = createProductApi(client);
    const data = { variantId: 'var-1', quantity: 10, reason: 'restock' };

    await api.adjustStock('prod-123', data);

    expect(client.patch).toHaveBeenCalledWith('/api/admin/products/prod-123/stock', data);
  });
});

describe('Order API', () => {
  it('placeOrder는 POST /api/orders를 호출한다', async () => {
    const client = createMockClient();
    const api = createOrderApi(client);
    const data = {
      items: [{ productId: 'p1', variantId: 'v1', productName: 'Test Product', optionName: '기본', quantity: 1, unitPrice: 10000 }],
      shippingAddress: {
        recipient: 'Test',
        phone: '010-1234-5678',
        zipCode: '12345',
        address1: 'Seoul',
        address2: 'Apt 101',
      },
    };

    await api.placeOrder(data);

    // placeOrder forwards an optional per-request config (TASK-BE-430 Idempotency-Key);
    // undefined when none is supplied.
    expect(client.post).toHaveBeenCalledWith('/api/orders', data, undefined);
  });

  it('placeOrder는 config(Idempotency-Key 헤더)를 전달한다', async () => {
    const client = createMockClient();
    const api = createOrderApi(client);
    const data = {
      items: [{ productId: 'p1', variantId: 'v1', productName: 'A', quantity: 1, unitPrice: 1000 }],
      shippingAddress: { recipient: 'T', phone: '0', zipCode: '1', address1: 'x', address2: '' },
    };
    const config = { headers: { 'Idempotency-Key': 'key-1' } };

    await api.placeOrder(data, config);

    expect(client.post).toHaveBeenCalledWith('/api/orders', data, config);
  });

  it('getOrders는 GET /api/orders를 호출한다', async () => {
    const client = createMockClient();
    const api = createOrderApi(client);

    await api.getOrders({ page: 0, size: 10 });

    expect(client.get).toHaveBeenCalledWith('/api/orders', {
      params: { page: 0, size: 10 },
    });
  });

  it('getOrders는 status 파라미터를 전달한다', async () => {
    const client = createMockClient();
    const api = createOrderApi(client);

    await api.getOrders({ page: 0, size: 10, status: 'PENDING' });

    expect(client.get).toHaveBeenCalledWith('/api/orders', {
      params: { page: 0, size: 10, status: 'PENDING' },
    });
  });

  it('getOrder는 GET /api/orders/{orderId}를 호출한다', async () => {
    const client = createMockClient();
    const api = createOrderApi(client);

    await api.getOrder('order-123');

    expect(client.get).toHaveBeenCalledWith('/api/orders/order-123');
  });

  it('cancelOrder는 POST /api/orders/{orderId}/cancel을 호출한다', async () => {
    const client = createMockClient();
    const api = createOrderApi(client);

    await api.cancelOrder('order-123');

    expect(client.post).toHaveBeenCalledWith('/api/orders/order-123/cancel', {});
  });
});

describe('Search API', () => {
  it('searchProducts는 GET /api/search/products를 호출한다', async () => {
    const client = createMockClient();
    const api = createSearchApi(client);
    const params = { q: 'laptop', page: 0, size: 20 };

    await api.searchProducts(params);

    expect(client.get).toHaveBeenCalledWith('/api/search/products', { params });
  });
});

describe('Payment API', () => {
  it('getPayment는 GET /api/payments/orders/{orderId}를 호출한다', async () => {
    const client = createMockClient();
    const api = createPaymentApi(client);

    await api.getPayment('order-123');

    expect(client.get).toHaveBeenCalledWith('/api/payments/orders/order-123');
  });
});
