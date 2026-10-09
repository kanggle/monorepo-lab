import { Fragment, type ReactNode } from 'react';
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
  RBAC_ROLE_NATURE,
  RBAC_SEED_MATRIX,
} from '@/shared/guide/permission-map';
import { DOMAIN_FEATURES } from '@/shared/guide/domain-features';
import { DomainFeatureSummary } from '@/shared/guide/DomainFeatureSummary';
import {
  ARCHITECTURE_FACTS,
  BUSINESS_FLOWS,
  DOMAIN_SERVICE_GROUPS,
  REQUEST_PATH,
  SERVER_LAYERS,
  SHUTDOWN_RULES,
  STARTUP_STEPS,
  type GuideFact,
} from '../data';

/**
 * 전역 가이드(/guide — TASK-PC-FE-298). 8개 탭 — 도메인 한눈에(321) / 시스템 아키텍처 /
 * 도메인별 서비스 구성 / 서버 구성 / 전체 메뉴 소개 / 권한 및 테스트 계정 / 대표 업무 흐름 /
 * 서버 기동·종료 방식.
 *
 * TASK-PC-FE-322 — 처음 보는 사람이 읽는 화면이다. 쉬운 말 · 짧은 카드, 출처 표시 없음
 * (사실의 출처는 `data.ts` 의 `sources` 에 남아 시험이 지킨다).
 *
 * 순수 정적(server component) — 데이터 페치·권한 게이트 없음. 그래서 로그인하지 않은
 * 샘플 방문자(ADR-MONO-074)에게도 백엔드 호출 없이 그대로 렌더된다. 탭 전환만 클라이언트
 * 섬(`GuideTabs`)이 맡는다.
 */

export const GLOBAL_GUIDE_TABS = [
  { id: 'global-guide-domains', label: '도메인 한눈에' },
  { id: 'global-guide-architecture', label: '시스템 아키텍처' },
  { id: 'global-guide-services', label: '도메인별 서비스 구성' },
  { id: 'global-guide-servers', label: '서버 구성' },
  { id: 'global-guide-menus', label: '전체 메뉴 소개' },
  { id: 'global-guide-permissions', label: '권한 및 테스트 계정' },
  { id: 'global-guide-flows', label: '대표 업무 흐름' },
  { id: 'global-guide-lifecycle', label: '서버 기동·종료 방식' },
] as const;

function FactCards({ facts, testid }: { facts: GuideFact[]; testid: string }) {
  return (
    <div className="grid gap-4" data-testid={testid}>
      {facts.map((f, i) => (
        <Card key={f.title} data-testid={`${testid}-${i}`}>
          <p className="mb-1 text-sm font-semibold text-foreground">{f.title}</p>
          <p className="text-sm text-muted-foreground">{f.body}</p>
        </Card>
      ))}
    </div>
  );
}

