import {
  CONFIG_NOTE,
  DOMAIN_SERVICES,
  NODE_NOTE,
  PO_NOTE,
  PO_STATES,
  POLICY_FIELDS,
  REPLENISHMENT_LOOP_NOTE,
  S5_NOTE,
  SCM_ROLE_NOTE,
  STALENESS_STATES,
  SUGGESTION_STATES,
  SUPPLIER_FIELDS,
  type ConfigField,
} from '../data';
import {
  GuideToc,
  Mono,
  NoteCard,
  StateFlow,
  StateTh,
  TerminalCell,
} from '@/shared/ui/guide-primitives';

/**
 * SCM 가이드 화면 (TASK-PC-FE-188). 순수 정적 참조 화면 — scm-platform 도메인
 * 서비스 구성과, 콘솔 3개 운영 화면(개요=발주+재고 가시성 · 보충 · 설정)이
 * 보여주는 값의 의미·상태머신을 한 화면에서 설명한다. 데이터 페치·권한 게이트
 * 없음(server component, no 'use client'): 가이드는 콘솔 진입자 누구나 열람 가능.
 * IAM 가이드(IamGuideScreen) · WMS 가이드(WmsGuideScreen) · E-Commerce 가이드
 * (EcommerceGuideScreen)와 동일 패턴.
 */

const SECTIONS = [
  { id: 'scm-guide-services', label: '도메인 서비스' },
  { id: 'scm-guide-procurement', label: '발주' },
  { id: 'scm-guide-visibility', label: '재고 가시성' },
  { id: 'scm-guide-replenishment', label: '보충 추천' },
  { id: 'scm-guide-config', label: '설정' },
  { id: 'scm-guide-roles', label: '참고: SCM 도메인 롤 · 단일테넌트' },
];

