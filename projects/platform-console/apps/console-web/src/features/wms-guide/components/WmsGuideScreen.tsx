import Link from 'next/link';
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
  GuideReadingPath,
  GuideRecipe,
  Mono,
  NoteCard,
  StateFlow,
} from '@/shared/ui/guide-primitives';
import { DomainGuideTabs } from '@/shared/guide/DomainGuideTabs';
import { DomainFeatureSummary } from '@/shared/guide/DomainFeatureSummary';
import { domainFeatureByKey } from '@/shared/guide/domain-features';

/**
 * WMS 가이드 화면 (TASK-PC-FE-183). 순수 정적 참조 화면 — 재고(수량 버킷·예약
 * 흐름·저재고·읽기모델)와 출고(주문 상태머신·TMS 통보·사가)의 의미를 한 화면에서
 * 설명한다. 데이터 페치·권한 게이트 없음(server component, no 'use client'):
 * 가이드는 콘솔 진입자 누구나 열람 가능. IAM 가이드(IamGuideScreen)와 동일 패턴.
 *
 * TASK-PC-FE-298 — 기존 섹션을 **그대로 옮겨** 8개 탭(`DomainGuideTabs`)으로 재구성했다
 * (재작성 아님 — `git diff -w` 로 보면 이동만 보인다). 페이지 내 목차(GuideToc)는 탭
 * 목록이 대신한다. 「메뉴별 설명」·「메뉴별 사용 절차」는 권한·기능 매핑 표에서 생성된다.
 */

export function WmsGuideScreen() {
  return (
    <section aria-labelledby="wms-guide-heading" data-testid="wms-guide">
      <h1 id="wms-guide-heading" className="mb-2 text-2xl font-semibold">
        WMS 가이드
      </h1>
      <DomainGuideTabs
        prefix="wms-guide"
        areas={['wms']}
        domainLabel="WMS"
        panels={{
          overview: (
            <>
              <DomainFeatureSummary
                domain={domainFeatureByKey('wms')}
                testid="wms-guide-domain-features"
              />
              <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
                WMS 콘솔에는 <strong>입고 · 재고 · 출고 · 마스터 · 운영설정</strong> 5개
                화면과 개요가 있습니다. 이 가이드는 그중{' '}
                <strong>재고</strong> 화면과 <strong>출고</strong> 화면에 나오는 숫자와
                상태의 뜻을 설명합니다. 화면을 보려면 알맞은 권한이 필요합니다 — 자세한
                내용은 「권한 안내」 탭에서 확인하세요.
              </p>
              <GuideReadingPath testid="wms-guide-reading-path">
                처음이라면 「공통 정의 및 용어」의 <strong>재고</strong>와 「대표 업무
                흐름」의 <strong>출고</strong>부터 읽으세요. 권한과 용어 설명은 필요할 때
                찾아보면 됩니다.
              </GuideReadingPath>
            </>
          ),
          terms: (
            <>
              {/* ───────────────── 재고 ───────────────── */}
              <h2
                id="wms-guide-inventory"
                data-testid="wms-guide-inventory"
                className="mb-2 text-xl font-semibold"
              >
                재고 (재고 현황)
              </h2>
              <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
                <strong>재고</strong> 화면은 창고 위치·상품(SKU)·로트별로 현재 수량을
                보여줍니다. 수량은 아래 4개 항목으로 나뉩니다.
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
                <Mono>보유 = 가용 + 예약 + 손상</Mono> — 보유는 따로 저장하는 값이 아니라
                이 세 수량을 더한 값입니다. 수량은 0보다 작아질 수 없어서, 가진 것보다
                많이 빼려고 하면 처리가 거부됩니다. 손상 재고는 창고에 실제로 있지만
                출고에는 쓸 수 없습니다.
              </p>
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
            </>
          ),
          usage: (
            <>
              {/* ───────────────── 자주 하는 작업 (레시피) ───────────────── */}
              <h2
                id="wms-guide-recipes"
                data-testid="wms-guide-recipes"
                className="mb-2 text-xl font-semibold"
              >
                자주 하는 작업
              </h2>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                자주 생기는 상황과 처리 방법을 정리했습니다.
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
              {/* 저재고 */}
              <h3 className="mb-2 text-lg font-medium">저재고 (두 가지 의미)</h3>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                “저재고”를 알려주는 방법은 <strong>두 가지</strong>이고, 기준이 서로 달라
                결과가 다르게 보일 수 있습니다. 재고 표의 배지와 운영자 알림을 헷갈리지
                마세요.
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
              <NoteCard title={READ_MODEL_NOTE.title} body={READ_MODEL_NOTE.body} />
            </>
          ),
          permissions: (
            <>
              {/* ───────────────── 도메인 롤 ───────────────── */}
              <h2
                id="wms-guide-roles"
                className="mb-2 text-xl font-semibold"
              >
                WMS 도메인 롤
              </h2>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                재고·출고 화면을 보거나 쓰려면 <strong>WMS 권한(롤)</strong>이 있어야
                합니다. 이 권한은 운영자가 WMS를 쓰는 회사(테넌트)를 선택하면 자동으로
                주어집니다. IAM 관리 화면의 권한과는 다른 종류입니다 — 자세한 내용은{' '}
                <Link
                  href="/iam/guide"
                  data-testid="wms-guide-xlink-iam"
                  className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  IAM 가이드
                </Link>
                를 참고하세요.
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
            </>
          ),
          flows: (
            <>
              {/* 예약 흐름 */}
              <h3 className="mb-2 text-lg font-medium">예약 흐름 (가용 ↔ 예약)</h3>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                출고가 시작되면 재고가 <strong>가용 → 예약</strong>으로 옮겨갑니다. 출고가
                끝나면 예약이 사라지고(확정), 취소되거나 시간이 지나면 다시 가용으로
                돌아갑니다(해제). 한 번 확정되거나 해제된 예약은 되돌리지 않습니다.
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
              {/* ───────────────── 출고 ───────────────── */}
              <h2
                id="wms-guide-outbound"
                data-testid="wms-guide-outbound"
                className="mb-2 text-xl font-semibold"
              >
                출고
              </h2>
              <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
                <strong>출고</strong> 화면은 주문을 꺼내기(피킹)→포장(패킹)→출고 확정
                순서로 처리하고, 처리된 결과를 택배 목록에서 보여줍니다. 주문은 아래
                순서대로 진행됩니다.
              </p>

              {/* 주문 상태머신 */}
              <h3 className="mb-2 text-lg font-medium">주문 상태</h3>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                정상적으로는{' '}
                <Mono>접수 → 피킹중 → 피킹완료 → 패킹중 → 패킹완료 → 출고완료</Mono> 순서로
                진행합니다. 중간에 <strong>취소</strong>되거나 재고가 모자라{' '}
                <strong>재고부족 이월</strong>로 끝날 수도 있습니다.
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
                출고가 끝나면(<Mono>출고완료</Mono>) 택배 목록에 나타나고, 운송사에
                전달하는 과정이 별도로 진행됩니다.
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
            </>
          ),
          services: null,
        }}
      />
    </section>
  );
}
