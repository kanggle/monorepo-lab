import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Button } from '@/shared/ui/Button';

interface CloseButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Defaults to "닫기"; override for a different dismiss label if ever needed. */
  children?: ReactNode;
}

/**
 * Shared dismiss control for detail views that live inside a list screen
 * (inline drill panels + modal dialogs across erp / scm / wms ops). Every such
 * "상세 → 목록" return button renders identically as a compact `ghost` button
 * labelled "닫기", mirroring the ecommerce-ops `DetailHeader` back-button
 * convention so the whole console presents one back-to-list affordance style.
 * Extracted 2026-07-02 to remove per-domain drift (some were `secondary`).
 *
 * `ref` is forwarded so dialogs can move focus onto it on open; all native
 * button props (`onClick`, `disabled`, `data-testid`, …) pass through.
 */
export const CloseButton = forwardRef<HTMLButtonElement, CloseButtonProps>(
  function CloseButton({ children = '닫기', ...rest }, ref) {
    return (
      <Button ref={ref} variant="ghost" size="sm" {...rest}>
        {children}
      </Button>
    );
  },
);
