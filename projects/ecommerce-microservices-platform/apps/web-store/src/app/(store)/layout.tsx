import { Header } from '@/widgets/header';
import { Footer } from '@/widgets/footer';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';

export default function StoreLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      {/* TASK-MONO-580 — 데모 배포에서 백엔드가 꺼져 있으면 그렇다고 말한다.
          로컬·CI 에서는 아무것도 렌더하지 않는다(그쪽은 애초에 데모가 아니다). */}
      <DemoBackendNotice />
      <Header />
      <main style={{ flex: 1 }}>{children}</main>
      <Footer />
    </div>
  );
}
