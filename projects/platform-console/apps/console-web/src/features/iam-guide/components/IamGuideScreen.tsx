import { Card } from '@/shared/ui/Card';
import { GuideToc, Mono, NoteCard } from '@/shared/ui/guide-primitives';
import {
  ACCOUNT_HATS,
  AUTH_PLANE_DISJOINT,
  AUTH_PLANES,
  CONSOLE_MENUS,
  DELEGATION_CHAIN,
  DELEGATION_GUARDS,
  DOMAIN_ROLE_MAP,
  OPERATOR_ONBOARDING_AXES,
  PERMISSION_KEYS,
  SCREEN_ACCESS,
  SEED_ROLES,
  type AccessLevel,
} from '../data';

/**
 * IAM 가이드 화면 (TASK-PC-FE-163, 재구성 TASK-PC-FE-238). 순수 정적 참조 화면 —
 * 데이터 페치·권한 게이트 없음(server component, no 'use client'): 가이드는
 * 콘솔에 진입한 누구나 열람 가능하다.
 *
 * 세 부분으로 **분리**된다 — 처음 온 사람은 1~2 만 읽으면 되고, 3 은 필요할 때
 * 찾아보는 표다:
 *   1. 개념     — 계정 4개의 모자 · 권한 두 종류
 *   2. 메뉴 사용법 — 메뉴별 하는 일/작업/필요 권한 · 온보딩 흐름 · 도달 범위
 *   3. 레퍼런스 — 역할 7종 · 접근 매트릭스 · 권한 키 · 구독 도메인별 도메인 롤
 *
 * 역할/권한 키/구독 도메인 **카탈로그는 3에만** 존재한다(2 는 그것을 참조만 한다).
 */

const SECTIONS = [
  { id: 'iam-guide-concepts', label: '1. 먼저 알아둘 것' },
  { id: 'iam-guide-usage', label: '2. 메뉴 사용법' },
  { id: 'iam-guide-reference', label: '3. 레퍼런스' },
];

function AccessCell({ level, note }: { level: AccessLevel; note?: string }) {
  const glyph = level === 'full' ? '✅' : level === 'partial' ? '△' : '✕';
  const label =
    level === 'full' ? '가능' : level === 'partial' ? '부분' : '불가';
  const tone =
    level === 'full'
      ? 'text-green-600 dark:text-green-400'
      : level === 'partial'
        ? 'text-amber-600 dark:text-amber-400'
        : 'text-muted-foreground';
  return (
    <span className="flex flex-col items-center gap-0.5">
      <span className={tone} aria-label={label} title={label}>
        {glyph}
      </span>
      {note && (
        <span className="text-[11px] leading-tight text-muted-foreground">
          {note}
        </span>
      )}
    </span>
  );
}

/** 권한 키 칩 — IAM 고유(공용 `Mono` 와 달리 키 카탈로그 전용 스타일). */
function PermChip({ label }: { label: string }) {
  return (
    <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-foreground">
      {label}
    </span>
  );
}

/** 절 제목 — 3부 구조의 최상위 구분자. */
function PartHeading({ id, num, children }: { id: string; num: string; children: string }) {
  return (
    <h2 id={id} className="mb-2 border-b border-border pb-2 text-xl font-semibold">
      <span className="mr-2 text-muted-foreground">{num}</span>
      {children}
    </h2>
  );
}