/** 설정 필드 표(재주문 정책 · 공급사 매핑 공용). */
function ConfigFieldTable({
  fields,
  testid,
}: {
  fields: ConfigField[];
  testid: string;
}) {
  return (
    <div className="mb-6 overflow-x-auto">
      <table className="data-table" data-testid={testid}>
        <caption className="sr-only">설정 필드</caption>
        <thead>
          <tr className="text-left">
            <th scope="col" className="p-2">
              필드
            </th>
            <th scope="col" className="p-2">
              의미
            </th>
          </tr>
        </thead>
        <tbody>
          {fields.map((f) => (
            <tr
              key={f.key}
              data-testid={`${testid}-${f.key}`}
              className="border-b border-border"
            >
              <StateTh label={f.label} name={f.field} />
              <td className="p-2 text-sm text-muted-foreground">{f.desc}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function ScmGuideScreen() {
  return (
    <section aria-labelledby="scm-guide-heading" data-testid="scm-guide">
      <h1 id="scm-guide-heading" className="mb-2 text-2xl font-semibold">
        SCM 가이드
      </h1>
      <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
        SCM 콘솔은 <strong>개요(발주 · 재고 가시성) · 보충 · 설정</strong> 3개
        화면으로 구성됩니다. 아래는 각 화면이 보여주는 값의 의미와 상태머신, 그
        뒤의 scm-platform 마이크로서비스 구성, 그리고 저재고 알림에서 발주까지
        이어지는 보충 루프를 정리한 참조입니다. (SCM 은 단일테넌트 도메인이며,
        접근은 맨 아래 롤 참조.)
      </p>

      <GuideToc items={SECTIONS} />

      {/* ───────────────── 도메인 서비스 맵 ───────────────── */}
      <h2
        id="scm-guide-services"
        data-testid="scm-guide-services"
        className="mb-2 text-xl font-semibold"
      >
        도메인 서비스
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        SCM 은 단일 엣지 게이트웨이 뒤의 3개 producer 로 이루어진 이벤트 기반
        시스템입니다. 콘솔은 게이트웨이를 경유해 이들의 운영자 API 를 호출해
        화면을 렌더합니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="scm-guide-services-table">
          <caption className="sr-only">SCM 도메인 서비스</caption>
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
                data-testid={`scm-guide-service-${s.key}`}
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

      {/* ───────────────── 발주 ───────────────── */}
      <h2
        id="scm-guide-procurement"
        data-testid="scm-guide-procurement"
        className="mb-2 text-xl font-semibold"
      >
        발주 (조달)
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>개요</strong> 화면(<Mono>/scm</Mono>)의 발주(PO)는 아래 9-상태
        생명주기를 따릅니다. 정상 경로는{' '}
        <Mono>초안 → 제출 → 접수 → 확정 → 부분입고 → 입고 → 정산</Mono> 이며,
        종료로 마감 · 취소가 있습니다. <strong>콘솔의 발주 목록은 읽기 전용</strong>
        이라는 점에 유의하세요.
      </p>
      <StateFlow states={PO_STATES} />
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="scm-guide-po-states">
          <caption className="sr-only">발주 상태</caption>
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
            {PO_STATES.map((s) => (
              <tr
                key={s.name}
                data-testid={`scm-guide-po-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm">
                  <TerminalCell terminal={s.terminal} inProgressLabel="진행" />
                </td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard title={PO_NOTE.title} body={PO_NOTE.body} />

      {/* ───────────────── 재고 가시성 ───────────────── */}
      <h2
        id="scm-guide-visibility"
        data-testid="scm-guide-visibility"
        className="mb-2 text-xl font-semibold"
      >
        재고 가시성
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>개요</strong> 화면의 재고 스냅샷(<Mono>inventory-visibility</Mono>)
        은 다중 노드(창고/매장) 재고를 이벤트로 투영한 교차 조회입니다. 노드별{' '}
        <strong>신선도(staleness)</strong>로 데이터 지연을 구분하며, 응답에는 항상
        S5 경고가 붙습니다.
      </p>

      <NoteCard title={S5_NOTE.title} body={S5_NOTE.body} />

      <div className="mb-6 overflow-x-auto">
        <table className="data-table" data-testid="scm-guide-staleness-states">
          <caption className="sr-only">노드 신선도 상태</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                신선도
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {STALENESS_STATES.map((s) => (
              <tr
                key={s.name}
                data-testid={`scm-guide-staleness-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard title={NODE_NOTE.title} body={NODE_NOTE.body} />

      {/* ───────────────── 보충 추천 ───────────────── */}
      <h2
        id="scm-guide-replenishment"
        data-testid="scm-guide-replenishment"
        className="mb-2 text-xl font-semibold"
      >
        보충 추천
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>보충</strong> 화면(<Mono>/scm/replenishment</Mono>)의 추천은 아래
        상태머신을 따릅니다. wms 저재고 알림이 <Mono>추천(SUGGESTED)</Mono>을
        만들고, 운영자가 승인하면 <Mono>물질화(MATERIALIZED)</Mono>되며 DRAFT
        발주가 생성됩니다. 승인·기각은 <strong>추천 · 승인</strong> 상태에서만
        가능합니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="scm-guide-suggestion-states">
          <caption className="sr-only">보충 추천 상태</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                운영자 작업
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
            {SUGGESTION_STATES.map((s) => (
              <tr
                key={s.name}
                data-testid={`scm-guide-suggestion-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm">
                  {s.operatorActionable ? (
                    <span className="text-green-600 dark:text-green-400">
                      승인 · 기각
                    </span>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )}
                </td>
                <td className="p-2 text-sm">
                  <TerminalCell terminal={s.terminal} inProgressLabel="진행" />
                </td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard
        title={REPLENISHMENT_LOOP_NOTE.title}
        body={REPLENISHMENT_LOOP_NOTE.body}
      />

      {/* ───────────────── 설정 ───────────────── */}
      <h2
        id="scm-guide-config"
        data-testid="scm-guide-config"
        className="mb-2 text-xl font-semibold"
      >
        설정
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>설정</strong> 화면(<Mono>/scm/config</Mono>)은 SKU 단위로 재주문
        정책과 공급사 매핑을 관리합니다. 이 두 설정이 보충 루프를 구동합니다 —
        정책의 재주문점이 추천을, 공급사 매핑이 DRAFT 발주를 만듭니다.
      </p>
      <h3 className="mb-3 text-lg font-medium">재주문 정책</h3>
      <ConfigFieldTable fields={POLICY_FIELDS} testid="scm-guide-policy-fields" />
      <h3 className="mb-3 text-lg font-medium">공급사 매핑</h3>
      <ConfigFieldTable
        fields={SUPPLIER_FIELDS}
        testid="scm-guide-supplier-fields"
      />

      <NoteCard title={CONFIG_NOTE.title} body={CONFIG_NOTE.body} />

      {/* ───────────────── 도메인 롤 ───────────────── */}
      <h2
        id="scm-guide-roles"
        className="mb-2 text-xl font-semibold"
      >
        참고: SCM 도메인 롤 · 단일테넌트
      </h2>
      <div data-testid="scm-guide-roles">
        <NoteCard title={SCM_ROLE_NOTE.title} body={SCM_ROLE_NOTE.body} />
      </div>
    </section>
  );
}
