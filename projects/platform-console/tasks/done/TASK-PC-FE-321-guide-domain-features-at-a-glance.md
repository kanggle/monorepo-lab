# Task ID

TASK-PC-FE-321

# Status

done

# Title

콘솔 가이드에 «도메인 한눈에» — 도메인마다 «한 줄 설명 + 주요 기능 묶음» 을 데이터 하나에 두고, 전역 가이드와 6개 도메인 가이드 첫 탭이 같은 데이터를 보여준다. 기능 항목은 사이드바 메뉴에 연결하고, 메뉴가 사라지면 시험이 빨개진다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- guide

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 화면 문구 · 정적 데이터 · 대조 시험. 도메인 로직 없음. 대조(코드·사이드바로 사실 확인) 가 일의 대부분이다.
>
> 🔵 **소유자 결정(2026-10-08 UTC):** 대화에서 도메인별 주요 기능 요약(채팅 답변)을 보고 «콘솔 가이드를 이렇게 깔끔하게» → 구현자 추천(데이터 하나 · 두 곳 표시 · 메뉴 연결 가드 · 콘솔 밖 표시) → «추천대로 진행». 열린 질문 셋의 답 = 추천 기본값: ① 전역 + 도메인 가이드 **둘 다** 표시 ② 콘솔 밖 기능도 **«콘솔 밖» 표시를 붙여 포함** ③ 기존 «무엇부터 읽으세요» 안내는 요약 **아래에 남긴다**.

---

# Dependency Markers

- 선행/후속: 없음.
- 같은 시기: 다른 세션은 콘솔 코드를 건드리지 않음(2026-10-08 확인 — AMI 재굽기 대기).

# Background (기안 시 측정, `origin/main` `1c8e203aa`)

