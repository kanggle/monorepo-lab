/**
 * TASK-PC-FE-321 — 도메인마다 「한 줄 설명 + 주요 기능 묶음」을 담는 **단일 데이터**.
 *
 * 전역 가이드(`features/global-guide`)의 새 절 «도메인 한눈에»와, 6개 도메인 가이드
 * (iam · wms · scm · finance · erp · ecommerce)의 첫 탭 «도메인 전체 설명» 맨 위가
 * **이 파일 하나**를 같이 읽는다 — 한쪽만 고쳐지는 일이 구조적으로 없다(Goal #2).
 *
 * =============================================================================
 * 사실 대조 규칙 (AC-7)
 * =============================================================================
 * 항목마다 다음 중 하나로 확인했다 — 추측으로 채운 항목은 없다:
 *   - 콘솔 사이드바 메뉴(`shared/ui/console-nav-config.ts` `GROUPS`) + 그 메뉴의 설명
 *     (`shared/guide/permission-map.ts` 의 해당 `href` 행 — 이미 실제 컨트롤러/계약에
 *     인용된 설명을 재사용한다).
 *   - 그 화면 컴포넌트(`features/*-guide/data.ts` 등 도메인 가이드 데이터) — 콘솔이
 *     실제로 쓰는 값·상태머신의 뜻.
 *   - producer 계약(`projects/<domain>/specs/contracts/**`, `specs/features/**`).
 *   - `outsideConsole: true` 항목은 그 기능이 실제로 보이는 **다른 앱의 라우트**
 *     (web-store 등) — 각 그룹 주석에 경로를 적었다.
 *
 * 확인하지 못해 뺀 항목(출발 자료 `docs/project-overview.md` 채팅 요약 기준)과, 자구만
 * 다듬은 항목은 TASK-PC-FE-321 Implementation Record § 사실 대조 표에 있다. 이 파일은
 * `docs/project-overview.md` 를 인용하지 않는다(그 문서는 갱신이 늦을 수 있다 — Background).
 *
 * 🔴 **드리프트 가드** — `tests/unit/domain-features-drift.test.ts` 가 (a) 모든 `href` 가
 * `navLeaves()`(사이드바 원장)의 href 인지 (b) `outsideConsole` 항목엔 `href` 가 없는지
 * (c) 사이드바의 도메인 드릴 부모(`GROUPS` 의 `key` 있는 노드) 마다 이 파일에 대응하는
 * 항목이 있는지 (d) 콘솔 링크 항목 수가 도메인 수 이상인지를 `GROUPS`/`navLeaves()`
 * 에서 직접 유도해 대조한다 — 목록을 하드코딩하지 않는다. 메뉴가 사라지거나 옮겨지면
 * 이 가드가 빨개진다(AC-4). 문구 금지어(`saga`·`Elasticsearch`·`OIDC`·`Kafka`·`outbox`·
 * `-service`·`/api/`)는 `tests/unit/domain-features-wording.test.ts` 가 막는다(AC-5).
 */

export type DomainFeatureKey =
  | 'iam'
  | 'wms'
  | 'scm'
  | 'finance'
  | 'erp'
  | 'ecommerce'
  | 'fan';

export interface DomainFeatureItem {
  /** 운영자·방문자가 읽는 한 줄. 구현 용어(서비스 이름·API 경로 등) 금지. */
  text: string;
  /** 이 기능이 열리는 사이드바 메뉴 주소 — 있을 때만. 실재하는 nav leaf 여야 한다(AC-4a). */
  href?: string;
  /** 콘솔 화면이 없는 기능(다른 앱의 화면, 또는 콘솔 범위 밖의 백엔드 동작). href 없음(AC-4b). */
  outsideConsole?: boolean;
  /**
   * 콘솔 화면은 **있지만** 플랫폼 운영자에게만 보이는 기능(레지스트리 게이트 `productKey`
   * 부모 — 예: fan 디렉터리). 고객사 운영자 · 방문자에겐 그 메뉴가 없으므로 링크를 달지
   * 않는다(href 없음). «콘솔 밖» 과 다르다 — 콘솔 밖이라고 적으면 거짓이다.
   * 드리프트 가드가 «그 도메인에 productKey 게이트 부모가 실제로 있다» 를 확인한다.
   */
  platformOnly?: boolean;
}

export interface DomainFeatureGroup {
  title: string;
  items: DomainFeatureItem[];
}

export interface DomainFeatures {
  key: DomainFeatureKey;
  label: string;
  oneLine: string;
  groups: DomainFeatureGroup[];
}

