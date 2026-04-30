# Project Taxonomy

> **Version**: 0.1
> **Status**: authoritative **narrative definition** for domains and traits used by the taxonomy-based rule system.

이 문서는 `PROJECT.md`에서 선언할 수 있는 **domain**과 **trait** 값의 **상세 정의**(정의·전형 서브시스템·언제 고르는가·호환성)를 담는다. `rules/README.md`의 규칙 해결(resolution) 절차가 이 카탈로그를 참조한다.

---

## Companion Routing Layer

짧고 테이블 형태인 **디스패치 카탈로그**와 **활성화 매핑**은 `.claude/config/` 아래에 별도로 존재하며, 에이전트 런타임이 빠르게 훑는 용도다:

- `.claude/config/domains.md` — domain 카탈로그 (list + rule + example)
- `.claude/config/traits.md` — trait 카탈로그 (list + rule + example)
- `.claude/config/activation-rules.md` — trait/domain → 활성화되는 규칙 카테고리·스킬 번들 매핑 + 각 항목에서 상세 규칙 파일로의 링크

**두 계층의 역할 분리**:

| 축 | `.claude/config/` | 이 문서 |
|---|---|---|
| 목적 | 에이전트가 빠르게 훑고 분기 판단 | 각 분류의 narrative 정의와 호환성 |
| 형식 | 리스트·표·발동 규칙 (짧음) | 3줄 블록 + Selection Guide + Common Combinations (김) |
| 진실 소스 | 카탈로그 멤버십 ("X가 유효한 값인가?") + 활성화 매핑 | narrative 정의 ("X가 무엇을 의미하는가?") |

**drift 방지 규칙**: 신규 domain/trait을 추가할 때는 반드시 **이 파일**과 `.claude/config/domains.md` (또는 `traits.md`)과 `.claude/config/activation-rules.md`를 **모두 같은 PR에서** 갱신해야 한다. 어느 한쪽만 수정하면 라우팅 레이어와 상세 정의가 어긋난다.

---

## Versioning

- **Current**: `v0.1` — 초기 카탈로그. 모든 `PROJECT.md`는 frontmatter에 `taxonomy_version: 0.1`을 선언해야 한다.
- **Change policy**:
  - 신규 domain/trait 추가는 minor bump (`0.2`, `0.3` …). 기존 프로젝트는 영향 없음.
  - domain/trait 의미의 breaking change 또는 삭제는 major bump (`1.0`). 모든 기존 `PROJECT.md`를 마이그레이션해야 하며, 이 파일 상단에 마이그레이션 노트를 남긴다.
  - **절대 금지**: 카탈로그에 없는 태그를 `PROJECT.md`에 선언. 새 태그가 필요하면 먼저 이 파일과 `rules/domains/` 또는 `rules/traits/` 아래 해당 rules 파일을 같은 변경에서 함께 추가해야 한다.

---

## Domains (38)

**선택 원칙**: 각 프로젝트는 **정확히 하나의 primary domain**을 선언한다. 여러 도메인의 특성이 섞여 있으면 **핵심 비즈니스가 무엇으로 규정되는가**를 기준으로 고르고, 나머지는 traits 또는 서브시스템 수준에서 다룬다.

### Commerce & Transactions

#### ecommerce
- **정의**: 단일 판매자가 상품/서비스를 직접 판매하는 B2C/B2B 상거래 시스템
- **전형 서브시스템**: Product, Cart, Order, Payment, Shipping, Promotion, Review, Wishlist, Notification
- **언제 고르는가**: 자체 상품 카탈로그를 소유하고, 셀러 온보딩/정산 로직이 없는 경우

#### marketplace
- **정의**: 다수의 판매자가 참여하는 양면 시장 플랫폼. 플랫폼 자체는 재고를 소유하지 않음
- **전형 서브시스템**: Seller Onboarding, Listing, Order Routing, Settlement, Commission, Dispute, Review
- **언제 고르는가**: 셀러/구매자가 분리되어 있고 정산·수수료 규칙이 도메인 핵심일 때

