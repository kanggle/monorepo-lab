# Task ID

TASK-MONO-635

# Title

공개 열람을 **백엔드 없이** 성립시킨다 — Vercel 버전별 저장본 + 세 화면의 공개 경로 (`ADR-MONO-070`)

# Status

review

# Owner

monorepo

# Task Tags

- infra
- demo
- frontend
- cross-project

---

# Goal

`ADR-MONO-067` 이 세 방문자 화면을 Vercel 로 옮겼지만 **데이터는 안 옮겼다.** 그래서 데모가
꺼진 대부분의 시간 동안 화면은 뜨지만 **내용이 없고**, 그 상태는 «아직 안 켰다» 와 «고장» 을
구별해 주지 않는다.

이 티켓은 공개 열람 데이터를 **Vercel 의 버전별 저장본**에서 제공하고, 세 화면의 공개 경로가
EC2·IAM·MinIO 에 **전혀 의존하지 않게** 만든다.

🔴 요구가 명시적으로 금지한 구조가 있다: *"먼저 백엔드를 호출했다가 실패하면 저장본을
보여주는 구조를 기본으로 만들지 않는다."* ⇒ 판독자에 **백엔드로 가는 코드가 없다.**

---

# Scope

## 포함

- `infra/demo/public-data/` (신규) — `@demo/public-data`: 계약·판독자·질의·발행자·번들 시드
- `projects/fan-platform/web/fan-platform-web/` — 공개 피드·아티스트·게시물·멤버십 소개
- `projects/ecommerce-microservices-platform/apps/web-store/` — 공개 카탈로그·검색·카테고리
- `projects/platform-console/apps/console-web/` — 공개 둘러보기 `(demo)` 라우트 그룹
- 세 앱 공통 — `POST /api/demo/heartbeat`(세션 있을 때만), 출처 배너

## 제외

- **공개 댓글** — `ADR-MONO-070 § D7`. 요구가 «안전하게 분리 가능한 경우» 라는 **조건**을 달았고
  실측 결과 그 조건이 거짓이다(프런트에 읽는 경로 0, 스펙이 v1 범위 밖으로 선언, 공개 정책
  미정의). 정의된 적 없는 공개 정책을 에이전트가 정하는 것은 권한 밖이다.
- **게스트 장바구니** — 요구가 «기존 구현이 있으면 보존, 없으면 확대하지 말라» 고 했고,
  실측상 없다(`cart-context.tsx` 가 비로그인 시 장바구니를 비운다). 그대로 뒀다.
- 선택 기동 — `TASK-MONO-634`.

---

# Acceptance Criteria

## AC-0 — 착수 전 실측 (완료)

- [x] 팬: `middleware.ts` 가 `/login`·`/api/auth`·`/_next`·`favicon`·`build-info.json` 외 전부를
      막는다 ⇒ **공개 열람이 하나도 없었다.**
- [x] 스토어: `/`·`/products*` 는 이미 공개. **데이터만** 백엔드에서 왔다.
- [x] 콘솔: `(console)/layout.tsx` 가 65 페이지를 서버에서 막고 `/` 가 `/login` 으로 간다.
- [x] 아티스트 `status` 어휘가 **`PUBLISHED`** 다 — 초판이 `ACTIVE` 로 짰다가 `seed-fan.sh:110`
      실측이 뒤집었다. 🔴 그대로 뒀으면 시드된 3명이 **전부 걸러져** 빈 목록이 됐고, 그 빈
      목록은 «데이터 없음» 으로 보여 수집 실패와 구별되지 않았다.
- [x] 카테고리 조회 API 가 **없다**(엔티티만 있고 컨트롤러 매핑 없음) ⇒ 파생이 유일한 정직한 방법.
- [x] `GET /api/v1/memberships` 는 **개인 구독** 엔드포인트다 ⇒ 요금제 안내의 출처로 쓸 수 없다.

## AC-1 — 저장본 계약

