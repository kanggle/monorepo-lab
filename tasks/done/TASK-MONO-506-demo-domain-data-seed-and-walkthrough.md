# Task ID

TASK-MONO-506

# Title

데모 계정 하나가 전 기능을 밟을 수 있도록 도메인 데이터를 시드하고, 그 과정을 라이브로 검증해 가이드로 남긴다

# Status

done

# Owner

monorepo

# Task Tags

- infra
- demo
- test
- onboarding

---

# 배경

포트폴리오 데모의 목적은 **면접관이 직접 눌러 보는 것**이다. 계정과 배선이 갖춰져도 화면이 비어 있으면
아무것도 증명하지 못한다. 이 태스크는 데모 계정(`TASK-BE-571` 이 심는 `demo@demo.com`)이
스토어프런트 · 팬 · 콘솔 5도메인에서 밟게 될 **데이터**를 만든다.

**시드 방법의 원칙**: 가능한 것은 **실제 API/UI 로 넣는다.** 넣는 행위 자체가 그 기능의 검증이기
때문이다. API 가 없거나 권한이 막힌 것만 직접 DB INSERT 하고, **왜 직접-DB 인지를 스크립트에 주석으로
남긴다.** (선례: fan-platform 은 아티스트·그룹 생성이 JWT `roles` 부족으로 403 이라 직접-DB 가 유일 경로임이
이미 확인됐다. 이유를 적지 않은 직접-DB 시드는 다음 사람에게 "API 가 없다"는 거짓을 물려준다.)

---

# 선행 태스크 (착수 전 확인)

- `TASK-BE-571` (iam-platform) — 데모 아이덴티티 시드. **하드 선행**: 계정이 없으면 어느 화면도 못 연다
- `TASK-MONO-505` — 콘솔 federation env 승격. **하드 선행**(콘솔 도메인 섹션 시드 검증에 한해)
- `TASK-FAN-FE-014` (fan-platform) — fan 웹 컨테이너화. **하드 선행**(팬 표면 시드 검증에 한해)
- `TASK-BE-572` (ecommerce) — 데모 mock PG 프로파일. **하드 선행**(결제 완주 시드 경로에 한해)

선행이 미완이면 그 슬라이스만 보류하고 나머지를 진행한다 — 전체를 막지 않는다.

---

# Goal

`bash infra/demo/demo-up.sh <도메인…>` 으로 뜬 스택에서 `demo@demo.com` 으로 로그인하면
아래 세 표면의 **모든 주요 화면이 비어 있지 않고**, 각 화면의 대표 동작(구매 · 구독 · 운영)이
실제로 수행 가능하다. 시드는 idempotent 하고 저장소에 커밋되어 fresh clone 과 AMI 재굽기에서 재현된다.

---

# Scope

## In Scope

- `infra/demo/seed/` — 도메인별 idempotent 시드 스크립트(신규 디렉터리) + 드라이버
- `infra/demo/demo-up.sh` — 시드 드라이버 호출 훅 (`DEMO_SEED=0` 로 끌 수 있게)
- `docs/guides/interview-demo-walkthrough.md` — 계정 · 화면별 클릭 경로 · "이 화면이 무엇을 증명하는가"
- 루트 `README.md` 데모 섹션 갱신

## Out of Scope

- IAM 아이덴티티/테넌트/구독 시드 — `TASK-BE-571` 소유
- 제품 코드 변경 — 시드 중 결함이 나오면 **별도 티켓**으로 분리한다(선례: fan 시드 중 발굴한 3건이
  각각 독립 티켓이 됐다). 이 티켓에서 제품 코드를 고치지 않는다
- CloudFront URL 을 README 에 싣는 것 — 배포마다 바뀌는 썩는 리터럴(MONO-389 가 제거한 결함)
- AWS 재굽기 — `TASK-MONO-399` AC-6 / `TASK-MONO-477` AC-8

---

# 시드 · 검증 슬라이스

로컬 호스트(WSL 12GB)로 8프로젝트 동시 기동은 불가능하다. 슬라이스별로 띄우고 → 화면을 밟으며
시드하고 → 증거를 남기고 → 내린다.

