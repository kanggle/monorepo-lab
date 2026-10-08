/**
 * 전역 가이드(/guide — TASK-PC-FE-298)의 정적 데이터.
 *
 * 🔵 **문장은 처음 보는 사람용이다**(TASK-PC-FE-322) — 쉬운 말, 카드 하나에 한두 문장.
 * 파일 경로 · 줄 번호 · 티켓 번호는 화면 문장에 쓰지 않는다
 * (`tests/unit/GlobalGuideScreen.test.tsx` 가 검사한다).
 *
 * 🔴 **모든 사실 항목은 `sources` 에 저장소 파일을 인용한다**(298 AC-6). 화면에는 보이지 않고
 * (322 — 소유자 결정 «출처는 생략»), 유지보수자가 사실의 출처를 찾는 데 쓴다. 경로는 저장소
 * 루트 기준이고, `:줄` · `§절` · 괄호 설명은 경로 뒤에 붙인다. 시험이 모든 항목에 인용이
 * 1개 이상 있는지, 그리고 **인용된 경로가 실제로 존재하는지**(`fs.existsSync`)를 검사한다 —
 * 파일이 옮겨지거나 지워지면 이 가이드가 빨개진다. 인용이 가리키는 **줄의 내용**까지는
 * 검사하지 못한다(그건 사람의 몫).
 *
 * 이 파일은 도메인 가이드 feature 를 import 하지 않는다(feature 간 직접 import 금지 —
 * console-web architecture § Forbidden Dependencies). 도메인 가이드의 내용을 요약할 때는
 * 그 파일을 **인용**한다.
 */

export interface GuideFact {
  title: string;
  body: string;
  sources: string[];
}

/* ─────────────────────────── 1. 시스템 아키텍처 ─────────────────────────── */

/** 「요청이 지나가는 길」 — 아키텍처 탭 맨 위의 흐름 한 줄. */
export const REQUEST_PATH: { label: string; note: string }[] = [
  { label: '브라우저', note: '운영자 · 방문자' },
  { label: '콘솔', note: '화면 (Vercel)' },
  { label: '게이트웨이', note: '도메인마다 하나' },
  { label: '도메인 서비스', note: '데이터 (AWS)' },
];

export const ARCHITECTURE_FACTS: GuideFact[] = [
  {
    title: '화면은 콘솔 하나',
    body: '모든 도메인의 운영 화면이 이 콘솔 안에 있습니다. 도메인마다 따로 사이트가 있지 않습니다.',
    sources: ['projects/platform-console/PROJECT.md:20', 'docs/adr/ADR-MONO-013-platform-console-foundation.md'],
  },
  {
    title: '로그인은 IAM 한 곳',
    body: '한 번 로그인하면 IAM 관리 메뉴가 열립니다. 회사(테넌트)를 고르면 그 회사가 구독한 도메인의 화면이 더 열립니다.',
    sources: [
      'projects/platform-console/apps/console-web/src/features/iam-guide/data.ts:113-137 (AUTH_PLANES)',
      'infra/demo/README.md:264-270 (operator 토큰은 admin-service 가 자기 키로 서명)',
      'projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/OperatorRoleDerivation.java:102-107',
    ],
  },
  {
    title: '도메인마다 문지기(게이트웨이)',
    body: '콘솔의 요청은 도메인 앞의 게이트웨이를 먼저 지납니다. 게이트웨이와 그 뒤의 서비스가 로그인 정보를 각각 확인합니다.',
    sources: ['infra/demo/README.md:222-228', 'infra/demo/README.md:192-198'],
  },
  {
    title: '개요는 콘솔이 모아서 보여 준다',
    body: '「개요」의 도메인 요약은 콘솔 서버가 여러 도메인에 한꺼번에 물어 한 화면으로 합칩니다.',
    sources: [
      'projects/platform-console/apps/console-web/src/shared/composition/console-composition.ts',
      'docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md',
      'projects/platform-console/apps/console-web/src/shared/sample/coverage.ts:58-60',
      'projects/platform-console/specs/services/console-web/architecture.md:19 (console-web → ecommerce gateway direct)',
    ],
  },
  {
    title: '로그인 안 해도 둘러볼 수 있다',
    body: '로그인하지 않은 방문자도 같은 화면을 봅니다. 데이터만 샘플로 바뀝니다.',
    sources: [
      'projects/platform-console/apps/console-web/src/app/(console)/layout.tsx:101-121',
      'docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md',
      'projects/platform-console/apps/console-web/src/shared/sample/coverage.ts:172-195',
    ],
  },
];

