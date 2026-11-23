import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-385 — `makeIamProxyErrorMapper` / `badRequest` (shared) and the 7
 * IAM-family `mapError` exports built from it. The IAM mapping is NOT the
 * non-IAM `makeProxyErrorMapper`: 403 passes through unchanged, `details` is
 * dropped, the final fallback code is `DOWNSTREAM_ERROR`.
 */

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

import { logger } from '@/shared/lib/logger';
import {
  ApiError,
  TenantsUnavailableError,
  OperatorsUnavailableError,
  GroupsUnavailableError,
  OrgNodesUnavailableError,
  PartnershipsUnavailableError,
  SubscriptionsUnavailableError,
  DomainPermissionSetsUnavailableError,
} from '@/shared/api/errors';
import {
  makeIamProxyErrorMapper,
  badRequest,
} from '@/shared/api/proxy-factory';
import { mapError as tenantsMap } from '@/app/api/tenants/_proxy';
import { mapError as operatorsMap } from '@/app/api/operators/_proxy';
import { mapError as groupsMap } from '@/app/api/groups/_proxy';
import { mapError as orgNodesMap } from '@/app/api/org-nodes/_proxy';
import { mapError as partnershipsMap } from '@/app/api/partnerships/_proxy';
import { mapError as subscriptionsMap } from '@/app/api/subscriptions/_proxy';
import { mapError as dpsMap } from '@/app/api/domain-permission-sets/_proxy';

class FooUnavailableError extends Error {
  readonly reason = 'timeout' as const;
  readonly code = 'FOO_UNAVAILABLE';
}

const mapper = makeIamProxyErrorMapper({
  logPrefix: 'foo',
  UnavailableErrorClass: FooUnavailableError,
  unavailableMessage: 'foo unavailable',
});

beforeEach(() => {
  vi.clearAllMocks();
});

describe('makeIamProxyErrorMapper', () => {
  it('401 -> 401 with the server code and fixed "session expired"', async () => {
    const res = mapper(new ApiError(401, 'TOKEN_EXPIRED', 'raw'), 'r1');
    expect(res.status).toBe(401);
    expect(await res.json()).toEqual({
      code: 'TOKEN_EXPIRED',
      message: 'session expired',
    });
  });

  it('401 keeps an empty code as-is (no UNAUTHORIZED substitution)', async () => {
    const res = mapper(new ApiError(401, '', 'raw'), 'r1');
    expect(await res.json()).toEqual({ code: '', message: 'session expired' });
  });

  it('NO_ACTIVE_TENANT -> 400 even when the status is something else', async () => {
    const res = mapper(new ApiError(403, 'NO_ACTIVE_TENANT', 'x'), 'r1');
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({
      code: 'NO_ACTIVE_TENANT',
      message: 'no active tenant selected',
    });
  });

  it('403 passes the server code and message through unchanged', async () => {
    const res = mapper(
      new ApiError(403, 'PERMISSION_DENIED', 'operator lacks tenant.write'),
      'r1',
    );
    expect(res.status).toBe(403);
    expect(await res.json()).toEqual({
      code: 'PERMISSION_DENIED',
      message: 'operator lacks tenant.write',
    });
  });

  it('other ApiError passes through with its status and drops details', async () => {
    const res = mapper(
      new ApiError(409, 'TENANT_ALREADY_EXISTS', 'dup', undefined, {
        cause: 'secret',
      }),
      'r1',
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body).toEqual({ code: 'TENANT_ALREADY_EXISTS', message: 'dup' });
    expect('details' in body).toBe(false);
  });

  it('Unavailable -> warn <prefix>_proxy_degraded + 503 with the configured message', async () => {
    const res = mapper(new FooUnavailableError('boom'), 'req-9');
    expect(res.status).toBe(503);
    expect(await res.json()).toEqual({
      code: 'FOO_UNAVAILABLE',
      message: 'foo unavailable',
    });
    expect(logger.warn).toHaveBeenCalledWith('foo_proxy_degraded', {
      requestId: 'req-9',
      reason: 'timeout',
    });
    expect(logger.error).not.toHaveBeenCalled();
  });

  it('unknown error -> error <prefix>_proxy_error + 503 DOWNSTREAM_ERROR', async () => {
    const res = mapper(new Error('kaboom'), 'req-7');
    expect(res.status).toBe(503);
    expect(await res.json()).toEqual({
      code: 'DOWNSTREAM_ERROR',
      message: 'foo unavailable',
    });
    expect(logger.error).toHaveBeenCalledWith('foo_proxy_error', {
      requestId: 'req-7',
    });
    expect(logger.warn).not.toHaveBeenCalled();
  });
});

describe('badRequest', () => {
  it('422 VALIDATION_ERROR "invalid request body"', async () => {
    const res = badRequest();
    expect(res.status).toBe(422);
    expect(await res.json()).toEqual({
      code: 'VALIDATION_ERROR',
      message: 'invalid request body',
    });
  });
});

describe('the 7 IAM-family mapError exports keep their own prefix and message', () => {
  const cases = [
    ['tenants', tenantsMap, TenantsUnavailableError, 'tenants', 'tenant management unavailable'],
    ['operators', operatorsMap, OperatorsUnavailableError, 'operators', 'operators unavailable'],
    ['groups', groupsMap, GroupsUnavailableError, 'groups', 'operator-group service unavailable'],
    ['org-nodes', orgNodesMap, OrgNodesUnavailableError, 'org_nodes', 'org-node hierarchy unavailable'],
    ['partnerships', partnershipsMap, PartnershipsUnavailableError, 'partnerships', 'partnerships unavailable'],
    ['subscriptions', subscriptionsMap, SubscriptionsUnavailableError, 'subscriptions', 'subscriptions unavailable'],
    ['domain-permission-sets', dpsMap, DomainPermissionSetsUnavailableError, 'domain_permission_sets', '업무 권한 세트 service unavailable'],
  ] as const;

  it.each(cases)('%s', async (_name, map, Unavailable, prefix, message) => {
    const degraded = map(new Unavailable('downstream', 'X_UNAVAILABLE', 'm'), 'rid');
    expect(degraded.status).toBe(503);
    expect(await degraded.json()).toEqual({ code: 'X_UNAVAILABLE', message });
    expect(logger.warn).toHaveBeenCalledWith(`${prefix}_proxy_degraded`, {
      requestId: 'rid',
      reason: 'downstream',
    });

    const unknown = map(new Error('x'), 'rid2');
    expect(await unknown.json()).toEqual({ code: 'DOWNSTREAM_ERROR', message });
    expect(logger.error).toHaveBeenCalledWith(`${prefix}_proxy_error`, {
      requestId: 'rid2',
    });
  });
});
