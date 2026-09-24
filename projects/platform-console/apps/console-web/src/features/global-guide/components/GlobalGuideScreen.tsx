import type { ReactNode } from 'react';
import Link from 'next/link';
import { Card } from '@/shared/ui/Card';
import { GuideReadingPath, Mono } from '@/shared/ui/guide-primitives';
import { GuideTabs } from '@/shared/ui/guide-tabs';
import {
  KnownMismatches,
  PermissionMapTable,
} from '@/shared/guide/PermissionMapTable';
import {
  DEMO_TEST_ACCOUNT,
  RBAC_ROLES,
  RBAC_SEED_MATRIX,
  RBAC_SEED_MATRIX_SOURCE,
} from '@/shared/guide/permission-map';
import {
  ARCHITECTURE_FACTS,
  BUSINESS_FLOWS,
  DOMAIN_SERVICE_GROUPS,
  SERVER_LAYERS,
  SHUTDOWN_RULES,
  STARTUP_STEPS,
  type GuideFact,
} from '../data';

/**
 * 전역 가이드(/guide — TASK-PC-FE-298). 7개 탭 — 시스템 아키텍처 / 도메인별 서비스 구성 /
 * 서버 구성 / 전체 메뉴 소개 / 권한 및 테스트 계정 / 대표 업무 흐름 / 서버 기동·종료 방식.
 *
 * 순수 정적(server component) — 데이터 페치·권한 게이트 없음. 그래서 로그인하지 않은
 * 샘플 방문자(ADR-MONO-074)에게도 백엔드 호출 없이 그대로 렌더된다. 탭 전환만 클라이언트
 * 섬(`GuideTabs`)이 맡는다.
 */

export const GLOBAL_GUIDE_TABS = [
  { id: 'global-guide-architecture', label: '시스템 아키텍처' },
  { id: 'global-guide-services', label: '도메인별 서비스 구성' },
  { id: 'global-guide-servers', label: '서버 구성' },
  { id: 'global-guide-menus', label: '전체 메뉴 소개' },
  { id: 'global-guide-permissions', label: '권한 및 테스트 계정' },
  { id: 'global-guide-flows', label: '대표 업무 흐름' },
  { id: 'global-guide-lifecycle', label: '서버 기동·종료 방식' },
] as const;

function Sources({ sources }: { sources: string[] }) {
  return (
    <p className="mt-2 text-[11px] text-muted-foreground">
      <span className="font-medium">출처</span>{' '}
      {sources.map((s, i) => (
        <span key={s} className="break-all font-mono">
          {i > 0 && ' · '}
          {s}
        </span>
      ))}
    </p>
  );
}

function FactCards({ facts, testid }: { facts: GuideFact[]; testid: string }) {
  return (
    <div className="grid gap-4 md:grid-cols-2" data-testid={testid}>
      {facts.map((f, i) => (
        <Card key={f.title} data-testid={`${testid}-${i}`}>
          <p className="mb-1 text-sm font-semibold text-foreground">{f.title}</p>
          <p className="text-sm text-muted-foreground">{f.body}</p>
          <Sources sources={f.sources} />
        </Card>
      ))}
    </div>
  );
}

function H2({ children }: { children: string }) {
  return <h2 className="mb-3 text-xl font-semibold">{children}</h2>;
}

