import { Card } from '@/shared/ui/Card';
import {
  INVENTORY_EVENTS,
  LOW_STOCK_MECHANISMS,
  ORDER_STATES,
  READ_MODEL_NOTE,
  RESERVATION_STAGES,
  SAGA_NOTE,
  STOCK_BUCKETS,
  TMS_STATES,
  WMS_GLOSSARY,
  WMS_RECIPES,
  WMS_ROLES,
} from '../data';
import {
  Glossary,
  GuideRecipe,
  GuideToc,
  Mono,
  NoteCard,
  StateFlow,
} from '@/shared/ui/guide-primitives';

/**
 * WMS 가이드 화면 (TASK-PC-FE-183). 순수 정적 참조 화면 — 재고(수량 버킷·예약
 * 흐름·저재고·읽기모델)와 출고(주문 상태머신·TMS 통보·사가)의 의미를 한 화면에서
 * 설명한다. 데이터 페치·권한 게이트 없음(server component, no 'use client'):
 * 가이드는 콘솔 진입자 누구나 열람 가능. IAM 가이드(IamGuideScreen)와 동일 패턴.
 */

const SECTIONS = [
  { id: 'wms-guide-recipes', label: '자주 하는 작업' },
  { id: 'wms-guide-inventory', label: '재고' },
  { id: 'wms-guide-outbound', label: '출고' },
  { id: 'wms-guide-roles', label: '참고: WMS 도메인 롤' },
  { id: 'wms-guide-glossary', label: '용어집' },
];