/* ─────────────────────────── 2. 도메인별 서비스 구성 ─────────────────────────── */

export interface DomainServiceGroup {
  project: string;
  label: string;
  /** `projects/<project>/apps/` 의 디렉터리 이름 그대로. */
  apps: string[];
  consoleRole: string;
  sources: string[];
}

export const DOMAIN_SERVICE_GROUPS: DomainServiceGroup[] = [
  {
    project: 'iam-platform',
    label: 'IAM',
    apps: ['gateway-service', 'auth-service', 'account-service', 'admin-service', 'security-service'],
    consoleRole: '로그인 · 운영자 · 테넌트 · 권한 · 감사',
    sources: ['projects/iam-platform/apps', 'projects/iam-platform/specs/services/admin-service/rbac.md:5'],
  },
  {
    project: 'wms-platform',
    label: 'WMS',
    apps: ['gateway-service', 'master-service', 'inbound-service', 'inventory-service', 'outbound-service', 'admin-service', 'notification-service'],
    consoleRole: '입고 · 재고 · 출고 · 마스터 · 운영설정',
    sources: ['projects/wms-platform/apps', 'projects/wms-platform/apps/gateway-service/src/main/resources/application.yml:58-112'],
  },
  {
    project: 'scm-platform',
    label: 'SCM',
    apps: ['gateway-service', 'procurement-service', 'inventory-visibility-service', 'demand-planning-service', 'logistics-service'],
    consoleRole: '발주 · 재고 현황 · 보충 계획. 출고된 화물의 운송사 연락도 맡습니다',
    sources: ['projects/scm-platform/apps', 'projects/platform-console/apps/console-web/src/features/scm-guide/data.ts:60-90'],
  },
  {
    project: 'finance-platform',
    label: 'Finance',
    apps: ['gateway-service', 'account-service', 'ledger-service'],
    consoleRole: '계좌 · 잔액 · 거래 · 회계 장부 · 마감 · 대사 · 환율',
    sources: ['projects/finance-platform/apps', 'projects/platform-console/apps/console-web/src/features/finance-guide/data.ts:58-75'],
  },
  {
    project: 'erp-platform',
    label: 'ERP',
    apps: ['gateway-service', 'masterdata-service', 'approval-service', 'read-model-service', 'notification-service'],
    consoleRole: '부서 · 직원 · 거래처 같은 기준 정보 · 결재 · 위임',
    sources: ['projects/erp-platform/apps', 'projects/platform-console/apps/console-web/src/features/erp-guide/data.ts:65-95'],
  },
  {
    project: 'ecommerce-microservices-platform',
    label: 'E-Commerce',
    apps: [
      'gateway-service',
      'auth-service',
      'product-service',
      'order-service',
      'payment-service',
      'shipping-service',
      'promotion-service',
      'user-service',
      'notification-service',
      'settlement-service',
      'review-service',
      'search-service',
      'batch-worker',
      'web-store',
    ],
    consoleRole: '상품 · 주문 · 배송 · 프로모션 · 회원 · 셀러 · 정산. 구매자용 쇼핑몰(web-store)은 콘솔 밖에 있습니다',
    sources: ['projects/ecommerce-microservices-platform/apps', 'projects/platform-console/apps/console-web/src/features/ecommerce-guide/data.ts:54-125'],
  },
  {
    project: 'platform-console',
    label: '콘솔',
    apps: ['console-web'],
    consoleRole: '지금 보고 있는 이 화면',
    sources: ['projects/platform-console/apps', 'projects/platform-console/specs/services/console-web/architecture.md:29'],
  },
];

