import { z } from 'zod';

const ClientEnvSchema = z.object({
  NEXT_PUBLIC_API_BASE_URL: z.string().url().default('http://localhost:8080'),
  NEXT_PUBLIC_APP_URL: z.string().url().default('http://localhost:3000'),
  NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL: z.string().url().default('https://grafana.internal/d/accounts'),
  NEXT_PUBLIC_GRAFANA_SECURITY_URL: z.string().url().default('https://grafana.internal/d/security'),
  NEXT_PUBLIC_GRAFANA_SYSTEM_URL: z.string().url().default('https://grafana.internal/d/system'),
});

const ServerEnvSchema = ClientEnvSchema.extend({
  LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info'),
});

export const clientEnv = ClientEnvSchema.parse({
  NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
  NEXT_PUBLIC_APP_URL: process.env.NEXT_PUBLIC_APP_URL,
  NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL: process.env.NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL,
  NEXT_PUBLIC_GRAFANA_SECURITY_URL: process.env.NEXT_PUBLIC_GRAFANA_SECURITY_URL,
  NEXT_PUBLIC_GRAFANA_SYSTEM_URL: process.env.NEXT_PUBLIC_GRAFANA_SYSTEM_URL,
});

export function getServerEnv() {
  return ServerEnvSchema.parse({
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
    NEXT_PUBLIC_APP_URL: process.env.NEXT_PUBLIC_APP_URL,
    NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL: process.env.NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL,
    NEXT_PUBLIC_GRAFANA_SECURITY_URL: process.env.NEXT_PUBLIC_GRAFANA_SECURITY_URL,
    NEXT_PUBLIC_GRAFANA_SYSTEM_URL: process.env.NEXT_PUBLIC_GRAFANA_SYSTEM_URL,
    LOG_LEVEL: process.env.LOG_LEVEL,
  });
}
