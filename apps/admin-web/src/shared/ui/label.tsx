import * as React from 'react';
import { cn } from '@/shared/lib/cn';

export const Label = React.forwardRef<HTMLLabelElement, React.LabelHTMLAttributes<HTMLLabelElement>>(
  function Label({ className, ...props }, ref) {
    return <label ref={ref} className={cn('text-sm font-medium text-foreground', className)} {...props} />;
  },
);