export function IamGuideScreen() {
  return (
    <section aria-labelledby="iam-guide-heading" data-testid="iam-guide">
      <h1 id="iam-guide-heading" className="mb-2 text-2xl font-semibold">
        IAM 가이드
      </h1>
      <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
        IAM 은 <strong>누가 콘솔의 어떤 메뉴를 쓸 수 있는지</strong>를 정하는
        곳입니다. 처음이라면 <strong>1 · 2</strong> 만 읽으세요 —{' '}
        <strong>3</strong> 은 역할 · 권한 키 · 도메인 롤을 찾아보는 표입니다.
      </p>

      <GuideToc items={SECTIONS} />

      {/* ═════════════════ 1. 개념 ═════════════════ */}
      <PartHeading id="iam-guide-concepts" num="1.">
        먼저 알아둘 것
      </PartHeading>
      <p className="mb-8 max-w-3xl text-sm text-muted-foreground">
        계정은 하나지만, 어떤 관계로 접속했느냐에 따라 쓸 수 있는 권한이
        달라집니다.
      </p>

      <h3 className="mb-2 text-lg font-medium">하나의 계정, 4가지 상황</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        로그인은 언제나 하나입니다. 그 위에 얹히는 권한만 상황별로 바뀝니다.
        아래 <strong>②~④</strong> 가 이 가이드가 다루는 범위입니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="iam-guide-hats">
          <caption className="sr-only">하나의 계정, 4가지 상황</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                관계
              </th>
              <th scope="col" className="p-2">
                이 사람은 누구인가
              </th>
              <th scope="col" className="p-2">
                권한이 붙는 시점
              </th>
              <th scope="col" className="p-2">
                콘솔에서
              </th>
            </tr>
          </thead>
          <tbody>
            {ACCOUNT_HATS.map((hat, i) => (
              <tr key={hat.marker} data-testid={`iam-guide-hat-${i}`}>
                <th
                  scope="row"
                  className="p-2 text-left align-top text-sm font-medium text-foreground"
                >
                  <span className="mr-1" aria-hidden="true">
                    {hat.marker}
                  </span>
                  {hat.relation}
                </th>
                <td className="p-2 align-top text-sm text-muted-foreground">
                  {hat.role}
                </td>
                <td className="p-2 align-top text-sm text-muted-foreground">
                  {hat.token}
                </td>
                <td className="p-2 align-top text-sm text-muted-foreground">
                  {hat.consoleNote}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 className="mb-2 text-lg font-medium">권한은 두 종류</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        하나는 <strong>IAM 메뉴를 여는 권한</strong>, 다른 하나는{' '}
        <strong>도메인 화면을 여는 권한</strong>입니다. 생기는 방식이 다릅니다.
      </p>
      <div className="mb-4 grid gap-4 md:grid-cols-2">
        {AUTH_PLANES.map((plane, i) => (
          <Card key={plane.koName} data-testid={`iam-guide-plane-${i}`}>
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span className="rounded bg-muted px-2 py-0.5 text-sm font-semibold text-foreground">
                {plane.koName}
              </span>
              <span className="text-xs text-muted-foreground">
                {plane.token}
              </span>
            </div>
            <p className="mb-1 text-sm text-muted-foreground">{plane.purpose}</p>
            <p className="mb-2 text-xs text-muted-foreground">{plane.storage}</p>
            <p className="font-mono text-xs leading-relaxed text-foreground">
              {plane.roles}
            </p>
          </Card>
        ))}
      </div>
      <NoteCard title="두 권한은 절대 섞이지 않습니다" body={AUTH_PLANE_DISJOINT} />

      {/* ═════════════════ 2. 메뉴 사용법 ═════════════════ */}
      <PartHeading id="iam-guide-usage" num="2.">
        메뉴 사용법
      </PartHeading>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        각 메뉴가 무엇을 하는 곳이고, 무엇을 할 수 있고, 무엇이 있어야 열리는지.
        권한 키의 뜻은 <strong>3. 레퍼런스</strong>에서 찾아보세요.
      </p>
      <div className="mb-4 overflow-x-auto">
        <table className="data-table" data-testid="iam-guide-menus">
          <caption className="sr-only">IAM 관련 콘솔 메뉴 사용법</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                메뉴
              </th>
              <th scope="col" className="p-2">
                하는 일
              </th>
              <th scope="col" className="p-2">
                할 수 있는 작업
              </th>
              <th scope="col" className="p-2">
                열리는 조건
              </th>
            </tr>
          </thead>
          <tbody>
            {CONSOLE_MENUS.map((menu) => (
              <tr
                key={menu.href}
                data-testid={`iam-guide-menu-${menu.href}`}
                className="border-b border-border"
              >
                <th scope="row" className="p-2 text-left align-top">
                  <span className="block text-sm font-medium text-foreground">
                    {menu.label}
                  </span>
                  <span className="mt-0.5 block font-mono text-[11px] font-normal text-muted-foreground">
                    {menu.href}
                  </span>
                  <span className="mt-1 inline-block rounded bg-muted px-1.5 py-0.5 text-[11px] font-normal text-muted-foreground">
                    {menu.stub ? '준비 중' : menu.mutates ? '변경 가능' : '조회 전용'}
                  </span>
                </th>
                <td className="p-2 align-top text-sm text-muted-foreground">
                  {menu.purpose}
                </td>
                <td className="p-2 align-top text-sm text-muted-foreground">
                  {menu.actions}
                  {menu.note && (
                    <span className="mt-1 block text-[11px] leading-tight text-muted-foreground">
                      {menu.note}
                    </span>
                  )}
                </td>
                <td className="p-2 align-top text-sm">
                  {menu.gate === '—' ? (
                    <span className="text-muted-foreground">누구나</span>
                  ) : menu.gate === '카드별로 다름' ? (
                    <span className="text-muted-foreground">{menu.gate}</span>
                  ) : (
                    <Mono>{menu.gate}</Mono>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mb-10 max-w-3xl text-xs text-muted-foreground">
        권한이 없어도 메뉴는 보입니다 — 콘솔은 메뉴를 미리 숨기지 않고, 열었을 때
        안내 문구를 보여줍니다. 어떤 역할이 어떤 메뉴를 여는지는{' '}
        <strong>3. 레퍼런스</strong>의 접근 매트릭스에 있습니다.
      </p>

      <h3 className="mb-2 text-lg font-medium">운영자를 온보딩하는 흐름</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        플랫폼 운영자 → 회사 관리자 → 직원 순으로 권한이 내려갑니다.
      </p>
      <ol className="mb-4 space-y-3">
        {DELEGATION_CHAIN.map((step, i) => (
          <li
            key={step.actor}
            className="flex gap-3"
            data-testid={`iam-guide-delegation-${i}`}
          >
            <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-foreground">
              {i + 1}
            </span>
            <div>
              <p className="text-sm font-medium text-foreground">{step.actor}</p>
              <p className="text-sm text-muted-foreground">{step.action}</p>
            </div>
          </li>
        ))}
      </ol>
      <div className="mb-10 grid gap-3 md:grid-cols-2">
        {DELEGATION_GUARDS.map((g) => (
          <Card key={g.name} className="bg-muted/40">
            <p className="mb-1 text-sm font-medium text-foreground">{g.name}</p>
            <p className="text-sm text-muted-foreground">{g.desc}</p>
          </Card>
        ))}
      </div>

      <h3 className="mb-2 text-lg font-medium">
        운영자가 어디까지 일할 수 있는지 정하는 3가지
      </h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        역할을 받은 뒤, 실제 도달 범위는 아래 세 가지가 각각 독립적으로 정합니다.
        모두 「운영자 관리」 화면에서 다룹니다.
      </p>
      <div className="mb-10 grid gap-4 md:grid-cols-3">
        {OPERATOR_ONBOARDING_AXES.map((axis) => (
          <Card
            key={axis.term}
            data-testid={`iam-guide-onboarding-axis-${axis.term.replace(
              /\s+/g,
              '-',
            )}`}
          >
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span className="rounded bg-muted px-2 py-0.5 text-sm font-semibold text-foreground">
                {axis.koName}
              </span>
              <span className="font-mono text-[11px] text-muted-foreground">
                {axis.term}
              </span>
            </div>
            <p className="mb-2 text-[11px] text-muted-foreground">{axis.api}</p>
            <p className="mb-3 text-sm text-muted-foreground">{axis.desc}</p>
            <p className="text-sm text-foreground">
              <span className="mr-1 rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
                예시
              </span>
              {axis.ecommerceNote}
            </p>
          </Card>
        ))}
      </div>

      {/* ═════════════════ 3. 레퍼런스 ═════════════════ */}
      <PartHeading id="iam-guide-reference" num="3.">
        레퍼런스
      </PartHeading>
      <p className="mb-8 max-w-3xl text-sm text-muted-foreground">
        찾아보는 표입니다 — 역할의 종류, 역할별로 열리는 메뉴, 권한 키의 뜻, 그리고
        구독 도메인에서 파생되는 도메인 롤.
      </p>

      <h3 className="mb-4 text-lg font-medium">역할 7종 (IAM 메뉴를 여는 권한)</h3>
      <div className="mb-10 grid gap-4 md:grid-cols-2">
        {SEED_ROLES.map((role) => (
          <Card key={role.name} data-testid={`iam-guide-role-${role.name}`}>
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span
                className={
                  role.elevated
                    ? 'rounded bg-destructive/15 px-2 py-0.5 text-sm font-semibold text-destructive'
                    : 'rounded bg-muted px-2 py-0.5 text-sm font-semibold text-foreground'
                }
              >
                {role.name}
              </span>
              <span className="text-sm text-muted-foreground">
                {role.koName}
              </span>
              <span
                className="rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground"
                data-testid={`iam-guide-scope-${role.name}`}
              >
                {role.scope === 'platform'
                  ? `플랫폼 전체${role.elevated ? '(제약 없음)' : ''}`
                  : role.scope === 'org-node'
                    ? '자기 노드 subtree'
                    : '자기 테넌트'}
              </span>
            </div>
            <p className="mb-3 text-sm text-muted-foreground">{role.intent}</p>
            <div className="flex flex-wrap gap-1">
              {role.permissions.map((p) => (
                <PermChip key={p} label={p} />
              ))}
            </div>
          </Card>
        ))}
      </div>

      <h3 className="mb-2 text-lg font-medium">역할별로 열리는 메뉴</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        ✅ 가능 · △ 부분 · ✕ 불가. 권한은 보유한 역할들의 합집합으로 평가되고,
        메뉴가 요구하는 키가 없으면 서버가 거부합니다.
      </p>
      <div className="mb-4 overflow-x-auto">
        <table className="data-table" data-testid="iam-guide-access-matrix">
          <caption className="sr-only">메뉴별 역할 접근 권한</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                메뉴 \ 역할
              </th>
              {SEED_ROLES.map((role) => (
                <th key={role.name} scope="col" className="p-2 text-center">
                  <span className="block font-mono text-[11px] font-normal text-foreground">
                    {role.name}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {SCREEN_ACCESS.map((s) => (
              <tr key={s.href} data-testid={`iam-guide-matrix-row-${s.href}`}>
                <th scope="row" className="p-2 text-left align-top">
                  <span className="block text-sm font-medium text-foreground">
                    {s.screen}
                  </span>
                  <span className="block font-mono text-[11px] font-normal text-muted-foreground">
                    {s.gate}
                  </span>
                </th>
                {SEED_ROLES.map((role) => {
                  const cell = s.cells[role.name] ?? { level: 'none' as const };
                  return (
                    <td
                      key={role.name}
                      className="p-2 text-center align-top"
                      data-testid={`iam-guide-cell-${role.name}-${s.href}`}
                      data-level={cell.level}
                    >
                      <AccessCell level={cell.level} note={cell.note} />
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="mb-10 max-w-3xl list-disc space-y-1 pl-5 text-xs text-muted-foreground">
        <li>
          <strong className="text-foreground">계정 운영</strong> 은{' '}
          <PermChip label="account.read" /> 로 열립니다. 잠금 권한만 가진
          SUPPORT_LOCK 은 목록을 열 수 없습니다.
        </li>
        <li>
          <strong className="text-foreground">감사 · 보안</strong> 의 로그인 이력 ·
          의심 활동은 <PermChip label="audit.read" /> 위에{' '}
          <PermChip label="security.event.read" /> 를 더 요구합니다(△ = 그 부분만
          안 보임).
        </li>
        <li>
          <strong className="text-foreground">파트너십</strong> 은 SUPER_ADMIN 도
          열 수 없습니다 — 두 고객사 사이의 관계라서 플랫폼은 당사자가 아닙니다.
        </li>
      </ul>

      <h3 className="mb-4 text-lg font-medium">권한 키</h3>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="iam-guide-permission-keys">
          <caption className="sr-only">권한 키 카탈로그</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                권한 키
              </th>
              <th scope="col" className="p-2">
                이 키가 있으면
              </th>
            </tr>
          </thead>
          <tbody>
            {PERMISSION_KEYS.map((p) => (
              <tr key={p.key}>
                <td className="p-2">
                  <PermChip label={p.key} />
                </td>
                <td className="p-2 text-sm text-muted-foreground">{p.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 className="mb-2 text-lg font-medium">
        구독 도메인에서 생기는 도메인 롤
      </h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        위 7개 역할과는 <strong>다른 축</strong>입니다. 직원 · 협력사는 이 역할을
        따로 받지 않습니다 — 로그인 후 테넌트를 고르면, 그 테넌트가 구독 중인
        도메인에서 아래처럼 자동으로 생깁니다. 협력사는 같은 롤을 받되 배정된 부서
        범위만큼만 데이터를 봅니다.
      </p>
      <div className="overflow-x-auto">
        <table className="data-table" data-testid="iam-guide-domain-role-map">
          <caption className="sr-only">테넌트 구독 도메인별 파생 도메인 롤</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                구독 도메인
              </th>
              <th scope="col" className="p-2">
                생기는 도메인 롤
              </th>
            </tr>
          </thead>
          <tbody>
            {DOMAIN_ROLE_MAP.map((d) => (
              <tr key={d.domain}>
                <td className="p-2 font-mono text-xs text-foreground">
                  {d.domain}
                </td>
                <td className="p-2 text-sm text-muted-foreground">{d.roles}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
