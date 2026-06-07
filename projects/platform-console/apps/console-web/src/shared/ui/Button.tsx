import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/shared/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost';
type Size = 'md' | 'sm';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  /**
   * `md` (default) — standalone / form buttons.
   * `sm` — compact buttons for dense contexts such as per-row table-cell
   * actions (수정 / 이동 / 폐기 / 상세); smaller padding + text so they read
   * as inline row actions rather than primary controls.
   */
  size?: Size;
}

const VARIANTS: Record<Variant, string> = {
  primary:
    'bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50',
  secondary:
    'border border-border bg-background text-foreground hover:bg-accent disabled:opacity-50',
  ghost: 'text-foreground hover:bg-accent disabled:opacity-50',
};

const SIZES: Record<Size, string> = {
  md: 'px-4 py-2 text-sm',
  sm: 'px-2 py-1 text-xs',
};

/** Accessible button primitive (native <button>, visible focus ring). */
export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { variant = 'primary', size = 'md', className, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(
        'inline-flex items-center justify-center rounded-md font-medium transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
        'disabled:cursor-not-allowed',
        SIZES[size],
        VARIANTS[variant],
        className,
      )}
      {...rest}
    />
  );
});
