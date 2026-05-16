import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn } from '@/shared/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
}

const VARIANTS: Record<Variant, string> = {
  primary:
    'bg-primary text-primary-foreground hover:opacity-90 disabled:opacity-50',
  secondary:
    'border border-border bg-background hover:bg-muted disabled:opacity-50',
  ghost: 'hover:bg-muted disabled:opacity-50',
};

/** Accessible button primitive (native <button>, visible focus ring). */
export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { variant = 'primary', className, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(
        'inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
        'disabled:cursor-not-allowed',
        VARIANTS[variant],
        className,
      )}
      {...rest}
    />
  );
});
