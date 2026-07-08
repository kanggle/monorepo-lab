import { Card } from '@/shared/ui/Card';
import {
  APPROVAL_ROUTING_NOTE,
  APPROVAL_STATUSES,
  ASOF_NOTE,
  CONSOLE_SCREENS,
  DELEGATION_NOTE,
  DELEGATION_SCOPES,
  DOMAIN_SERVICES,
  EMPLOYMENT_STATUSES,
  MASTER_STATUSES,
  NOTIFICATION_NOTE,
  READ_MODEL_NOTE,
} from '../data';

/**
 * ERP 가이드 화면 (TASK-PC-FE-232). 순수 정적 참조 화면 — erp-platform
 * 도메인 서비스 구성과, 콘솔 6개 화면(개요·가이드·마스터·통합 조회·결재함·
 * 위임)이 보여주는 값의 의미·개념(effective-dating·결재 상태머신·위임·
 * read-model·알림)을 한 화면에서 설명한다. 데이터 페치·권한 게이트 없음
 * (server component, no 'use client'): 가이드는 콘솔 진입자 누구나 열람
 * 가능. Finance 가이드(FinanceGuideScreen) · SCM 가이드(ScmGuideScreen) ·
 * IAM 가이드(IamGuideScreen) · WMS 가이드(WmsGuideScreen)와 동일 패턴.
 */

function Mono({ children }: { children: React.ReactNode }) {
  return (
    <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-foreground">
      {children}
    </span>
  );
}

function NoteCard({ title, body }: { title: string; body: string }) {
  return (
    <Card className="mb-10 bg-muted/40">
      <p className="mb-1 text-sm font-medium text-foreground">{title}</p>
      <p className="text-sm text-muted-foreground">{body}</p>
    </Card>
  );
}

