import type { CellStatus } from '../api/overview-state';

/**
 * Shared label / status-vocabulary maps for the IAM overview snapshot
 * (TASK-PC-FE-180 — extracted from {@link IamOverviewScreen}, TASK-PC-FE-212
 * presentational split). Behavior-preserving: the maps + `cellPlaceholder`
 * copy are verbatim from the former god-file.
 */

export function cellPlaceholder(status: CellStatus): string {
  return status === 'forbidden' ? '권한 없음' : '점검 필요';
}

/** Per-card status dot — same vocabulary as the ecommerce snapshot / console
 *  home 도메인 상태 요약: ok = 정상, degraded = 점검 필요, forbidden = 권한 없음. */
export const STATUS_DOT: Record<CellStatus, string> = {
  ok: 'bg-green-500',
  degraded: 'bg-red-500',
  forbidden: 'bg-muted-foreground/40',
};
export const STATUS_LABEL: Record<CellStatus, string> = {
  ok: '정상',
  degraded: '점검 필요',
  forbidden: '권한 없음',
};

export const AUDIT_SOURCE_LABEL: Record<string, string> = {
  admin: '관리 작업',
  login_history: '로그인',
  suspicious: '의심 활동',
};
