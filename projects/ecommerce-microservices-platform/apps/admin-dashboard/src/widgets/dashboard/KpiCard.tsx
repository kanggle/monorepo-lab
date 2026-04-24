import type { ReactNode } from 'react';
import { DASHBOARD_COLORS, DASHBOARD_RADII, DASHBOARD_SHADOWS } from './lib/dashboard-styles';

interface KpiCardProps {
  title: string;
  value: ReactNode;
  subValue?: ReactNode;
  isLoading?: boolean;
  error?: string | null;
  onRetry?: () => void;
}

export function KpiCard({ title, value, subValue, isLoading, error, onRetry }: KpiCardProps) {
  return (
    <div
      style={{
        backgroundColor: DASHBOARD_COLORS.surface,
        borderRadius: DASHBOARD_RADII.card,
        border: `1px solid ${DASHBOARD_COLORS.border}`,
        padding: '20px 24px',
        boxShadow: DASHBOARD_SHADOWS.card,
        minHeight: '104px',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
      }}
    >
      <p style={{ fontSize: '0.8125rem', color: DASHBOARD_COLORS.textMuted, fontWeight: 500, margin: 0 }}>{title}</p>
      {error ? (
        <div>
          <p style={{ fontSize: '0.8125rem', color: DASHBOARD_COLORS.textDanger, margin: '4px 0' }}>{error}</p>
          {onRetry && (
            <button
              onClick={onRetry}
              style={{
                padding: '4px 10px',
                borderRadius: DASHBOARD_RADII.button,
                border: `1px solid ${DASHBOARD_COLORS.border}`,
                backgroundColor: DASHBOARD_COLORS.surface,
                cursor: 'pointer',
                fontSize: '0.75rem',
                color: '#374151',
              }}
            >
              다시 시도
            </button>
          )}
        </div>
      ) : isLoading ? (
        <div
          aria-label="로딩 중"
          style={{
            height: '32px',
            width: '60%',
            borderRadius: DASHBOARD_RADII.skeleton,
            backgroundColor: DASHBOARD_COLORS.surfaceMuted,
          }}
        />
      ) : (
        <div>
          <p
            style={{
              fontSize: '1.75rem',
              fontWeight: 700,
              color: DASHBOARD_COLORS.textPrimary,
              margin: 0,
              lineHeight: 1.2,
            }}
          >
            {value}
          </p>
          {subValue != null && (
            <p style={{ fontSize: '0.8125rem', color: DASHBOARD_COLORS.textMuted, margin: '4px 0 0 0' }}>
              {subValue}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
