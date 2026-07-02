import { SHIPPING_STEPS } from '../lib/shipping-steps';

interface ShippingStepIndicatorProps {
  currentIndex: number;
}

export function ShippingStepIndicator({ currentIndex }: ShippingStepIndicatorProps) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        marginTop: 'var(--space-4)',
        marginBottom: 'var(--space-4)',
      }}
      role="list"
      aria-label="배송 진행 상태"
    >
      {SHIPPING_STEPS.map((step, index) => {
        const isCompleted = index <= currentIndex;
        const isCurrent = index === currentIndex;

        return (
          <div key={step.status} style={{ display: 'flex', alignItems: 'center', flex: index < SHIPPING_STEPS.length - 1 ? 1 : undefined }}>
            <div
              role="listitem"
              aria-current={isCurrent ? 'step' : undefined}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                minWidth: '64px',
              }}
            >
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '50%',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  backgroundColor: isCompleted ? 'var(--color-primary)' : 'var(--color-border-light)',
                  color: isCompleted ? 'var(--color-on-primary)' : 'var(--color-text-secondary)',
                  fontWeight: 'var(--font-weight-bold)',
                  fontSize: 'var(--font-size-sm)',
                  transition: 'background-color 0.2s',
                }}
              >
                {isCompleted ? '\u2713' : index + 1}
              </div>
              <span
                style={{
                  marginTop: 'var(--space-1)',
                  fontSize: 'var(--font-size-xs)',
                  color: isCompleted ? 'var(--color-text-primary)' : 'var(--color-text-secondary)',
                  fontWeight: isCurrent ? 'var(--font-weight-bold)' : 'var(--font-weight-normal)',
                  textAlign: 'center',
                  whiteSpace: 'nowrap',
                }}
              >
                {step.label}
              </span>
            </div>
            {index < SHIPPING_STEPS.length - 1 && (
              <div
                style={{
                  flex: 1,
                  height: '2px',
                  backgroundColor: index < currentIndex ? 'var(--color-primary)' : 'var(--color-border-light)',
                  marginLeft: 'var(--space-1)',
                  marginRight: 'var(--space-1)',
                  marginBottom: 'var(--space-4)',
                  transition: 'background-color 0.2s',
                }}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