/* ─────────────────────────── 3. 서버 구성 ─────────────────────────── */

export interface ServerLayer {
  key: 'vercel' | 'aws' | 'backend' | 'auth' | 'database';
  label: string;
  facts: GuideFact[];
}

export const SERVER_LAYERS: ServerLayer[] = [
  {
    key: 'vercel',
    label: '화면 — Vercel',
    facts: [
      {
        title: '콘솔 화면',
        body: 'console.hubwang.com 에서 열립니다. 데모 서버가 꺼져 있어도 화면은 열립니다.',
        sources: [
          'projects/platform-console/apps/console-web/VERCEL.md:7-12',
          'TEMPLATE.md:536',
        ],
      },
      {
        title: '시작 페이지 · 로그인 주소',
        body: 'Start Demo 버튼이 있는 hubwang.com 과 로그인 주소 auth.hubwang.com 도 Vercel 에 있습니다.',
        sources: ['TEMPLATE.md:533,538', 'infra/demo/aws/README.md:54-55', 'infra/demo/auth-forwarder/README.md'],
      },
    ],
  },
  {
    key: 'aws',
    label: '데모 서버 — AWS',
    facts: [
      {
        title: '서버 한 대, 안 쓰면 꺼진다',
        body: '모든 백엔드가 서울 리전의 EC2 한 대에서 돕니다. 쓰는 사람이 없으면 꺼져서 비용이 들지 않습니다.',
        sources: [
          'infra/demo/aws/terraform/variables.tf:1-4,18-42,62-65',
          'infra/demo/aws/README.md:3',
        ],
      },
      {
        title: '켜고 끄는 스위치',
        body: '작은 함수(Lambda)가 서버를 켜고 끄며 사용 시간 한도를 지킵니다. Start Demo 버튼이 이 스위치를 누릅니다.',
        sources: ['infra/demo/aws/terraform/lambda/handler.py:1036-1049', 'infra/demo/aws/README.md:13-23'],
      },
      {
        title: '미리 만든 서버 이미지',
        body: '서버는 미리 만들어 둔 이미지로 켜집니다. 그래서 백엔드 코드를 고쳐도 이미지를 다시 만들기 전에는 데모에 반영되지 않습니다.',
        sources: ['infra/demo/aws/packer/demo-ami.pkr.hcl', 'infra/demo/aws/README.md:85-97'],
      },
    ],
  },
  {
    key: 'backend',
    label: '백엔드 — 도커 컨테이너',
    facts: [
      {
        title: '8개 프로젝트, 약 96개 컨테이너',
        body: 'iam · wms · scm · finance · erp · ecommerce · fan · console 이 각자 따로 뜹니다. 맨 앞의 Traefik 이 주소를 보고 알맞은 곳으로 보냅니다.',
        sources: ['infra/demo/README.md:3-22', 'infra/demo/projects.sh', 'infra/demo/aws/README.md:3-5'],
      },
      {
        title: '밖에서 닿는 것은 게이트웨이뿐',
        body: '각 프로젝트의 게이트웨이만 밖으로 열려 있고, 나머지 서비스는 안쪽에만 있습니다.',
        sources: ['infra/demo/README.md:192-198,244-248'],
      },
    ],
  },
  {
    key: 'auth',
    label: '로그인 서버',
    facts: [
      {
        title: 'IAM 로그인 서비스',
        body: '로그인 화면은 IAM 의 로그인 서비스가 직접 띄웁니다. 로그인이 끝나면 콘솔 서버가 열쇠(토큰)를 받아 보관합니다.',
        sources: ['infra/demo/README.md:164', 'infra/demo/README.md:264-270', 'infra/demo/iam-traefik.override.yml'],
      },
      {
        title: '로그인 주소는 고정',
        body: '서버 IP 는 켤 때마다 바뀌어서, auth.hubwang.com 이 늘 같은 주소로 이어 줍니다. 데모가 꺼져 있으면 로그인할 수 없습니다.',
        sources: ['TEMPLATE.md:538', 'infra/demo/auth-forwarder/src/app/[[...path]]/route.ts'],
      },
    ],
  },
  {
    key: 'database',
    label: '데이터베이스',
    facts: [
      {
        title: 'MySQL — iam · erp · finance',
        body: '세 프로젝트가 MySQL 을 씁니다.',
        sources: [
          'projects/iam-platform/docker-compose.yml:55',
          'projects/erp-platform/docker-compose.yml:313',
          'projects/finance-platform/docker-compose.yml:207,238',
        ],
      },
      {
        title: 'PostgreSQL — ecommerce · wms · scm · fan',
        body: 'ecommerce 는 서비스마다 DB 를 따로 둡니다(10개). 나머지는 하나씩입니다.',
        sources: [
          'projects/ecommerce-microservices-platform/docker-compose.yml:416-641',
          'projects/wms-platform/docker-compose.yml:51',
          'projects/scm-platform/docker-compose.yml:88',
          'projects/fan-platform/docker-compose.yml:373',
        ],
      },
      {
        title: 'Redis · Kafka 도 프로젝트마다 따로',
        body: '캐시(Redis)와 메시지(Kafka)도 프로젝트끼리 섞이지 않게 따로 띄웁니다.',
        sources: ['infra/demo/README.md:9-17'],
      },
    ],
  },
];

