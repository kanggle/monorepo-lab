import { Suspense } from 'react';
import { OAuthCallbackHandler } from '@/features/auth/components/OAuthCallbackHandler';

export const metadata = { title: '소셜 로그인 처리 — Admin Console' };
export const dynamic = 'force-dynamic';

export default function OAuthCallbackPage() {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="flex w-full max-w-sm flex-col gap-4 text-center">
        <h1 className="text-xl font-semibold">소셜 로그인 처리 중</h1>
        <Suspense fallback={<p className="text-sm text-muted-foreground">잠시만 기다려주세요...</p>}>
          <OAuthCallbackHandler />
        </Suspense>
      </div>
    </main>
  );
}
