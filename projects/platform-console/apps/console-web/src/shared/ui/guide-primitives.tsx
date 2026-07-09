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
