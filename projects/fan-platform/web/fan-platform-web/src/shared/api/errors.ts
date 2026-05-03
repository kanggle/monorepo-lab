/**
 * Typed API error mirroring the gateway's flat envelope:
 *
 *   { code: string, message: string, details?: object, timestamp: string }
 *
 * Codes documented in `specs/contracts/http/community-api.md` and `artist-api.md`.
 */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  timestamp?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: Record<string, unknown>;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message ?? `API error ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? 'UNKNOWN';
    this.details = body.details;
  }

  /** True for 401 / 403 codes — caller should redirect to /login or show auth UI. */
  get isAuthError(): boolean {
    return this.status === 401 || this.code === 'UNAUTHORIZED';
  }

  get isTenantForbidden(): boolean {
    return this.status === 403 && this.code === 'TENANT_FORBIDDEN';
  }

  get isMembershipRequired(): boolean {
    return this.code === 'MEMBERSHIP_REQUIRED';
  }
}

/** Best-effort conversion of `Response` → ApiError. */
export async function toApiError(response: Response): Promise<ApiError> {
  let body: ApiErrorBody;
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    body = { code: 'UNKNOWN', message: response.statusText };
  }
  return new ApiError(response.status, body);
}
