import type { ConsoleSampleMetric } from '@demo/public-data';
import { Card } from '@/shared/ui/Card';

/**
 * 도메인 지표 카드 줄. 실시간 콘솔의 개요 화면들과 **같은 지표 이름**을 쓰고 값만
 * 합성이다(샘플 픽스처 헤더 § "컬럼·지표 이름은 실제 콘솔 화면의 것과 같고, 값만 합성").
 *
 * 🔴 `hint` 를 숨기지 않는다. 픽스처가 그 자리에 *"샘플 값입니다"* 같은 문장을 넣어
 *    두었고, 그것을 안 그리면 화면이 그 숫자를 **실측처럼** 보이게 한다.
 */
export function SampleMetrics({
  metrics,
  testIdPrefix,
}: {
  metrics: ConsoleSampleMetric[];
  testIdPrefix: string;
}) {
  if (metrics.length === 0) return null;

  return (
    <dl
      data-testid={`${testIdPrefix}-metrics`}
      className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
    >
      {metrics.map((m) => (
        <Card key={m.label} data-testid={`${testIdPrefix}-metric-${m.label}`}>
          <dt className="text-sm text-muted-foreground">{m.label}</dt>
          <dd className="mt-1 text-2xl font-semibold text-foreground">
            {m.value}
          </dd>
          <dd className="mt-1 text-xs text-muted-foreground">{m.hint}</dd>
        </Card>
      ))}
    </dl>
  );
}