/* ─────────────────────────── 6. 대표 업무 흐름 ─────────────────────────── */

export interface GuideFlow {
  key: string;
  title: string;
  steps: string[];
  sources: string[];
}

export const BUSINESS_FLOWS: GuideFlow[] = [
  {
    key: 'onboarding',
    title: '새 회사 직원이 도메인 화면을 열기까지 (IAM)',
    steps: [
      '플랫폼 관리자가 회사 관리자를 운영자로 등록합니다(「운영자 관리」).',
      '회사 관리자가 자기 직원을 운영자로 등록합니다.',
      '회사가 「도메인 구독」에서 쓸 도메인을 켭니다.',
      '직원이 로그인해 회사(테넌트)를 고르면 구독한 도메인의 화면이 열립니다.',
    ],
    sources: [
      'projects/platform-console/apps/console-web/src/features/iam-guide/data.ts:295-311 (DELEGATION_CHAIN)',
      'projects/iam-platform/specs/services/admin-service/rbac.md:94-95',
    ],
  },
  {
    key: 'order-to-ship',
    title: '주문 → 배송 → 창고 재고 차감 (E-Commerce ↔ WMS)',
    steps: [
      '구매자가 쇼핑몰에서 주문하면 「주문」에 쌓입니다.',
      '「배송」에서 운송사와 송장 번호를 넣고 발송을 확정합니다.',
      '창고(WMS)에서 나가는 주문이면 「WMS 재고 차감」을 켜고 발송합니다. 그러면 창고 재고가 자동으로 줄어듭니다.',
    ],
    sources: ['projects/platform-console/apps/console-web/src/features/ecommerce-guide/data.ts:297-300 (SHIPPING_WMS_NOTE)'],
  },
  {
    key: 'wms-outbound',
    title: '출고 처리 (WMS)',
    steps: [
      '출고 주문이 들어오면 그만큼 재고를 잡아 둡니다.',
      '「출고」에서 꺼내기(피킹) → 포장(패킹) → 출고 확정 순으로 진행합니다. 재고가 모자라면 나중으로 미룹니다.',
      '출고가 확정되면 운송사에 자동으로 알립니다. 실패하면 다시 보냅니다.',
    ],
    sources: [
      'projects/platform-console/apps/console-web/src/features/wms-guide/data.ts:95-112 (RESERVATION_STAGES)',
      'projects/platform-console/apps/console-web/src/features/wms-guide/data.ts:201-280 (ORDER_STATES · TMS_STATES)',
    ],
  },
  {
    key: 'replenishment',
    title: '재고 부족 → 보충 추천 → 발주 (WMS ↔ SCM)',
    steps: [
      '창고 재고가 기준 아래로 떨어지면 알림이 갑니다.',
      'SCM 이 기준에 맞춰 얼마나 채울지 추천합니다.',
      '「보충 계획」에서 승인하면 발주 초안이 만들어집니다. 공급사는 「보충 계획 설정」에서 먼저 정해 둡니다.',
      '초안의 제출과 확정은 「조달」에서 합니다.',
    ],
    sources: ['projects/platform-console/apps/console-web/src/features/scm-guide/data.ts:279-282 (REPLENISHMENT_LOOP_NOTE)'],
  },
  {
    key: 'approval',
    title: '결재 올리기 → 승인 → 대신 결재 (ERP)',
    steps: [
      '「결재함」에서 결재를 올립니다. 승인자는 한 명이어도, 순서대로 여러 명이어도 됩니다.',
      '지금 차례인 승인자만 승인하거나 반려할 수 있고, 모든 처리가 기록에 남습니다.',
      '자리를 비울 때는 「위임」으로 다른 사람에게 맡깁니다. 누가 대신 처리했는지도 표시됩니다.',
    ],
    sources: ['projects/platform-console/apps/console-web/src/features/erp-guide/data.ts:295-310 (APPROVAL_ROUTING_NOTE · DELEGATION_NOTE)'],
  },
];