export function WmsGuideScreen() {
  return (
    <section aria-labelledby="wms-guide-heading" data-testid="wms-guide">
      <h1 id="wms-guide-heading" className="mb-2 text-2xl font-semibold">
        WMS 가이드
      </h1>
      <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
        WMS 콘솔은 <strong>재고(재고 현황)</strong>와{' '}
        <strong>출고(출고 운영 · 택배/출고)</strong> 두 라이브 화면과 개요로
        구성됩니다. 아래는 각 화면이 보여주는 값의 의미 — 재고의 수량 버킷과 예약
        흐름, 출고 주문의 상태 변화 — 를 정리한 참조입니다. (모든 화면은 도메인
        롤로 게이트되며, 맨 아래 참조.)
      </p>

      <GuideToc items={SECTIONS} />

      {/* ───────────────── 자주 하는 작업 (레시피) ───────────────── */}
      <h2
        id="wms-guide-recipes"
        data-testid="wms-guide-recipes"
        className="mb-2 text-xl font-semibold"
      >
        자주 하는 작업
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        “이럴 땐 이렇게” — 각 단계는 재고·출고 화면의 실제 상태·작업만
        참조합니다.
      </p>
      <div className="mb-10">
        {WMS_RECIPES.map((recipe, i) => (
          <GuideRecipe
            key={recipe.title}
            recipe={recipe}
            testid={`wms-guide-recipe-${i}`}
          />
        ))}
      </div>

      {/* ───────────────── 재고 ───────────────── */}
      <h2
        id="wms-guide-inventory"
        data-testid="wms-guide-inventory"
        className="mb-2 text-xl font-semibold"
      >
        재고 (재고 현황)
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        <strong>재고</strong> 화면(<Mono>/wms/inventory</Mono>)은 위치·SKU·로트
        별 현재 수량을 보여줍니다. 핵심은 수량이 4개의 버킷으로 나뉜다는 점입니다.
      </p>

      {/* 수량 버킷 */}
      <h3 className="mb-3 text-lg font-medium">수량 버킷</h3>
      <div className="mb-4 overflow-x-auto">
        <table className="data-table" data-testid="wms-guide-buckets">
          <caption className="sr-only">재고 수량 버킷</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                버킷
              </th>
              <th scope="col" className="p-2">
                픽업 가능
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {STOCK_BUCKETS.map((b) => (
              <tr
                key={b.key}
                data-testid={`wms-guide-bucket-${b.key}`}
                className="border-b border-border"
              >
                <th scope="row" className="p-2 text-left">
                  <span className="font-medium text-foreground">{b.label}</span>
                  <span className="ml-2 font-mono text-[11px] text-muted-foreground">
                    {b.field}
                  </span>
                </th>
                <td className="p-2 text-sm">
                  {b.pickable ? (
                    <span className="text-green-600 dark:text-green-400">
                      가능
                    </span>
                  ) : (
                    <span className="text-muted-foreground">불가</span>
                  )}
                </td>
                <td className="p-2 text-sm text-muted-foreground">{b.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mb-10 text-sm text-muted-foreground">
        불변식: <Mono>보유 = 가용 + 예약 + 손상</Mono> — 보유(on-hand)는 저장값이
        아니라 세 버킷의 합입니다. 모든 버킷은 0 이상이며, 음수로 만드는 연산은{' '}
        <Mono>INSUFFICIENT_STOCK</Mono> 으로 거부됩니다. 손상은 물리적으로
        보유에는 포함되지만 픽업 대상이 아닙니다.
      </p>

      {/* 예약 흐름 */}
      <h3 className="mb-2 text-lg font-medium">예약 흐름 (가용 ↔ 예약)</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        출고가 시작되면 재고가 <strong>가용 → 예약</strong>으로 옮겨가고, 출고
        확정 시 예약이 소진되거나(확정) 취소/만료 시 가용으로 돌아옵니다(해제).
        예약 상태는 <Mono>RESERVED → CONFIRMED</Mono> 또는{' '}
        <Mono>RESERVED → RELEASED</Mono> 로만 진행합니다(재활성 없음).
      </p>
      <ol className="mb-10 space-y-3">
        {RESERVATION_STAGES.map((s, i) => (
          <li
            key={s.step}
            className="flex gap-3"
            data-testid={`wms-guide-reservation-${i}`}
          >
            <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-foreground">
              {i + 1}
            </span>
            <div>
              <p className="text-sm font-medium text-foreground">{s.step}</p>
              <p className="text-sm text-muted-foreground">{s.trigger}</p>
              <p className="mt-0.5 text-sm text-foreground">{s.effect}</p>
            </div>
          </li>
        ))}
      </ol>

      {/* 저재고 */}
      <h3 className="mb-2 text-lg font-medium">저재고 (두 가지 의미)</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        “저재고”는 <strong>서로 다른 두 메커니즘</strong>이며 임계값이 달라 서로
        불일치할 수 있습니다. 재고 테이블의 배지와 운영자 알림을 혼동하지 마세요.
      </p>
      <div className="mb-10 grid gap-4 md:grid-cols-2">
        {LOW_STOCK_MECHANISMS.map((m, i) => (
          <Card key={m.where} data-testid={`wms-guide-lowstock-${i}`}>
            <p className="mb-1 text-sm font-medium text-foreground">
              {m.where}
            </p>
            <p className="mb-2">
              <Mono>{m.threshold}</Mono>
            </p>
            <p className="text-sm text-muted-foreground">{m.desc}</p>
          </Card>
        ))}
      </div>

      {/* 재고 변동 이벤트 */}
      <h3 className="mb-3 text-lg font-medium">재고 변동</h3>
      <div className="mb-8 overflow-x-auto">
        <table className="data-table" data-testid="wms-guide-inventory-events">
          <caption className="sr-only">재고 변동 이벤트</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                변동
              </th>
              <th scope="col" className="p-2">
                이벤트
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {INVENTORY_EVENTS.map((e) => (
              <tr
                key={e.event}
                data-testid={`wms-guide-invevent-${e.event}`}
                className="border-b border-border"
              >
                <td className="p-2 text-sm font-medium text-foreground">
                  {e.label}
                </td>
                <td className="p-2">
                  <Mono>{e.event}</Mono>
                </td>
                <td className="p-2 text-sm text-muted-foreground">{e.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard title={READ_MODEL_NOTE.title} body={READ_MODEL_NOTE.body} />

      {/* ───────────────── 출고 ───────────────── */}
      <h2
        id="wms-guide-outbound"
        data-testid="wms-guide-outbound"
        className="mb-2 text-xl font-semibold"
      >
        출고
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        <strong>출고</strong> 화면(<Mono>/wms/outbound</Mono>)은 출고 운영(주문을
        피킹→패킹→출고 확정)과 그 결과인 택배/출고 조회를 함께 보여줍니다. 주문은
        아래 상태머신을 따라 진행합니다.
      </p>

      {/* 주문 상태머신 */}
      <h3 className="mb-2 text-lg font-medium">주문 상태</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        정상 경로 6단계 —{' '}
        <Mono>접수 → 피킹중 → 피킹완료 → 패킹중 → 패킹완료 → 출고완료</Mono> — 와
        예외 종료 2개(취소 · 재고부족 이월)로 구성됩니다. 피킹·패킹은 각각
        “진행 중”과 “완료” 두 단계로 나뉩니다.
      </p>
      <StateFlow states={ORDER_STATES} />
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="wms-guide-order-states">
          <caption className="sr-only">출고 주문 상태</caption>
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
            {ORDER_STATES.map((s) => (
              <tr
                key={s.name}
                data-testid={`wms-guide-order-${s.name}`}
                className="border-b border-border"
              >
                <th scope="row" className="p-2 text-left">
                  <span className="font-medium text-foreground">{s.label}</span>
                  <span className="ml-2 font-mono text-[11px] text-muted-foreground">
                    {s.name}
                  </span>
                </th>
                <td className="p-2 text-sm">
                  {s.terminal ? (
                    <span
                      className="text-foreground"
                      aria-label="종료 상태"
                      title="종료 상태"
                    >
                      ●
                    </span>
                  ) : (
                    <span className="text-muted-foreground" aria-label="진행">
                      —
                    </span>
                  )}
                </td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* TMS 통보 상태 */}
      <h3 className="mb-2 text-lg font-medium">택배/출고 · 운송사(TMS) 통보</h3>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        출고가 확정(<Mono>출고완료</Mono>)되면 화물이 생성되고 택배/출고 표에
        나타납니다. 이후 운송사(TMS)에 통보되는 별도 상태가 붙습니다.
      </p>
      <div className="mb-8 overflow-x-auto">
        <table className="data-table" data-testid="wms-guide-tms-states">
          <caption className="sr-only">TMS 통보 상태</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {TMS_STATES.map((t) => (
              <tr
                key={t.name}
                data-testid={`wms-guide-tms-${t.name}`}
                className="border-b border-border"
              >
                <th scope="row" className="p-2 text-left">
                  <span className="font-medium text-foreground">{t.label}</span>
                  <span className="ml-2 font-mono text-[11px] text-muted-foreground">
                    {t.name}
                  </span>
                </th>
                <td className="p-2 text-sm text-muted-foreground">{t.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard title={SAGA_NOTE.title} body={SAGA_NOTE.body} />

      {/* ───────────────── 도메인 롤 ───────────────── */}
      <h2
        id="wms-guide-roles"
        className="mb-2 text-xl font-semibold"
      >
        참고: WMS 도메인 롤
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        재고·출고 화면은 <strong>WMS 도메인 롤</strong>로 게이트됩니다. 운영자가
        wms 구독 테넌트로 <strong>테넌트 선택(assume-tenant)</strong> 할 때
        자동으로 파생되어 주입됩니다. (IAM 콘솔 3화면을 게이트하는 admin-console
        역할과는 별도 축 — IAM 가이드 참조.)
      </p>
      <div className="overflow-x-auto">
        <table className="data-table" data-testid="wms-guide-roles">
          <caption className="sr-only">WMS 도메인 롤</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                롤
              </th>
              <th scope="col" className="p-2">
                대상
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {WMS_ROLES.map((r) => (
              <tr
                key={r.role}
                data-testid={`wms-guide-role-${r.role}`}
                className="border-b border-border"
              >
                <td className="p-2">
                  <Mono>{r.role}</Mono>
                </td>
                <td className="p-2 text-sm text-foreground">{r.surface}</td>
                <td className="p-2 text-sm text-muted-foreground">{r.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ───────────────── 용어집 ───────────────── */}
      <h2
        id="wms-guide-glossary"
        data-testid="wms-guide-glossary"
        className="mb-2 mt-10 text-xl font-semibold"
      >
        용어집
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        이 화면에 나오는 낯선 용어의 뜻입니다.
      </p>
      <Glossary entries={WMS_GLOSSARY} testid="wms-guide-glossary-table" />
    </section>
  );
}
