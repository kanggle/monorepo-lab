import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

/**
 * Global test setup. Mocks `next-auth/react` so tests that mount
 * `AuthProvider` (or any component reading `useSession`) get a deterministic
 * unauthenticated session by default. Per-test overrides via
 * `vi.mocked(useSession).mockReturnValue(...)` work as usual.
 */
vi.mock('next-auth/react', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('next-auth/react');
  return {
    ...actual,
    SessionProvider: ({ children }: { children: React.ReactNode }) => children,
    useSession: vi.fn(() => ({ data: null, status: 'unauthenticated', update: vi.fn() })),
    signIn: vi.fn(),
    signOut: vi.fn(),
    getSession: vi.fn(async () => null),
  };
});
