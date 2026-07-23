import {
  ACCOUNT_ID_DRIVEN_NOTE,
  ACCOUNT_STATES,
  CONSOLE_SCREENS,
  DOMAIN_SERVICES,
  DOUBLE_ENTRY_NOTE,
  F5_NOTE,
  FINANCE_GLOSSARY,
  FINANCE_RECIPES,
  FX_NOTE,
  KYC_LEVELS,
  RECONCILIATION_NOTE,
} from '../data';
import {
  AttentionCell,
  Glossary,
  GuideRecipe,
  GuideToc,
  Mono,
  NoteCard,
  StateTh,
} from '@/shared/ui/guide-primitives';

/**
 * Finance 가이드 화면 (TASK-PC-FE-229). 순수 정적 참조 화면 — finance-platform
 * 도메인 서비스 구성과, 콘솔 4개 화면(개요·가이드·계좌·원장)이 보여주는 값의
 * 의미·개념(규제 계좌 상태·KYC·F5·복식부기·대사·FX 신선도)을 한 화면에서
 * 설명한다. 데이터 페치·권한 게이트 없음(server component, no 'use client'):
 * 가이드는 콘솔 진입자 누구나 열람 가능. SCM 가이드(ScmGuideScreen) · IAM
 * 가이드(IamGuideScreen) · WMS 가이드(WmsGuideScreen)와 동일 패턴.
 */

const SECTIONS = [
  { id: 'finance-guide-recipes', label: '자주 하는 작업' },
  { id: 'finance-guide-services', label: '도메인 서비스' },
  { id: 'finance-guide-screens', label: '콘솔 화면' },
  { id: 'finance-guide-account-states', label: '계좌 상태' },
  { id: 'finance-guide-kyc', label: 'KYC 레벨' },
  { id: 'finance-guide-concepts', label: '핵심 개념' },
  { id: 'finance-guide-glossary', label: '용어집' },
];

export function FinanceGuideScreen() {
  return (
    <section aria-labelledby="finance-guide-heading" data-testid="finance-guide">
      <h1 id="finance-guide-heading" className="mb-2 text-2xl font-semibold">
        Finance 가이드
      </h1>
      <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
        Finance 콘솔은 <strong>개요 · 가이드 · 계좌 · 원장</strong> 4개 화면으로
        구성됩니다. 아래는 각 화면이 보여주는 값의 의미, 그 뒤의
        finance-platform 마이크로서비스 구성, 그리고 규제 계좌 상태 · KYC ·
        금액 표현(F5) · 복식부기 · 대사 · FX 신선도 개념을 정리한 참조입니다.
      </p>

      <GuideToc items={SECTIONS} />

      {/* ───────────────── 자주 하는 작업 (레시피) ───────────────── */}
      <h2
        id="finance-guide-recipes"
        data-testid="finance-guide-recipes"
        className="mb-2 text-xl font-semibold"
      >
        자주 하는 작업
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        “이럴 땐 이렇게” — 각 단계는 계좌·원장·개요 화면의 실제 상태·작업만
        참조합니다.
      </p>
      <div className="mb-10">
        {FINANCE_RECIPES.map((recipe, i) => (
          <GuideRecipe
            key={recipe.title}
            recipe={recipe}
            testid={`finance-guide-recipe-${i}`}
          />
        ))}
      </div>

      {/* ───────────────── 도메인 서비스 맵 ───────────────── */}
      <h2
        id="finance-guide-services"
        data-testid="finance-guide-services"
        className="mb-2 text-xl font-semibold"
      >
        도메인 서비스
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        Finance 는 별도 게이트웨이 없이 2개 producer 로 구성됩니다. 콘솔은 각
        서비스를 도메인-facing IAM OIDC 토큰으로 직접 호출해 화면을 렌더합니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="finance-guide-services-table">
          <caption className="sr-only">Finance 도메인 서비스</caption>
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
                data-testid={`finance-guide-service-${s.key}`}
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
        id="finance-guide-screens"
        data-testid="finance-guide-screens"
        className="mb-2 text-xl font-semibold"
      >
        콘솔 화면
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        4개 화면 각각이 보여주는 값입니다. <strong>개요</strong>는 계좌 목록을
        집계하지 않고(finance v1 에 계좌 list/search GET 없음), 원장
        browsable read + 운영자 본인의 기본계좌 단건 스냅샷만 보여줍니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="finance-guide-screens-table">
          <caption className="sr-only">Finance 콘솔 화면</caption>
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
                data-testid={`finance-guide-screen-${s.key}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.route} />
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard
        title={ACCOUNT_ID_DRIVEN_NOTE.title}
        body={ACCOUNT_ID_DRIVEN_NOTE.body}
      />

      {/* ───────────────── 계좌 상태 ───────────────── */}
      <h2
        id="finance-guide-account-states"
        data-testid="finance-guide-account-states"
        className="mb-2 text-xl font-semibold"
      >
        계좌 상태
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>계좌</strong> 화면(<Mono>/finance/accounts</Mono>)과{' '}
        <strong>개요</strong>의 기본계좌 스냅샷에 표시되는 규제 계좌 상태
        5종입니다. <Mono>FROZEN</Mono>/<Mono>RESTRICTED</Mono> 는 절대 숨기지
        않고 있는 그대로 표시됩니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="finance-guide-account-states-table">
          <caption className="sr-only">계좌 상태</caption>
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
            {ACCOUNT_STATES.map((s) => (
              <tr
                key={s.name}
                data-testid={`finance-guide-account-state-${s.name}`}
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

      {/* ───────────────── KYC 레벨 ───────────────── */}
      <h2
        id="finance-guide-kyc"
        data-testid="finance-guide-kyc"
        className="mb-2 text-xl font-semibold"
      >
        KYC 레벨
      </h2>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="finance-guide-kyc-table">
          <caption className="sr-only">KYC 레벨</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                레벨
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {KYC_LEVELS.map((k) => (
              <tr
                key={k.name}
                data-testid={`finance-guide-kyc-${k.name}`}
                className="border-b border-border"
              >
                <StateTh label={k.label} name={k.name} />
                <td className="p-2 text-sm text-muted-foreground">{k.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ───────────────── 개념 노트 ───────────────── */}
      <h2
        id="finance-guide-concepts"
        data-testid="finance-guide-concepts"
        className="mb-2 text-xl font-semibold"
      >
        핵심 개념
      </h2>

      <NoteCard title={F5_NOTE.title} body={F5_NOTE.body} />
      <NoteCard title={DOUBLE_ENTRY_NOTE.title} body={DOUBLE_ENTRY_NOTE.body} />
      <NoteCard
        title={RECONCILIATION_NOTE.title}
        body={RECONCILIATION_NOTE.body}
      />
      <NoteCard title={FX_NOTE.title} body={FX_NOTE.body} />

      {/* ───────────────── 용어집 ───────────────── */}
      <h2
        id="finance-guide-glossary"
        data-testid="finance-guide-glossary"
        className="mb-2 mt-10 text-xl font-semibold"
      >
        용어집
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        이 화면에 나오는 낯선 회계·규제 용어의 뜻입니다.
      </p>
      <Glossary entries={FINANCE_GLOSSARY} testid="finance-guide-glossary-table" />
    </section>
  );
}
