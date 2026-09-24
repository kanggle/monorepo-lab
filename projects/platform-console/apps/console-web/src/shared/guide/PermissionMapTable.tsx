import type { ReactNode } from 'react';
import { Card } from '@/shared/ui/Card';
import type { ScreenStatus } from '@/shared/sample/coverage';
import {
  DEMO_TEST_ACCOUNT,
  gateLabel,
  resolvePermissionMap,
  type Access,
  type Crud,
  type MapArea,
  type ResolvedRow,
} from './permission-map';

/**
 * TASK-PC-FE-298 — 권한·기능 매핑 표와 그 파생 뷰(메뉴별 설명 · 메뉴별 사용 절차 ·
 * 알려진 불일치). 전부 `resolvePermissionMap()` 한 곳에서 읽는다 — 도메인 가이드 6개와
 * 전역 가이드가 같은 행을 보여주므로 한 행을 고치면 모든 화면이 같이 바뀐다.
 * 순수 서버 컴포넌트, 데이터 페치 없음(샘플 방문자도 그대로 본다).
 */

function rowsFor(areas?: MapArea[]): ResolvedRow[] {
  const all = resolvePermissionMap();
  return areas ? all.filter((r) => areas.includes(r.area)) : all;
}

const SAMPLE_LABEL: Record<ScreenStatus, string> = {
  static: '가능 (정적)',
  ready: '가능 (샘플 데이터)',
  pending: '준비 중',
};

/** 절차 문장용 — 「로그인하지 않았다면 …」 뒤에 붙는다. */
const SAMPLE_VISIT: Record<ScreenStatus, string> = {
  static: '같은 정적 화면이 그대로 열립니다',
  ready: '샘플 데이터로 열립니다',
  pending: '「샘플 데이터 준비 중」 안내와 함께 열립니다',
};

const ACCESS_LABEL: Record<Access, string> = {
  yes: '가능',
  partial: '일부',
  no: '불가',
};

function crudText(c: Crud): string {
  return (
    [c.read && '조회', c.create && '생성', c.update && '수정', c.delete && '삭제']
      .filter(Boolean)
      .join(' · ') || '—'
  );
}

function CrudCells({ c }: { c: Crud }) {
  const cell = (on: boolean, label: string) => (
    <span
      aria-label={`${label} ${on ? '가능' : '불가'}`}
      className={on ? 'text-foreground' : 'text-muted-foreground/60'}
    >
      {on ? '●' : '—'}
    </span>
  );
  return (
    <span className="flex gap-2 font-mono text-xs">
      {cell(c.read, '조회')}
      {cell(c.create, '생성')}
      {cell(c.update, '수정')}
      {cell(c.delete, '삭제')}
    </span>
  );
}

function Code({ children }: { children: ReactNode }) {
  return (
    <span className="break-all rounded bg-muted px-1.5 py-0.5 font-mono text-[11px] text-foreground">
      {children}
    </span>
  );
}

