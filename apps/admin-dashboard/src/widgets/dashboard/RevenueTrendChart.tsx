'use client';

import { useQuery } from '@tanstack/react-query';
import { getOrders } from '@/features/order-management/api/order-api';
import { aggregateLast7DaysRevenue, type RevenuePoint } from './lib/aggregate-orders';
import { computeLineChartGeometry, type LineChartCoord } from './lib/chart-geometry';
import { DASHBOARD_COLORS } from './lib/dashboard-styles';
import { ListError } from '@/shared/ui';

// 집계 한계: 첫 페이지만 조회해 7일 매출을 프론트 집계한다.
// totalElements > PAGE_SIZE 인 경우 과거 주문이 집계에서 누락될 수 있어 경고를 표시.
// 백엔드 집계 API가 생기면 제거 대상.
const PAGE_SIZE = 200;
const WIDTH = 640;
const HEIGHT = 220;
const PADDING = { top: 20, right: 24, bottom: 32, left: 56 };
const AXIS_COLOR = DASHBOARD_COLORS.border;
const AXIS_LABEL_COLOR = DASHBOARD_COLORS.textMuted;
const LINE_COLOR = DASHBOARD_COLORS.accent;

function formatShortDate(iso: string): string {
  return iso.slice(5).replace('-', '.');
}

function formatWon(value: number): string {
  if (value >= 10000) return `${Math.round(value / 10000).toLocaleString('ko-KR')}만`;
  return value.toLocaleString('ko-KR');
}

function ChartAxis({ max, baselineY }: { max: number; baselineY: number }) {
  return (
    <>
      <line
        x1={PADDING.left}
        x2={WIDTH - PADDING.right}
        y1={baselineY}
        y2={baselineY}
        stroke={AXIS_COLOR}
      />
      <text x={PADDING.left - 8} y={PADDING.top + 4} fontSize="10" fill={AXIS_LABEL_COLOR} textAnchor="end">
        {formatWon(max)}원
      </text>
      <text x={PADDING.left - 8} y={baselineY + 4} fontSize="10" fill={AXIS_LABEL_COLOR} textAnchor="end">
        0
      </text>
    </>
  );
}

function ChartDataPoints({ coords }: { coords: LineChartCoord<RevenuePoint>[] }) {
  return (
    <>
      {coords.map((c) => (
        <g key={c.data.date}>
          <circle cx={c.x} cy={c.y} r="3" fill={LINE_COLOR} />
          <text x={c.x} y={HEIGHT - 10} fontSize="10" fill={AXIS_LABEL_COLOR} textAnchor="middle">
            {formatShortDate(c.data.date)}
          </text>
          <title>{`${c.data.date}: ${c.value.toLocaleString('ko-KR')}원`}</title>
        </g>
      ))}
    </>
  );
}

function Chart({ points }: { points: RevenuePoint[] }) {
  const geometry = computeLineChartGeometry(
    points.map((p) => ({ value: p.revenue, data: p })),
    WIDTH,
    HEIGHT,
    PADDING,
  );

  return (
    <svg width="100%" viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label="최근 7일 매출 추이">
      <ChartAxis max={geometry.max} baselineY={geometry.baselineY} />
      <path d={geometry.path} fill="none" stroke={LINE_COLOR} strokeWidth="2" />
      <ChartDataPoints coords={geometry.coords} />
    </svg>
  );
}

export function RevenueTrendChart() {
  const query = useQuery({
    queryKey: ['admin', 'dashboard', 'revenue-trend'],
    queryFn: () => getOrders({ page: 0, size: PAGE_SIZE }),
  });

  if (query.isError) {
    return <ListError message="매출 추이를 불러오지 못했습니다." onRetry={() => query.refetch()} />;
  }

  if (query.isLoading || !query.data) {
    return (
      <div
        aria-label="매출 추이 로딩 중"
        style={{ height: `${HEIGHT}px`, backgroundColor: DASHBOARD_COLORS.surfaceSoft, borderRadius: '8px' }}
      />
    );
  }

  const points = aggregateLast7DaysRevenue(query.data.content);
  const totalRevenue = points.reduce((sum, p) => sum + p.revenue, 0);
  const truncated = query.data.totalElements > PAGE_SIZE;

  if (totalRevenue === 0) {
    return (
      <p style={{ color: AXIS_LABEL_COLOR, fontSize: '0.875rem', padding: '24px 0', textAlign: 'center' }}>
        최근 7일 매출 데이터가 없습니다.
      </p>
    );
  }

  return (
    <div>
      <Chart points={points} />
      {truncated && (
        <p style={{ color: DASHBOARD_COLORS.textSubtle, fontSize: '0.75rem', marginTop: '8px', textAlign: 'right' }}>
          ※ 최근 {PAGE_SIZE}건 주문 기준 집계 (그 이전 주문은 제외됨)
        </p>
      )}
    </div>
  );
}
