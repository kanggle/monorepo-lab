import { getCatalog } from '@/features/catalog';
import { selectableTenants } from '@/features/tenant';
import { getActiveTenant } from '@/shared/lib/session';

/** What a screen needs to render {@link OtherTenantHint}. */
export interface TenantScope {
  /** The assumed tenant, or `null` when none is (the 292 gate owns that case). */
  activeTenant: string | null;
  /** Selectable tenants MINUS the active one. Empty ⇒ nothing to suggest. */
  otherTenants: string[];
}

/**
 * Server-side: «어느 테넌트가 활성이고, 전환할 수 있는 다른 것이 있는가»
 * (TASK-MONO-719, 소유자 결정 ⓑ).
 *
 * 🔵 **새 컨텍스트를 만들지 않는 이유.** 빈 목록은 클라이언트 화면만 알고
 * (`OrdersScreen` 은 `'use client'`), 테넌트 집합은 서버만 안다. 이 앱에는
 * `createContext` 가 **한 곳도 없으므로** 첫 컨텍스트를 도입하는 대신
 * 서버 페이지가 이 함수를 불러 **prop 으로 내려보낸다** — 그 페이지는 이미
 * 서버 컴포넌트이고 `getCatalog()` 는 그 요청에서 이미 불린다.
 *
 * 🔴 **`selectableTenants()` 를 여기서 다시 구현하지 않는다.** 그 정의는
 * `features/tenant/lib/tenant-options.ts` 에 있고 테넌트 스위처가 쓰는 바로 그
 * 함수다 — 스위처가 제시하지 않는 테넌트를 힌트가 권하면 **누를 수 없는 조언**이 된다.
 *
 * 🔴 **모든 불확실은 «힌트 없음» 으로 떨어진다.** 레지스트리가 degraded 거나
 * 던지면 `otherTenants: []` 다. 이 힌트는 **부가 정보**이므로, 못 확인했을 때
 * 침묵하는 것이 틀린 방향으로 말하는 것보다 낫다(718 이 세운 규칙과 같은 방향).
 */
export async function getTenantScope(): Promise<TenantScope> {
  const activeTenant = await getActiveTenant();
  if (!activeTenant) return { activeTenant: null, otherTenants: [] };
  try {
    const catalog = await getCatalog();
    if (catalog.degraded) return { activeTenant, otherTenants: [] };
    return {
      activeTenant,
      otherTenants: selectableTenants(catalog.products).filter((t) => t !== activeTenant),
    };
  } catch {
    return { activeTenant, otherTenants: [] };
  }
}
