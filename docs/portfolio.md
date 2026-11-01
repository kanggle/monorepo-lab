# 포트폴리오 — 8개 서비스로 이루어진 하나의 플랫폼

> **이 문서는 아무것도 실행하지 않고 5분만 쓰는 사람을 위한 것입니다.**
> 클론도, 설치도, 로그인도 필요 없습니다. 스크롤만 하면 됩니다.
>
> 개발자용 안내는 따로 있습니다 — [로컬 개발 시작하기](../README.md#️-getting-started) ·
> [면접 데모 워크스루](guides/interview-demo-walkthrough.md) · [개발 프로세스](guides/development-process.md).

---

## 한 문단으로

**여덟 개를 따로 만든 것이 아니라, 한 회사가 실제로 굴리는 시스템을 조각낸 것입니다.**
고객은 스토어에서 주문하고(ecommerce), 그 주문은 창고로 흘러가 피킹·출고되며(wms), 재고가
떨어지면 보충 계획이 서고 발주가 나갑니다(scm). 돈은 복식부기 원장에 남고(finance), 사람과
조직·결재는 ERP 가 들고(erp), **누가 무엇을 할 수 있는가는 한 곳에서만 정해집니다**(iam).
운영자는 도메인마다 다른 화면을 갈아타지 않고 **통합 콘솔 하나**로 여섯을 다룹니다
(platform-console). 팬 플랫폼은 같은 뼈대 위에 **전혀 다른 도메인**(멤버십 구독·게이팅된
콘텐츠)을 올려 본 것입니다 — 규칙과 공용 라이브러리가 도메인에 안 묶여 있다는 증거입니다.

🔵 서비스 사이는 **이벤트**로 잇습니다. 주문 → 출고는 saga 로 조율하고, 발행은 outbox 를
거칩니다. 그래서 한 서비스가 잠깐 죽어도 다른 쪽이 같이 죽지 않습니다.

---

## 지금 열리는 데모

**[hubwang.com](https://hubwang.com)** — 세 화면은 **로그인 없이 지금 바로** 둘러볼 수
있습니다. 서버를 안 켜도 됩니다.

> 🔴 **글쓰기·주문·운영 같은 로그인 후 기능은 데모 서버가 필요하고, 켜는 데 약 10분 걸립니다.**
> (2026-09-09 실측: 콜드 9분 14초 · 웜 9분 16초. 비용 때문에 평소엔 꺼 두고, 20분간 활동이
> 없으면 자동으로 종료됩니다.) 그 10분을 **모르면 고장으로 읽히기 때문에** 여기 적습니다.

| | 로그인 없이 | 로그인 후 |
|---|---|---|
| [스토어](https://store.hubwang.com) | 상품 목록·상세·검색·카테고리·정렬 | 장바구니 · 주문 · 결제 · 마이페이지 |
| [팬 플랫폼](https://fan.hubwang.com) | 공개 피드 · 아티스트 · 멤버십 소개 | 글쓰기 · 댓글 · 팔로우 · 멤버십 가입 |
| [운영자 콘솔](https://console.hubwang.com) | 실제 화면을 샘플 데이터로 봅니다 | 6개 도메인 운영 · IAM · 감사 로그 (같은 화면, 실제 데이터) |

**[알려진 한계는 숨기지 않고 전부 적어 두었습니다](guides/interview-demo-walkthrough.md#6-알려진-한계-조용한-누락-없이)**
— 설계상 그런 것과 아직 못 고친 것을 갈라서.

---

## 서비스

> 🔵 아래 화면 중 **운영자 콘솔이 그리는 것**은 그렇게 밝혀 두었습니다. wms·scm·finance·erp 는
> 자체 UI 가 없고 **API 입니다** — 콘솔이 그 여섯을 한 화면에서 다룹니다(Model B).

### 이커머스 — [`projects/ecommerce-microservices-platform`](../projects/ecommerce-microservices-platform/)

백엔드 **12개 마이크로서비스** + Next.js 스토어프런트. saga 오케스트레이션 · outbox ·
Elasticsearch 상품 검색 · MinIO 업로드 · 셀러 정산.

<p align="center">
  <img src="../projects/ecommerce-microservices-platform/docs/screenshots/09-store-products.jpg" width="820" alt="스토어 상품 카탈로그"><br>
  <em>스토어 — 카테고리·정렬 필터, 검색, 위시리스트 토글, 페이지네이션</em>
</p>

### 팬 플랫폼 — [`projects/fan-platform`](../projects/fan-platform/)

백엔드 **5개** + Next.js. 구독 상태 기계 · PG 모의 연동 · outbox · 멤버십 이벤트를 받아
쌓는 알림 인박스.

<p align="center">
  <img src="../projects/fan-platform/docs/screenshots/03-fan-artist-profile.jpg" width="820" alt="팬 플랫폼 아티스트 프로필"><br>
  <em>아티스트 프로필 — 팔로잉 상태와 포스트. <strong>멤버 전용</strong> 글은 제목만 보이고 본문 자리에 가입 안내가 선다(게이팅은 응답에서 걸린다)</em>
</p>

### IAM — [`projects/iam-platform`](../projects/iam-platform/)

**모노레포 전체의 OIDC IdP.** Spring Authorization Server 기반. PKCE public client ·
refresh token rotation · revoke · 멀티테넌트 행 단위 격리 · RFC 8693 assume-tenant 토큰 교환.
테넌트 진입 토큰에는 **그 운영자·그룹에 배정된 업무 권한 세트 ∩ 테넌트 구독 상한**만 실리고,
각 도메인 서버가 그것으로 세분 인가합니다. **모든 프로젝트가 여기에 붙습니다.**

<p align="center">
  <img src="../projects/iam-platform/docs/screenshots/02-iam-permissions.jpg" width="820" alt="IAM 권한 카탈로그 (운영자 콘솔)"><br>
  <em>운영자 콘솔 — 권한 키 카탈로그와 역할별 보유 권한. 역할 정의는 seed 로만 바뀌는 읽기 전용이다</em>
</p>

### ERP — [`projects/erp-platform`](../projects/erp-platform/)

**4개 서비스**: 조직 마스터(부서·직원·직급·원가센터·거래처) · 다단계 결재와 위임 ·
조직도 read-model · 결재 인박스.

<p align="center">
  <img src="../projects/erp-platform/docs/screenshots/02-erp-masters.jpg" width="820" alt="ERP 마스터 (운영자 콘솔)"><br>
  <em>운영자 콘솔 — 마스터 5종. 행마다 <strong>유효기간</strong>이 붙고 <code>asOf</code> 로 과거 시점 상태를 조회한다</em>
</p>

### Finance — [`projects/finance-platform`](../projects/finance-platform/)

**2개 서비스**: 계좌(KYC · 가용/원장 잔액 · 홀드·해제·캡처 · 멱등 자금 이동) ·
원장(복식부기 · 시산표 · 회계 기간 · 대사).

<p align="center">
  <img src="../projects/finance-platform/docs/screenshots/01-finance-ledger.jpg" width="820" alt="Finance 원장 시산표 (운영자 콘솔)"><br>
  <em>운영자 콘솔 — 시산표. 차변·대변과 기준통화 환산이 나란히 서고 <strong>대차 일치</strong>를 화면이 직접 판정한다</em>
</p>

### WMS — [`projects/wms-platform`](../projects/wms-platform/)

**7개 서비스**: 마스터(5 애그리거트 + Lot 유효기간) · 재고(예약 · 출고 saga 컨슈머 4종 ·
저재고 알림) · 입고(ASN/검수/적치) · 출고(주문·피킹·패킹·배송 + saga 오케스트레이터) ·
알림 · 어드민(CQRS 읽기 측) · 게이트웨이. **이커머스 주문을 실제로 처리합니다** — 그리고
반대 방향도 받습니다: 스토어에서 주문이 취소되면 출고 취소 요청이 와서, 접수 단계면 취소 ·
피킹 중이면 자동 취소 · 피킹이 끝났으면 운영자 경보로 갈립니다.

<p align="center">
  <img src="../projects/wms-platform/docs/screenshots/03-wms-outbound.jpg" width="820" alt="WMS 출고 (운영자 콘솔)"><br>
  <em>운영자 콘솔 — 출고 주문과 saga 상태, 그 아래 출고 확정된 화물의 택배 행. 데모 시드가 스토어 주문을 실제로 wms 로 흘려 보낸 결과다</em>
</p>

### SCM — [`projects/scm-platform`](../projects/scm-platform/)

**5개 서비스**: 조달(발주 생애주기 · 공급사 확인 · ASN 인테이크) · 재고 가시성(창고 스냅샷
위의 교차 노드 read-model) · 수요 계획(저재고 알림 소비 + 야간 스윕 → 보충 제안) · 물류 ·
게이트웨이.

<p align="center">
  <img src="../projects/scm-platform/docs/screenshots/01-scm-procurement.jpg" width="820" alt="SCM 조달 발주 목록 (운영자 콘솔)"><br>
  <em>운영자 콘솔 — 발주(PO) 목록. <code>DRAFT → ACKNOWLEDGED → CONFIRMED</code> 상태 기계를 지나는 발주들이 공급사·금액과 함께 선다</em>
</p>

### 운영자 콘솔 — [`projects/platform-console`](../projects/platform-console/)

`console-web`(테넌트 스위처 · 도메인별 운영 화면 · 결재 인박스 · 알림, 그리고 그 서버에서 하는
교차 도메인 합성). **wms·scm·finance·erp 의 유일한 프런트엔드입니다** —
위 화면들 중 「운영자 콘솔」이라 적힌 것이 전부 이 앱입니다.

도메인마다 「개요」 맨 위에 **실적 줄**이 있습니다 — 이번 주·이번 달을 같은 길이의 직전 기간과
비교하고 일별 추이를 그립니다(한국 시간 기준, 집계는 각 도메인 백엔드가 합니다). 메뉴와 쓰기
버튼은 그 운영자가 그 테넌트에서 받은 **업무 권한**대로 보이고 숨습니다.

<p align="center">
  <img src="../projects/platform-console/docs/screenshots/05-console-ecommerce-orders.jpg" width="820" alt="운영자 콘솔 이커머스 주문"><br>
  <em>운영자 콘솔 — 스토어 주문을 운영자 쪽에서 본 화면. 결제 대기부터 배송 완료·취소까지 상태별로 선다</em>
</p>

---

## 최근에 더한 것 (2026-10)

| | 무엇이 바뀌었나 |
|---|---|
| **테넌트별 업무 권한 세트** ([ADR-MONO-083](adr/ADR-MONO-083-domain-permission-sets-per-tenant.md)) | 「운영자면 다 된다」에서 「이 테넌트에서 배정받은 일만 된다」로. 이커머스·SCM·ERP·Finance 가 서버에서 세분 인가하고, 콘솔은 권한 없는 메뉴·버튼을 숨깁니다 |
| **도메인 실적 지표** ([ADR-MONO-084](adr/ADR-MONO-084-domain-overview-performance-metrics.md)) | 개요가 「지금 상태」만이 아니라 「이번 주·이번 달 얼마나」를 보여 줍니다. 매출·판매 수량, 입출고 처리량, 발주 금액, 결재 상신·승인, 이체 금액, 로그인 |
| **주문 취소 → 출고 취소** | 스토어 주문 취소가 출고 전 배송을 취소하고 wms 에 출고 취소를 요청합니다 — 서비스 경계를 넘는 보상 흐름 |
| **데드레터 이름 통일** ([ADR-MONO-085](adr/ADR-MONO-085-dead-letter-topic-suffix-dlt.md)) | 모든 Kafka 소비자의 실패 토픽을 `<topic>.DLT` 하나로, 실패 원인을 남기게 |
| **4주 이력 시드** | 데모가 켜지면 wms·scm·erp·iam·finance 에 최근 4주 기록이 깔려, 실적 그래프가 부팅 당일 막대 하나가 아니라 한 달을 그립니다 |

---

## 어떻게 만들었나 — 규칙 주도 AI 협업

이 저장소는 **Claude Code 와 규칙 기반으로 함께 개발**하도록 짜여 있습니다. 요점은
「AI 에게 시켰다」가 아니라 **「AI 가 지킬 규칙을 저장소가 들고 있다」** 입니다.

- 프로젝트마다 `PROJECT.md` 가 `domain`·`traits` 를 선언하고, AI 는 거기 맞는 **규칙 레이어를
  자동으로** 읽습니다.
- [`CLAUDE.md`](../CLAUDE.md) 가 최소 운영 규칙을 정합니다 — **Hard Stop 조건**, 소스 오브
  트루스 우선순위, 태스크 생애주기.
- 스펙이 없거나 충돌하면 **우회 구현을 하지 않고 멈추고 보고합니다.**
- 작업은 `tasks/ready/` 에 있는 것만 착수하고 `ready → in-progress → review → done` 으로
  흐릅니다. `review`·`done` 은 얼어 있습니다.
- 🔵 **판정은 문서가 아니라 가드가 합니다.** `scripts/` 의 검사기들이 CI 에서 돌며 문서와
  코드가 어긋나는 순간 빨개집니다 — 이 문서의 이미지 배선도 그중 하나가 지킵니다.

**전체 과정**: [개발 프로세스 워크스루](guides/development-process.md)

---

## 저장소

**[github.com/kanggle/monorepo-lab](https://github.com/kanggle/monorepo-lab)** — 이것이
**살아 있는 저장소**입니다. 전체 개발 이력과 공용 라이브러리가 여기 있습니다.

각 프로젝트는 [`scripts/sync-portfolio.sh`](../scripts/sync-portfolio.sh) 로 독립 리포에
추출됩니다. 🔵 **위 링크는 전부 모노레포 안의 경로를 가리킵니다** — 사본은 추출본이고,
이력·공용 라이브러리·이 문서가 모두 여기 있기 때문입니다.

사본을 보고 싶으면 (**2026-11-01 UTC 확인**):

| 사본 | 마지막 동기화 |
|---|---|
| [scm](https://github.com/kanggle/scm-platform) | 2026-09-11 |
| [erp](https://github.com/kanggle/erp-platform) · [finance](https://github.com/kanggle/finance-platform) | 2026-09-10 |
| [wms](https://github.com/kanggle/wms-platform) · [iam](https://github.com/kanggle/iam-platform) · [ecommerce](https://github.com/kanggle/ecommerce-microservices-platform) · [fan](https://github.com/kanggle/fan-platform) | 2026-08-04 |

🔴 사본은 전부 **위 「최근에 더한 것」 이전 상태**입니다. 그 프로젝트가 멈춘 것이 아니라
추출을 안 돌린 것이고, 최신 상태는 위의 모노레포 링크에서 보실 수 있습니다.
