import {
  CONFIG_NOTE,
  DOMAIN_SERVICES,
  NODE_NOTE,
  PO_NOTE,
  PO_STATES,
  POLICY_FIELDS,
  REPLENISHMENT_LOOP_NOTE,
  S5_NOTE,
  SCM_GLOSSARY,
  SCM_RECIPES,
  SCM_ROLE_NOTE,
  STALENESS_STATES,
  SUGGESTION_STATES,
  SUPPLIER_FIELDS,
  type ConfigField,
} from '../data';
import Link from 'next/link';
import {
  Glossary,
  GuideReadingPath,
  GuideRecipe,
  Mono,
  NoteCard,
  StateFlow,
  StateTh,
  TerminalCell,
} from '@/shared/ui/guide-primitives';
import { DomainGuideTabs } from '@/shared/guide/DomainGuideTabs';
import { DomainFeatureSummary } from '@/shared/guide/DomainFeatureSummary';
import { domainFeatureByKey } from '@/shared/guide/domain-features';

/**
 * SCM 가이드 화면 (TASK-PC-FE-188). 순수 정적 참조 화면 — scm-platform 도메인
 * 서비스 구성과, 콘솔 3개 운영 화면(개요=발주+재고 가시성 · 보충 · 설정)이
 * 보여주는 값의 의미·상태머신을 한 화면에서 설명한다. 데이터 페치·권한 게이트
 * 없음(server component, no 'use client'): 가이드는 콘솔 진입자 누구나 열람 가능.
 * IAM 가이드(IamGuideScreen) · WMS 가이드(WmsGuideScreen) · E-Commerce 가이드
 * (EcommerceGuideScreen)와 동일 패턴.
 */

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
      <DomainGuideTabs
        prefix="scm-guide"
        areas={['scm']}
        domainLabel="SCM"
        panels={{
          overview: (
            <>
              <DomainFeatureSummary
                domain={domainFeatureByKey('scm')}
                testid="scm-guide-domain-features"
              />
              <p className="mb-10 max-w-3xl text-sm text-muted-foreground">
                SCM 콘솔은 <strong>개요 · 조달 · 재고 · 보충 계획 · 보충 계획 설정</strong>{' '}
                5개 화면으로 이루어져 있습니다. 각 탭에서 화면에 나오는 값의 의미와 상태,
                그리고 재고 부족 알림부터 발주까지 이어지는 흐름을 설명합니다. 권한에
                관한 내용은 「권한 안내」 탭을 참고하세요.
              </p>
              <GuideReadingPath testid="scm-guide-reading-path">
                처음이라면 「대표 업무 흐름」 탭의 <strong>발주</strong>와 <strong>보충 추천</strong>부터
                보세요 — 재고 부족 알림에서 발주까지 이어지는 흐름이 SCM 의 핵심입니다.
                나머지 탭은 필요할 때 찾아보면 됩니다.
              </GuideReadingPath>
            </>
          ),
          terms: (
            <>
              {/* ───────────────── 재고 가시성 ───────────────── */}
              <h2
                id="scm-guide-visibility"
                data-testid="scm-guide-visibility"
                className="mb-2 text-xl font-semibold"
              >
                재고 가시성
              </h2>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                <strong>개요</strong> 화면의 재고 스냅샷은 여러 창고 · 매장(노드)의 재고를
                모아 보여줍니다. 노드별 <strong>신선도(staleness)</strong>로 정보가 얼마나
                최신인지 알 수 있고, 항상 S5 경고가 함께 표시됩니다.
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
              {/* ───────────────── 용어집 ───────────────── */}
              <h2
                id="scm-guide-glossary"
                data-testid="scm-guide-glossary"
                className="mb-2 mt-10 text-xl font-semibold"
              >
                용어집
              </h2>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                이 화면에 나오는 낯선 용어의 뜻입니다.
              </p>
              <Glossary entries={SCM_GLOSSARY} testid="scm-guide-glossary-table" />
            </>
          ),
          usage: (
            <>
              {/* ───────────────── 자주 하는 작업 (레시피) ───────────────── */}
              <h2
                id="scm-guide-recipes"
                data-testid="scm-guide-recipes"
                className="mb-2 text-xl font-semibold"
              >
                자주 하는 작업
              </h2>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                “이럴 땐 이렇게” — 보충 · 설정 · 재고 화면의 실제 상태와 버튼만 기준으로
                설명합니다.
              </p>
              <div className="mb-10">
                {SCM_RECIPES.map((recipe, i) => (
                  <GuideRecipe
                    key={recipe.title}
                    recipe={recipe}
                    testid={`scm-guide-recipe-${i}`}
                  />
                ))}
              </div>
              {/* ───────────────── 설정 ───────────────── */}
              <h2
                id="scm-guide-config"
                data-testid="scm-guide-config"
                className="mb-2 text-xl font-semibold"
              >
                설정
              </h2>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                <strong>설정</strong> 화면(<Mono>/scm/config</Mono>)은 상품(SKU) 단위로
                재주문 정책과 공급사 매핑을 관리합니다. 재주문점은 추천을 만드는 기준이
                되고, 공급사 정보는 발주 초안을 만드는 데 쓰입니다.
              </p>
              <h3 className="mb-3 text-lg font-medium">재주문 정책</h3>
              <ConfigFieldTable fields={POLICY_FIELDS} testid="scm-guide-policy-fields" />
              <h3 className="mb-3 text-lg font-medium">공급사 매핑</h3>
              <ConfigFieldTable
                fields={SUPPLIER_FIELDS}
                testid="scm-guide-supplier-fields"
              />

              <NoteCard title={CONFIG_NOTE.title} body={CONFIG_NOTE.body} />
            </>
          ),
          permissions: (
            <>
              {/* ───────────────── 도메인 롤 ───────────────── */}
              <h2
                id="scm-guide-roles"
                className="mb-2 text-xl font-semibold"
              >
                SCM 권한 안내
              </h2>
              <div data-testid="scm-guide-roles">
                <NoteCard title={SCM_ROLE_NOTE.title} body={SCM_ROLE_NOTE.body} />
              </div>
            </>
          ),
          flows: (
            <>
              {/* ───────────────── 발주 ───────────────── */}
              <h2
                id="scm-guide-procurement"
                data-testid="scm-guide-procurement"
                className="mb-2 text-xl font-semibold"
              >
                발주 (조달)
              </h2>
              <p className="mb-4 max-w-3xl text-sm text-muted-foreground">
                <strong>개요</strong> 화면(<Mono>/scm</Mono>)의 발주(PO)는 아래 상태를
                거칩니다. 정상적으로는{' '}
                <Mono>초안 → 제출 → 접수 → 확정 → 부분입고 → 입고 → 정산</Mono> 순서로
                진행되고, 마감 · 취소로 끝나는 경우도 있습니다.{' '}
                <strong>콘솔에서는 발주 목록을 조회만 할 수 있습니다.</strong>
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
                상태를 거칩니다.{' '}
                <Link
                  href="/wms/guide"
                  data-testid="scm-guide-xlink-wms"
                  className="underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  창고의 재고 부족 알림
                </Link>
                이 <Mono>추천(SUGGESTED)</Mono>을 만들고, 운영자가 승인하면{' '}
                <Mono>물질화(MATERIALIZED)</Mono> 상태가 되며 발주 초안이
                만들어집니다. 승인과 기각은 <strong>추천 · 승인</strong> 상태에서만
                할 수 있습니다.
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
            </>
          ),
          services: (
            <>
              {/* ───────────────── 도메인 서비스 맵 ───────────────── */}
              <h2
                id="scm-guide-services"
                data-testid="scm-guide-services"
                className="mb-2 text-xl font-semibold"
              >
                도메인 서비스
              </h2>
              <p className="mb-6 max-w-3xl text-sm text-muted-foreground">
                SCM 은 발주 · 재고 · 보충 계획을 각각 담당하는 서비스로 이루어져
                있습니다. 콘솔은 이 서비스들과 통신해 화면을 보여줍니다.
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
            </>
          ),
        }}
      />
    </section>
  );
}