| 슬라이스 | 스택 | 대상 |
|---|---|---|
| S1 | iam + console | IAM(개요·가이드·운영자·운영자그룹·조직계층·테넌트·권한·권한세트·감사) · 고객신원(계정운영) · 조직설정(도메인구독·파트너십) |
| S2 | iam + fan + console | 아티스트 · 그룹 · 팬덤 · 게시물(PUBLIC/MEMBERS_ONLY/PREMIUM) · 팔로우 · 댓글 · 리액션 · 멤버십 구독(ACTIVE) · 알림 |
| S3 | iam + ecommerce + console | 카테고리 · 셀러 · 상품 · 이미지(MinIO) · 쿠폰/프로모션 · 주문(상태 다양) · 배송 · 리뷰 · 정산 / 콘솔 E-Commerce 9탭 |
| S4 | iam + wms + console | 창고 · 구역 · 로케이션 · SKU · 거래처 · ASN 입고 · 재고 · 출고 / 콘솔 WMS 6탭 |
| S5 | iam + scm + erp + finance + console | PO · 재고가시성 · 보충추천 / 부서 · 코스트센터 · 직급 · 사원 · 결재 · 위임 / 계좌 · 잔액 · 거래 · 시산표 |

---

# Acceptance Criteria

- [x] **AC-0 (착수 = 재측정)** — 각 슬라이스 착수 시 대상 화면 목록을 **코드에서 다시 센다**
      (콘솔은 `console-nav-config.ts` 의 리프, 스토어프런트/팬은 라우트 트리). 이 티켓에 적힌 목록은
      가설이지 모집단이 아니다. 실제 수와 다르면 실제가 이긴다
- [x] **AC-1 (API 우선)** — 시드 항목마다 "API/UI 로 넣음" 또는 "직접-DB + 사유" 중 하나가 스크립트에
      명시된다. 사유 없는 직접-DB 0건
- [x] **AC-2 (라이브 검증)** — 각 슬라이스에서 대상 화면을 **브라우저로** 열어 비어 있지 않음을 확인한다.
      직접 토큰 스모크나 API 200 은 화면 검증을 대체하지 않는다(선례: verify-seed 는 200 인데 SSR 은
      전 페이지 401 이었다)
- [x] **AC-3 (대표 동작)** — 읽기뿐 아니라 각 표면의 대표 쓰기 동작이 실제로 성공한다:
      스토어프런트=장바구니→주문(→ BE-572 완료 시 결제 완주), 팬=팬 게시물 작성 + 멤버십 구독,
      콘솔=운영자 생성 또는 상품/주문 상태 변경 중 최소 1건
- [x] **AC-4 (idempotent)** — 시드 드라이버를 **연속 2회** 실행해도 오류 없이 같은 상태에 수렴한다
      (`INSERT IGNORE` / `ON CONFLICT DO NOTHING` / upsert)
- [ ] **AC-5 (fresh 재현)** — 볼륨을 지운 상태에서 `demo-up.sh` → 시드 → AC-2 가 그대로 성립한다.
      로컬 개발자의 gitignored `.env` 나 수동 DB 조작에 의존하지 않는다
- [x] **AC-6 (메모리 실측)** — 슬라이스별 컨테이너 수 + 실측 메모리 사용량을 태스크에 기록한다.
      `TASK-MONO-399` AC-2 의 EC2 사이징 재측정 입력이 된다
- [x] **AC-7 (가이드)** — `docs/guides/interview-demo-walkthrough.md` 가 계정 · 진입 URL · 화면별 클릭
      경로 · 각 화면이 증명하는 것을 담는다. 열지 못하는 화면이 있으면 **그 사실과 이유를 적는다**
      (조용한 누락 금지)
- [x] **AC-8 (발굴 결함 분리)** — 시드 중 발견한 제품 결함은 이 티켓에서 고치지 않고 별도 티켓으로
      기재한다. 발견 0건이면 "0건" 이라고 적는다

---

# Related Specs

- `infra/demo/README.md` · `infra/demo/projects.sh`
- `projects/platform-console/apps/console-web/src/shared/ui/console-nav-config.ts` — 콘솔 화면 모집단
- 각 프로젝트 `specs/` — 시드할 엔티티의 enum·필수 컬럼은 **DB CHECK 제약이 권위**이지 spec 이 아니다
  (선례: fan enum 값은 `pg_get_constraintdef` 로 확인해야 맞았다)

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- 시드가 API 를 쓰는 경우 해당 `specs/contracts/http/` — **계약 변경 없음**. 계약을 바꿔야 시드가
  된다면 그것은 결함이므로 AC-8 대로 별도 티켓

---

# Implementation Notes

- **enum·제약의 권위는 DB** — `SELECT pg_get_constraintdef(...)` / `SHOW CREATE TABLE` 로 확인한다.
  spec 문서와 어긋나면 DB 가 이긴다(그리고 그 어긋남은 AC-8 티켓 후보다).
