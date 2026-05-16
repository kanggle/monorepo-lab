import type { HTMLAttributes } from 'react';
import { cn } from '@/shared/lib/cn';

/** Generic surface container. */
export function Card({
  className,
  ...rest
}: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('rounded-lg border border-border bg-background p-5', className)}
      {...rest}
    />
  );
}
