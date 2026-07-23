import { Card } from '@/shared/ui/Card';
import {
  COUPON_NOTE,
  DISCOUNT_TYPES,
  DOMAIN_SERVICES,
  ECOMMERCE_GLOSSARY,
  ECOMMERCE_RECIPES,
  ECOMMERCE_ROLE_NOTE,
  NOTIFICATION_CHANNELS,
  NOTIFICATION_NOTE,
  ORDER_LIFECYCLE_NOTE,
  ORDER_STATES,
  PAYMENT_STATES,
  PRODUCT_CONCEPTS,
  PRODUCT_STATES,
  PROMOTION_STATES,
  SELLER_STATES,
  SHIPPING_STATES,
  SHIPPING_WMS_NOTE,
  TEMPLATE_TYPES,
  USER_NOTE,
  USER_STATES,
} from '../data';
import {
  Glossary,
  GuideRecipe,
  GuideToc,
  Mono,
  NoteCard,
  StateFlow,
  StateTh,
  TerminalCell,
} from '@/shared/ui/guide-primitives';

/**
 * E-Commerce 가이드 화면 (TASK-PC-FE-184). 순수 정적 참조 화면 — 도메인 서비스
 * 구성과, 콘솔 7개 운영 화면(상품·주문·배송·프로모션·사용자·셀러·알림)이 보여주는
 * 상태값의 의미·상태머신을 한 화면에서 설명한다. 데이터 페치·권한 게이트 없음
 * (server component, no 'use client'): 가이드는 콘솔 진입자 누구나 열람 가능.
 * IAM 가이드(IamGuideScreen)·WMS 가이드(WmsGuideScreen)와 동일 패턴.
 */

const SECTIONS = [
  { id: 'ecommerce-guide-recipes', label: '자주 하는 작업' },
  { id: 'ecommerce-guide-services', label: '도메인 서비스' },
  { id: 'ecommerce-guide-order', label: '주문' },
  { id: 'ecommerce-guide-payment', label: '결제' },
  { id: 'ecommerce-guide-shipping', label: '배송' },
  { id: 'ecommerce-guide-product', label: '상품' },
  { id: 'ecommerce-guide-promotion', label: '프로모션' },
  { id: 'ecommerce-guide-seller', label: '셀러' },
  { id: 'ecommerce-guide-user', label: '사용자' },
  { id: 'ecommerce-guide-notification', label: '알림' },
  { id: 'ecommerce-guide-roles', label: '참고: E-Commerce 도메인 롤' },
  { id: 'ecommerce-guide-glossary', label: '용어집' },
];