#### reservation
- **정의**: 시간·좌석·자원을 예약하고 취소/변경을 관리하는 시스템 (호텔, 항공, 식당, 병원 등)
- **전형 서브시스템**: Inventory Calendar, Booking, Cancellation, Overbooking Control, Refund
- **언제 고르는가**: 상품이 "한정된 슬롯"이고 재고가 시간에 묶여 있을 때

### Enterprise & Internal Systems

#### mes
- **정의**: Manufacturing Execution System. 생산 현장 실행·추적 시스템
- **전형 서브시스템**: Work Order, Routing, Machine Monitoring, Quality Inspection, Yield Tracking
- **언제 고르는가**: 공장 현장 장비/공정 데이터를 실시간 수집·제어할 때

#### erp
- **정의**: Enterprise Resource Planning. 회계·구매·재고·HR을 통합 관리하는 기간계 시스템
- **전형 서브시스템**: GL, AP/AR, Procurement, Inventory, HR, Fixed Assets
- **언제 고르는가**: 전사 기간 업무 데이터가 중심이고 모듈 간 일관성이 핵심일 때

#### groupware
- **정의**: 사내 커뮤니케이션/문서/결재/일정 협업 시스템
- **전형 서브시스템**: Approval, Document, Calendar, Organization, Attendance
- **언제 고르는가**: 내부 임직원 대상 생산성 도구가 핵심일 때

#### accounting-system
- **정의**: 회계 장부·전표·결산 처리에 특화된 시스템
- **전형 서브시스템**: Journal, Ledger, Trial Balance, Closing, Tax
- **언제 고르는가**: 복식부기 기반 장부 처리가 중심이고 ERP 전체가 아닐 때

### Data & Analytics

#### data-platform
- **정의**: 조직 전반의 데이터 수집·저장·처리를 제공하는 플랫폼
- **전형 서브시스템**: Ingestion, Data Lake, Data Warehouse, Catalog, Lineage, Access Control
- **언제 고르는가**: 비즈니스 로직보다 데이터 흐름 자체가 제품일 때

#### analytics
- **정의**: 제품/사용자 행동 분석 시스템
- **전형 서브시스템**: Event Tracking, Funnel, Cohort, Retention, A/B Test
- **언제 고르는가**: 특정 제품의 분석 기능이 핵심이며 BI 도구와는 구분될 때

#### bi
- **정의**: 비즈니스 인텔리전스 — 지표 대시보드, 리포트, 셀프서비스 쿼리
- **전형 서브시스템**: Semantic Layer, Dashboard, Scheduled Report, Drilldown
- **언제 고르는가**: 비즈니스 사용자가 데이터를 시각화·탐색하는 것이 주 목적일 때

#### reporting
- **정의**: 정형·주기적 보고서 생성 전용 시스템
- **전형 서브시스템**: Template, Scheduled Job, Export (PDF/Excel), Distribution
- **언제 고르는가**: 생성-배포 워크플로가 중심이고 사용자 탐색은 부차적일 때

#### ad-platform
- **정의**: 광고 입찰·노출·과금·리포팅 시스템
- **전형 서브시스템**: Campaign, Bidding, Targeting, Ad Serving, Attribution, Billing
- **언제 고르는가**: 광고주·매체·사용자 3자 구조가 핵심일 때

#### cdp
- **정의**: Customer Data Platform — 통합 고객 프로필·세그먼트 빌더
- **전형 서브시스템**: Identity Resolution, Profile Store, Segment, Activation
- **언제 고르는가**: 고객 데이터 통합·활성화가 독립 제품일 때

#### dmp
- **정의**: Data Management Platform — 서드파티 세그먼트·오디언스 관리 (주로 광고 도메인)
- **전형 서브시스템**: Audience, Lookalike, Cookie/ID Sync, Exchange Integration
- **언제 고르는가**: 광고 오디언스 데이터 거래·매칭이 중심일 때

### Content & Community