- **PUBLISHED 계열은 `published_at` 을 세팅해야 목록/피드에 노출된다** (fan 선례).
- **게이트웨이 경로 접두사** — fan 게이트웨이는 `/api/v1/**` 만 받아 다운스트림 `/api/**` 로 rewrite 한다.
  `/api/...` 직접 호출은 404. 검증 URL 을 게이트웨이 기준으로 쓴다.
- **직접 토큰 검증 스크립트를 만들되, 그것으로 AC-2 를 대체하지 않는다.** 두 경로는 다른 것을 증명한다.
- **`--remove-orphans` 금지** — 다른 도메인 컨테이너가 삭제된다.
- 재배포는 단일 서비스 `up -d --no-deps <svc>` 로 한다(전체 재기동은 호스트 자원 고갈을 부른다).

---

# Edge Cases

- 시드 대상 테이블이 tenant 컬럼을 요구하는데 데모 테넌트가 도메인마다 다르다
  (콘솔 운영은 `demo-corp`, 팬은 `fan-platform`, 스토어프런트는 `ecommerce`) — 시드 스크립트가
  테넌트를 **파라미터로** 받게 하고 하드코딩하지 않는다
- 일부 화면은 read-model 프로젝션에 의존한다(ERP 통합조회, WMS 재고). 프로듀서만 시드하면 화면은
  여전히 빈다 — 프로젝션 서비스 기동 여부를 슬라이스 정의에 포함한다
- 알림/이벤트 기반 화면은 Kafka 컨슈머가 떠 있어야 채워진다
- 이미지가 MinIO 에 없으면 상품 카드가 깨진 이미지로 보인다 — 이미지도 시드 대상이다

---

# Failure Scenarios

- **초록 시드, 빈 화면** — API 가 201 을 돌려줬는데 화면이 다른 테넌트/상태를 조회하는 경우.
  AC-2 브라우저 확인만이 잡는다
- **첫 실행만 성공** — idempotent 가 아니어서 2회차에 unique 위반. AC-4
- **로컬에서만 성공** — 개발자의 gitignored `.env` 나 수동 조작에 의존. AC-5 (fresh clone/볼륨 삭제)
- **시드 중 결함 발견 → 티켓에서 즉석 수정** → 스코프 폭발 + 검증 안 된 제품 변경. AC-8 이 막는다
- **화면 목록을 이 티켓에서 복사** → 실제 nav 와 어긋나 커버리지 착시. AC-0 이 막는다

---

# Test Requirements

- 시드 드라이버 2회 연속 실행(AC-4)
- 볼륨 삭제 후 fresh 재현(AC-5)
- 슬라이스별 브라우저 실주행(AC-2/AC-3)
- `infra/demo/verify-demo-wrapper.sh` 통과 (시드 훅 추가가 래퍼 불변식을 깨지 않는지)

---

# Definition of Done

- [ ] 시드 스크립트 + 드라이버 커밋
- [ ] 슬라이스별 라이브 검증 증거 기록
- [ ] 메모리 실측 기록(AC-6)
- [ ] `docs/guides/interview-demo-walkthrough.md` 작성
- [ ] 발굴 결함 티켓화(또는 0건 명시)
- [ ] Ready for review

---

# 실행 결과 (2026-08-05)

## 범위 조정 — S2 · S4 · S5 를 분리했다

이 티켓은 **시드 프레임워크 + S3(ecommerce) + S1(콘솔/IAM) 검증 + 가이드**를 끝냈고,
나머지 슬라이스를 `TASK-MONO-509`(팬) · `TASK-MONO-510`(WMS·SCM·ERP·Finance)로 분리했다.
사유 두 가지:

1. **호스트 자원.** 도커 가용 11.7 GiB 중 iam + console + ecommerce(축소본) 35 컨테이너가
   이미 9.2 GiB 를 쓴다(AC-6). S2·S4·S5 는 각각 다른 스택을 띄워야 한다.
2. **검증하지 않은 시드는 거짓 약속이다.** 이 티켓의 원칙이 "넣는 행위가 곧 검증" 인데
   띄워 보지 않은 스크립트를 커밋하면 그 원칙을 스스로 어긴다. 프레임워크와
   `operator_token` 은 이미 있으므로 후속 티켓의 비용은 크지 않다.

## AC-0 — 화면 모집단 재측정 (티켓의 목록이 틀렸다)

