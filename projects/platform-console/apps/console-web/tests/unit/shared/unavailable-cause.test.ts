import { describe, expect, it } from 'vitest';
import { ApiError } from '@/shared/api/errors';
import { classifyUnavailable, formatUnavailableCause } from '@/shared/api/unavailable-cause';

/**
 * TASK-PC-FE-341 re-measure — the instrumented nightlies (39128709878 · 39130456238) each logged one
 * `operator_overview_bff_unavailable cause=transport:TypeError` after ~10 s with no composition leg.
 * `TypeError` alone cannot tell a connect timeout from a reset: undici puts the reason on
 * `err.cause.code` (`UND_ERR_CONNECT_TIMEOUT` · `ECONNRESET` · `UND_ERR_SOCKET` …) and the classifier
 * dropped it. These pin that the CODE (a shape, not a value) is carried and nothing else is.
 */
function undiciFetchFailed(code?: unknown): TypeError {
  const err = new TypeError('fetch failed');
  if (code !== undefined) (err as { cause?: unknown }).cause = { code, message: 'connect to 127.0.0.1:3000' };
  return err;
}

describe('classifyUnavailable — transport carries the undici cause code', () => {
  it('a fetch TypeError with cause.code → transport + code, and the log form names it', () => {
    const c = classifyUnavailable(undiciFetchFailed('UND_ERR_CONNECT_TIMEOUT'));
    expect(c).toEqual({ kind: 'transport', name: 'TypeError', code: 'UND_ERR_CONNECT_TIMEOUT' });
    expect(formatUnavailableCause(c)).toBe('transport:TypeError/UND_ERR_CONNECT_TIMEOUT');
  });

  it('two different codes stay distinguishable (the point of carrying it)', () => {
    const a = formatUnavailableCause(classifyUnavailable(undiciFetchFailed('ECONNRESET')));
    const b = formatUnavailableCause(classifyUnavailable(undiciFetchFailed('UND_ERR_SOCKET')));
    expect(a).toBe('transport:TypeError/ECONNRESET');
    expect(b).toBe('transport:TypeError/UND_ERR_SOCKET');
  });

  it('control: no cause → exactly the old form (no invented code)', () => {
    const c = classifyUnavailable(undiciFetchFailed());
    expect(c).toEqual({ kind: 'transport', name: 'TypeError' });
    expect(formatUnavailableCause(c)).toBe('transport:TypeError');
  });

  it('a cause.code that is not an error-code shape is NOT logged (no values, URLs or messages)', () => {
    for (const bad of ['connect to http://console.local:3000', 'x'.repeat(80), 42, null]) {
      const c = classifyUnavailable(undiciFetchFailed(bad));
      expect(c).toEqual({ kind: 'transport', name: 'TypeError' });
    }
  });

  it('status and timeout are unchanged', () => {
    expect(classifyUnavailable(new ApiError(502, 'BAD_GATEWAY', 'x'))).toEqual({
      kind: 'status',
      status: 502,
      code: 'BAD_GATEWAY',
    });
    const abort = Object.assign(new Error('aborted'), { name: 'TimeoutError' });
    expect(classifyUnavailable(abort)).toEqual({ kind: 'timeout', name: 'TimeoutError' });
  });
});