export function PermissionMapTable({
  areas,
  testid,
  caption = '권한·기능 매핑 표',
  showSources = true,
}: {
  areas?: MapArea[];
  testid: string;
  caption?: string;
  showSources?: boolean;
}) {
  const rows = rowsFor(areas);
  return (
    <div className="overflow-x-auto" data-testid={testid}>
      <table className="data-table min-w-[1500px] table-fixed text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="text-left">
            <th scope="col" className="w-12 whitespace-nowrap p-2">뎁스</th>
            <th scope="col" className="w-36 p-2">메뉴명</th>
            <th scope="col" className="w-44 p-2">라우트</th>
            <th scope="col" className="w-56 p-2">권한 코드</th>
            <th scope="col" className="w-60 p-2">설명</th>
            <th scope="col" className="w-36 p-2" title="조회 · 생성 · 수정 · 삭제">
              조회·생성·수정·삭제
            </th>
            <th scope="col" className="w-52 p-2">용도</th>
            <th scope="col" className="w-52 p-2">연결 서비스</th>
            <th scope="col" className="w-28 p-2">비로그인 데모</th>
            <th scope="col" className="w-28 p-2">
              테스트 계정
              <span className="block font-mono text-[10px] font-normal text-muted-foreground">
                {DEMO_TEST_ACCOUNT.operatorId}
              </span>
            </th>
            {showSources && (
              <th scope="col" className="w-72 p-2">
                근거
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr
              key={r.href}
              data-testid={`${testid}-row-${r.href}`}
              className="border-b border-border align-top"
            >
              <td className="p-2 text-xs text-muted-foreground">{r.depth}</td>
              <th scope="row" className="p-2 text-left">
                <span className="font-medium text-foreground">{r.label}</span>
                <span className="block text-[11px] font-normal text-muted-foreground">
                  {r.path.join(' › ')}
                </span>
              </th>
              <td className="p-2">
                <Code>{r.href}</Code>
              </td>
              <td className="p-2">
                <Code>{gateLabel(r.gate)}</Code>
                {'extra' in r.gate && r.gate.extra && (
                  <span className="mt-1 block text-[11px] text-muted-foreground">
                    {r.gate.extra}
                  </span>
                )}
                {r.gate.kind === 'operator' && (
                  <span className="mt-1 block text-[11px] text-muted-foreground">
                    {r.gate.note}
                  </span>
                )}
                {r.mismatch && (
                  <span
                    data-testid={`${testid}-mismatch-${r.href}`}
                    className="mt-1 block rounded border border-amber-500/40 bg-amber-500/10 px-1.5 py-1 text-[11px] text-foreground"
                  >
                    ⚠ 불일치·특수 케이스: {r.mismatch}
                  </span>
                )}
              </td>
              <td className="p-2 text-muted-foreground">{r.description}</td>
              <td className="p-2">
                <CrudCells c={r.crud} />
                {r.crudNote && (
                  <span className="mt-1 block text-[11px] text-muted-foreground">
                    {r.crudNote}
                  </span>
                )}
              </td>
              <td className="p-2 text-muted-foreground">{r.purpose}</td>
              <td className="break-words p-2 text-xs text-muted-foreground">
                {r.services.length ? r.services.join(' · ') : '없음 (정적)'}
              </td>
              <td className="p-2 text-xs" data-sample={r.sample}>
                {SAMPLE_LABEL[r.sample]}
              </td>
              <td className="p-2 text-xs" data-access={r.access}>
                {ACCESS_LABEL[r.access]}
              </td>
              {showSources && (
                <td className="p-2">
                  <ul className="space-y-0.5">
                    {r.sources.map((s) => (
                      <li key={s} className="break-all font-mono text-[10px] text-muted-foreground">
                        {s}
                      </li>
                    ))}
                  </ul>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** 메뉴별 설명 — 표보다 읽기 쉬운 카드형(도메인 가이드 탭용). */
export function MenuDescriptions({ areas, testid }: { areas: MapArea[]; testid: string }) {
  return (
    <div className="grid gap-4 md:grid-cols-2" data-testid={testid}>
      {rowsFor(areas).map((r) => (
        <Card key={r.href} data-testid={`${testid}-${r.href}`}>
          <p className="mb-1 text-sm font-semibold text-foreground">
            {r.label} <span className="ml-1 font-mono text-[11px] font-normal text-muted-foreground">{r.href}</span>
          </p>
          <p className="mb-2 text-sm text-muted-foreground">{r.description}</p>
          <p className="text-xs text-muted-foreground">
            <span className="font-medium text-foreground">용도</span> · {r.purpose}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            <span className="font-medium text-foreground">할 수 있는 조작</span> · {crudText(r.crud)}
            {r.crudNote ? ` (${r.crudNote})` : ''}
          </p>
        </Card>
      ))}
    </div>
  );
}

/**
 * 메뉴별 사용 절차 — 각 메뉴를 여는 방법 · 필요한 권한 · 그 화면에서 할 수 있는 조작을
 * 번호형 절차로. 문장은 전부 매핑 행(설명 · CRUD · 게이트)에서 조립한다 — 화면에 없는
 * 버튼을 지어내지 않기 위해 손으로 쓴 단계가 없다.
 */
export function MenuProcedures({ areas, testid }: { areas: MapArea[]; testid: string }) {
  return (
    <div data-testid={testid}>
      {rowsFor(areas).map((r) => {
        const steps = [
          `사이드바 「${r.path.join(' › ')}」 를 누릅니다 (${r.href}).`,
          r.gate.kind === 'public'
            ? '권한이 필요 없습니다 — 로그인하지 않아도 열립니다.'
            : `필요한 권한: ${gateLabel(r.gate)}${'extra' in r.gate && r.gate.extra ? ` (${r.gate.extra})` : ''}. 로그인하지 않았다면 ${SAMPLE_VISIT[r.sample]}.`,
          `화면 내용: ${r.description}`,
          `할 수 있는 조작: ${crudText(r.crud)}${r.crudNote ? ` — ${r.crudNote.replace(/\.$/, '')}` : ''}.`,
        ];
        return (
          <Card key={r.href} className="mb-4" data-testid={`${testid}-${r.href}`}>
            <p className="mb-3 text-sm font-semibold text-foreground">{r.label}</p>
            <ol className="space-y-2">
              {steps.map((s, i) => (
                <li key={s} className="flex gap-3">
                  <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-foreground">
                    {i + 1}
                  </span>
                  <p className="text-sm text-muted-foreground">{s}</p>
                </li>
              ))}
            </ol>
          </Card>
        );
      })}
    </div>
  );
}

/** 이 영역의 알려진 불일치 목록(없으면 그렇다고 말한다). */
export function KnownMismatches({ areas, testid }: { areas?: MapArea[]; testid: string }) {
  const rows = rowsFor(areas).filter((r) => r.mismatch);
  if (rows.length === 0) {
    return (
      <p data-testid={testid} className="text-sm text-muted-foreground">
        이 영역에 기록된 권한 불일치는 없습니다.
      </p>
    );
  }
  return (
    <ul data-testid={testid} className="space-y-2">
      {rows.map((r) => (
        <li key={r.href} className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm">
          <span className="font-medium text-foreground">{r.label}</span>{' '}
          <span className="font-mono text-[11px] text-muted-foreground">{r.href}</span>
          <span className="block text-muted-foreground">{r.mismatch}</span>
        </li>
      ))}
    </ul>
  );
}
