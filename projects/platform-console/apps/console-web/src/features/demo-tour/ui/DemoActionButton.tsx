import { Button } from '@/shared/ui/Button';
import { DEMO_DISABLED_REASON } from '../lib/sample-actions';

/**
 * 둘러보기의 쓰기 작업 버튼. **항상 비활성이다.**
 *
 * 🔴🔴 `disabled` 를 prop 으로 받지 않는다. 받으면 그것을 안 넘기는 호출부가 생길 수
 *    있고, 그 호출부는 «익명 방문자에게 눌리는 승인 버튼» 을 그린다. 성질을 규율이 아니라
 *    **타입**으로 만든다 — 이 컴포넌트로는 활성 버튼을 만들 방법이 없다.
 *
 * 🔴 사유를 세 곳에 단다: 툴팁(`title`, 마우스) · 접근성 이름(`aria-label`, 스크린리더) ·
 *    그리고 버튼 묶음 옆의 안내문(`SampleTableCard`). 하나만 달면 그 하나를 못 보는
 *    사용자에게는 «이유 없이 죽은 버튼» 이 된다.
 *
 * 🔵 `aria-disabled` 를 함께 단다. 네이티브 `disabled` 는 포커스를 못 받아 스크린리더
 *    사용자가 버튼의 **존재 자체**를 지나칠 수 있는데, 이 화면에서 버튼의 존재는 곧
 *    "이 화면은 이런 일을 합니다" 라는 정보다.
 */
export function DemoActionButton({
  label,
  testId,
}: {
  label: string;
  testId: string;
}) {
  return (
    <Button
      variant="secondary"
      size="sm"
      disabled
      aria-disabled="true"
      title={DEMO_DISABLED_REASON}
      aria-label={`${label} — ${DEMO_DISABLED_REASON}`}
      data-testid={testId}
      data-demo-disabled="true"
    >
      {label}
    </Button>
  );
}