/* ─────────────────────────── 7. 서버 기동·종료 ─────────────────────────── */

export const STARTUP_STEPS: GuideFact[] = [
  {
    title: 'Start Demo 를 누른다',
    body: 'hubwang.com 의 버튼을 누르면 서버를 켜 달라는 요청이 갑니다. 이번 달 사용 한도를 다 썼으면 켜지지 않습니다.',
    sources: ['infra/demo/aws/terraform/lambda/handler.py:1036-1039', 'infra/demo/aws/README.md:234-238'],
  },
  {
    title: '서버가 켜진다',
    body: '서버가 켜지면 기동 스크립트가 자동으로 실행됩니다.',
    sources: ['infra/demo/demo-stack.service:70', 'infra/demo/README.md:172-178'],
  },
  {
    title: '8개 프로젝트를 올린다',
    body: '이번에 받은 IP 로 접속 주소를 만들고 8개 프로젝트를 띄웁니다.',
    sources: ['infra/demo/demo-boot.sh', 'infra/demo/README.md:172-182'],
  },
  {
    title: '샘플 데이터를 넣는다',
    body: '마지막으로 데모용 데이터를 채웁니다. 여러 번 넣어도 겹치지 않고, 실패해도 데모는 켜집니다.',
    sources: ['infra/demo/seed/README.md:65-80'],
  },
];

export const SHUTDOWN_RULES: GuideFact[] = [
  {
    title: '20분 동안 안 쓰면 꺼진다',
    body: '로그인한 운영자가 콘솔을 쓰는 동안만 켜져 있습니다. 로그인하지 않은 방문자의 열린 탭은 서버를 붙잡아 두지 않습니다.',
    sources: [
      'infra/demo/aws/terraform/variables.tf:133-136',
      'projects/platform-console/apps/console-web/src/app/(console)/layout.tsx:225-230',
    ],
  },
  {
    title: '한 번에 최대 180분',
    body: '계속 쓰고 있어도 켠 지 180분이 지나면 꺼집니다.',
    sources: ['infra/demo/aws/terraform/variables.tf:139-142'],
  },
  {
    title: '한 달에 최대 1800분',
    body: '이번 달 켜진 시간이 합쳐서 1800분을 넘으면 그달에는 더 켤 수 없습니다.',
    sources: ['infra/demo/aws/terraform/variables.tf:185-188', 'infra/demo/aws/README.md:238-240'],
  },
  {
    title: '꺼져 있을 때',
    body: '콘솔 화면은 그대로 열리고, 로그인한 운영자에게는 「데모가 꺼져 있다」는 안내가 보입니다.',
    sources: [
      'infra/demo/README.md:36-38',
      'projects/platform-console/apps/console-web/src/app/(console)/layout.tsx:220-224',
    ],
  },
];
