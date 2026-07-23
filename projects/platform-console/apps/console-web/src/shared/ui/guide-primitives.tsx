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

/**
 * 작업 레시피 한 개(TASK-PC-FE-256). "이럴 땐 이렇게" — 제목 + 번호형 절차
 * `<ol>`. 번호 원형 마커는 IAM `DELEGATION_CHAIN` / WMS 예약 흐름이 쓰던
 * `h-6 w-6 rounded-full bg-muted …` 스타일을 그대로 일반화한 것이다. 순수
 * 정적 — 데이터/상태 없음. 각 단계 문자열은 화면이 아니라 도메인 `data.ts`
 * 상수에서 온다(하드코딩 산재 금지). 시맨틱 순서 목록(`<ol>`).
 */
export interface GuideRecipeData {
  /** 레시피 제목("환불 요청이 들어왔을 때" 등). */
  title: string;
  /** 한 줄 도입(선택). */
  intro?: string;
  /** 번호형 절차 — 실제 존재하는 상태·메뉴만 참조. */
  steps: string[];
}

export function GuideRecipe({
  recipe,
  testid,
}: {
  recipe: GuideRecipeData;
  testid: string;
}) {
  return (
    <Card data-testid={testid} className="mb-4">
      <p className="mb-1 text-sm font-semibold text-foreground">
        {recipe.title}
      </p>
      {recipe.intro && (
        <p className="mb-3 text-sm text-muted-foreground">{recipe.intro}</p>
      )}
      <ol className="space-y-3">
        {recipe.steps.map((step, i) => (
          <li key={step} className="flex gap-3" data-testid={`${testid}-step-${i}`}>
            <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-foreground">
              {i + 1}
            </span>
            <p className="text-sm text-muted-foreground">{step}</p>
          </li>
        ))}
      </ol>
    </Card>
  );
}

/**
 * 인라인 용어(TASK-PC-FE-256). 약어를 `<abbr title>` 로 감싸 마우스 hover 와
 * 스크린리더에 확장형을 노출한다. 정의 자체는 항상 `Glossary` 표의 "뜻" 열에
 * 상시 보이므로(hover-only 아님) 이 인라인 태그는 보조 강조일 뿐이다.
 */
export function Term({
  children,
  title,
}: {
  children: ReactNode;
  title: string;
}) {
  return (
    <abbr
      title={title}
      className="cursor-help underline decoration-dotted decoration-muted-foreground/70 underline-offset-2"
    >
      {children}
    </abbr>
  );
}

/**
 * 가이드 하단 미니 용어집(TASK-PC-FE-256) — "용어 | 뜻" 2열 표. 각 도메인
 * `data.ts` 의 `*_GLOSSARY` 상수(그 화면에 실제 렌더되는 용어만)로 배선된다.
 * 정의가 "뜻" 열에 상시 보여 키보드·모바일에서도 접근 가능하다(hover 의존 없음).
 * 용어는 `<dfn>` 시맨틱으로 감싸고, 약어에는 `Term`(`<abbr>`)으로 확장형을
 * 덧붙인다.
 */
export interface GlossaryEntry {
  /** testid 안전 슬러그(표시 term 과 별개). */
  key: string;
  /** 표시 용어(한글/약어 가능). */
  term: string;
  /** 뜻 — 일반 운영자가 읽어 이해할 평이한 설명. */
  meaning: string;
  /** 약어 확장형(있으면 `<abbr title>` 로 감싼다). */
  full?: string;
}

export function Glossary({
  entries,
  testid,
}: {
  entries: GlossaryEntry[];
  testid: string;
}) {
  return (
    <div className="overflow-x-auto" data-testid={testid}>
      <table className="data-table">
        <caption className="sr-only">용어 정의</caption>
        <thead>
          <tr className="text-left">
            <th scope="col" className="p-2">
              용어
            </th>
            <th scope="col" className="p-2">
              뜻
            </th>
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <tr
              key={e.key}
              data-testid={`${testid}-${e.key}`}
              className="border-b border-border"
            >
              <th scope="row" className="p-2 text-left align-top">
                <dfn className="font-medium not-italic text-foreground">
                  {e.full ? <Term title={e.full}>{e.term}</Term> : e.term}
                </dfn>
              </th>
              <td className="p-2 text-sm text-muted-foreground">{e.meaning}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