| 표면 | 실측 | 티켓이 적은 값 |
|---|---|---|
| 콘솔 nav 리프 | **47** (+ nav 밖 `/dashboards/health` = 48) | — |
| └ E-Commerce | **10** | "9탭" |
| └ WMS | **7** | "6탭" |
| └ 최상단(개요·카탈로그) | **2** | S1 목록에서 누락 |
| 스토어프런트 `page.tsx` | **22** | — |
| 팬 웹 `page.tsx` | **9** | — |

## 🔴 가장 큰 발견 — 운영자 권한은 계정이 아니라 assume 에서 나온다

데모 계정은 각 도메인 테넌트에서 `CUSTOMER` 일 뿐이라 `/api/admin/**` 이 **6/6 403** 이다.
콘솔 로그인(공개 클라이언트 `platform-console-web`, PKCE) → RFC 8693 assume `demo-corp` 를
거치면 5도메인 `*_OPERATOR` 역할이 한 토큰에 실리고 같은 엔드포인트가 **7/7 200** 이 된다.
`lib.sh` 의 `operator_token` 이 이 경로를 고정했다 — 그리고 이것은 면접관이 콘솔에서 밟는
바로 그 경로다.

`user_token` 도 curl 로 authorization_code + PKCE 를 **끝까지** 밟는다(authorize 는
`Accept: text/html` 없으면 401, 로그인 폼은 CSRF 요구 — 둘 다 실측). 시드가 통과하면
**로그인 경로가 살아 있다는 것이 이미 증명된 셈**이다.

## AC-1 — 규약이 아니라 게이트로 구현했다

`dbexec` 는 `--why` 를 **필수 인자**로 받고 없으면 실행을 거부한다. 주석 규약은 깜빡할 수
있지만 필수 인자는 그럴 수 없다. 신규 가드 **(y)** 는 그 게이트를 **우회하는 경로**(시드가
`docker exec … psql` 을 직접 호출)를 막고 배선까지 검사한다(드라이버 존재 · `demo-up.sh`
호출 · `DEMO_SEED` 스위치). **물기 확인 완료** — 위반 파일을 만들면 그 파일을 지목한다.

**직접-DB 는 전체에서 1건뿐**: ecommerce 소비자 프로필. 사유 = user-service 에 프로필 생성
엔드포인트가 존재하지 않는다(컨트롤러 4개 전수 확인) → `TASK-BE-575`.

## AC-2 · AC-3 — 라이브 검증

**스토어프런트** (진짜 Chromium, 로그인 → 17 라우트): **15 PASS**.
빈 2개는 결함이 아니다 — `/cart` 는 `localStorage` 기반이라 서버가 시드할 수 없고(면접관이
담으면 즉시 찬다), `/my/reviews` 는 배송 완료가 전제다(아래).

**시드가 연 화면**: `/my/profile` 이 이제 `demo@demo.com / 데모 구매자` 를 보여준다 —
직전까지 **404** 였다.

**콘솔** (브라우저 경로 로그인 + `demo-corp` 전환, 48 화면): **측정 가능한 33개 전부 PASS**.
나머지 15개는 WMS·SCM·ERP·Finance 스택이 이 슬라이스에 없어서다(부분 기동의 정상 동작).

**대표 쓰기**: 배송지 생성 · 위시리스트 추가(소비자) · 셀러 등록/활성화 · 수수료율 설정 ·
정산 기간 개설 · 알림 템플릿 3종 · 프로모션 생성 + 쿠폰 발급(운영자) 전부 API 로 성공.

### 🔴 술어를 세 번 고쳤다 — 화면이 아니라 술어가 틀렸다

1. 마커 목록에 맨 `'404'` 를 넣었더니 주문번호·가격에 매치돼 **17/17 FAIL**.
2. `body.textContent` 가 **Next 의 RSC flight 페이로드(`<script>` 안)** 까지 긁어,
   프레임워크가 늘 싣고 다니는 `This page could not be found` 가 **모든 페이지**에서 매치.
   → 보이는 텍스트(`innerText`)만 본다.
3. 콘솔 `/iam/guide` 가 "권한 없음" 마커에 걸렸다 — 그 화면이 **"권한이 없는 카드만
   '권한 없음' 으로 표시됩니다" 라고 설명하는 산문**이었다.

## AC-4 — 멱등

시드를 연속 4회 실행. 행 수 불변: `user_addresses` 4 · `wishlist_items` 3.
2회차부터 정산 기간·알림 템플릿은 **409**(서버가 중복을 거절하는 것이 올바른 동작이므로
"존재" 로 센다), 배송지·위시리스트는 목록 마커 탐지로 건너뛴다.