| 사실 | 근거 |
|---|---|
| 전역 가이드 절 7개, 그중 «도메인별 서비스 구성» 은 **서비스 이름 위주**(`consoleRole` 에 «admin-service 가 준다», «/api/v1/admin/** 를 거친다» 같은 구현 문장) | `apps/console-web/src/features/global-guide/components/GlobalGuideScreen.tsx:36-42` · `features/global-guide/data.ts:71-137` |
| 도메인 가이드 = 8탭(`DomainGuideTabs`), 첫 탭 «도메인 전체 설명» 은 각 화면이 직접 쓴 산문 | `shared/guide/DomainGuideTabs.tsx:35-44, 105` |
| 🔴 WMS 첫 탭이 «재고와 출고 **두** 라이브 화면» 이라고 한다 — 실제 사이드바는 입고 · 재고 · 출고 · 마스터 · 운영설정 | `features/wms-guide/components/WmsGuideScreen.tsx:50-56` · `shared/ui/console-nav-config.ts` WMS children |
| 도메인 가이드가 있는 도메인 6개: iam · wms · scm · finance · erp · ecommerce. **fan 은 가이드 없음**(플랫폼 운영자 전용 디렉터리 부모만 있음, `productKey: 'fan'`) | `features/*-guide/` · `console-nav-config.ts` |
| 사이드바 → 권한 지도 대조 가드 선례 | `tests/unit/permission-map-drift.test.ts` (`navLeaves()` 를 원장으로 양방향 대조) |
| 가이드는 정적 화면 — 방문자(샘플) 모드에서도 백엔드 호출 없이 보인다 | `shared/sample/coverage.ts` 의 가이드 `static` |
| 출발점 요약(채팅) 은 `docs/project-overview.md` 기반 — **그 문서는 갱신이 늦을 수 있다**(구현 용어 · 콘솔 밖 기능이 섞여 있다) | 대화 기록 |

# Goal

1. 운영자 · 포트폴리오 방문자가 가이드를 열면 **도메인이 무엇을 하는지** 한눈에 읽는다 — 도메인마다 한 줄 + 기능 묶음 4~6개.
2. 그 내용은 **한 곳**에만 쓰이고 두 화면(전역 · 도메인)이 같이 읽는다 — 한쪽만 고쳐지는 일이 구조적으로 없다.
3. 콘솔에 화면이 있는 기능은 그 메뉴로 가는 링크를 갖고, 메뉴가 사라지거나 옮겨지면 **시험이 빨개진다** — WMS 같은 낡은 문장이 다시 생기지 않는다.

# Scope

## In Scope

- **새 데이터** `apps/console-web/src/shared/guide/domain-features.ts` — 도메인마다
  `{ key, label, oneLine, groups: [{ title, items: [{ text, href?, outsideConsole? }] }] }`.
  - 도메인 7개: iam · wms · scm · finance · erp · ecommerce · fan(전역 가이드 전용).
  - `href` = 그 기능의 사이드바 메뉴 주소(있을 때만). `outsideConsole: true` = 콘솔 밖 기능(쇼핑몰 화면 web-store · 팬 커뮤니티 · 소셜 로그인 등) — 이 항목엔 `href` 가 없다.
- **표시 컴포넌트** 하나(예: `shared/guide/DomainFeatureSummary.tsx`) — 한 줄 + 묶음 제목(굵게) + 항목. 링크 항목은 메뉴로 이동, 콘솔 밖 항목엔 «콘솔 밖» 표시. 두 화면이 같은 컴포넌트를 쓴다.
- **전역 가이드**: 새 절 «도메인 한눈에» 를 절 목록 **맨 앞**(아키텍처 앞)에 추가, 7개 도메인 전부. 기존 절은 그대로(«도메인별 서비스 구성» 은 서비스 구성 참조로 남는다).
- **도메인 가이드 6개**(iam · wms · scm · finance · erp · ecommerce) 첫 탭 «도메인 전체 설명»: 맨 위에 그 도메인 요약, 그 **아래에** 기존 «무엇부터 읽으세요»(`GuideReadingPath`) 유지. 기존 첫 탭 산문 중 **사실과 다른 문장은 고치거나 지운다**(최소 WMS «두 라이브 화면»; 6개 전부 사이드바와 대조).
- **문구 규칙**: 독자는 운영자 · 방문자다. 구현 용어(saga · Elasticsearch · OIDC · Kafka · outbox · admin-service 같은 서비스 이름 · API 경로)는 요약에 쓰지 않는다 — 필요하면 «연결 서비스» 탭의 몫이다.
- **사실 대조**: 항목마다 «콘솔 사이드바 메뉴 · 그 화면 컴포넌트 · producer 계약 · 콘솔 밖이면 그 앱의 라우트» 중 하나로 확인하고, 묶음별 출처를 Implementation Record 에 표로 남긴다. 확인 못 한 항목은 넣지 않는다.
- **시험**(AC 참고).

## Out of Scope

- 가이드의 다른 탭(용어 · 사용 가이드 · 메뉴 · 절차 · 권한 · 흐름 · 서비스) 개편.
- `docs/project-overview.md` 수정(공유 경로 — 낡은 곳을 찾으면 Implementation Record 에 적기만).
- fan 도메인 가이드 신설.
- 사이드바 · 권한 지도 변경(읽기만).

# Acceptance Criteria

- [x] **AC-0** — 착수 측정: 6개 도메인 가이드 첫 탭 산문을 사이드바(`GROUPS`) 와 대조해 **사실과 다른 문장 목록**을 Implementation Record 에 적는다(WMS 포함, 없으면 «없음» 도 기록).
- [x] **AC-1** — `domain-features.ts` 에 7개 도메인, 도메인마다 `oneLine` 1개 + 묶음 4~6개, 묶음마다 항목 ≥ 1.
- [x] **AC-2** — 전역 가이드 맨 앞 절 «도메인 한눈에» 가 7개 도메인을 데이터 순서대로 렌더한다(단위 시험: 도메인 라벨 7개 + 데이터의 항목 문구가 화면에 있다).
- [x] **AC-3** — 6개 도메인 가이드 첫 탭 맨 위에 그 도메인 요약이 있고, 그 아래 기존 `GuideReadingPath` 가 남아 있다(단위 시험: 6개 각각).
- [x] **AC-4** — 🔴 **드리프트 가드**(새 시험, `permission-map-drift.test.ts` 와 같은 꼴 — 목록을 하드코딩하지 않고 `navLeaves()` 를 원장으로):
  - (a) 모든 `href` 가 `navLeaves()` 의 href 다(사이드바에 없는 링크 = 빨강).
  - (b) `outsideConsole: true` 항목엔 `href` 가 없다.
  - (c) 사이드바의 도메인 운영 부모(드릴 부모) 각각에 대응하는 도메인 요약이 있다(새 도메인이 사이드바에 생기면 빨강).
  - (d) 비공허: 링크 항목 수 ≥ 도메인 수(전부 콘솔 밖이 되는 퇴행 방지).
- [x] **AC-5** — 문구 가드: 요약 문구(`oneLine` · 묶음 제목 · 항목)에 금지어(`saga` · `Elasticsearch` · `OIDC` · `Kafka` · `outbox` · `-service` · `/api/`)가 없다(시험).
- [x] **AC-6** — bite: (a) 데이터의 `href` 하나를 없는 주소로 바꾸면 AC-4 시험이 그 칸만 빨강 (b) 금지어 하나를 넣으면 AC-5 가 빨강 — 둘 다 확인 후 되돌림을 기록.
- [x] **AC-7** — 사실 대조 표(묶음별 출처)가 Implementation Record 에 있고, 확인 못 해 뺀 항목이 있으면 그 목록도 있다.
- [x] **AC-8** — 기존 가이드 시험(`GlobalGuideScreen.test.tsx` · `IamGuideScreen.test.tsx` · 다른 `*GuideScreen` 시험 · axe 포함) 전부 통과. e2e(`tests/e2e` · `e2e-smoke`)가 가이드 첫 탭 문구 · testid 를 단언하는지 grep 하고 결과를 적는다.
- [x] **AC-9** — `tsc --noEmit` · `next lint` rc=0 · vitest 전량(부하 실패는 그 파일 단독 재실행 결과와 함께).
- [ ] **AC-10** — 라이브: 다음 데모 창에서 전역 가이드 · 도메인 가이드 첫 탭 눈 확인. ⚪ 허용.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/platform-console/specs/contracts/console-integration-contract.md` (도메인 목록)

# Related Contracts

- 없음 — 정적 화면. 백엔드 호출 추가 금지(방문자 모드 정적 유지).

# Edge Cases

- 한 기능이 메뉴 여러 개에 걸침(예: 결재 = 결재함 · 위임) → 대표 메뉴 하나에 링크하거나 항목을 나눈다. 링크는 **실재하는 leaf** 여야 한다(AC-4a).
- 플랫폼 운영자에게만 보이는 메뉴(fan 디렉터리, `productKey`)로의 링크 → 고객사 운영자에겐 그 메뉴가 안 보인다. 전역 가이드의 fan 항목은 링크 대신 «플랫폼 운영자 전용» 문구로.
- 요약과 «무엇부터 읽으세요» 가 같은 말을 반복 → 반복되는 문장은 안내 쪽에서 줄인다.

# Failure Scenarios

- `project-overview.md` 를 그대로 옮겨 낡은 사실이 화면에 실림 → AC-7 대조 표가 막는다(확인 못 하면 넣지 않는다).
- 요약을 화면마다 따로 써서 다시 갈라짐 → 데이터 하나 + 컴포넌트 하나(Scope), AC-2·AC-3 이 같은 데이터를 단언.
- 메뉴를 옮겼는데 가이드 링크가 죽음 → AC-4(a).

---

# Implementation Record

## AC-0 — 착수 측정: 6개 도메인 가이드 첫 탭 vs 사이드바(`GROUPS`) 대조

| 도메인 | 산문의 주장 | 사이드바 실제(`console-nav-config.ts` children, 가이드 제외) | 판정 |
|---|---|---|---|
| WMS | «재고(재고 현황)와 출고(출고 운영 · 택배/출고) **두** 라이브 화면과 개요로 구성» | 입고 · 재고 · 출고 · 마스터 · 운영설정 **5개** + 개요 | 🔴 **틀림** — 고쳤다(`WmsGuideScreen.tsx` overview panel, "5개 화면과 개요로 구성"). |
| SCM | «개요(발주 · 재고 가시성) · 보충 · 설정 **3개** 화면» | 개요 · 조달 · 재고 · 보충 계획 · 보충 계획 설정 **5개**(TASK-PC-FE-220 이 개요에서 조달/재고를 분리했는데 이 문장이 분리 전 상태로 남아 있었다) | 🔴 **틀림** — 고쳤다(`ScmGuideScreen.tsx`, "개요 · 조달 · 재고 · 보충 계획 · 보충 계획 설정 5개 화면"). |
| E-Commerce | «상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 · 알림 **7개** 라이브 운영 화면» | 상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 · **정산** · 알림 **8개**(정산이 누락됨, TASK-PC-FE-218 셀러 정산 화면 추가 이후 이 문장이 갱신되지 않았다) | 🔴 **틀림** — 고쳤다(`EcommerceGuideScreen.tsx`, "…셀러 · 정산 · 알림 8개 라이브 운영 화면"). |
| ERP | «개요 · 가이드 · 마스터 · 통합 조회 · 결재함 · 위임 **6개** 화면» | 가이드 · 개요 · 마스터 · 통합 조회 · 결재함 · 위임 = 6개 | ✅ 없음. |
| Finance | «개요 · 가이드 · 계좌 · 원장 **4개** 화면» | 가이드 · 개요 · 계좌 · 원장 = 4개 | ✅ 없음. |
| IAM | (화면 수 주장 없음 — "IAM 은 누가 콘솔의 어떤 메뉴를 쓸 수 있는지를 정하는 곳" 류의 일반 설명뿐) | — | ✅ 없음. |

## AC-7 — 사실 대조 표(묶음별 출처) + 확인 못 해 뺀 항목

### 사실 대조 표

| 도메인 | 묶음 | 출처 |
|---|---|---|
| iam | 로그인 | `projects/iam-platform/specs/features/oauth-social-login.md`(SAS 브라우저 플로우, 제공자 Google/Kakao/Microsoft/Naver), `infra/demo/demo.env:96-103`(4개 제공자 redirect URI 등록) |
| iam | 계정 | `projects/iam-platform/specs/features/multi-tenancy.md:396,406,465,483-485`(소비자 계정 풀 공유 + 사이트별 첫 방문 동의), `projects/iam-platform/specs/contracts/events/account-events.md:44,51`, `permission-map.ts` `/accounts` 행 |
| iam | 멀티테넌트 | `console-nav-config.ts` 「조직 설정」 그룹(조직 계층·테넌트·도메인 구독·파트너십), `permission-map.ts` 해당 4행 |
| iam | 운영자 권한 | `console-nav-config.ts` 「관리 ▸ IAM」 children, `permission-map.ts` `/operators`·`/operator-groups`·`/permission-sets`·`/permissions` 행 |
| iam | 보안 | `permission-map.ts` `/audit` 행("감사 로그 · 로그인 이력 · 의심 활동 조회") |
| wms | 마스터 | `permission-map.ts` `/wms/master` 행, `console-nav-config.ts` |
| wms | 입고 | `features/wms-guide/data.ts:124`("입고 적치"), `projects/wms-platform/specs/services/inbound-service/workflows/inbound-flow.md`, `permission-map.ts` `/wms/inbound` 행 |
| wms | 재고 | `permission-map.ts` `/wms/inventory` 행(crud: R — 조회 전용으로 서술) |
| wms | 출고 | `permission-map.ts` `/wms/outbound` 행, `features/wms-guide/data.ts` TMS_STATES(PENDING/NOTIFIED/NOTIFY_FAILED), `projects/ecommerce-microservices-platform/specs/contracts/events/wms-shipment-subscriptions.md:235-236`(ecommerce 주문도 WMS outbound 로 들어옴) |
| wms | 운영설정과 알림 | `permission-map.ts` `/wms/operations`·`/wms` 행 |
| scm | 조달 | `features/scm-guide/data.ts:108,174`(조회 전용, 승인이 유일한 발주 생성 경로), `permission-map.ts` `/scm/procurement` 행(crud: R) |
| scm | 재고 가시성 | `permission-map.ts` `/scm/inventory` 행 |
| scm | 수요 계획(보충) | `features/scm-guide/data.ts:281`(저재고 알림→추천→승인 루프), `permission-map.ts` `/scm/replenishment` 행 |
| scm | 설정 | `permission-map.ts` `/scm/config` 행 |
| finance | 계좌 상태 | `features/finance-guide/data.ts:63,241,265`(KYC 승급·동결 해제는 콘솔 범위 밖, 조회만), `permission-map.ts` `/finance/accounts` 행(crud: R) |
| finance | 잔액과 거래 | `permission-map.ts` `/finance/accounts` 행 |
| finance | 원장 | `permission-map.ts` `/ledger` 행, `features/finance-guide/data.ts`(DOUBLE_ENTRY_NOTE) |
| finance | 대사 | `permission-map.ts` `/ledger` 행(RECONCILIATION_NOTE) |
| finance | 환율 | `permission-map.ts` `/ledger` 행(FX_NOTE) |
| erp | 마스터데이터 | `features/erp-guide/data.ts:69,84,115,127`(부서·직원·직급·**원가센터**·거래처), `permission-map.ts` `/erp/masters` 행 |
| erp | 결재 | `features/erp-guide/data.ts:76-77,139,296-297`(다단계 라우팅), `permission-map.ts` `/erp/approval` 행 |
| erp | 위임(대결) | `features/erp-guide/data.ts:145,272,277,301-342,384`(대결/위임 grant), `permission-map.ts` `/erp/delegation` 행 |
| erp | 통합 조회 | `features/erp-guide/data.ts:133`(직원 조직도 = 부서 계층 경로 + 원가센터 + 직급), `permission-map.ts` `/erp/orgview` 행 |
| ecommerce | 상품 | `permission-map.ts` `/ecommerce/products` 행(옵션·재고·이미지) |
| ecommerce | 주문 · 결제 | `features/ecommerce-guide/data.ts:73,184,566-571`(결제 전용 화면 없음 — 환불은 주문 취소의 보상), `permission-map.ts` `/ecommerce/orders` 행 |
| ecommerce | 마켓플레이스 | `permission-map.ts` `/ecommerce/sellers`·`/ecommerce/settlements` 행, `projects/ecommerce-microservices-platform/apps/web-store/src/app/(store)/seller-invitations`(셀러 멤버 초대 라우트 실존 확인), `projects/ecommerce-microservices-platform/specs/features/marketplace-settlement.md` |
| ecommerce | 배송 | `permission-map.ts` `/ecommerce/shippings` 행, `specs/contracts/events/wms-shipment-subscriptions.md:91`(`INSUFFICIENT_STOCK` 사유) |
| ecommerce | 고객 · 알림 | `permission-map.ts` `/ecommerce/users`·`/ecommerce/notifications/templates` 행 |
| ecommerce | 구매자 화면 | `projects/ecommerce-microservices-platform/apps/web-store/src/app/(store)/{products,cart,checkout,orders,my}`(라우트 실존 확인) |
| fan | 아티스트 디렉터리(플랫폼 운영자 전용) | `console-nav-config.ts` `productKey:'fan'`, `permission-map.ts` `/fan/agencies`·`/fan/artists`·`/fan/groups` 행 extra 주석(ADR-MONO-079 R3), `projects/fan-platform/specs/contracts/http/artist-api.md`(§Agencies·§Artists·§Artist groups) |
| fan | 아티스트 · 팬덤 | `projects/fan-platform/specs/contracts/http/artist-api.md:161-221`(follows), §Fandoms |
| fan | 커뮤니티 | `projects/fan-platform/specs/contracts/http/community-api.md:11-17,63-198`(posts/comments/reactions/follows/feed, 운영자 토큰 거부) |
| fan | 멤버십 | `projects/fan-platform/specs/contracts/http/membership-api.md:13-19,68-225`(MEMBERS_ONLY·PREMIUM, 월 7,900/17,900) |

### 확인 못 해 뺀 항목 (출발 자료 기준)

| 도메인 | 출발 자료의 항목 | 뺀 이유 |
|---|---|---|
| iam | 보안 — «토큰 재사용 탐지와 회수» | `refresh-token-rotation.md` 는 실존하나 콘솔에 그 결과가 보이는 화면이 없다(의심 활동은 `/audit` 로 이미 커버). |
| scm | «물류(출하 확정 → 운송사 배차)» | SCM 콘솔엔 물류 화면이 없다 — 운송사 통보는 WMS `/wms/outbound` 쪽에서 일어나는 일이라 WMS 쪽 항목에 담았다. |
| finance | 계좌 — «중복 없는 자금 이동»(idempotent transfer) | 구현 보장(아이디포턴시)이라 운영자 문구가 아니다 — 금지어는 아니지만 Scope 의 "구현 용어 금지" 취지에 어긋나 뺐다. |
| erp | 통합 조회 — «결재 현황» | `/erp/orgview` 투영은 직원·부서·원가센터·직급만 — 결재 현황은 그 화면에 없다(`erp-guide/data.ts:133`). |
| ecommerce | «검색(이미지 업로드)» | 별도 메뉴/화면이 아니라 상품 화면의 기능이라 상품 항목에 합쳤다(별도 그룹으로 안 둠). |
| ecommerce | «리뷰» | 콘솔 메뉴도, web-store 의 독립 리뷰 라우트도 확인하지 못했다(상품 상세 페이지 내부 구현까지는 못 들어가 봤다). |

### 자구만 고친 항목

| 도메인 | 출발 자료 | 고친 표현 | 근거 |
|---|---|---|---|
| erp | «비용센터» | «원가센터» | `features/erp-guide/data.ts` 전역에서 쓰는 저장소 용어는 "원가센터"다. |

### 사이드바에 있는데 출발 자료가 놓친 것(추가)

- iam «로그인» 묶음(이메일·비밀번호 + 소셜 로그인) — 출발 자료는 소셜 로그인만 꼬리 문구로 언급했고 독립 묶음으로 두지 않았다. 로그인은 모든 도메인·앱이 공유하는 진입점이라 별도 묶음으로 구조화했다.
- ecommerce 마켓플레이스 묶음에 «정산»(수수료율 · 지급 실행)을 명시적 항목으로 추가 — 출발 자료는 "마켓플레이스" 한 줄에 셀러 입점만 적어 정산을 따로 안 뺐다.
- fan을 "아티스트 디렉터리(플랫폼 운영자 전용)"와 "아티스트 · 팬덤"(팬이 보는 쪽)으로 분리 — 둘은 보이는 사람이 다르다(운영자 전용 vs fan-platform-web 방문자).

## AC-6 — bite 결과

- **(a) href 깨기**: `/wms/master` → `/wms/master-BITE-404` 로 바꾸고 `domain-features-drift.test.ts`·`domain-features-wording.test.ts`·`GlobalGuideScreen.test.tsx`·`WmsGuideScreen.test.tsx` 를 실행 — **정확히 `domain-features-drift.test.ts` 의 "every linked item (href) is a real sidebar leaf (AC-4a)" 1건만 빨강**, 나머지 33건 전부 초록. 되돌린 뒤 `diff` 로 원본과 바이트 동일함을 확인.
- **(b) 금지어 넣기**: `/audit` 항목 문구에 `Kafka` 를 끼워 넣고 같은 꼴로 실행 — **정확히 `domain-features-wording.test.ts` 의 "has no implementation jargon …" 1건만 빨강**, 나머지 36건 전부 초록(드리프트 가드는 영향 없음 — href 는 안 건드렸으므로). 되돌린 뒤 `diff` 로 원본과 바이트 동일함을 확인.

## AC-8 — e2e grep 결과

`grep -rn "guide\|가이드\|global-guide\|-guide-\|도메인 전체 설명\|reading-path"`:

- `projects/platform-console/apps/console-web/tests/e2e/`: 1건 — `overview-consolidation.spec.ts:101-103` 가 `nav-erp-guide` **사이드바 링크**의 href(`/erp/guide`)를 단언. 가이드 화면 **내부**(overview 탭 문구·testid)는 건드리지 않는다.
- `tests/federation-hardening-e2e/specs/`: 0건.
- `e2e-smoke`: 이 저장소에 그런 디렉터리가 없다(존재하지 않음 — grep 대상 자체가 없다).

**결론**: 이 작업이 건드린 가이드 overview 탭 내부 콘텐츠를 단언하는 e2e 가 없다 — nightly 에서 깨질 위험 없음(사이드바 링크 자체는 안 건드렸다).

## 게이트 결과

- `npx tsc --noEmit` → rc=0.
- `npx next lint` → rc=0 ("No ESLint warnings or errors").
- `npx vitest run`(전량) → **Test Files 2 failed | 348 passed (350)**, **Tests 5 failed | 3965 passed (3970)**. 실패 5건 전부 `OperatorsScreen.test.tsx`(4) · `WmsInventoryScreen.test.tsx`(1) — 이 티켓이 건드리지 않은 파일. 두 파일만 단독 재실행 → **Test Files 2 passed (2) | Tests 31 passed (31)** — 부하 flake 확인(이 저장소에 기록된 패턴과 일치).
- 신규/변경 시험 단독 실행(가이드 7개 화면 + 드리프트 + 문구 가드, 9 파일) → **103/103 통과**.

## 분석=Opus 5.5 / 구현=Sonnet 5

## CORRECTION — 리뷰 수정 (2026-10-09 UTC, 부모 세션 diff 대조)

머지 전 diff 를 티켓과 대조해 두 곳을 고쳤다(구현 보고에 «의도적 일탈» 로 적혀 있던 것 하나 포함).

1. 🔴 **fan 디렉터리가 화면에 «콘솔 밖» 으로 렌더됐다 — 거짓.** 구현이 «링크 대신 문구» 를 지키려고 `outsideConsole: true` 를 재사용했는데, 그 플래그는 «콘솔 밖» 배지를 그린다. 그 화면은 콘솔 안에 **있다**(플랫폼 운영자만 볼 뿐). ⇒ 항목 필드 `platformOnly` 를 새로 두고 «플랫폼 운영자 전용» 배지로 렌더(href 없음). 문구 꼬리 «(플랫폼 운영자 전용 화면)» 은 배지와 겹쳐 지웠다.
   - 가드 2개 추가(`domain-features-drift.test.ts`): ① `platformOnly` 는 href 도 `outsideConsole` 도 없다 ② `platformOnly` 를 주장하는 도메인은 사이드바에 `productKey` 게이트 부모가 실제로 있다(+ 비공허: 오늘 ≥ 1 = fan). 게이트가 풀리거나 부모가 없어지면 빨강.
   - 화면 시험 1개 추가(`GlobalGuideScreen.test.tsx`): fan 디렉터리 묶음에 «플랫폼 운영자 전용» 이 있고 «콘솔 밖» · 링크가 없다.
   - bite: fan 항목을 `outsideConsole` 로 되돌리면 **정확히 위 2개**가 빨강(2 실패 / 16 통과), 복원 후 바이트 동일 확인.
2. **WMS 출고의 «운송사에 자동 통보 · 실패 시 재시도» 가 `/wms/outbound` 링크로 붙어 있었다** — 그 화면은 확정된 출고(운송사 · 송장 · 출고 시각)를 **보여주기만** 한다(`WmsShipmentsScreen.tsx:21`). 자동 배차 · 재시도는 공급망(SCM) 물류 쪽 백엔드 동작이다. ⇒ 둘로 나눴다: «출고 확정 건의 운송사 · 송장 번호 · 출고 시각 조회»(`/wms/outbound`) + «출고 확정 시 운송사에 자동 배차 요청(공급망 물류가 처리)»(`outsideConsole`).

재검증: 가이드 9파일 105/105(수정 직후) · 전역+드리프트 18/18 · `tsc --noEmit` rc=0 · `next lint` rc=0.

## CORRECTION — close (2026-10-09 UTC, 4차원 검증)

- **AC-10 닫힘**: 소유자가 **데모 창**에서 전역 가이드 «도메인 한눈에» 와 도메인 가이드 첫 탭 요약을 눈으로 확인했다(2026-10-09 UTC 대화 — «321 가이드, 313 · 315 메뉴» · «데모 창»). 위 AC 목록의 `[ ]` 는 이 절이 닫는다.
- (a) #4242 `MERGED` 2026-10-08T15:26:09Z, squash `56608bab3`.
- (b) `56608bab3` 이 `origin/main` 에 있음.
- (c) 머지 시점 체크 SUCCESS 12 · SKIPPED 54 · FAILURE 0. (첫 머지 시도는 늦게 붙은 체크 pending 으로 막혔고, 그때 원격 브랜치를 잘못 지웠다가 같은 커밋 `001ef9139` 으로 되살린 뒤 체크 완료를 다시 확인하고 머지했다 — 코드 변경 없음.)
- (d) AC-0~10 전부 닫힘.