- [x] 봉투에 `schemaVersion`/`dataVersion`/`generatedAt`/`source`/`origin`/`coverage`/`collectionStatus`.
- [x] 🔴🔴 **정상적인 0건과 수집 실패를 가른다**(`collectionStatus`). 없으면 둘 다 `[]` 다.
- [x] `coverage` 와 `collectionStatus` 의 **키 집합이 같아야** 한다.
- [x] 포인터가 말한 세대와 봉투의 세대가 다르면 **읽지 않는다**.
- [x] 404 HTML·잘린 JSON·다른 데이터셋 봉투를 전부 거부한다.

## AC-2 — 공개 필드 허용 목록

- [x] 원본을 **spread 하지 않고** 손으로 재조립한다.
- [x] 아티스트 `realName`/`accountId`/`tenantId` 없음 — **양성 대조군**으로 픽스처에 실재 확인.
- [x] 🔴🔴 잠긴 글: `body` `null` + `bodyPreview` **없음** + `imageUrls` 빈 배열(경로도 본문이다).
- [x] 🔴🔴 상품·옵션에 `stock` **필드 자체가 없다**.
- [x] `HIDDEN` 상품 · `DELETED` 글 · 비공개 아티스트 · 고아 글이 전부 사라진다.
- [x] 판독자도 같은 검사를 한다(**두 번째 겹** — 한 겹은 그 겹이 빠진 날 조용하다).
- [x] 다중 테넌트: **읽는 경로에 테넌트 파라미터가 존재하지 않는다**(검사가 아니라 구조).

## AC-3 — 백엔드 없이 열람

- [x] 판독자 폴백 사슬이 **두 칸**뿐이다(저장본 → 번들 시드). 백엔드로 가는 코드 없음.
- [x] 목록·상세·검색이 **한 봉투**에서 파생된다(세대 혼합 불가능).
- [x] 검색·필터·정렬·페이지 의미 보존(`SearchSortOrder` 값 집합 동일).
- [x] `corpusSize` 로 «검색 결과 없음» 과 «저장본이 비었다» 를 가른다.
- [x] 화면이 **출처를 말한다**(`source` + `degraded`).
- [x] 세 앱 `build` — fan 12/12 · console 66/66 · store 23/23 정적 페이지가 **백엔드 없이**
      생성됐다(컴파일·타입체크 통과). 🔵 store 는 마지막 `output: 'standalone'` 복사 단계가
      Windows 심링크 권한(EPERM)으로 실패하지만 그것은 **호스트 제약**이고, CI 의
      `Frontend lint & build` 가 SUCCESS 로 그 축을 닫았다.

## AC-4 — 발행 절차

- [x] 불변 세대 → **되읽기 검증** → 포인터 교체 → 구세대 정리.
- [x] 부분 수집(`failed`)이 **정상본을 못 덮는다** — 판정은 «예외가 났나» 가 아니라
      **«포인터가 그대로인가»** 다.
- [x] 오래된 세대가 새 세대를 못 덮는다(`--force` 만 예외).
- [x] 같은 세대 재발행 거부.
- [x] 깨진 포인터를 «최초 발행» 으로 오인하지 않는다.
- [x] 구세대 정리가 **현재 세대를 절대 안 지운다**.
- [x] 이미지 내용 주소 + **허용 오리진 명시해야 복사**(Unsplash 는 복사 안 함 — 재배포 회피).
- [x] 발행 트리거 4종 문서화 + **꺼진 EC2 를 깨우는 Cron 없음**.

## AC-5 — 권한 보존

- [x] 콘솔 `(console)/layout.tsx` 의 `isAuthenticated()` 가드 **행위 무변경**(추가만: heartbeat 마운트).
- [x] 콘솔 둘러보기는 **새 라우트 그룹**이고 실제 API 라우트를 안 부른다.
- [x] 팬 middleware 는 공개 경로만 예외이고 나머지는 **fail-closed 유지**.
- [x] 공개 둘러보기는 heartbeat 를 안 보낸다.
- [x] 비로그인 쓰기·직접 API 호출 차단 — **자동 검사로 닫혔다**: 콘솔 e2e-smoke 가
      «둘러보기는 보호 경로를 열지 않는다» 와 «미인증 `/operators`·`/dashboards/overview`
      → `/login`» 을 백엔드 차단 상태에서 단언하고(14/14), 팬 e2e-smoke 가 보호 경로 4종 +
      판별자(존재하지 않는 경로)를 단언한다(18/18). 세 앱 유닛에도 같은 축이 있다.
