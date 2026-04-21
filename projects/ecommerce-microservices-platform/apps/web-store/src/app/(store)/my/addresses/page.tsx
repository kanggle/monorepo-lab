'use client';

import { useRequireAuth } from '@/features/auth';
import { AddressManager } from '@/features/user';

export default function AddressesPage() {
  useRequireAuth();
  return <AddressManager />;
}
