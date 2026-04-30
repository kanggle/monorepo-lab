import { clientEnv } from '@/shared/config/env';
import { DashboardTabs, DASHBOARD_TABS, type DashboardTabId } from './_components/DashboardTabs';

export const metadata = { title: '대시보드 — Admin Console' };

const GRAFANA_URLS: Record<DashboardTabId, string> = {
  accounts: clientEnv.NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL,
  security: clientEnv.NEXT_PUBLIC_GRAFANA_SECURITY_URL,
  system: clientEnv.NEXT_PUBLIC_GRAFANA_SYSTEM_URL,
};

const VALID_TABS = new Set(DASHBOARD_TABS.map((t) => t.id));

function resolveTab(raw: string | undefined): DashboardTabId {
  const normalized = raw?.toLowerCase();
  if (normalized && VALID_TABS.has(normalized as DashboardTabId)) {
    return normalized as DashboardTabId;
  }
  return 'accounts';
}

interface PageProps {
  searchParams: Promise<{ tab?: string }>;
}

export default async function DashboardsPage({ searchParams }: PageProps) {
  const { tab } = await searchParams;
  const activeTab = resolveTab(tab);
  const iframeSrc = GRAFANA_URLS[activeTab];

  return (
    <section className="flex h-full flex-col gap-4">
      <h1 className="text-xl font-semibold">대시보드</h1>
      <DashboardTabs activeTab={activeTab} />
      <iframe
        key={activeTab}
        title={DASHBOARD_TABS.find((t) => t.id === activeTab)!.label}
        src={iframeSrc}
        className="h-[80vh] w-full border border-border"
      />
    </section>
  );
}