/* ─────────────────────────── IAM ───────────────────────────
 * 사이드바: 관리 ▸ IAM(가이드·개요·운영자 관리·운영자 그룹·권한 세트·권한·감사·보안) +
 * 고객 신원(계정 운영) + 조직 설정(조직 계층·테넌트·도메인 구독·파트너십).
 * (`console-nav-config.ts` GROUPS, `permission-map.ts` PERMISSION_MAP area 'iam'|
 * 'customer-identity'|'org'.)
 * 로그인·소셜 로그인·소비자 계정 풀 공유는 콘솔 화면이 아니라 IAM 자체 로그인
 * 페이지/백엔드 동작 — `projects/iam-platform/specs/features/oauth-social-login.md`,
 * `projects/iam-platform/specs/features/multi-tenancy.md:396,406,465,483-485`,
 * `projects/iam-platform/specs/contracts/events/account-events.md:44,51`.
 * 보안(토큰 재사용 탐지 회수)은 `projects/iam-platform/specs/use-cases/refresh-token-rotation.md`
 * — 콘솔에 보이는 결과가 없어 이 항목은 뺐다(Implementation Record 참조).
 */
const IAM: DomainFeatures = {
  key: 'iam',
  label: 'IAM',
  oneLine:
    '로그인, 회사·테넌트 구조, 운영자 권한, 보안 기록을 관리하는 곳입니다.',
  groups: [
    {
      title: '로그인',
      items: [
        {
          text: '이메일·비밀번호 로그인 — 콘솔과 쇼핑몰 · 팬 사이트가 모두 이 로그인을 함께 쓴다',
          outsideConsole: true,
        },
        { text: '구글 · 네이버 등 소셜 로그인', outsideConsole: true },
      ],
    },
    {
      title: '계정',
      items: [
        {
          text: '소비자 계정 하나를 팬 사이트와 쇼핑몰이 함께 쓰고, 처음 쓰는 사이트에서만 이용 동의를 한 번 받는다',
          outsideConsole: true,
        },
        {
          text: '소비자 계정 검색 · 잠금/해제 · 세션 강제 종료 · 데이터 내보내기 · 삭제',
          href: '/accounts',
        },
      ],
    },
    {
      title: '멀티테넌트',
      items: [
        { text: '회사(조직) 아래 테넌트를 묶고 쓸 수 있는 도메인의 상한을 건다', href: '/org-hierarchy' },
        { text: '격리 경계(테넌트)의 생성과 상태 관리', href: '/tenants' },
        { text: '테넌트가 쓸 도메인(창고 · 공급망 등) 구독을 켜고 끈다', href: '/subscriptions' },
        { text: '다른 회사와의 협력 관계(파트너십) 관리', href: '/partnerships' },
      ],
    },
    {
      title: '운영자 권한',
      items: [
        { text: '운영자 초대(대기 초대 취소·재발송), 테넌트 배정과 부서 단위 데이터 범위 설정', href: '/operators' },
        { text: '운영자 그룹으로 여러 사람에게 역할·테넌트 배정을 한 번에 부여', href: '/operator-groups' },
        { text: '배정에 붙는 권한 묶음(권한 세트) 열람', href: '/permission-sets' },
        { text: '권한 키 카탈로그 열람', href: '/permissions' },
      ],
    },
    {
      title: '보안',
      items: [
        { text: '감사 로그 · 로그인 이력 · 이상 로그인 탐지 결과(의심 활동) 조회', href: '/audit' },
      ],
    },
  ],
};

/* ─────────────────────────── WMS ───────────────────────────
 * 사이드바: 도메인 운영 ▸ WMS(가이드·개요·입고·재고·출고·마스터·운영설정).
 * 설명은 `permission-map.ts` 의 /wms/* 행(이미 inbound-service/outbound-service 계약에
 * 인용된 설명)을 거의 그대로 옮긴다. 재고·입고·마스터·운영설정 화면은 **조회 전용**
 * (permission-map.ts crud: R) — "조정"·"이동" 같은 쓰기 동작을 콘솔이 준다고 적지 않는다.
 */
