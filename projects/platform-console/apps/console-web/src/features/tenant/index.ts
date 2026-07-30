export { TenantSwitcher } from './components/TenantSwitcher';
export { useTenantSwitch } from '@/shared/api/use-tenant-switch';
export { selectableTenants, groupTenantsByCompany } from './lib/tenant-options';
export type {
  CompanyGroup,
  CompanyGroupInput,
  GroupedTenants,
} from './lib/tenant-options';