#### community
- **정의**: 사용자 간 대화·관계를 중심에 두는 커뮤니티 플랫폼
- **전형 서브시스템**: Profile, Post, Comment, Follow, Feed, Moderation
- **언제 고르는가**: 사용자 생성 콘텐츠와 상호작용이 제품 가치의 핵심일 때

#### sns
- **정의**: 대규모 소셜 네트워킹 서비스 — 관계 그래프와 피드 알고리즘이 중심
- **전형 서브시스템**: Social Graph, Timeline, Notification, DM, Recommendation
- **언제 고르는가**: 팔로우 관계와 피드 배포가 아키텍처의 핵심일 때

#### forum
- **정의**: 게시판 스타일 커뮤니티 (threaded discussion)
- **전형 서브시스템**: Board, Thread, Reply, Tag, Moderation
- **언제 고르는가**: 주제별 토론 구조가 주력일 때

#### content-platform
- **정의**: 뉴스/블로그/매거진 등 에디토리얼 콘텐츠 제공 플랫폼
- **전형 서브시스템**: CMS, Editorial Workflow, Taxonomy, Syndication, Paywall
- **언제 고르는가**: 편집 워크플로와 콘텐츠 배포가 중심일 때

### Media & Streaming

#### ott
- **정의**: Over-The-Top 비디오 스트리밍 서비스
- **전형 서브시스템**: VOD, Playback, DRM, Recommendation, Subscription, CDN
- **언제 고르는가**: 장편/시리즈 영상 제공 및 구독 경험이 핵심일 때

#### media-streaming
- **정의**: 오디오/비디오 스트리밍 일반 (ott 아닌 경우 — 팟캐스트, 음악, 교육 영상)
- **전형 서브시스템**: Catalog, Playback, Encoding, CDN, Playlist
- **언제 고르는가**: OTT 특유의 DRM/구독 모델과 구분되는 미디어 전달일 때

#### live-streaming
- **정의**: 실시간 스트리밍 (방송, 라이브 커머스, 라이브 코딩 등)
- **전형 서브시스템**: Ingest, Transcoding, Low-latency Delivery, Chat, Recording
- **언제 고르는가**: 실시간성과 양방향 상호작용이 핵심일 때

### SaaS & Collaboration

#### saas
- **정의**: 일반 B2B SaaS (특정 도메인에 묶이지 않음)
- **전형 서브시스템**: Tenant, User/Role, Billing, Usage Metering, Admin
- **언제 고르는가**: 제품이 구독형 B2B 소프트웨어이고 구체 도메인을 특정하기 어려울 때

#### collaboration-tool
- **정의**: 다중 사용자 협업 도구 (문서, 화이트보드, 프로젝트 관리 등)
- **전형 서브시스템**: Workspace, Document, Real-time Sync, Comment, Permission
- **언제 고르는가**: 실시간 공동 작업이 제품 가치의 중심일 때

#### crm
- **정의**: Customer Relationship Management — 영업/마케팅/고객 지원 파이프라인
- **전형 서브시스템**: Lead, Contact, Opportunity, Pipeline, Activity, Ticket
- **언제 고르는가**: 고객 관계·영업 파이프라인이 도메인 중심일 때

#### developer-platform
- **정의**: 개발자 대상 플랫폼 (API 게이트웨이, CI/CD, 모니터링, PaaS 등)
- **전형 서브시스템**: API Key, SDK, Usage Metrics, Documentation Portal, Webhook
- **언제 고르는가**: 주 사용자가 개발자이고 API/SDK가 핵심 인터페이스일 때

### Financial Services

#### fintech
- **정의**: 금융 서비스 일반 (송금, 대출, 투자, 보험 등 비은행 금융)
- **전형 서브시스템**: Account, Wallet, Transaction, KYC, Risk, Compliance
- **언제 고르는가**: 금융 제품 자체가 핵심이지만 전통 은행/PG가 아닐 때

