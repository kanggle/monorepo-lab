type Level = 'debug' | 'info' | 'warn' | 'error';

interface LogFields {
  [k: string]: unknown;
}

/**
 * Structured JSON logger for server components / route handlers.
 * Per architecture.md § Observability: server-side logs carry
 * `requestId` + `operatorId` + `tenantId` context where available.
 */
function emit(level: Level, msg: string, fields?: LogFields) {
  const entry = {
    ts: new Date().toISOString(),
    level,
    msg,
    ...fields,
  };
  // eslint-disable-next-line no-console
  console[level === 'debug' ? 'log' : level](JSON.stringify(entry));
}

export const logger = {
  debug: (msg: string, fields?: LogFields) => emit('debug', msg, fields),
  info: (msg: string, fields?: LogFields) => emit('info', msg, fields),
  warn: (msg: string, fields?: LogFields) => emit('warn', msg, fields),
  error: (msg: string, fields?: LogFields) => emit('error', msg, fields),
};

export function newRequestId(): string {
  const g = globalThis as unknown as { crypto?: { randomUUID?: () => string } };
  return g.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2);
}
