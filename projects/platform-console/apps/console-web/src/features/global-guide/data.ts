/**
 * 전역 가이드(/guide — TASK-PC-FE-298)의 정적 데이터.
 *
 * 🔴 **모든 사실 항목은 `sources` 에 저장소 파일을 인용한다**(AC-6). 경로는 저장소 루트
 * 기준이고, `:줄` · `§절` · 괄호 설명은 경로 뒤에 붙인다. `tests/unit/GlobalGuideScreen.test.tsx`
 * 가 모든 항목에 인용이 1개 이상 있는지, 그리고 **인용된 경로가 실제로 존재하는지**
 * (`fs.existsSync`)를 검사한다 — 파일이 옮겨지거나 지워지면 이 가이드가 빨개진다.
 * 인용이 가리키는 **줄의 내용**까지는 검사하지 못한다(그건 사람의 몫).
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

export const ARCHITECTURE_FACTS: GuideFact[] = [
  {
    title: '콘솔이 유일한 프런트엔드 (Model B)',
    body: 'wms · scm · erp · finance 는 화면이 없는 백엔드이고, 운영 화면은 전부 이 콘솔 안에서 그려진다. 콘솔이 각 도메인의 게이트웨이/관리 REST API 를 호출한다 — 제품별 사이트로 보내는 런처 모델이 아니다.',
    sources: ['projects/platform-console/PROJECT.md:20', 'docs/adr/ADR-MONO-013-platform-console-foundation.md'],
  },
  {
    title: '신원은 IAM 한 곳 — 두 개의 권한 평면',
    body: '운영자는 IAM(OIDC IdP)에 한 번 로그인한다. 로그인하면 admin-service 가 서명한 운영자 토큰이 붙어 IAM 메뉴(관리 권한)를 열고, 테넌트를 고르면(assume-tenant) 그 테넌트가 구독한 도메인에서 도메인 운영 롤이 파생되어 도메인 화면을 연다. 두 평면은 섞이지 않는다.',
    sources: [
      'projects/platform-console/apps/console-web/src/features/iam-guide/data.ts:113-137 (AUTH_PLANES)',
      'infra/demo/README.md:264-270 (operator 토큰은 admin-service 가 자기 키로 서명)',
      'projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/OperatorRoleDerivation.java:102-107',
    ],
  },
  {
    title: '도메인 게이트웨이 → 도메인 서비스 (2층 리소스 서버)',
    body: '각 도메인 앞단의 gateway-service 가 JWT 와 테넌트를 먼저 검사하고, 그 뒤의 서비스들이 JWKS 로 토큰을 한 번 더 검증한다. 콘솔은 도메인 서비스를 직접 부르지 않고 게이트웨이를 통과한다.',
    sources: ['infra/demo/README.md:222-228', 'infra/demo/README.md:192-198'],
  },
  {
    title: '대시보드는 console-bff 가 모은다',
    body: '「개요」의 5개 도메인 요약과 「도메인 상태」는 console-bff 가 여러 도메인을 fan-out 해 한 응답으로 만든다. 도메인별 운영 화면은 console-web 이 게이트웨이를 직접 부른다(ecommerce 쓰기도 console-bff 를 거치지 않는다).',
    sources: [
      'projects/platform-console/apps/console-bff',
      'projects/platform-console/apps/console-web/src/shared/sample/coverage.ts:55-58',
      'projects/platform-console/specs/services/console-web/architecture.md:19 (console-web → ecommerce gateway direct, no console-bff write leg)',
    ],
  },
  {
    title: '로그인하지 않은 방문자 = 같은 화면, 샘플 데이터',
    body: '세션 쿠키가 하나도 없는 방문자는 로그인 화면으로 튕기지 않고 같은 콘솔 셸로 들어온다. 이때 모든 백엔드 호출 지점은 샘플 라우터가 답하고, 이 가이드 같은 정적 화면은 백엔드를 아예 부르지 않는다.',
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
    consoleRole: '로그인(OIDC IdP) · 운영자/테넌트/권한 관리 · 소비자 계정 · 감사. 콘솔 카탈로그(레지스트리)도 admin-service 가 준다.',
    sources: ['projects/iam-platform/apps', 'projects/iam-platform/specs/services/admin-service/rbac.md:5'],
  },
  {
    project: 'wms-platform',
    label: 'WMS',
    apps: ['gateway-service', 'master-service', 'inbound-service', 'inventory-service', 'outbound-service', 'admin-service', 'notification-service'],
    consoleRole: '입고 · 재고 · 출고 · 마스터 · 운영설정. 콘솔의 조회는 대부분 admin-service 읽기 모델(/api/v1/admin/**)을 거친다.',
    sources: ['projects/wms-platform/apps', 'projects/wms-platform/apps/gateway-service/src/main/resources/application.yml:58-112'],
  },
  {
    project: 'scm-platform',
    label: 'SCM',
    apps: ['gateway-service', 'procurement-service', 'inventory-visibility-service', 'demand-planning-service', 'logistics-service'],
    consoleRole: '조달(발주) · 재고 가시성 · 보충 계획/설정. logistics-service 는 WMS 출고의 운송사 통보를 맡는다.',
    sources: ['projects/scm-platform/apps', 'projects/platform-console/apps/console-web/src/features/scm-guide/data.ts:60-90'],
  },
  {
    project: 'finance-platform',
    label: 'Finance',
    apps: ['gateway-service', 'account-service', 'ledger-service'],
    consoleRole: '계좌 · 잔액 · 거래(account-service)와 복식부기 원장 · 기간 · 대사 · FX(ledger-service).',
    sources: ['projects/finance-platform/apps', 'projects/platform-console/apps/console-web/src/features/finance-guide/data.ts:58-75'],
  },
  {
    project: 'erp-platform',
    label: 'ERP',
    apps: ['gateway-service', 'masterdata-service', 'approval-service', 'read-model-service', 'notification-service'],
    consoleRole: '마스터(부서·직원·직급·비용센터·거래처) · 결재 · 위임 · 통합 조회(읽기 모델).',
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
    consoleRole: '상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 · 정산 · 알림. web-store 는 구매자용 스토어프런트(콘솔 밖).',
    sources: ['projects/ecommerce-microservices-platform/apps', 'projects/platform-console/apps/console-web/src/features/ecommerce-guide/data.ts:54-125'],
  },
  {
    project: 'platform-console',
    label: '콘솔',
    apps: ['console-web', 'console-bff'],
    consoleRole: '이 화면(Next.js App Router)과 대시보드 fan-out BFF.',
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
    label: 'Vercel (프런트엔드)',
    facts: [
      {
        title: '콘솔 = Vercel 프로젝트 kanggle-console',
        body: 'console-web 은 Vercel 에서 빌드·서빙된다(Root Directory = projects/platform-console/apps/console-web). 공개 호스트는 console.hubwang.com. 그래서 데모 백엔드가 꺼져 있어도 콘솔 화면 자체는 뜬다.',
        sources: [
          'projects/platform-console/apps/console-web/VERCEL.md:7-12',
          'TEMPLATE.md:536',
        ],
      },
      {
        title: '론처(Start Demo 페이지)와 IdP 포워더도 Vercel',
        body: 'hubwang.com 의 론처는 kanggle-portfolio, auth.hubwang.com 의 IdP 포워더는 kanggle-auth 프로젝트다. 론처는 머지 즉시 배포된다.',
        sources: ['TEMPLATE.md:533,538', 'infra/demo/aws/README.md:54-55', 'infra/demo/auth-forwarder/README.md'],
      },
    ],
  },
  {
    key: 'aws',
    label: 'AWS (데모 호스트)',
    facts: [
      {
        title: 'EC2 한 대 — scale-to-zero',
        body: '데모 백엔드 전체가 서울 리전(ap-northeast-2)의 EC2 한 대(기본 r6i.2xlarge, 루트 볼륨 100GB)에서 돈다. 아무도 안 쓰면 꺼져서 컴퓨트 비용이 0 이다.',
        sources: [
          'infra/demo/aws/terraform/variables.tf:1-4,18-42,62-65',
          'infra/demo/aws/README.md:3',
        ],
      },
      {
        title: 'API Gateway + Lambda = 켜고 끄는 제어 API',
        body: 'Lambda 하나가 /status · /start · /stop · /heartbeat(및 도메인·묶음 선택) 라우트를 처리하고, 유휴 정지와 월 예산 가드를 건다. 방문자가 누르는 Start Demo 가 이 /start 를 부른다.',
        sources: ['infra/demo/aws/terraform/lambda/handler.py:1036-1049', 'infra/demo/aws/README.md:13-23'],
      },
      {
        title: 'AMI(Packer) — 저장소의 스냅샷',
        body: '도커·저장소 클론·프리빌드 이미지·systemd 유닛을 구운 AMI 로 부팅한다. 부팅 시 git pull 을 하지 않으므로, 백엔드 코드 변경은 재굽기 전까지 데모에 도달하지 않는다.',
        sources: ['infra/demo/aws/packer/demo-ami.pkr.hcl', 'infra/demo/aws/README.md:85-97'],
      },
    ],
  },
  {
    key: 'backend',
    label: '백엔드 (도커 컴포즈)',
    facts: [
      {
        title: '8개 프로젝트, 프로젝트마다 별도 compose 프로젝트',
        body: 'iam · wms · scm · finance · erp · ecommerce · fan · console 이 각자 `-p <slug>` 로 뜨고, 공유 Traefik 이 호스트명으로 라우팅한다. 컨테이너는 약 96개.',
        sources: ['infra/demo/README.md:3-22', 'infra/demo/projects.sh', 'infra/demo/aws/README.md:3-5'],
      },
      {
        title: '외부로 열린 것은 게이트웨이뿐',
        body: 'traefik-net 에 라우터로 붙는 것은 각 프로젝트의 gateway-service 이고, 백엔드 서비스는 JWKS 도달을 위해 네트워크에만 붙는다(라우터 라벨 없음).',
        sources: ['infra/demo/README.md:192-198,244-248'],
      },
    ],
  },
  {
    key: 'auth',
    label: '인증 서버',
    facts: [
      {
        title: 'IAM auth-service (OIDC)',
        body: '브라우저의 OIDC 경로(/oauth2 · /login · /.well-known)는 IAM 게이트웨이를 거치지 않고 auth-service 로 직행한다. 콘솔은 로그인 후 서버 측에서 토큰을 교환하고, 운영자 토큰은 admin-service 가 따로 서명한다.',
        sources: ['infra/demo/README.md:164', 'infra/demo/README.md:264-270', 'infra/demo/iam-traefik.override.yml'],
      },
      {
        title: '공개 IdP 이름 = auth.hubwang.com',
        body: '데모 호스트의 IP 는 부팅마다 바뀌므로 Vercel 의 포워더가 고정된 https 이름을 제공한다. 데모가 꺼져 있으면 503 이다.',
        sources: ['TEMPLATE.md:538', 'infra/demo/auth-forwarder/src/app/[[...path]]/route.ts'],
      },
    ],
  },
  {
    key: 'database',
    label: '데이터베이스',
    facts: [
      {
        title: 'MySQL 8.0 — iam · erp · finance',
        body: '세 프로젝트는 MySQL 컨테이너를 쓴다(finance 는 서비스별 2개).',
        sources: [
          'projects/iam-platform/docker-compose.yml:55',
          'projects/erp-platform/docker-compose.yml:313',
          'projects/finance-platform/docker-compose.yml:207,238',
        ],
      },
      {
        title: 'PostgreSQL 16 — ecommerce · wms · scm · fan',
        body: 'ecommerce 는 서비스마다 DB 를 따로 둔다(10개). wms · scm · fan 은 각 1개.',
        sources: [
          'projects/ecommerce-microservices-platform/docker-compose.yml:416-641',
          'projects/wms-platform/docker-compose.yml:51',
          'projects/scm-platform/docker-compose.yml:88',
          'projects/fan-platform/docker-compose.yml:373',
        ],
      },
      {
        title: 'Redis · Kafka 도 프로젝트마다',
        body: 'redis·kafka 는 여러 프로젝트가 같은 키 이름을 쓰지만 compose 프로젝트가 달라 서로 다른 컨테이너다.',
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
    title: '운영자 온보딩 → 도메인 화면 열기 (IAM)',
    steps: [
      'SUPER_ADMIN 이 회사 관리자를 운영자로 만들고 TENANT_ADMIN 을 준다(「운영자 관리」).',
      'TENANT_ADMIN 이 자기 회사 직원을 운영자로 등록하고 테넌트에 배정한다.',
      '테넌트 주인(TENANT_BILLING_ADMIN)이 「도메인 구독」에서 쓸 도메인을 켠다.',
      '직원이 로그인해 그 테넌트를 고르면 구독 도메인의 운영 롤이 파생되어 도메인 화면이 열린다.',
    ],
    sources: [
      'projects/platform-console/apps/console-web/src/features/iam-guide/data.ts:295-311 (DELEGATION_CHAIN)',
      'projects/iam-platform/specs/services/admin-service/rbac.md:94-95',
    ],
  },
  {
    key: 'order-to-ship',
    title: '주문 → 배송 → WMS 재고 차감 (E-Commerce ↔ WMS)',
    steps: [
      '구매자가 스토어에서 주문하면 「주문」에 쌓인다.',
      '「배송」에서 운송사·운송장을 넣고 발송(SHIPPED)을 확정한다.',
      'WMS 로 라우팅된 주문이면 「WMS 재고 차감」 토글을 켜 발송하고, 이커머스가 이벤트를 발행해 WMS 가 물리 재고를 차감한다.',
    ],
    sources: ['projects/platform-console/apps/console-web/src/features/ecommerce-guide/data.ts:297-300 (SHIPPING_WMS_NOTE)'],
  },
  {
    key: 'wms-outbound',
    title: '출고 처리 (WMS)',
    steps: [
      '출고 주문이 접수되면 가용 재고가 예약으로 옮겨간다.',
      '「출고」에서 피킹 → 패킹 → 출고 확정을 진행한다(재고 부족이면 이월).',
      '출고가 확정되면 화물이 생기고 운송사(TMS)에 통보된다 — 실패하면 발송 재시도.',
    ],
    sources: [
      'projects/platform-console/apps/console-web/src/features/wms-guide/data.ts:95-112 (RESERVATION_STAGES)',
      'projects/platform-console/apps/console-web/src/features/wms-guide/data.ts:201-280 (ORDER_STATES · TMS_STATES)',
    ],
  },
  {
    key: 'replenishment',
    title: '저재고 → 보충 추천 → 발주 (WMS ↔ SCM)',
    steps: [
      'WMS 재고가 임계 아래로 내려가면 저재고 알림이 발행된다.',
      'SCM demand-planning 이 재주문 정책과 비교해 보충 추천을 만든다.',
      '「보충 계획」에서 승인하면 공급사 매핑으로 DRAFT 발주가 생긴다(매핑이 없으면 「보충 계획 설정」에서 먼저 등록).',
      'DRAFT 발주의 제출·확정은 「조달」에서 따로 진행한다.',
    ],
    sources: ['projects/platform-console/apps/console-web/src/features/scm-guide/data.ts:279-282 (REPLENISHMENT_LOOP_NOTE)'],
  },
  {
    key: 'approval',
    title: '결재 상신 → 다단계 승인 → 대결 (ERP)',
    steps: [
      '「결재함」에서 결재 요청을 올린다(승인자 1명 또는 순서 있는 N명).',
      '현재 단계의 승인자만 승인·반려·철회할 수 있고 모든 전이가 이력에 남는다.',
      '승인자가 부재하면 「위임」으로 대결 권한을 맡기고, 대결 처리는 실제 승인자가 표시된다.',
    ],
    sources: ['projects/platform-console/apps/console-web/src/features/erp-guide/data.ts:295-310 (APPROVAL_ROUTING_NOTE · DELEGATION_NOTE)'],
  },
];

/* ─────────────────────────── 7. 서버 기동·종료 ─────────────────────────── */