#### pg
- **정의**: Payment Gateway — 카드/계좌 기반 결제 중계 서비스
- **전형 서브시스템**: Acquirer Integration, Tokenization, Settlement, Chargeback, Fraud
- **언제 고르는가**: 가맹점-카드사/은행 사이 결제 처리가 핵심일 때

#### banking
- **정의**: 전통 은행 — 예금, 대출, 송금, 계좌 운영
- **전형 서브시스템**: Core Banking, Ledger, Loan, Remittance, AML
- **언제 고르는가**: 은행업 인가를 전제로 한 기간계 시스템일 때

#### securities
- **정의**: 증권사·자산운용 — 주문/체결/포지션/정산
- **전형 서브시스템**: Order Management, Execution, Clearing, Settlement, Portfolio
- **언제 고르는가**: 시장 주문 처리와 포지션 관리가 도메인 핵심일 때

### Logistics & Mobility

#### logistics
- **정의**: 물류 일반 — 운송 계획, 창고, 배송 추적
- **전형 서브시스템**: Shipment, Route Planning, Tracking, Fleet, POD (Proof of Delivery)
- **언제 고르는가**: 화물/물품의 물리적 이동이 도메인 핵심일 때

#### wms
- **정의**: Warehouse Management System — 창고 내 재고·입출고·피킹 관리
- **전형 서브시스템**: Receiving, Putaway, Picking, Packing, Shipping, Cycle Count
- **언제 고르는가**: 창고 운영 워크플로가 제품 핵심일 때

#### delivery-platform
- **정의**: 라스트마일 배송 플랫폼 (음식, 퀵 등)
- **전형 서브시스템**: Order, Dispatch, Rider/Courier, Tracking, Settlement
- **언제 고르는가**: 실시간 라이더 배차·배송이 핵심일 때

#### fleet-management
- **정의**: 차량/장비 플릿 운영 관리
- **전형 서브시스템**: Vehicle, Driver, Maintenance, Telematics, Route
- **언제 고르는가**: 다수의 차량·장비의 상태와 운용이 중심일 때

### Education

#### edtech
- **정의**: 교육 기술 일반 — 학습 콘텐츠, 평가, 학습자 관리
- **전형 서브시스템**: Content, Assessment, Progress, Certification
- **언제 고르는가**: 교육 제품이 핵심이지만 LMS/온라인 강의가 아닌 혼합 형태일 때

#### lms
- **정의**: Learning Management System — 기관 대상 학습 관리 시스템
- **전형 서브시스템**: Course, Enrollment, Assignment, Grade, Attendance
- **언제 고르는가**: 학교/기업이 학습자를 기관 단위로 관리할 때

#### online-course
- **정의**: 개인 학습자 대상 온라인 강의 플랫폼
- **전형 서브시스템**: Course Catalog, Video Playback, Quiz, Certificate, Instructor Portal
- **언제 고르는가**: 유료 비디오 강의 판매·수강이 핵심일 때

### Gaming

#### game-platform
- **정의**: 게임 플레이어 대상 서비스 — 계정, 매칭, 랭킹, 상점, 소셜
- **전형 서브시스템**: Account, Match, Lobby, Leaderboard, In-game Shop, Friend
- **언제 고르는가**: 실시간 게임 플레이어 경험이 도메인 핵심일 때

#### game-backoffice
- **정의**: 게임 운영·CS·정산을 위한 백오피스 시스템
- **전형 서브시스템**: Operator Console, Ban, Reward, Log Viewer, Anti-cheat, Support Ticket
- **언제 고르는가**: 게임 운영 효율이 제품 가치이고 플레이어 대면 서비스는 아닐 때

---

## Traits (11)

**선택 원칙**: trait은 **복수 선택 가능**하지만, 남발하면 규칙이 부풀어 실행 비용이 오른다. "이 특성이 있으면 아키텍처가 달라진다"는 기준으로만 선언한다.

### transactional
- **정의**: 강한 일관성(consistency)과 멱등성(idempotency)이 요구되는 쓰기 트래픽이 도메인의 중심
- **활성화 시 추가 규칙**: idempotency key, distributed transaction/saga 패턴, optimistic locking, audit trail for state transitions
- **언제 고르는가**: 주문·결제·재고·송금 등 "두 번 실행되면 안 되는" 연산이 핵심일 때