export function GlobalGuideScreen({ demoLoginEmail }: { demoLoginEmail: string }) {
  const content: Record<(typeof GLOBAL_GUIDE_TABS)[number]['id'], ReactNode> = {
    'global-guide-architecture': (
      <>
        <H2>시스템 아키텍처</H2>
        <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
          브라우저 → <strong>console-web</strong>(Vercel) → 각 도메인의{' '}
          <strong>gateway-service</strong> → 도메인 서비스. 로그인은 <strong>IAM</strong> 한 곳,
          대시보드 요약만 <strong>console-bff</strong> 가 모은다.
        </p>
        <FactCards facts={ARCHITECTURE_FACTS} testid="global-guide-arch" />
      </>
    ),
    'global-guide-services': (
      <>
        <H2>도메인별 서비스 구성</H2>
        <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
          서비스 이름은 각 프로젝트의 <Mono>apps/</Mono> 디렉터리 이름 그대로입니다.
        </p>
        <div className="overflow-x-auto">
          <table className="data-table" data-testid="global-guide-services-table">
            <caption className="sr-only">도메인별 서비스 구성</caption>
            <thead>
              <tr className="text-left">
                <th scope="col" className="p-2">도메인</th>
                <th scope="col" className="p-2">서비스</th>
                <th scope="col" className="p-2">콘솔에서의 역할</th>
                <th scope="col" className="p-2">출처</th>
              </tr>
            </thead>
            <tbody>
              {DOMAIN_SERVICE_GROUPS.map((g) => (
                <tr key={g.project} data-testid={`global-guide-services-${g.project}`} className="border-b border-border align-top">
                  <th scope="row" className="p-2 text-left">
                    <span className="font-medium text-foreground">{g.label}</span>
                    <span className="block font-mono text-[11px] font-normal text-muted-foreground">{g.project}</span>
                  </th>
                  <td className="p-2">
                    <span className="flex flex-wrap gap-1">
                      {g.apps.map((a) => (
                        <Mono key={a}>{a}</Mono>
                      ))}
                    </span>
                  </td>
                  <td className="p-2 text-sm text-muted-foreground">{g.consoleRole}</td>
                  <td className="p-2">
                    {g.sources.map((s) => (
                      <span key={s} className="block break-all font-mono text-[10px] text-muted-foreground">{s}</span>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </>
    ),
    'global-guide-servers': (
      <>
        <H2>서버 구성</H2>
        <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
          화면(Vercel)과 데이터(AWS 의 데모 호스트)는 <strong>다른 곳에서 돈다</strong> — 그래서
          데모가 꺼져 있어도 콘솔은 열리고, 로그인하지 않은 방문자는 샘플 데이터로 본다.
        </p>
        {SERVER_LAYERS.map((layer) => (
          <section key={layer.key} aria-labelledby={`global-guide-server-${layer.key}`} className="mb-8">
            <h3 id={`global-guide-server-${layer.key}`} className="mb-3 text-lg font-medium">
              {layer.label}
            </h3>
            <FactCards facts={layer.facts} testid={`global-guide-server-${layer.key}`} />
          </section>
        ))}
      </>
    ),
    'global-guide-menus': (
      <>
        <H2>전체 메뉴 소개</H2>
        <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
          사이드바의 <strong>모든</strong> 메뉴를 한 표로 모았습니다 — 권한 코드, 할 수 있는 조작,
          연결 서비스, 로그인 없이 볼 수 있는지, 테스트 계정으로 열리는지. 메뉴명·뎁스는 사이드바
          설정에서, 비로그인 여부는 샘플 원장에서, 테스트 계정 여부는 역할 매트릭스에서 계산합니다.
        </p>
        <PermissionMapTable testid="global-guide-map" />
      </>
    ),
    'global-guide-permissions': (
      <>
        <H2>권한 및 테스트 계정</H2>
        <h3 className="mb-2 text-lg font-medium">테스트 계정</h3>
        <Card className="mb-8" data-testid="global-guide-test-account">
          <p className="text-sm text-foreground">
            로그인 <Mono>{demoLoginEmail}</Mono> → 운영자 <Mono>{DEMO_TEST_ACCOUNT.operatorId}</Mono>
          </p>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-muted-foreground">
            <li>
              관리 역할: <Mono>{DEMO_TEST_ACCOUNT.adminRoles.join(', ')}</Mono> — 아래 매트릭스의 첫 열.
              단 <Mono>partnership.manage</Mono> 가 없어서 「파트너십」은 열리지 않습니다(<Mono>tenant.admin.delegate</Mono> 도 없지만 — SUPER_ADMIN 은 이 키 없이 플랫폼 권한으로 위임한다 — 이 키는 화면이 아니라 TENANT_ADMIN 부여를 게이트합니다, rbac.md:71,118).
            </li>
            <li>
              고를 수 있는 테넌트: <Mono>{DEMO_TEST_ACCOUNT.assumableTenants.join(', ')}</Mono> — demo-corp 는 다섯 도메인(
              {DEMO_TEST_ACCOUNT.entitledDomains.join(' · ')})을 모두 구독하므로 고르면 다섯 도메인의 운영 롤이 붙습니다.
            </li>
            <li>비밀번호는 데모 배포의 로그인 화면과 론처에 표시됩니다(이 가이드는 반복하지 않습니다).</li>
          </ul>
          <Sources sources={[...DEMO_TEST_ACCOUNT.sources]} />
        </Card>

        <h3 className="mb-2 text-lg font-medium">역할 × 권한 매트릭스</h3>
        <div className="mb-2 overflow-x-auto">
          <table className="data-table" data-testid="global-guide-rbac-matrix">
            <caption className="sr-only">관리 역할별 보유 권한</caption>
            <thead>
              <tr className="text-left">
                <th scope="col" className="p-2">권한</th>
                {RBAC_ROLES.map((r) => (
                  <th key={r} scope="col" className="p-2 font-mono text-[11px]">{r}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {Object.entries(RBAC_SEED_MATRIX).map(([perm, cells]) => (
                <tr key={perm} className="border-b border-border" data-testid={`global-guide-rbac-${perm}`}>
                  <th scope="row" className="p-2 text-left">
                    <Mono>{perm}</Mono>
                  </th>
                  {RBAC_ROLES.map((r) => (
                    <td key={r} className="p-2 text-center text-sm">
                      {cells[r] ? (
                        <span aria-label="보유" className="text-foreground">●</span>
                      ) : (
                        <span aria-label="미보유" className="text-muted-foreground/60">—</span>
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Sources sources={[RBAC_SEED_MATRIX_SOURCE]} />

        <h3 className="mb-2 mt-8 text-lg font-medium">알려진 불일치 · 특수 케이스</h3>
        <KnownMismatches testid="global-guide-mismatches" />

        <h3 className="mb-2 mt-8 text-lg font-medium">이 표가 스스로 지키는 것과 못 지키는 것</h3>
        <p className="max-w-3xl text-sm text-muted-foreground">
          사이드바에 메뉴가 생겼는데 매핑 행이 없으면 단위 테스트가 실패합니다(
          <Mono>tests/unit/permission-map-drift.test.ts</Mono>). 하지만{' '}
          <strong>권한 코드 자체의 최신성은 자동으로 검사되지 않습니다</strong> — IAM 의 역할 정의(
          <Mono>rbac.md</Mono>)나 컨트롤러 애노테이션이 바뀌면 이 표도 사람이 함께 고쳐야 합니다.
        </p>
      </>
    ),
    'global-guide-flows': (
      <>
        <H2>대표 업무 흐름</H2>
        {BUSINESS_FLOWS.map((f) => (
          <Card key={f.key} className="mb-4" data-testid={`global-guide-flow-${f.key}`}>
            <p className="mb-3 text-sm font-semibold text-foreground">{f.title}</p>
            <ol className="space-y-2">
              {f.steps.map((s, i) => (
                <li key={s} className="flex gap-3">
                  <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-foreground">
                    {i + 1}
                  </span>
                  <p className="text-sm text-muted-foreground">{s}</p>
                </li>
              ))}
            </ol>
            <Sources sources={f.sources} />
          </Card>
        ))}
      </>
    ),
    'global-guide-lifecycle': (
      <>
        <H2>서버 기동·종료 방식</H2>
        <h3 className="mb-3 text-lg font-medium">기동</h3>
        <div className="mb-8">
          <FactCards facts={STARTUP_STEPS} testid="global-guide-startup" />
        </div>
        <h3 className="mb-3 text-lg font-medium">종료</h3>
        <FactCards facts={SHUTDOWN_RULES} testid="global-guide-shutdown" />
      </>
    ),
  };

  return (
    <section aria-labelledby="global-guide-heading" data-testid="global-guide">
      <h1 id="global-guide-heading" className="mb-2 text-2xl font-semibold">
        콘솔 가이드
      </h1>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        이 콘솔이 무엇으로 이루어져 있고, 어디서 돌며, 어떤 메뉴를 누가 열 수 있는지를 한곳에
        모았습니다. 도메인별 자세한 설명은 각 도메인의 「가이드」 메뉴(예:{' '}
        <Link href="/wms/guide" className="underline underline-offset-2">
          WMS 가이드
        </Link>
        )에 있습니다.
      </p>
      <GuideReadingPath testid="global-guide-reading-path">
        처음이라면 <strong>시스템 아키텍처</strong> → <strong>전체 메뉴 소개</strong> 순서로 보세요.
        로그인하지 않았다면 모든 화면이 샘플 데이터로 열립니다.
      </GuideReadingPath>
      <GuideTabs
        label="콘솔 가이드 탭"
        testid="global-guide-tabs"
        tabs={GLOBAL_GUIDE_TABS.map((t) => ({ ...t, content: content[t.id] }))}
      />
    </section>
  );
}
