export interface LineChartPadding {
  top: number;
  right: number;
  bottom: number;
  left: number;
}

export interface LineChartPoint<T> {
  value: number;
  data: T;
}

export interface LineChartCoord<T> {
  x: number;
  y: number;
  value: number;
  data: T;
}

export interface LineChartGeometry<T> {
  coords: LineChartCoord<T>[];
  max: number;
  innerWidth: number;
  innerHeight: number;
  path: string;
  baselineY: number;
}

export function computeLineChartGeometry<T>(
  points: LineChartPoint<T>[],
  width: number,
  height: number,
  padding: LineChartPadding,
): LineChartGeometry<T> {
  const innerWidth = width - padding.left - padding.right;
  const innerHeight = height - padding.top - padding.bottom;
  const max = Math.max(...points.map((p) => p.value), 1);
  const step = points.length > 1 ? innerWidth / (points.length - 1) : 0;
  const baselineY = padding.top + innerHeight;

  const coords: LineChartCoord<T>[] = points.map((p, i) => ({
    x: padding.left + step * i,
    y: padding.top + innerHeight - (p.value / max) * innerHeight,
    value: p.value,
    data: p.data,
  }));

  const path = coords.map((c, i) => `${i === 0 ? 'M' : 'L'} ${c.x} ${c.y}`).join(' ');

  return { coords, max, innerWidth, innerHeight, path, baselineY };
}