/** 번호 붙은 단계 목록 — 업무 흐름 · 기동 순서가 같이 쓴다. */
function NumberedSteps({ steps }: { steps: { key: string; title?: string; body: string }[] }) {
  return (
    <ol className="space-y-3">
      {steps.map((s, i) => (
        <li key={s.key} className="flex gap-3">
          <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-foreground">
            {i + 1}
          </span>
          <div className="text-sm">
            {s.title && <p className="font-medium text-foreground">{s.title}</p>}
            <p className="text-muted-foreground">{s.body}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}

function H2({ children }: { children: string }) {
  return <h2 className="mb-3 text-xl font-semibold">{children}</h2>;
}

function Lead({ children }: { children: ReactNode }) {
  return <p className="mb-6 max-w-3xl text-sm text-muted-foreground">{children}</p>;
}

export function GlobalGuideScreen({ demoLoginEmail }: { demoLoginEmail: string }) {
  const testAccounts: { key: string; email: string; who: string; opens: ReactNode }[] = [
    {
      key: 'demo',
      email: demoLoginEmail,
      who: '전체 둘러보기',
      opens: (
        <>
          관리 권한이 모두 있습니다. 테넌트 <Mono>demo-corp</Mono> 를 고르면 다섯 도메인(
          {DEMO_TEST_ACCOUNT.entitledDomains.join(' · ')}) 화면이 모두 열립니다. 「파트너십」만 열리지
          않습니다.
        </>
      ),
    },
    {
      key: 'viewer',
      email: 'viewer@demo.com',
      who: '권한 없음 체험',
      opens: '로그인은 되지만 역할이 없어, 대부분의 화면에서 「권한 없음」이 보입니다.',
    },
    {
      key: 'platform',
      email: 'platform@demo.com',
      who: '플랫폼 운영자',
      opens: (
        <>
          테넌트 <Mono>fan-platform</Mono> 만 고를 수 있고, 그때 「팬 디렉터리」(소속사 · 아티스트 ·
          그룹)가 열립니다. IAM 관리 화면은 열리지 않습니다.
        </>
      ),
    },
  ];

  const content: Record<(typeof GLOBAL_GUIDE_TABS)[number]['id'], ReactNode> = {
    'global-guide-domains': (
      <>
        <H2>도메인 한눈에</H2>
        <Lead>
          7개 도메인이 하는 일을 한 줄과 주요 기능으로 모았습니다. 메뉴로 가는 항목은
          눌러서 바로 열 수 있고, 콘솔에 화면이 없는 기능은 「콘솔 밖」 표시가 붙습니다.
        </Lead>
        {DOMAIN_FEATURES.map((d) => (
          <section
            key={d.key}
            aria-labelledby={`global-guide-domain-${d.key}-heading`}
            className="mb-10"
            data-testid={`global-guide-domain-${d.key}`}
          >
            <h3 id={`global-guide-domain-${d.key}-heading`} className="mb-2 text-lg font-medium">
              {d.label}
            </h3>
            <DomainFeatureSummary domain={d} testid={`global-guide-domain-${d.key}-summary`} />
          </section>
        ))}
      </>
    ),
    'global-guide-architecture': (
      <>
        <H2>시스템 아키텍처</H2>
        <Lead>요청 하나가 지나가는 길입니다.</Lead>
        <ol
          aria-label="요청이 지나가는 길"
          className="mb-8 flex flex-wrap items-stretch gap-2"
          data-testid="global-guide-request-path"
        >
          {REQUEST_PATH.map((p, i) => (
            <Fragment key={p.label}>
              {i > 0 && (
                <li aria-hidden="true" className="flex items-center text-muted-foreground">
                  →
                </li>
              )}
              <li className="rounded-lg border border-border bg-muted/40 px-4 py-2 text-center">
                <span className="block text-sm font-semibold text-foreground">{p.label}</span>
                <span className="block text-xs text-muted-foreground">{p.note}</span>
              </li>
            </Fragment>
          ))}
        </ol>
        <FactCards facts={ARCHITECTURE_FACTS} testid="global-guide-arch" />
      </>
    ),
    'global-guide-services': (
      <>
        <H2>도메인별 서비스 구성</H2>
        <Lead>도메인마다 하는 일과, 그 일을 나눠 맡은 서비스입니다.</Lead>
        <div className="overflow-x-auto">
          <table className="data-table" data-testid="global-guide-services-table">
            <caption className="sr-only">도메인별 서비스 구성</caption>
            <thead>
              <tr className="text-left">
                <th scope="col" className="p-2">도메인</th>
                <th scope="col" className="p-2">하는 일</th>
                <th scope="col" className="p-2">서비스</th>
              </tr>
            </thead>
            <tbody>
              {DOMAIN_SERVICE_GROUPS.map((g) => (
                <tr key={g.project} data-testid={`global-guide-services-${g.project}`} className="border-b border-border align-top">
                  <th scope="row" className="whitespace-nowrap p-2 text-left font-medium text-foreground">
                    {g.label}
                  </th>
                  <td className="p-2 text-sm text-foreground">{g.consoleRole}</td>
                  <td className="p-2">
                    <span className="flex flex-wrap gap-1">
                      {g.apps.map((a) => (
                        <Mono key={a}>{a}</Mono>
                      ))}
                    </span>
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
        <Lead>
          화면은 <strong>Vercel</strong>, 데이터는 <strong>AWS 서버 한 대</strong>에서 돕니다. 그래서
          서버가 꺼져 있어도 콘솔은 열리고, 로그인하지 않은 방문자는 샘플 데이터로 봅니다.
        </Lead>
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
        <Lead>
          사이드바의 모든 메뉴를 한 표에 모았습니다. 메뉴마다 필요한 권한, 할 수 있는 일,
          로그인 없이 볼 수 있는지가 나옵니다. 표가 넓으면 옆으로 밀어 보세요.
        </Lead>
        <PermissionMapTable testid="global-guide-map" showSources={false} />
      </>
    ),
    'global-guide-permissions': (
      <>
        <H2>권한 및 테스트 계정</H2>
        <h3 className="mb-2 text-lg font-medium">테스트 계정</h3>
        <div className="mb-2 overflow-x-auto" data-testid="global-guide-test-account">
          <table className="data-table">
            <caption className="sr-only">테스트 계정</caption>
            <thead>
              <tr className="text-left">
                <th scope="col" className="p-2">계정</th>
                <th scope="col" className="p-2">용도</th>
                <th scope="col" className="p-2">열리는 화면</th>
              </tr>
            </thead>
            <tbody>
              {testAccounts.map((a) => (
                <tr
                  key={a.key}
                  className="border-b border-border align-top"
                  data-testid={a.key === 'platform' ? 'global-guide-platform-operator' : `global-guide-test-account-${a.key}`}
                >
                  <th scope="row" className="p-2 text-left">
                    <Mono>{a.email}</Mono>
                  </th>
                  <td className="whitespace-nowrap p-2 text-sm text-foreground">{a.who}</td>
                  <td className="p-2 text-sm text-muted-foreground">{a.opens}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="mb-8 text-xs text-muted-foreground">
          비밀번호는 데모 로그인 화면과 시작 페이지(hubwang.com)에 나옵니다.
        </p>

        <h3 className="mb-2 text-lg font-medium">역할별 권한</h3>
        <p className="mb-3 max-w-3xl text-sm text-muted-foreground">
          ● 는 그 역할이 가진 권한입니다.
        </p>
        <div className="mb-8 overflow-x-auto">
          <table className="data-table" data-testid="global-guide-rbac-matrix">
            <caption className="sr-only">관리 역할별 보유 권한</caption>
            <thead>
              <tr className="text-left">
                <th scope="col" className="p-2">권한</th>
                {RBAC_ROLES.map((r) => (
                  <th key={r} scope="col" className="p-2 align-bottom" data-testid={`global-guide-rbac-role-${r}`}>
                    <span className="block font-mono text-[11px]">{r}</span>
                    <span className="block text-[11px] font-normal text-muted-foreground">
                      {RBAC_ROLE_NATURE[r]}
                    </span>
                  </th>
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

        <h3 className="mb-2 text-lg font-medium">알아 둘 예외</h3>
        <KnownMismatches testid="global-guide-mismatches" />
      </>
    ),
    'global-guide-flows': (
      <>
        <H2>대표 업무 흐름</H2>
        <Lead>자주 쓰는 일이 어떤 메뉴를 거쳐 끝나는지 순서대로 보여 줍니다.</Lead>
        {BUSINESS_FLOWS.map((f) => (
          <Card key={f.key} className="mb-4" data-testid={`global-guide-flow-${f.key}`}>
            <p className="mb-3 text-sm font-semibold text-foreground">{f.title}</p>
            <NumberedSteps steps={f.steps.map((s) => ({ key: s, body: s }))} />
          </Card>
        ))}
      </>
    ),
    'global-guide-lifecycle': (
      <>
        <H2>서버 기동·종료 방식</H2>
        <Lead>데모 서버는 필요할 때만 켜지고, 안 쓰면 스스로 꺼집니다.</Lead>
        <h3 className="mb-3 text-lg font-medium">켜지는 순서</h3>
        <Card className="mb-8" data-testid="global-guide-startup">
          <NumberedSteps steps={STARTUP_STEPS.map((s) => ({ key: s.title, title: s.title, body: s.body }))} />
        </Card>
        <h3 className="mb-3 text-lg font-medium">꺼지는 조건</h3>
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
        이 콘솔이 무엇으로 이루어져 있고, 어디서 돌며, 누가 어떤 메뉴를 열 수 있는지 모았습니다.
        도메인별 자세한 설명은 각 도메인의 「가이드」 메뉴(예:{' '}
        <Link href="/wms/guide" className="underline underline-offset-2">
          WMS 가이드
        </Link>
        )에 있습니다.
      </p>
      <GuideReadingPath testid="global-guide-reading-path">
        처음이라면 <strong>도메인 한눈에</strong> → <strong>시스템 아키텍처</strong> 순서로 보세요.
        로그인하지 않아도 모든 화면을 샘플 데이터로 볼 수 있습니다.
      </GuideReadingPath>
      <GuideTabs
        label="콘솔 가이드 탭"
        testid="global-guide-tabs"
        tabs={GLOBAL_GUIDE_TABS.map((t) => ({ ...t, content: content[t.id] }))}
      />
    </section>
  );
}