### regulated
- **정의**: 법·규제 요구사항(PCI-DSS, GDPR, HIPAA, SOX, K-FSC 등)을 준수해야 하는 시스템
- **활성화 시 추가 규칙**: 데이터 보존·파기 정책, 암호화 의무, 접근 감사, 컴플라이언스 증빙 문서
- **언제 고르는가**: 규제 인증/감사를 받아야 하거나 법적 의무가 명시되어 있을 때

### data-intensive
- **정의**: 대용량 데이터 저장·처리·이동이 시스템 설계의 주요 제약
- **활성화 시 추가 규칙**: 파티셔닝/샤딩 전략, 배치-스트리밍 혼합 처리, 데이터 보존 정책, 스키마 진화
- **언제 고르는가**: TB~PB급 데이터 이동이 일상이고 스토리지/네트워크 비용이 설계를 좌우할 때

### real-time
- **정의**: 초 단위 이하의 지연과 즉각적 반응이 도메인 요구사항
- **활성화 시 추가 규칙**: 이벤트 스트림 아키텍처, 백프레셔, 저지연 프로토콜(WS/SSE/gRPC streaming), SLO 강화
- **언제 고르는가**: 주식 체결, 라이브 채팅, 모니터링 대시보드, 게임 매치메이킹 등

### read-heavy
- **정의**: 읽기 트래픽이 쓰기보다 수십~수백 배 많음
- **활성화 시 추가 규칙**: 다계층 캐시(CDN/앱/DB), 읽기 복제본, 쿼리 페이지네이션 최적화, 검색 인덱싱
- **언제 고르는가**: 조회/검색이 사용자 경험의 중심이고 쓰기는 상대적으로 드물 때

### integration-heavy
- **정의**: 외부 시스템 연동(API, 웹훅, 파일 교환)이 다수이며, 연동 안정성이 시스템 품질을 좌우
- **활성화 시 추가 규칙**: circuit breaker, retry with backoff, DLQ, idempotent side effects, vendor fallback
- **언제 고르는가**: PG/통신사/이메일/SMS/배송사 등 3개 이상의 외부 벤더와 상시 연동할 때

### internal-system
- **정의**: 내부 임직원 전용. 외부 공개 트래픽 없음
- **활성화 시 추가 규칙**: SSO 연동, 권한 매트릭스, 감사 로그, 외부 노출 금지, 내부 네트워크 제약
- **언제 고르는가**: 퍼블릭 인터넷에 노출되지 않고 사내 네트워크 또는 VPN 뒤에만 배치될 때

### multi-tenant
- **정의**: 하나의 시스템이 다수의 고객/조직(tenant)을 서비스하며 데이터가 논리적/물리적으로 격리됨
- **활성화 시 추가 규칙**: tenant isolation (DB/row/schema 레벨), per-tenant quota, cross-tenant leak 방지 테스트
- **언제 고르는가**: 제품이 B2B SaaS이거나 고객사별 데이터 격리가 계약 요건일 때

### audit-heavy
- **정의**: 모든 주요 상태 변경의 행위자·시점·이유를 추적·보존해야 하는 시스템
- **활성화 시 추가 규칙**: 변경 이력(event sourcing 또는 change log), 불변 감사 저장소, 조회 API, 보존 기간
- **언제 고르는가**: 금융, 의료, 법무, 보안, 게임 제재 등에서 "누가 무엇을 바꿨는가"가 책임 추적에 필수일 때

### batch-heavy
- **정의**: 대량 배치 처리가 시스템 가치의 핵심이며, 실시간 처리보다 스케줄 기반 일괄 처리가 지배적
- **활성화 시 추가 규칙**: 배치 스케줄러, 재시도·체크포인트, 배치-온라인 경합 관리, 리소스 격리
- **언제 고르는가**: 야간 ETL, 월 결산, 대량 리포트 생성, 정산, 빌링 등이 중심일 때

