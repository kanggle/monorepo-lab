import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-[60vh] max-w-lg flex-col items-center justify-center gap-4 p-8 text-center">
      <p className="text-5xl font-bold text-brand-600">404</p>
      <h1 className="text-xl font-semibold">페이지를 찾을 수 없습니다</h1>
      <Link href="/" className="text-brand-600 hover:underline">
        피드로 돌아가기
      </Link>
    </main>
  );
}