export function EcommerceGuideScreen() {
  return (
    <section aria-labelledby="ecommerce-guide-heading" data-testid="ecommerce-guide">
      <h1 id="ecommerce-guide-heading" className="mb-2 text-2xl font-semibold">
        E-Commerce 가이드
      </h1>
      <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
        E-Commerce 콘솔은 <strong>상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 ·
        알림</strong> 7개 라이브 운영 화면과 개요로 구성됩니다. 아래는 각 화면이
        보여주는 상태값의 의미와, 그 뒤의 이커머스 마이크로서비스 구성을 정리한
        참조입니다. (모든 화면은 도메인 롤로 게이트되며, 맨 아래 참조.)
      </p>

      <GuideToc items={SECTIONS} />

      {/* ───────────────── 자주 하는 작업 (레시피) ───────────────── */}
      <h2
        id="ecommerce-guide-recipes"
        data-testid="ecommerce-guide-recipes"
        className="mb-2 text-xl font-semibold"
      >
        자주 하는 작업
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        “이럴 땐 이렇게” — 각 단계는 주문·배송·셀러 화면의 실제 상태·작업만
        참조합니다.
      </p>
      <div className="mb-10">
        {ECOMMERCE_RECIPES.map((recipe, i) => (
          <GuideRecipe
            key={recipe.title}
            recipe={recipe}
            testid={`ecommerce-guide-recipe-${i}`}
          />
        ))}
      </div>

      {/* ───────────────── 도메인 서비스 맵 ───────────────── */}
      <h2
        id="ecommerce-guide-services"
        data-testid="ecommerce-guide-services"
        className="mb-2 text-xl font-semibold"
      >
        도메인 서비스
      </h2>
      <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
        E-Commerce 는 이벤트로 협업하는 여러 마이크로서비스로 나뉩니다. 콘솔은 이
        중 7개 서비스의 운영자 API 를 호출해 화면을 렌더합니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="ecommerce-guide-services-table">
          <caption className="sr-only">이커머스 도메인 서비스</caption>
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
                data-testid={`ecommerce-guide-service-${s.key}`}
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

      {/* ───────────────── 주문 ───────────────── */}
      <h2
        id="ecommerce-guide-order"
        data-testid="ecommerce-guide-order"
        className="mb-2 text-xl font-semibold"
      >
        주문
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>주문</strong> 화면(<Mono>/ecommerce/orders</Mono>)의 주문은 아래
        상태머신을 따릅니다. 정상 경로는{' '}
        <Mono>대기 → 확정 → 배송중 → 배송완료</Mono> 이며, 예외 종료로 취소 ·
        복구실패가 있습니다. <strong>운영자가 바꿀 수 있는 상태</strong>와{' '}
        <strong>이벤트가 구동하는 읽기 전용 상태</strong>를 구분하세요 —
        배송중/배송완료는 배송 서비스가 구동하며 운영자가 직접 못 바꿉니다.
      </p>
      <StateFlow states={ORDER_STATES} />
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="ecommerce-guide-order-states">
          <caption className="sr-only">주문 상태</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                운영자 변경
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
                data-testid={`ecommerce-guide-order-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm">
                  {s.operatorActionable ? (
                    <span className="text-green-600 dark:text-green-400">
                      가능
                    </span>
                  ) : (
                    <span className="text-muted-foreground">불가</span>
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
        title={ORDER_LIFECYCLE_NOTE.title}
        body={ORDER_LIFECYCLE_NOTE.body}
      />

      {/* ───────────────── 결제 ───────────────── */}
      <h2
        id="ecommerce-guide-payment"
        data-testid="ecommerce-guide-payment"
        className="mb-2 text-xl font-semibold"
      >
        결제
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        결제는 Toss Payments PG 로 처리되며 <strong>전용 콘솔 화면은 없습니다</strong>{' '}
        — 결제 결과는 주문 상태(확정/취소)로 간접 노출됩니다. 상태머신은 아래와
        같습니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="ecommerce-guide-payment-states">
          <caption className="sr-only">결제 상태</caption>
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
            {PAYMENT_STATES.map((p) => (
              <tr
                key={p.name}
                data-testid={`ecommerce-guide-payment-${p.name}`}
                className="border-b border-border"
              >
                <StateTh label={p.label} name={p.name} />
                <td className="p-2 text-sm text-muted-foreground">{p.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ───────────────── 배송 ───────────────── */}
      <h2
        id="ecommerce-guide-shipping"
        data-testid="ecommerce-guide-shipping"
        className="mb-2 text-xl font-semibold"
      >
        배송
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>배송</strong> 화면(<Mono>/ecommerce/shippings</Mono>)의 배송은{' '}
        <strong>엄격 선형</strong> 상태머신입니다 — 각 상태의 후속은 하나뿐이라
        콘솔은 한 방향 전이만 노출합니다. <Mono>준비중 → 발송 → 배송중 → 배송완료</Mono>.
        발송 전이에는 운송사 + 운송장번호가 필수입니다.
      </p>
      <StateFlow states={SHIPPING_STATES} />
      <div className="mb-10 overflow-x-auto">
        <table
          className="data-table"
          data-testid="ecommerce-guide-shipping-states"
        >
          <caption className="sr-only">배송 상태</caption>
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
            {SHIPPING_STATES.map((s) => (
              <tr
                key={s.name}
                data-testid={`ecommerce-guide-shipping-${s.name}`}
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

      <NoteCard title={SHIPPING_WMS_NOTE.title} body={SHIPPING_WMS_NOTE.body} />

      {/* ───────────────── 상품 ───────────────── */}
      <h2
        id="ecommerce-guide-product"
        data-testid="ecommerce-guide-product"
        className="mb-2 text-xl font-semibold"
      >
        상품
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>상품</strong> 화면(<Mono>/ecommerce/products</Mono>)은 상품 마스터와
        옵션(variant)·재고를 다룹니다. 판매 상태는 3가지입니다.
      </p>
      <div className="mb-6 overflow-x-auto">
        <table className="data-table" data-testid="ecommerce-guide-product-states">
          <caption className="sr-only">상품 판매 상태</caption>
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
            {PRODUCT_STATES.map((p) => (
              <tr
                key={p.name}
                data-testid={`ecommerce-guide-product-${p.name}`}
                className="border-b border-border"
              >
                <StateTh label={p.label} name={p.name} />
                <td className="p-2 text-sm text-muted-foreground">{p.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="mb-10 grid gap-4 md:grid-cols-2">
        {PRODUCT_CONCEPTS.map((c) => (
          <Card key={c.key} data-testid={`ecommerce-guide-product-concept-${c.key}`}>
            <p className="mb-1 text-sm font-medium text-foreground">{c.term}</p>
            <p className="text-sm text-muted-foreground">{c.desc}</p>
          </Card>
        ))}
      </div>

      {/* ───────────────── 프로모션 ───────────────── */}
      <h2
        id="ecommerce-guide-promotion"
        data-testid="ecommerce-guide-promotion"
        className="mb-2 text-xl font-semibold"
      >
        프로모션
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>프로모션</strong> 화면(<Mono>/ecommerce/promotions</Mono>)의 상태는
        저장값이 아니라 시작/종료일과 현재시각으로 <strong>파생</strong>됩니다.
      </p>
      <div className="mb-6 overflow-x-auto">
        <table
          className="data-table"
          data-testid="ecommerce-guide-promotion-states"
        >
          <caption className="sr-only">프로모션 상태</caption>
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
            {PROMOTION_STATES.map((p) => (
              <tr
                key={p.name}
                data-testid={`ecommerce-guide-promotion-${p.name}`}
                className="border-b border-border"
              >
                <StateTh label={p.label} name={p.name} />
                <td className="p-2 text-sm text-muted-foreground">{p.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <h3 className="mb-3 text-lg font-medium">할인 종류</h3>
      <div className="mb-6 grid gap-4 md:grid-cols-2">
        {DISCOUNT_TYPES.map((d) => (
          <Card key={d.name} data-testid={`ecommerce-guide-discount-${d.name}`}>
            <p className="mb-1 text-sm font-medium text-foreground">
              {d.label} <Mono>{d.name}</Mono>
            </p>
            <p className="text-sm text-muted-foreground">{d.desc}</p>
          </Card>
        ))}
      </div>

      <NoteCard title={COUPON_NOTE.title} body={COUPON_NOTE.body} />

      {/* ───────────────── 셀러 ───────────────── */}
      <h2
        id="ecommerce-guide-seller"
        data-testid="ecommerce-guide-seller"
        className="mb-2 text-xl font-semibold"
      >
        셀러
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>셀러</strong> 화면(<Mono>/ecommerce/sellers</Mono>)의 셀러는
        수정/삭제가 없고 <strong>상태 전이</strong>로만 변합니다. 각 상태에서
        가능한 운영자 액션이 다릅니다.
      </p>
      <div className="mb-10 overflow-x-auto">
        <table className="data-table" data-testid="ecommerce-guide-seller-states">
          <caption className="sr-only">셀러 생명주기</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                가능 액션
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {SELLER_STATES.map((s) => (
              <tr
                key={s.name}
                data-testid={`ecommerce-guide-seller-${s.name}`}
                className="border-b border-border"
              >
                <StateTh label={s.label} name={s.name} />
                <td className="p-2 text-sm text-foreground">{s.actions}</td>
                <td className="p-2 text-sm text-muted-foreground">{s.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* ───────────────── 사용자 ───────────────── */}
      <h2
        id="ecommerce-guide-user"
        data-testid="ecommerce-guide-user"
        className="mb-2 text-xl font-semibold"
      >
        사용자
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>사용자</strong> 화면(<Mono>/ecommerce/users</Mono>)은 회원(고객)을
        조회합니다. 상태는 3가지입니다.
      </p>
      <div className="mb-6 overflow-x-auto">
        <table className="data-table" data-testid="ecommerce-guide-user-states">
          <caption className="sr-only">사용자 상태</caption>
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
            {USER_STATES.map((u) => (
              <tr
                key={u.name}
                data-testid={`ecommerce-guide-user-${u.name}`}
                className="border-b border-border"
              >
                <StateTh label={u.label} name={u.name} />
                <td className="p-2 text-sm text-muted-foreground">{u.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <NoteCard title={USER_NOTE.title} body={USER_NOTE.body} />

      {/* ───────────────── 알림 ───────────────── */}
      <h2
        id="ecommerce-guide-notification"
        data-testid="ecommerce-guide-notification"
        className="mb-2 text-xl font-semibold"
      >
        알림
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        <strong>알림</strong> 화면(<Mono>/ecommerce/notifications/templates</Mono>)은
        알림 템플릿을 관리합니다. 템플릿 타입은 이벤트 트리거에 대응하며, 각 채널로
        발송됩니다.
      </p>
      <h3 className="mb-3 text-lg font-medium">템플릿 타입</h3>
      <div className="mb-6 overflow-x-auto">
        <table className="data-table" data-testid="ecommerce-guide-template-types">
          <caption className="sr-only">알림 템플릿 타입</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className="p-2">
                타입
              </th>
              <th scope="col" className="p-2">
                의미
              </th>
            </tr>
          </thead>
          <tbody>
            {TEMPLATE_TYPES.map((t) => (
              <tr
                key={t.name}
                data-testid={`ecommerce-guide-template-${t.name}`}
                className="border-b border-border"
              >
                <StateTh label={t.label} name={t.name} />
                <td className="p-2 text-sm text-muted-foreground">{t.desc}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <h3 className="mb-3 text-lg font-medium">채널</h3>
      <ul
        className="mb-6 flex flex-wrap gap-2"
        data-testid="ecommerce-guide-channels"
      >
        {NOTIFICATION_CHANNELS.map((c) => (
          <li
            key={c.name}
            data-testid={`ecommerce-guide-channel-${c.name}`}
            className="rounded-md border border-border px-3 py-1.5 text-sm"
          >
            <span className="text-foreground">{c.label}</span>{' '}
            <span className="font-mono text-[11px] text-muted-foreground">
              {c.name}
            </span>
          </li>
        ))}
      </ul>

      <NoteCard title={NOTIFICATION_NOTE.title} body={NOTIFICATION_NOTE.body} />

      {/* ───────────────── 도메인 롤 ───────────────── */}
      <h2
        id="ecommerce-guide-roles"
        className="mb-2 text-xl font-semibold"
      >
        참고: E-Commerce 도메인 롤
      </h2>
      <div data-testid="ecommerce-guide-roles">
        <NoteCard
          title={ECOMMERCE_ROLE_NOTE.title}
          body={ECOMMERCE_ROLE_NOTE.body}
        />
      </div>

      {/* ───────────────── 용어집 ───────────────── */}
      <h2
        id="ecommerce-guide-glossary"
        data-testid="ecommerce-guide-glossary"
        className="mb-2 mt-10 text-xl font-semibold"
      >
        용어집
      </h2>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
        이 화면에 나오는 낯선 용어의 뜻입니다.
      </p>
      <Glossary
        entries={ECOMMERCE_GLOSSARY}
        testid="ecommerce-guide-glossary-table"
      />
    </section>
  );
}
