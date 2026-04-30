export const routes = {
  login: '/login',
  accounts: '/accounts',
  account: (id: string) => `/accounts/${id}`,
  audit: '/audit',
  loginHistory: '/security/login-history',
  suspicious: '/security/suspicious',
  dashboards: '/dashboards',
} as const;