const WMS: DomainFeatures = {
  key: 'wms',
  label: 'WMS',
  oneLine: '창고의 입고부터 재고, 출고까지 물류 전체 흐름을 운영합니다.',
  groups: [
    {
      title: '마스터',
      items: [
        { text: '창고 · 구역 · 로케이션 · SKU · Lot · 거래처 참조 데이터 조회', href: '/wms/master' },
      ],
    },
    {
      title: '입고',
      items: [
        { text: '입고예정(ASN) 접수 → 검수 → 적치 결과 확인', href: '/wms/inbound' },
      ],
    },
    {
      title: '재고',
      items: [
        {
          text: '위치 · SKU · 로트별 재고 수량(가용 · 예약 · 손상) 조회 — 입고 적치나 조정으로 바뀐 수량이 그대로 반영된다',
          href: '/wms/inventory',
        },
      ],
    },
    {
      title: '출고',
      items: [
        {
          text: '주문을 피킹 → 패킹 → 출고까지 진행 — 쇼핑몰(이커머스) 주문도 같은 화면에서 처리한다',
          href: '/wms/outbound',
        },
        { text: '출고 확정 건의 운송사 · 송장 번호 · 출고 시각 조회', href: '/wms/outbound' },
        // 운송사 자동 통보 · 재시도는 공급망(SCM) 물류 쪽 백엔드 동작이다 — 이 콘솔 화면은
        // 확정된 출고를 «보여주기만» 한다(WmsShipmentsScreen: carrier / tracking / shipped-at).
        { text: '출고가 확정되면 운송사에 자동으로 배차를 요청한다(공급망 물류가 처리)', outsideConsole: true },
      ],
    },
    {
      title: '운영설정과 알림',
      items: [
        { text: '예약 TTL · 저재고 임계치 등 운영 파라미터 조회', href: '/wms/operations' },
        { text: '재고 · 처리량 · 주문 요약과 운영 알림 확인', href: '/wms' },
      ],
    },
  ],
};

/* ─────────────────────────── SCM ───────────────────────────
 * 사이드바: 도메인 운영 ▸ SCM(가이드·개요·조달·재고·보충 계획·보충 계획 설정).
 * 🔴 출발 자료는 콘솔에서 "PO 발행·확정·취소"가 된다고 했으나, 조달 화면은 조회
 * 전용이다 — `features/scm-guide/data.ts:108,174`("콘솔 SCM 조달 화면의 발주 목록은
 * 조회 전용이다 ... 콘솔에서 발주 생성을 촉발하는 유일한 경로는 보충 추천 승인") +
 * `permission-map.ts` /scm/procurement crud: R. 아래는 그 사실대로 고쳐 적었다
 * (Implementation Record § 사실 대조 표).
 */
const SCM: DomainFeatures = {
  key: 'scm',
  label: 'SCM',
  oneLine: '저재고 알림에서 발주로 이어지는 공급망 보충을 관리합니다.',
  groups: [
    {
      title: '조달',
      items: [
        {
          text: '발주(PO) 상태 조회 — 제출 · 확정 · 입고는 조달 쪽에서 진행하고, 콘솔에서 발주를 새로 만드는 유일한 경로는 보충 추천 승인이다',
          href: '/scm/procurement',
        },
      ],
    },
    {
      title: '재고 가시성',
      items: [
        { text: '여러 창고(노드)의 재고를 한곳에서 조회', href: '/scm/inventory' },
      ],
    },
    {
      title: '수요 계획(보충)',
      items: [
        {
          text: '저재고 알림에서 생긴 보충 추천을 검토해 승인하거나 기각 — 승인하면 초안 발주가 생긴다',
          href: '/scm/replenishment',
        },
      ],
    },
    {
      title: '설정',
      items: [
        { text: 'SKU별 재주문 정책과 SKU-공급사 매핑 등록', href: '/scm/config' },
      ],
    },
  ],
};

/* ─────────────────────────── Finance ───────────────────────────
 * 사이드바: 도메인 운영 ▸ Finance(가이드·개요·계좌·원장).
 * 🔴 출발 자료의 "보류·해제·확정"은 콘솔 밖(백엔드) 동작이다 —
 * `features/finance-guide/data.ts:63,241,265`("KYC 승급·동결 해제는 콘솔 범위 밖(백엔드
 * 조치)입니다 — 콘솔은 규제 상태를 있는 그대로 표시만 합니다"). 아래는 조회로 고쳐
 * 적었다. "중복 없는 자금 이동"은 구현 보장(아이디포턴시)이라 운영자 문구로 옮기지
 * 않고 뺐다(Implementation Record).
 */