/** 주의 필요 여부를 ●/— 로 표시하는 셀. */
function AttentionCell({ attention }: { attention: boolean }) {
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

/** 종료(terminal) 여부를 ●/— 로 표시하는 셀. */
function TerminalCell({ terminal }: { terminal: boolean }) {
  return terminal ? (
    <span className="text-foreground" aria-label="종료 상태" title="종료 상태">
      ●
    </span>
  ) : (
    <span className="text-muted-foreground" aria-label="진행 중">
      —
    </span>
  );
}

/** 상태명(한글 라벨 + enum) 행 헤더. */
function StateTh({ label, name }: { label: string; name: string }) {
  return (
    <th scope="row" className="p-2 text-left">
      <span className="font-medium text-foreground">{label}</span>
      <span className="ml-2 font-mono text-[11px] text-muted-foreground">
        {name}
      </span>
    </th>
  );
}

export function ErpGuideScreen() {
  return (
    <section aria-labelledby="erp-guide-heading" data-testid="erp-guide">
      <h1 id="erp-guide-heading" className="mb-2 text-2xl font-semibold">
        ERP 가이드
      </h1>
      <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
        ERP 콘솔은 <strong>개요 · 가이드 · 마스터 · 통합 조회 · 결재함 ·
        위임</strong> 6개 화면으로 구성됩니다. 아래는 각 화면이 보여주는 값의
        의미, 그 뒤의 erp-platform 마이크로서비스 구성, 그리고 마스터/직원
        상태 · 결재 상태머신 · 위임 스코프 · effective-dating(`asOf`) ·
        read-model 투영 · 알림 통합 개념을 정리한 참조입니다.
      </p>

      {/* ───────────────── 도메인 서비스 맵 ───────────────── */}
      <h2
        id="erp-guide-services"
        data-testid="erp-guide-services"
        className="mb-2 text-xl font-semibold"
      >
        도메인 서비스
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        ERP 는 별도 게이트웨이 없이 4개 producer 로 구성됩니다. 콘솔은 각
        서비스를 도메인-facing IAM OIDC 토큰으로 직접 호출해 화면을 렌더합니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="erp-guide-services-table">
          <caption className="sr-only">ERP 도메인 서비스</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                서비스
              </th>
              <th scope="col" className="p-2">
                컨텍스트
              </th>
              <th scope="col" className="p-2">
                책임
              </th>
              <th scope="col" className="p-2">
                콘솔 화면
              </th>
            </tr>
          </thead>
          <tbody>
            {DOMAIN_SERVICES.map((s) => (
              <tr
                key={s.key}
                data-testid={`erp-guide-service-${s.key}`}
                className="border-b border-border"
              >
                <td className="p-2">
                  <Mono>{s.name}</Mono>
                </td>
                <td className="p-2 text-sm text-foreground">{s.context}</td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
                <td className="p-2 text-sm text-muted-foreground">
                  {s.console}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ───────────────── 콘솔 화면 맵 ───────────────── */}
      <h2
        id="erp-guide-screens"
        data-testid="erp-guide-screens"
        className="mb-2 text-xl font-semibold"
      >
        콘솔 화면
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        6개 화면 각각이 보여주는 값입니다. <strong>개요</strong>는 마스터
        카운트에 더해 본인 결재 대기·활성 위임 건수를 함께 집계합니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="erp-guide-screens-table">
          <caption className="sr-only">ERP 콘솔 화면</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                화면 (라우트)
              </th>
              <th scope="col" className="p-2">
                보여주는 값
              </th>
            </tr>
          </thead>
          <tbody>
            {CONSOLE_SCREENS.map((s) => (
              <tr
                key={s.key}
                data-testid={`erp-guide-screen-${s.key}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.route} />
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard title={ASOF_NOTE.title} body={ASOF_NOTE.body} />

      {/* ───────────────── 마스터 상태 ───────────────── */}
      <h2
        id="erp-guide-master-states"
        data-testid="erp-guide-master-states"
        className="mb-2 text-xl font-semibold"
      >
        마스터 상태
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>마스터</strong> 화면(<Mono>/erp/masters</Mono>)의 5종 마스터
        공통 상태입니다. <Mono>RETIRED</Mono> 는 절대 숨기지 않고 있는 그대로
        표시됩니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="erp-guide-master-states-table">
          <caption className="sr-only">마스터 상태</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                주의
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {MASTER_STATUSES.map((s) => (
              <tr
                key={s.name}
                data-testid={`erp-guide-master-state-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm">
                  <AttentionCell attention={s.attention} />
                </td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ───────────────── 직원 재직 상태 ───────────────── */}
      <h2
        id="erp-guide-employment-states"
        data-testid="erp-guide-employment-states"
        className="mb-2 text-xl font-semibold"
      >
        직원 재직 상태
      </h2>
      <div className="mb-10 overflow-x-auto">
        <table
          className="data-table"
          data-testid="erp-guide-employment-states-table"
        >
          <caption className="sr-only">직원 재직 상태</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                주의
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {EMPLOYMENT_STATUSES.map((s) => (
              <tr
                key={s.name}
                data-testid={`erp-guide-employment-state-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm">
                  <AttentionCell attention={s.attention} />
                </td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ───────────────── 결재 상태머신 ───────────────── */}
      <h2
        id="erp-guide-approval-states"
        data-testid="erp-guide-approval-states"
        className="mb-2 text-xl font-semibold"
      >
        결재 상태
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>결재함</strong> 화면(<Mono>/erp/approval</Mono>)의 결재 요청
        상태 6종입니다. 경로: <Mono>DRAFT → SUBMITTED → IN_REVIEW(2~N 단계) →
        APPROVED | REJECTED | WITHDRAWN</Mono>.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table
          className="data-table"
          data-testid="erp-guide-approval-states-table"
        >
          <caption className="sr-only">결재 상태</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                종료
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {APPROVAL_STATUSES.map((s) => (
              <tr
                key={s.name}
                data-testid={`erp-guide-approval-state-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm">
                  <TerminalCell terminal={s.terminal} />
                </td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard
        title={APPROVAL_ROUTING_NOTE.title}
        body={APPROVAL_ROUTING_NOTE.body}
      />

      {/* ───────────────── 위임 스코프 ───────────────── */}
      <h2
        id="erp-guide-delegation-scopes"
        data-testid="erp-guide-delegation-scopes"
        className="mb-2 text-xl font-semibold"
      >
        위임 스코프
      </h2>
      <div className="mb-10 overflow-x-auto">
        <table
          className="data-table"
          data-testid="erp-guide-delegation-scopes-table"
        >
          <caption className="sr-only">위임 스코프</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                스코프
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {DELEGATION_SCOPES.map((s) => (
              <tr
                key={s.name}
                data-testid={`erp-guide-delegation-scope-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard title={DELEGATION_NOTE.title} body={DELEGATION_NOTE.body} />

      {/* ───────────────── 개념 노트 ───────────────── */}
      <h2
        id="erp-guide-concepts"
        data-testid="erp-guide-concepts"
        className="mb-2 text-xl font-semibold"
      >
        핵심 개념
      </h2>

      <NoteCard title={READ_MODEL_NOTE.title} body={READ_MODEL_NOTE.body} />
      <NoteCard title={NOTIFICATION_NOTE.title} body={NOTIFICATION_NOTE.body} />
    </section>
  );
}