- [ ] ⚪ 만료 세션·IAM 중단 상태에서 공개 열람 유지 — **실환경 미검증.** 자동 검사는
      «백엔드 차단» 은 재지만 «세션은 있는데 만료됐고 IAM 이 죽은» 조합은 안 잰다.
      🔴 그 조합을 재려면 EC2 기동 창이 필요하고, 이 티켓만을 위해 켜지 않는다
      (`TASK-MONO-633` 과 같은 규약). 다음 기동 창에 얹을 항목이다.

---

# Related Specs / Contracts

- [`ADR-MONO-070`](../../docs/adr/ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md)
- [`ADR-MONO-067`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) · [`ADR-MONO-068`](../../docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md)
- `TASK-MONO-575` — Vercel 무료 플랜 한도(**미해소** — 소유자 대시보드 작업)

---

# Edge Cases

| 상황 | 처리 |
|---|---|
| `DEMO_PUBLIC_DATA_BASE_URL` 미설정 | 번들 시드. **degraded 아님**(로컬·CI 의 정상 상태) |
| 저장본 읽기 실패 | 번들 시드 + `degraded=true` + 짧은 TTL 재시도 |
| 봉투가 계약 위반 | 번들 시드로. 조용히 빈 목록 안 그린다 |
| 번들 시드가 계약 위반 | **빌드가 죽는다**(조용한 빈 화면보다 낫다) |
| 발행 중 서버 다운 | 1단계에서 멈춤 → 포인터 무변경 |
| 동시 발행 | 사전순 세대 비교로 되돌림 거부. 🔴 락 아님 — 창을 코드에 적어 뒀다 |
| 공개 철회 | 재발행 즉시 방문자 경로에서 사라짐. 🔴 구세대 URL 은 정리 전까지 남는다(문서화) |

---

# Verification — 실제로 돌린 것

```
node --test infra/demo/public-data/tests/public-data.test.mjs   rc=0   31/31
node infra/demo/public-data/bin/build-bundled-snapshots.mjs     rc=0   누출 대조군 13/2/17건
node .../publish-public-data.mjs --seed --out <tmp> ×3          rc=0   포인터·세대 파일 생성 확인
tsc --noEmit (public-data 패키지)                                rc=0
독립 grep 누출 검사 (본명·MUST-NOT-LEAK·stock·realName·…)          전부 0건 + **양성 대조군 통과**
fan: npx vitest run --maxWorkers=2                              rc=0   239/239
```

🔴 **양성 대조군을 함께 돌렸다** — 「0건」이 내 가설을 지지하므로, 같은 grep 이 공개 값
(`루미`·`베이직 코튼 티셔츠`·`"locked": true`)은 **찾아내는지** 확인했다. 찾아냈다.

## 🔴 실환경 미검증 (모의 통과와 구별)

- **Vercel Blob 왕복 미검증** — 이 저장소에 토큰이 없다(`TASK-MONO-575` 실측). 발행 **절차**는
  로컬 저장소 어댑터로 실제 실행해 시험했지만, 그것이 통과시키는 것은 **절차이지 Blob 이
  아니다.** 「로컬로 돌았으니 Blob 도 된다」는 주장이 아니다.
- **백엔드 추출 경로 미검증** — 게이트웨이를 못 띄웠다. 🔵 다만 **보안 성질을 들고 있는 쪽은
  변환기**이고 그쪽은 네트워크 없이 전수 시험했다 — 그러니 「백엔드를 못 띄워서 검증 못 했다」가
  변환기에는 **해당되지 않는다.**
- 세 프런트엔드의 **실브라우저 공개 열람** 미검증(EC2 차단 상태 접속·새 세션·새로고침).
- 공개 요청이 EC2/IAM/MinIO 를 안 부르는지 **네트워크 수준 관측** 미실시(코드 독해로만 확인).

## 🔴🔴 이 호스트의 측정 제약 — 반드시 함께 읽어야 한다

세 스위트를 **동시에** 돌리면 vitest 기본 `testTimeout` 5000ms 를 넘겨 **매 실행마다 다른
테스트가** 타임아웃한다. 실측:

| 대상 | 단독 실행 | 전 스위트 동시 |
|---|---|---|
| fan `demo-heartbeat-route` | **527ms** 통과 | 5000ms 타임아웃 |
| fan `middleware-public-paths` | **438ms** 통과 | 5000ms 타임아웃 |
| console `AccountSelfService` | 간헐 | 간헐 |

⇒ 원인은 코드가 아니라 **호스트 부하**다(단독 0.5초짜리가 5초를 넘기려면 10배 경합이 필요하다).
🔴 그래서 판정을 «타임아웃을 늘려서» 얻지 않고 **동시성을 제한해서**(`--maxWorkers=2`) 다시
쟀다 — 재는 조건을 고친 것이지 기준을 낮춘 것이 아니다. 커밋에는 타임아웃 변경이 **없다.**
🔴 console `AccountSelfService` 는 교대 A/B 3패스에서 A 2/3 · B 3/3 로 **깨끗이 갈리지 않았다**
⇒ «우리 변경 탓» 이라고도 «무관» 하다고도 단정하지 않는다. 후속에서 그 파일만 따로 봐야 한다.

---

# 머지 검증 (4-dimension)

impl PR [#3681](https://github.com/kanggle/monorepo-lab/pull/3681) · 스쿼시 `9f0fcd2d6`

| 축 | 결과 |
|---|---|
| (a) `state=MERGED` | ✅ `mergedAt=2026-09-07T19:17:09Z` · `mergeCommit=9f0fcd2d6df70b3dee847163c82d4159cbe9d125` |
| (b) `origin/main` 팁 == 스쿼시 커밋 | ✅ `9f0fcd2d6` |
| (c) 머지 시점 실패 체크 | ✅ **0건** (SUCCESS 29 · SKIPPED 28 · FAILURE 0) · required 4/4 SUCCESS |
| (d) AC 가 닫혔나 | 🔴 **아래 § AC 참조 — 전부는 아니다.** 그래서 이 파일은 `review/` 에 남는다 |

🔴 (a)(b)(c) 는 **PR** 을 재고 (d) 만 **티켓** 을 잰다. (d) 가 안 닫혔으므로 `done/` 로
옮기지 않는다 — `done/` 는 frozen 이고, 거기 남긴 잔여는 다시 읽히지 않는다.

## CI 가 닫아 준 것 — 로컬에서 «미검증» 이라고 적었던 항목의 정정

🔵 **`web-store` 유닛 테스트가 CI 에서 통과했다** (`Frontend unit tests` = SUCCESS, 922 tests).
이 호스트에서는 vitest 4.1.0 이 Node 24.14 에서 `ERR_PACKAGE_IMPORT_NOT_DEFINED` 로 **기동
자체가 안 됐고**(대조군으로 우리 변경 이전에도 동일함을 확인), 그래서 커밋 메시지와 PR 본문에
「store 유닛 테스트는 안 돌았다」고 적었다. **그 문장은 CI 결과로 갱신된다** — 안 돈 것은
*이 호스트에서* 였고, 실제로는 돌았고 통과했다.
🔴 「로컬에서 못 쟀다」와 「검증되지 않았다」는 다른 사실이다. 앞의 것만 참이었다.

🔵 `Frontend E2E smoke` (세 앱 Playwright, 백엔드 차단) 도 SUCCESS · `Demo wrapper smoke` SUCCESS.

## AC — 무엇이 닫혔고 무엇이 안 닫혔나

- AC-0 · AC-1 · AC-2 · AC-3 · AC-4 — **닫혔다.**
- AC-5 — **한 칸 열림**(만료 세션 × IAM 중단 조합). 나머지 칸은 닫혔다.

🔴 체크박스 밖에서 열려 있는 것: **Vercel Blob 왕복 미검증**(토큰 없음) · **백엔드 추출 경로
   미검증**(게이트웨이 미기동) · **실브라우저 3주소 확인 미실시**. 🔵 다만 보안 성질을 들고
   있는 쪽(변환기·허용 목록)은 네트워크 없이 전수 시험됐고 CI 에서도 통과했다 — 「백엔드를
   못 띄워서 검증 못 했다」가 **그 축에는 해당되지 않는다.**
