import {
  type DegradedCard,
  type ForbiddenCard,
  type DegradedReason,
  type ForbiddenReason,
  type OperatorOverview,
} from '../api/operator-overview-types';
import { RetryButton } from './RetryButton';

/**
 * Non-`ok` state blocks for {@link DomainCard} (TASK-PC-FE-011 — extracted
 * TASK-PC-FE-212 presentational split). Server components (the `<RetryButton>`
 * inside {@link DegradedState} is the sole client leaf). Markup / testids /
 * retry gating are byte-verbatim from the former god-file.
 *
 *  - `degraded` — "data unavailable" placeholder + `<RetryButton>`.
 *    Reason ∈ { DOWNSTREAM_ERROR, TIMEOUT, CIRCUIT_OPEN } surfaced as
 *    user-friendly Korean copy.
 *  - `forbidden` — "not available to your role / tenant" placeholder.
 *    Reason ∈ { PERMISSION_DENIED, TENANT_FORBIDDEN, MISSING_PREREQUISITE };
 *    `MISSING_PREREQUISITE` on the finance card surfaces an actionable hint
 *    per § 2.4.9.1 Implementation guidance.
 */

const DEGRADED_COPY: Record<DegradedReason, string> = {
  DOWNSTREAM_ERROR: '하위 서비스에서 오류가 발생했습니다.',
  TIMEOUT: '응답 시간이 초과되었습니다.',
  CIRCUIT_OPEN: '서비스가 일시적으로 응답할 수 없습니다.',
};

const FORBIDDEN_COPY: Record<ForbiddenReason, string> = {
  PERMISSION_DENIED: '이 도메인 조회 권한이 없습니다.',
  TENANT_FORBIDDEN: '선택한 테넌트에 대한 권한이 없습니다.',
  MISSING_PREREQUISITE: '조회에 필요한 사전 설정이 누락되었습니다.',
};

export function DegradedState({
  card,
  overviewForRetry,
}: {
  card: DegradedCard;
  /** Seeds the per-card retry button. */
  overviewForRetry: OperatorOverview;
}) {
  return (
    <div
      role="status"
      data-testid={`operator-overview-card-${card.domain}-degraded`}
      className="space-y-3"
    >
      <p className="text-sm text-muted-foreground">
        {DEGRADED_COPY[card.reason]}
      </p>
      <p
        className="text-xs text-muted-foreground"
        data-testid={`operator-overview-card-${card.domain}-degraded-reason`}
      >
        사유: {card.reason}
      </p>
      <RetryButton
        initial={overviewForRetry}
        label="다시 시도"
        testidSuffix={`${card.domain}-degraded`}
      />
    </div>
  );
}

export function ForbiddenState({ card }: { card: ForbiddenCard }) {
  return (
    <div
      role="status"
      data-testid={`operator-overview-card-${card.domain}-forbidden`}
      className="space-y-2"
    >
      <p className="text-sm text-muted-foreground">
        {FORBIDDEN_COPY[card.reason]}
      </p>
      <p
        className="text-xs text-muted-foreground"
        data-testid={`operator-overview-card-${card.domain}-forbidden-reason`}
      >
        사유: {card.reason}
      </p>
      {card.domain === 'finance' &&
        card.reason === 'MISSING_PREREQUISITE' && (
          <p
            data-testid="operator-overview-card-finance-missing-hint"
            className="text-xs text-muted-foreground"
          >
            운영자 프로필에서 기본 finance 계정을 설정하세요.
          </p>
        )}
    </div>
  );
}