const FINANCE: DomainFeatures = {
  key: 'finance',
  label: 'Finance',
  oneLine: '계좌 잔액과 복식부기 원장을 관리합니다.',
  groups: [
    {
      title: '계좌 상태',
      items: [
        {
          text: '계좌 상태(KYC 단계 · 활성 · 보류 · 동결 등)를 있는 그대로 조회 — 상태를 바꾸는 조치 자체는 콘솔 범위 밖',
          href: '/finance/accounts',
        },
      ],
    },
    {
      title: '잔액과 거래',
      items: [
        { text: '통화별 가용 잔액과 거래 이력 조회', href: '/finance/accounts' },
      ],
    },
    {
      title: '원장',
      items: [
        { text: '복식부기 총계정원장과 시산표, 회계 기간 조회', href: '/ledger' },
        { text: '계정별 분개를 추적(계정 드릴)', href: '/ledger' },
      ],
    },
    {
      title: '대사',
      items: [
        { text: '다른 기록과 맞춰보고(대사) 차이를 해소', href: '/ledger' },
      ],
    },
    {
      title: '환율',
      items: [
        { text: '환율 데이터 신선도 확인과 새로고침', href: '/ledger' },
      ],
    },
  ],
};

/* ─────────────────────────── ERP ───────────────────────────
 * 사이드바: 도메인 운영 ▸ ERP(가이드·개요·마스터·통합 조회·결재함·위임).
 * 출발 자료의 "비용센터"는 저장소 용어 "원가센터"로 바로잡았다 —
 * `features/erp-guide/data.ts:69,84,115,127,133`. 통합 조회는 "결재 현황"이 아니라
 * 직원 조직도(부서 경로 · 원가센터 · 직급)만 투영한다(data.ts:133) — 결재 현황은 뺐다.
 */
const ERP: DomainFeatures = {
  key: 'erp',
  label: 'ERP',
  oneLine: '회사의 기준 데이터와 다단계 결재를 관리합니다.',
  groups: [
    {
      title: '마스터데이터',
      items: [
        {
          text: '부서 · 직원 · 직급 · 원가센터 · 거래처 마스터 조회 · 등록 · 수정 · 폐기 — 부서 단위로만 보이는 범위가 적용된다',
          href: '/erp/masters',
        },
      ],
    },
    {
      title: '결재',
      items: [
        { text: '다단계 결재 요청 상신, 결재함에서 승인 · 반려 · 회수', href: '/erp/approval' },
      ],
    },
    {
      title: '위임(대결)',
      items: [
        { text: '결재자 부재 시 대신 처리할 수 있게 위임 등록 · 회수', href: '/erp/delegation' },
      ],
    },
    {
      title: '통합 조회',
      items: [
        { text: '직원의 소속 부서 경로 · 원가센터 · 직급을 한 화면에서 조회', href: '/erp/orgview' },
      ],
    },
  ],
};

/* ─────────────────────────── E-Commerce ───────────────────────────
 * 사이드바: 도메인 운영 ▸ E-Commerce(가이드·개요·상품·주문·배송·프로모션·사용자·셀러·
 * 정산·알림). web-store 라우트는
 * `projects/ecommerce-microservices-platform/apps/web-store/src/app/(store)/**`
 * (products · cart · checkout · orders · my · seller-invitations).
 * 결제·환불은 전용 화면이 없다 — `features/ecommerce-guide/data.ts:73,184,566-571`
 * ("결제 전용 화면이 없어 환불은 주문 취소의 보상으로 처리됩니다"). 재고 부족 시 자동
 * 취소 사유는 `specs/contracts/events/wms-shipment-subscriptions.md:91`
 * (`INSUFFICIENT_STOCK`). 출발 자료의 "검색(이미지 업로드)"은 별도 메뉴가 아니라
 * 상품 화면의 기능이라 상품 항목에 합쳤다. "리뷰"는 콘솔 메뉴도 web-store 의 독립
 * 라우트도 확인하지 못해 뺐다(Implementation Record).
 */
