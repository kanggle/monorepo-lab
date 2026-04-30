'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/button';
import { BulkLockDialog } from './BulkLockDialog';

export function BulkLockButton() {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button variant="default" onClick={() => setOpen(true)}>
        일괄 잠금
      </Button>
      <BulkLockDialog open={open} onOpenChange={setOpen} />
    </>
  );
}
