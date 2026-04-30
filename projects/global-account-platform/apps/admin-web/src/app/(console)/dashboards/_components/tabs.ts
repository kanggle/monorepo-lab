export const DASHBOARD_TABS = [
  { id: 'accounts', label: '계정 현황' },
  { id: 'security', label: '보안 이벤트' },
  { id: 'system', label: '시스템 상태' },
] as const;

export type DashboardTabId = (typeof DASHBOARD_TABS)[number]['id'];
