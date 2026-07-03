import { Card } from '@/shared/ui/Card';
import {
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
 * IAM 개요(가이드) 화면 (TASK-PC-FE-163). 순수 정적 참조 화면 — role 카탈로그,
 * 권한 키, 화면 접근 매트릭스, 운영 시 롤 부여 위임 체인, 도메인 롤(별도 축)을
 * 한 화면에서 설명한다. 데이터 페치·권한 게이트 없음(server component, no
 * 'use client'): 가이드는 콘솔에 진입한 누구나 열람 가능하다.
 */

function AccessCell({
  level,
  note,
}: {
  level: AccessLevel;
  note?: string;
}) {
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

function PermChip({ label }: { label: string }) {
  return (
    <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-foreground">
      {label}
    </span>
  );
}

export function IamGuideScreen() {
  return (
    <section aria-labelledby="iam-guide-heading" data-testid="iam-guide">
      <h1 id="iam-guide-heading" className="mb-2 text-2xl font-semibold">
        IAM 개요
      </h1>
      <p className="mb-8 max-w-3xl text-sm text-muted-foreground">
        IAM 콘솔은 플랫폼 운영자의 <strong>역할 기반 접근제어(RBAC)</strong>로{' '}
        <strong>계정 운영 · 운영자 관리 · 감사·보안</strong> 3개 화면을
        게이트합니다. 아래는 운영자에게 부여하는 6개 역할의 의미·보유 권한과 각
        화면의 접근 권한, 그리고 운영 시 역할을 부여하는 위임 흐름입니다. (테넌트
        직원/협력업체가 받는 <strong>도메인 롤</strong>은 이와 다른 축 — 맨 아래
        참조.)
      </p>

      {/* 1. Role 종류 */}
      <h2 className="mb-4 text-lg font-semibold">역할(Role) 종류</h2>
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
                  ? `플랫폼 스코프${role.elevated ? '(*)' : ''}`
                  : '테넌트 스코프'}
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

      {/* 2. 메뉴 접근 권한 매트릭스 */}
      <h2 className="mb-2 text-lg font-semibold">메뉴 접근 권한</h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        각 화면은 특정 권한 키로 게이트됩니다. 권한은 역할의 합집합으로 평가되며,
        화면이 요구하는 키를 보유하지 않으면 서버가 403 으로 거부합니다(콘솔은
        생산자 권위 원칙에 따라 메뉴를 미리 숨기지 않고 진입 시 인라인 안내).
      </p>
      <div className="mb-4 overflow-x-auto">
        <table className="data-table" data-testid="iam-guide-access-matrix">
          <caption className="sr-only">역할별 IAM 화면 접근 권한</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                역할 \ 화면
              </th>
              {SCREEN_ACCESS.map((s) => (
                <th key={s.screen} scope="col" className="p-2 text-center">
                  <span className="block">{s.screen}</span>
                  <span className="block font-mono text-[11px] font-normal text-muted-foreground">
                    {s.gate}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {SEED_ROLES.map((role) => (
              <tr
                key={role.name}
                data-testid={`iam-guide-matrix-row-${role.name}`}
              >
                <th
                  scope="row"
                  className="p-2 text-left font-mono text-xs font-medium text-foreground"
                >
                  {role.name}
                </th>
                {SCREEN_ACCESS.map((s) => {
                  const cell = s.cells[role.name] ?? { level: 'none' as const };
                  return (
                    <td
                      key={s.screen}
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
      <p className="mb-10 text-xs text-muted-foreground">
        ✅ 가능 · △ 부분(기본 감사만, 보안이벤트 제외) · ✕ 불가. 감사·보안의
        보안이벤트(로그인 이력·의심 이벤트)는 <PermChip label="audit.read" /> 위에{' '}
        <PermChip label="security.event.read" /> 를 추가로 요구합니다. 계정
        운영은 <PermChip label="account.read" /> 로 게이트되므로 계정 제어
        권한(잠금/해제)만 가진 SUPPORT_LOCK 은 목록 화면을 열 수 없습니다.
      </p>

      {/* 3. 운영 시 롤 부여 예시 */}
      <h2 className="mb-4 text-lg font-semibold">운영 시 역할 부여 (위임 체인)</h2>
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
              <p className="text-sm font-medium text-foreground">
                {step.actor}
              </p>
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

      {/* 3.5 운영자 온보딩 3축 (운영 도달 범위) */}
      <h2 className="mb-2 text-lg font-semibold">
        운영자 온보딩 3축 (운영 도달 범위)
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        위임으로 역할을 받은 뒤, 운영자가 <strong>어디를 · 얼마나</strong> 운영할
        수 있는지는 아래 3개의 직교 축으로 정해집니다. assume-tenant 로 도메인
        롤을 받는 운영(E-Commerce·WMS·SCM…)에 그대로 적용되며, 이커머스
        입점사/협력업체 운영자 온보딩이 이 세 축으로 표현됩니다.
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
                {axis.term}
              </span>
              <span className="text-sm text-muted-foreground">
                {axis.koName}
              </span>
            </div>
            <p className="mb-2 font-mono text-[11px] text-muted-foreground">
              {axis.api}
            </p>
            <p className="mb-3 text-sm text-muted-foreground">{axis.desc}</p>
            <p className="text-sm text-foreground">
              <span className="mr-1 rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
                이커머스
              </span>
              {axis.ecommerceNote}
            </p>
          </Card>
        ))}
      </div>

      {/* 4. 도메인 롤 (별도 축) */}
      <h2 className="mb-2 text-lg font-semibold">
        참고: 테넌트 직원/협력업체의 도메인 롤 (별도 축)
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        위 6개는 IAM 콘솔 3화면을 게이트하는 <strong>admin-console 역할</strong>
        입니다. 테넌트 직원/협력업체는 이 역할이 아니라{' '}
        <strong>도메인 롤</strong>을 받습니다 — 로그인 후 테넌트 선택
        (assume-tenant) 시 그 테넌트의 <strong>구독 도메인</strong>에서 아래와
        같이 자동 파생되어 도메인 서비스(WMS·E-Commerce·SCM…)를 게이트합니다.
        협력업체는 같은 도메인 롤을 받되 배정의 <strong>org_scope(부서 subtree)</strong>
        로 데이터 범위가 좁혀집니다.
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
                파생 도메인 롤
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

      {/* 권한 키 카탈로그 (부록) */}
      <h2 className="mb-4 mt-10 text-lg font-semibold">부록: 권한 키</h2>
      <div className="overflow-x-auto">
        <table className="data-table" data-testid="iam-guide-permission-keys">
          <caption className="sr-only">권한 키 카탈로그</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                권한 키
              </th>
              <th scope="col" className="p-2">
                설명
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
    </section>
  );
}