export const STARTUP_STEPS: GuideFact[] = [
  {
    title: '① Start Demo',
    body: '방문자가 론처(hubwang.com)의 Start Demo 를 누르면 제어 API 의 POST /start 가 불린다. 월 예산을 다 썼으면 429 로 거절하고 켜지 않는다.',
    sources: ['infra/demo/aws/terraform/lambda/handler.py:1036-1039', 'infra/demo/aws/README.md:234-238'],
  },
  {
    title: '② EC2 부팅 → systemd 유닛',
    body: 'Lambda 가 인스턴스를 켜면 systemd 의 demo-stack.service 가 demo-boot.sh 를 실행한다.',
    sources: ['infra/demo/demo-stack.service:70', 'infra/demo/README.md:172-178'],
  },
  {
    title: '③ 데모 도메인 파생 → 전체 스택 기동',
    body: 'demo-boot.sh 가 IMDSv2 로 공인 IP 를 읽어 DEMO_DOMAIN(<ip>.sslip.io)을 만들고 demo-up.sh 로 8개 프로젝트를 올린다. 파생에 실패하면 local 로 떨어지고 그 사실을 말한다.',
    sources: ['infra/demo/demo-boot.sh', 'infra/demo/README.md:172-182'],
  },
  {
    title: '④ 데이터 시드',
    body: 'demo-up.sh 가 마지막에 시드를 실제 API 로 넣는다(멱등). 시드 실패가 데모 기동을 막지는 않는다.',
    sources: ['infra/demo/seed/README.md:65-80'],
  },
];

