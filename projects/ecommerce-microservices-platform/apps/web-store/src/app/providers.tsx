'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { AuthProvider } from '@/features/auth';
import { CartProvider } from '@/features/cart';
import { ProfileImageProvider } from '@/shared/context/ProfileImageContext';

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000,
            retry: 1,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CartProvider>
          <ProfileImageProvider>{children}</ProfileImageProvider>
        </CartProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