## AC-5 — **미완** (조용히 넘기지 않는다)

**정적 의존 검사는 통과**: 시드는 gitignored `.env` 를 읽지 않는다. 값의 출처는
`infra/demo/demo.env`(커밋됨) · 각 마이그레이션 헤더에 명시된 dev 클라이언트 시크릿 ·
`V9005` 가 심은 계정 UUID 뿐이다.

**볼륨 삭제 후 재현은 하지 못했다** — `docker compose rm -sfv` 가 파괴적 작업이라
샌드박스에서 차단됐다. 리뷰어가 직접 실행할 명령:

```bash
docker compose -p ecommerce -f projects/ecommerce-microservices-platform/docker-compose.yml \
  rm -sfv user-postgres
bash infra/demo/demo-up.sh iam ecommerce console   # 시드가 자동으로 다시 돈다
```

## AC-6 — 메모리 실측

| 슬라이스 | 컨테이너 | 메모리 |
|---|---|---|
| traefik + iam(13) + console(2) + ecommerce(축소 18) | **35** | **9.2 GiB** |
| (도커 가용) | | 11.7 GiB |

ecommerce 는 **축소본**이다 — compose 가 정의하는 서비스는 33개인데 18개만 띄웠다
(elasticsearch · minio · jaeger · prometheus · grafana · loki · batch-worker · auth-service 등 제외).
**전체 기동은 이 호스트에서 불가능하다.** `TASK-MONO-399` AC-2 의 입력.

## AC-8 — 발굴 결함 2건 (이 티켓에서 고치지 않았다)

| # | 결함 | 티켓 |
|---|---|---|
| 1 | user-service 에 프로필 생성 엔드포인트가 없다 ⇒ 마이페이지 3화면이 IAM 사용자에게 404/500, 배송지 없이는 결제 불가 | `TASK-BE-575` |
| 2 | 🔴 콘솔 E-Commerce 목록 탭이 스토어프런트 데이터를 못 본다 — 구매는 `ecommerce` 테넌트, 데모 운영자는 `demo-corp` 하나뿐. **entitlement 는 통과하는데 행 필터가 전부 거른다 ⇒ 200 + 빈 배열** | `TASK-BE-576` |

②는 **읽기만의 문제가 아니다.** 시드가 배송을 `DELIVERED` 로 진행시키려다 같은 벽에
부딪혔다 — 소비자 토큰으로 **방금 조회한** 배송 건이 운영자에게는 존재하지 않는다:

```
GET  /api/shippings/orders/{orderId}   200  (소비자)  shippingId=ae08bf5c-…
PUT  /api/shippings/ae08bf5c-…/status  404  (운영자)  SHIPPING_NOT_FOUND
```

배송을 완료시킬 수 없으니 **리뷰도 시드할 수 없다**(리뷰는 배송 완료 주문에만 허용 —
정당한 도메인 규칙이다). 하나의 테넌트 분리가 화면 5개와 쓰기 경로 2개를 함께 막는다.
시드는 이 상황을 실패로 세지 않고 **원인을 이름 붙여**(BE-576) 경고한다 — 조용히 넘기지도,
알려진 결함을 빨간불로 위장하지도 않기 위해서다.

②가 이 티켓이 존재하는 이유를 그대로 보여준다. `TASK-BE-572` 의 라이브 검증이 `/ecommerce`
200 을 확인하고 초록으로 지나갔는데, 그 화면(개요)은 테넌트 데이터를 나열하지 않는다.
**"게이트웨이가 토큰을 받았다" 와 "그 토큰이 데이터를 본다" 는 다른 명제이고, 후자는 목록의
원소 수로만 확인된다.**

## 부수 관찰 (결함으로 티켓화하지 않음 — 재현 조건이 약함)

- 게이트웨이는 **도달 불가 다운스트림을 503 이 아니라 500** 으로 낸다. 부팅 중인
  review/promotion 서비스에 시드가 걸려 원인이 전혀 보이지 않는 500 을 받았다.
  → 시드가 `wait_http` 로 엔드포인트 응답을 기다리도록 고쳤다.
- 콘솔 `/ecommerce/settlements` · `/notifications/templates` 가 **첫 렌더에서만** degrade
  했고(21,800 bytes) 재요청에서 정상(35,104 bytes)이었다. 콜드 스타트로 보이나 단일 관측이다.