export const SHUTDOWN_RULES: GuideFact[] = [
  {
    title: '유휴 정지 — 기본 20분',
    body: '마지막 하트비트 뒤 이 시간 동안 조용하면 인스턴스가 스스로 꺼진다. 콘솔은 로그인한 운영자에게만 하트비트를 보낸다 — 샘플 방문자의 열린 탭은 데모를 깨워 두지 않는다.',
    sources: [
      'infra/demo/aws/terraform/variables.tf:133-136',
      'projects/platform-console/apps/console-web/src/app/(console)/layout.tsx:225-230',
    ],
  },
  {
    title: '세션 상한 — 기본 180분',
    body: '하트비트가 계속 와도 한 세션은 이 시간을 넘기지 못한다.',
    sources: ['infra/demo/aws/terraform/variables.tf:139-142'],
  },
  {
    title: '월 예산 — 기본 1800분',
    body: '한 달 누적 가동 분이 이 값을 넘으면 /start 가 429 를 돌려준다. 세션 규칙만으로는 매일 켜는 것을 막지 못해 지출에 상한을 건다.',
    sources: ['infra/demo/aws/terraform/variables.tf:185-188', 'infra/demo/aws/README.md:238-240'],
  },
  {
    title: '수동 종료 · 꺼져 있을 때의 콘솔',
    body: '호스트 안에서는 demo-down.sh 가 전체를 내린다. 데모가 꺼져 있어도 콘솔은 Vercel 에서 뜨며, 로그인한 운영자에게는 「데모가 꺼져 있다」 배너가 나타난다.',
    sources: [
      'infra/demo/README.md:36-38',
      'projects/platform-console/apps/console-web/src/app/(console)/layout.tsx:220-224',
    ],
  },
];
