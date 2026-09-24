import type { ReactNode } from 'react';
import { GuideTabs } from '@/shared/ui/guide-tabs';
import {
  KnownMismatches,
  MenuDescriptions,
  MenuProcedures,
  PermissionMapTable,
} from './PermissionMapTable';
import { resolvePermissionMap, type MapArea } from './permission-map';

/**
 * TASK-PC-FE-298 — 도메인 가이드 6개가 공유하는 **8개 탭 골격**.
 *
 * 탭 이름·순서를 여기서 한 번 정의해 6개 화면이 갈라지지 않게 한다. 각 도메인은 자기 기존
 * 콘텐츠(PC-FE-163/183/184/188/229/232/255/256/257 이 쌓은 섹션들)를 탭에 **옮겨 담기만**
 * 한다. 「메뉴별 설명」·「메뉴별 사용 절차」는 권한·기능 매핑 표에서 자동으로 만들어지고,
 * 「권한 안내」 끝에는 그 도메인의 매핑 행과 알려진 불일치가 붙는다.
 *
 * 도메인이 할 말이 없는 탭에는 `null` 을 넘긴다 — 그러면 지어내지 않고 「정보 없음」을
 * 정직하게 표시한다(티켓 Edge Case).
 */

export const DOMAIN_GUIDE_TAB_KEYS = [
  'overview',
  'terms',
  'usage',
  'menus',
  'procedures',
  'permissions',
  'flows',
  'services',
] as const;
export type DomainGuideTabKey = (typeof DOMAIN_GUIDE_TAB_KEYS)[number];

export const DOMAIN_GUIDE_TAB_LABELS: Record<DomainGuideTabKey, string> = {
  overview: '도메인 전체 설명',
  terms: '공통 정의 및 용어',
  usage: '도메인 사용 가이드',
  menus: '메뉴별 설명',
  procedures: '메뉴별 사용 절차',
  permissions: '권한 안내',
  flows: '대표 업무 흐름',
  services: '연결 서비스',
};

type AuthoredTab = Exclude<DomainGuideTabKey, 'menus' | 'procedures'>;

function NoInfo({ what }: { what: string }) {
  return (
    <p className="text-sm text-muted-foreground" data-testid="guide-no-info">
      정보 없음 — 이 도메인에 대해 저장소가 {what}을(를) 따로 기록하고 있지 않습니다.
    </p>
  );
}

/** 매핑 표에서 이 영역 행들의 연결 서비스를 중복 없이 모은다. */
export function DerivedServices({ areas, testid }: { areas: MapArea[]; testid: string }) {
  const services = Array.from(
    new Set(
      resolvePermissionMap()
        .filter((r) => areas.includes(r.area))
        .flatMap((r) => r.services),
    ),
  );
  if (services.length === 0) return <NoInfo what="연결 서비스" />;
  return (
    <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground" data-testid={testid}>
      {services.map((s) => (
        <li key={s} className="font-mono text-xs">
          {s}
        </li>
      ))}
    </ul>
  );
}

export function DomainGuideTabs({
  prefix,
  areas,
  domainLabel,
  panels,
}: {
  /** testid/탭 id 접두사 — 예: `wms-guide`. */
  prefix: string;
  /** 매핑 표에서 이 가이드가 다루는 영역. */
  areas: MapArea[];
  domainLabel: string;
  panels: Record<AuthoredTab, ReactNode | null>;
}) {
  const tab = (key: DomainGuideTabKey, body: ReactNode) => ({
    id: `${prefix}-tab-${key}`,
    label: DOMAIN_GUIDE_TAB_LABELS[key],
    // 패널의 접근성 이름은 탭 라벨(aria-labelledby)이 준다 — 같은 제목을 패널 안에 또 두지 않는다.
    content: body,
  });

  const authored = (key: AuthoredTab, what: string) =>
    panels[key] ?? <NoInfo what={what} />;

  return (
    <GuideTabs
      label={`${domainLabel} 가이드 탭`}
      testid={`${prefix}-tabs`}
      tabs={[
        tab('overview', authored('overview', '도메인 설명')),
        tab('terms', authored('terms', '용어')),
        tab('usage', authored('usage', '사용 가이드')),
        tab(
          'menus',
          <>
            <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
              사이드바의 {domainLabel} 메뉴 전부 — 권한·기능 매핑 표에서 만들어집니다.
            </p>
            <MenuDescriptions areas={areas} testid={`${prefix}-menu-cards`} />
          </>,
        ),
        tab(
          'procedures',
          <>
            <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
              각 메뉴를 여는 방법 · 필요한 권한 · 그 화면에서 할 수 있는 조작입니다.
            </p>
            <MenuProcedures areas={areas} testid={`${prefix}-menu-procedures`} />
          </>,
        ),
        tab(
          'permissions',
          <>
            {panels.permissions}
            <h2 className="mb-2 mt-8 text-lg font-semibold">메뉴별 권한 (권한·기능 매핑 표)</h2>
            <PermissionMapTable
              areas={areas}
              testid={`${prefix}-map`}
              caption={`${domainLabel} 권한·기능 매핑`}
            />
            <h2 className="mb-2 mt-8 text-lg font-semibold">알려진 불일치 · 특수 케이스</h2>
            <KnownMismatches areas={areas} testid={`${prefix}-mismatches`} />
          </>,
        ),
        tab('flows', authored('flows', '업무 흐름')),
        tab(
          'services',
          panels.services ?? <DerivedServices areas={areas} testid={`${prefix}-derived-services`} />,
        ),
      ]}
    />
  );
}