### content-heavy
- **정의**: 콘텐츠(상품 정보, 기사, 미디어, 리뷰) 자체가 자산이며 생산·큐레이션·배포가 핵심 흐름
- **활성화 시 추가 규칙**: CMS/에디토리얼 워크플로, 미디어 인코딩, 검색 인덱싱, 콘텐츠 캐시
- **언제 고르는가**: 콘텐츠 품질과 전달 속도가 사용자 경험의 중심일 때

---

## Common Combinations (예시)

현실 프로젝트에서 자주 등장하는 조합:

- **ecommerce + transactional + content-heavy + read-heavy + integration-heavy** → 일반적인 B2C 이커머스 (first-project 자체)
- **fintech + transactional + regulated + audit-heavy** → 송금/지갑 앱
- **pg + transactional + regulated + audit-heavy + integration-heavy** → PG 사업자
- **banking + regulated + audit-heavy + transactional + internal-system** → 은행 기간계
- **saas + multi-tenant + regulated + audit-heavy** → 엔터프라이즈 B2B SaaS
- **marketplace + transactional + integration-heavy + content-heavy** → 오픈마켓
- **ott + content-heavy + read-heavy + real-time** → 스트리밍 서비스
- **logistics + real-time + integration-heavy** → 물류 추적 플랫폼
- **lms + internal-system + multi-tenant + content-heavy** → 기업 교육 플랫폼
- **cdp + data-intensive + batch-heavy + integration-heavy** → 고객 데이터 플랫폼
- **game-backoffice + internal-system + audit-heavy** → 게임 운영 콘솔

---

## Incompatibilities & Notes

| 조합 | 상태 | 비고 |
|---|---|---|
| `batch-heavy` + `real-time` | 경고 | 공존 가능하지만 서브시스템 분리 정당화 필요. 두 트레이드오프가 한 컴포넌트에 섞이면 안 됨 |
| `internal-system` + `multi-tenant` | 허용 | 내부 사용이어도 조직 단위 격리가 필요하면 모순 아님 |
| `content-heavy` + `transactional` | 허용 | 이커머스에서 전형적 |
| `regulated` 없이 `audit-heavy` | 허용 | 법적 의무가 없어도 내부 정책상 감사 로그가 필요할 수 있음 |
| `regulated` 있는데 `audit-heavy` 없음 | 경고 | 대부분의 규제는 감사 추적을 요구하므로 재검토 권장 |

---

## Selection Guide

새 프로젝트에서 `PROJECT.md`를 작성할 때 다음 질문을 순서대로 답한다:

1. **이 시스템의 주 사용자는?** 외부 고객(B2C), 다른 사업자(B2B), 내부 임직원(internal)?
2. **핵심 비즈니스 활동 한 줄로는?** → 해당 활동이 속하는 상위 도메인 카테고리 선택
3. **그 카테고리에서 우리가 하는 일은?** → 단일 domain 선택 (정확히 1개)
4. **쓰기 트래픽이 '두 번 실행되면 안 되는가'?** → `transactional`
5. **법·규제 준수 의무가 있는가?** → `regulated` (+ 대개 `audit-heavy` 함께)
6. **초 단위 지연이 제품 가치에 직접적인가?** → `real-time`
7. **조회가 쓰기보다 압도적으로 많은가?** → `read-heavy`
8. **외부 API 3개 이상과 상시 연동인가?** → `integration-heavy`
9. **대량 데이터 이동이 일상인가?** → `data-intensive` (경우에 따라 `batch-heavy` 함께)
10. **콘텐츠 자체가 자산인가?** → `content-heavy`
11. **여러 테넌트를 서비스하는가?** → `multi-tenant`
12. **외부 인터넷 노출이 없는가?** → `internal-system`

확신이 없는 trait는 **선언하지 말 것**. on-demand 원칙에 따라 나중에 추가해도 규칙이 자동 활성화된다.