const ECOMMERCE: DomainFeatures = {
  key: 'ecommerce',
  label: 'E-Commerce',
  oneLine: '상품 · 주문 · 셀러 · 배송까지 온라인 스토어 운영을 관리합니다.',
  groups: [
    {
      title: '상품',
      items: [
        { text: '상품 등록 · 수정 · 삭제, 옵션(variant)과 재고, 이미지 올리기', href: '/ecommerce/products' },
      ],
    },
    {
      title: '주문 · 결제',
      items: [
        {
          text: '주문 확인과 상태 변경 — 취소하면 결제 환불(또는 캡처 전 취소)이 자동으로 처리된다',
          href: '/ecommerce/orders',
        },
      ],
    },
    {
      title: '마켓플레이스',
      items: [
        { text: '셀러 입점 신청 처리, 정지 · 폐점', href: '/ecommerce/sellers' },
        { text: '셀러 멤버 초대는 콘솔이 아니라 쇼핑몰 화면에서 받는다', outsideConsole: true },
        { text: '정산 기간 마감, 수수료율 설정, 셀러 잔액과 지급 실행', href: '/ecommerce/settlements' },
      ],
    },
    {
      title: '배송',
      items: [
        {
          text: '배송 상태 변경과 추적 새로고침 — 창고(WMS) 출고와 연동되고, 재고가 부족하면 출고가 자동으로 취소되고 환불된다',
          href: '/ecommerce/shippings',
        },
      ],
    },
    {
      title: '고객 · 알림',
      items: [
        { text: '구매자 정보 조회', href: '/ecommerce/users' },
        { text: '고객에게 가는 알림 문구(템플릿) 관리', href: '/ecommerce/notifications/templates' },
      ],
    },
    {
      title: '구매자 화면',
      items: [
        {
          text: '상품 보기 · 장바구니 · 결제 · 내 주문은 콘솔이 아니라 쇼핑몰 웹(web-store)에서 열린다',
          outsideConsole: true,
        },
      ],
    },
  ],
};

/* ─────────────────────────── fan ───────────────────────────
 * fan 은 도메인 가이드가 없다(Scope — 신설 안 함). 디렉터리(소속사·아티스트·그룹)는
 * 사이드바에 있지만 **플랫폼 운영자 전용**(`console-nav-config.ts` productKey: 'fan',
 * `permission-map.ts` /fan/* extra 주석, ADR-MONO-079 R3) — 고객사 운영자 · 방문자에겐
 * 보이지 않으므로 href 를 달지 않고 `platformOnly` 로 표시한다(Edge Case). 🔴 «콘솔 밖»
 * (`outsideConsole`) 으로 적으면 안 된다 — 콘솔 화면이 실제로 있으니 거짓이 된다.
 * 커뮤니티 · 멤버십 · 알림은 콘솔 화면이 전혀 없다(그 운영자도 못 본다,
 * `projects/fan-platform/specs/contracts/http/community-api.md:11-17`,
 * `membership-api.md:13-19`) — fan-platform-web 전용. 소속사/아티스트/팬덤은
 * `specs/contracts/http/artist-api.md` §Artists·§Fandoms·§Agencies.
 */
const FAN: DomainFeatures = {
  key: 'fan',
  label: '팬',
  oneLine:
    '아티스트와 그룹을 소개하고, 팬은 커뮤니티와 유료 멤버십으로 더 가까이 다가갑니다.',
  groups: [
    {
      title: '아티스트 디렉터리',
      items: [
        { text: '소속사 등록과 이름 변경, 보관', platformOnly: true },
        { text: '아티스트 프로필 · 소속 · 공개 상태 관리', platformOnly: true },
        { text: '그룹 생성과 소속 관리', platformOnly: true },
      ],
    },
    {
      title: '아티스트 · 팬덤',
      items: [
        { text: '팬이 아티스트를 팔로우하고 팬덤에 모인다', outsideConsole: true },
      ],
    },
    {
      title: '커뮤니티',
      items: [
        { text: '팬이 남기는 게시글 · 댓글 · 반응, 팔로우한 아티스트의 소식을 모은 피드', outsideConsole: true },
        { text: '아티스트 본인이 직접 올리는 공식 글', outsideConsole: true },
      ],
    },
    {
      title: '멤버십',
      items: [
        { text: '월정액 유료 구독(일반 · 프리미엄 단계)', outsideConsole: true },
        { text: '구독 단계에 따라 일부 게시글은 구독자에게만 보인다', outsideConsole: true },
      ],
    },
  ],
};

/**
 * 데이터 순서 = 사이드바 순서(도메인 운영 ▸ WMS·SCM·Finance·ERP·E-Commerce·fan → 관리 ▸ IAM).
 * TASK-PC-FE-325 — 사이드바가 도메인 운영을 관리 앞으로 올리면서 IAM 을 맨 끝으로 옮겼다.
 */
export const DOMAIN_FEATURES: readonly DomainFeatures[] = [
  WMS,
  SCM,
  FINANCE,
  ERP,
  ECOMMERCE,
  FAN,
  IAM,
];

export function domainFeatureByKey(key: DomainFeatureKey): DomainFeatures {
  const found = DOMAIN_FEATURES.find((d) => d.key === key);
  if (!found) {
    throw new Error(`domain-features: no entry for key ${key}`);
  }
  return found;
}
