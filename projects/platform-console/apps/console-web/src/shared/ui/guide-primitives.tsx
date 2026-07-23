import type { ReactNode } from 'react';

import { Card } from '@/shared/ui/Card';

/**
 * 콘솔 도메인 가이드 화면(finance · erp · scm · wms · ecommerce GuideScreen)
 * 공용 presentation 원자. 각 GuideScreen 이 개별 정의하던 동일 구현을
 * 여기로 통합한다(TASK-PC-FE-236). 순수 정적·무상태 — 데이터/권한 없음.
 * app 레이어가 아닌 feature(GuideScreen)에서만 import 한다.
 */

/** 인라인 mono 칩(식별자·enum·경로 강조). */
export function Mono({ children }: { children: ReactNode }) {
  return (
    <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-foreground">
      {children}
    </span>
  );
}

/** 화면 상단/구획 안내 카드(제목 + 본문). */
export function NoteCard({ title, body }: { title: string; body: string }) {
  return (
    <Card className="mb-10 bg-muted/40">
      <p className="mb-1 text-sm font-medium text-foreground">{title}</p>
      <p className="text-sm text-muted-foreground">{body}</p>
    </Card>
  );
}

/** 상태명(한글 라벨 + enum) 행 헤더. */
export function StateTh({ label, name }: { label: string; name: string }) {
  return (
    <th scope="row" className="p-2 text-left">
      <span className="font-medium text-foreground">{label}</span>
      <span className="ml-2 font-mono text-[11px] text-muted-foreground">
        {name}
      </span>
    </th>
  );
}

/** 운영자 주의 필요 여부를 ●/— 로 표시하는 셀. */
export function AttentionCell({ attention }: { attention: boolean }) {
  return attention ? (
    <span className="text-foreground" aria-label="운영자 주의 필요" title="운영자 주의 필요">
      ●
    </span>
  ) : (
    <span className="text-muted-foreground" aria-label="정상">
      —
    </span>
  );
}

/**
 * 종료(terminal) 여부를 ●/— 로 표시하는 셀.
 * 진행 중 케이스의 aria-label 은 도메인별 기존 표기를 보존하기 위해
 * `inProgressLabel` 로 주입한다(erp="진행 중", ecommerce·scm="진행").
 */
export function TerminalCell({
  terminal,
  inProgressLabel = '진행 중',
}: {
  terminal: boolean;
  inProgressLabel?: string;
}) {
  return terminal ? (
    <span className="text-foreground" aria-label="종료 상태" title="종료 상태">
      ●
    </span>
  ) : (
    <span className="text-muted-foreground" aria-label={inProgressLabel}>
      —
    </span>
  );
}

/**
 * 페이지 내 목차(in-page TOC, TASK-PC-FE-255). 각 GuideScreen 이 이미 갖고
 * 있는 섹션 `id` 를 소스로 앵커 링크 목록을 렌더한다 — 하드코딩 금지, 화면이
 * `items` 를 그 화면의 실제 섹션 heading 에서 구성해 넘긴다. 순수 정적 —
 * 데이터 페치·상태 없음. 키보드로 각 링크에 접근 가능(네이티브 `<a>`).
 */
export function GuideToc({
  items,
}: {
  items: { id: string; label: string }[];
}) {
  return (
    <nav
      aria-label="목차"
      data-testid="guide-toc"
      className="mb-8 rounded-lg border border-border bg-muted/30 p-3"
    >
      <ul className="flex flex-wrap gap-x-4 gap-y-1.5">
        {items.map((item) => (
          <li key={item.id}>
            <a
              href={`#${item.id}`}
              data-testid={`guide-toc-${item.id}`}
              className="rounded text-sm text-muted-foreground underline-offset-2 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              {item.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/**
 * 상태 전이 시각 다이어그램(TASK-PC-FE-255). 선형/분기 상태머신을 배열 순서
 * 그대로 mono 칩 + 화살표로 보여준다 — 기존 상태 표(테이블) **위**에 추가되는
 * 보조 시각화이며 표를 대체하지 않는다(상세·스크린리더용 정보원은 표가
 * 유지). 종료(terminal) 상태는 `TerminalCell` 의 ● 시맨틱과 일관되게
 * 채움(foreground) 스타일로 구분한다. 표가 이미 완전한 정보를 제공하므로
 * 이 다이어그램 자체는 스크린리더에서 숨김(`aria-hidden`) 처리한다.
 */
export function StateFlow({
  states,
}: {
  states: { label: string; name: string; terminal?: boolean }[];
}) {
  return (
    <div
      data-testid="state-flow"
      aria-hidden="true"
      className="mb-4 flex flex-wrap items-center gap-x-1.5 gap-y-2"
    >
      {states.map((s, i) => (
        <span key={s.name} className="flex items-center gap-1.5">
          <span
            data-testid={`state-flow-${s.name}`}
            className={
              s.terminal
                ? 'rounded bg-foreground px-2 py-1 font-mono text-xs font-medium text-background'
                : 'rounded bg-muted px-2 py-1 font-mono text-xs text-foreground'
            }
          >
            {s.terminal && <span aria-hidden="true">● </span>}
            {s.label}
          </span>
          {i < states.length - 1 && (
            <span className="text-muted-foreground">→</span>
          )}
        </span>
      ))}
    </div>
  );
}
