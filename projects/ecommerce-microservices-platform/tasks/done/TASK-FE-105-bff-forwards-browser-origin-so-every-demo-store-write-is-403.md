# Task ID

TASK-FE-105

# Status

done

# Title

BFF 가 브라우저 `Origin` 을 게이트웨이로 그대로 넘겨 데모 스토어의 쓰기가 전부 403

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- bff
- bug
- demo

---

> **분석 모델:** Opus 5.5 (1M context) / **구현 권장:** Opus 5.5 — 레이어 판단(BFF vs 게이트웨이 CORS)이 필요한 결함.

---

# 배경

라이브 데모(23차 AMI 창, 2026-10-06 UTC)에서 측정:

- `web-store` BFF 프록시(`apps/web-store/src/app/api/bff/[...path]/route.ts`)는 인바운드 요청
  헤더를 백엔드 게이트웨이 요청으로 그대로 복사하면서, hop-by-hop 목록(48~56행 부근, 이미
  `cookie` 는 벗긴다)만 벗긴다. **`origin` 은 벗기지 않는다.**
- 브라우저는 GET 이 아닌 모든 `fetch` 에 `Origin` 을 자동으로 붙인다. 그래서 BFF 는
  `Origin: https://store.hubwang.com` 을 ecommerce 게이트웨이로 그대로 넘긴다.
- 데모 게이트웨이의 Spring Cloud Gateway `globalcors` 허용 목록
  (`infra/demo/demo.env:258` `CORS_ALLOWED_ORIGINS` = `http://ecommerce.<dom>`,
  `http://web.ecommerce.<dom>`, `http://console.<dom>`)에는 배포된 Vercel 스토어 호스트가
  없다. 그래서 게이트웨이는 **본문 없는 403** 으로 답한다.

## 증거 (라이브 측정, 2026-10-06 UTC)

- UI `PATCH /api/bff/api/users/me` → 403(본문 없음); 같은 PATCH 를 서버측에서 `Origin` 헤더
  없이 보내면 → 200 + 값이 저장됨.
- 게이트웨이 직접 호출: `GET /api/products` — `Origin` 없음 → 200; `Origin: https://store.hubwang.com`,
  `http://store.<ip>.sslip.io`, `http://localhost:3000` 중 하나라도 있으면 → 403.
- BFF 를 통한 같은 요청(`Origin` 포함) → 403.
- 결과: 프로필 수정·배송지 추가·위시리스트·주문·결제 확인·리뷰 작성 등 데모 스토어의 **브라우저
  발 쓰기 전체**가 실패한다. CI 는 초록이었다 — compose 환경의 오리진이 그 환경의 허용 목록과
  우연히 일치하기 때문.

## 왜 이 레이어에서 고치는가

BFF 는 **같은 오리진, 서버-서버** 프록시다(파일 상단 주석 참조 — Phase 4.5 F2, 토큰
기밀성을 위해 이미 그렇게 설계됨). CORS 는 **브라우저-서버** 메커니즘이고, BFF 가 요청을
종단(terminate)한 뒤로는 브라우저의 `Origin` 은 백엔드에 아무 의미가 없다 — 백엔드가 보는
"호출자"는 이미 BFF 서버다. 따라서:

- **게이트웨이 CORS 설정(`demo.env`)을 넓히지 않는다** — 그러면 AMI 재굽기가 필요하고,
  고칠 필요가 없는 것(실제 브라우저-서버 CORS 요구)까지 넓어진다.
- **BFF 의 헤더 포워딩 제외 목록에 `origin` 을 추가한다** — 이미 `cookie`/`authorization` 을
  같은 이유(백엔드에 넘길 의미가 없는 클라이언트측 헤더)로 벗기고 있는 자리.

## `referer` 는 왜 같이 벗기지 않는가

게이트웨이의 403 판정은 `globalcors` 의 `Origin` 헤더 검사에서만 나온다(라이브 측정 —
`Origin` 없는 요청은 `Referer` 유무와 무관하게 통과). `Referer` 를 넘겨서 깨지는 동작을
찾지 못했고, 이 수정의 목적(쓰기 403 해소)에 불필요한 범위 확장이라 **벗기지 않는다** — 최소
수정 원칙.

---

# Goal

BFF 프록시가 브라우저의 `Origin` 헤더를 백엔드 게이트웨이로 넘기지 않게 하여, 데모
스토어의 모든 브라우저 발 쓰기(프로필 수정·배송지 추가·위시리스트·주문·결제 확인·리뷰
작성)가 게이트웨이 CORS 허용 목록에 의해 403 되는 결함을 없앤다.

---

# Scope

## In Scope

- `apps/web-store/src/app/api/bff/[...path]/route.ts` 의 `STRIPPED_REQUEST_HEADERS` 에
  `origin` 추가 + 이유를 설명하는 주석(이 티켓 ID 인용).
- `bff-proxy.test.ts` 에 단위 테스트: 인바운드 `origin` 헤더가 포워딩된 백엔드 요청에
  없음을 단언 + 대조군(평범한 헤더, 예: `content-type` 은 그대로 포워딩됨을 같은 테스트에서
  확인).

## Out of Scope

- 게이트웨이 CORS 설정(`infra/demo/demo.env` `CORS_ALLOWED_ORIGINS`) 변경 — AMI 재굽기가
  필요하고, 이 결함은 그 레이어의 문제가 아니다.
- `referer` 헤더 제거 — 구체적 근거 없음 (위 "왜 같이 벗기지 않는가" 참조).
- 게이트웨이 `globalcors` 자체의 동작 변경(어느 오리진을 허용할지는 그 설정의 권한 밖).

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 라이브 게이트웨이 직접 호출로 403 이 `Origin` 헤더 유무에만 달려
      있음을 확인했다(배경 § 증거).
- [x] **AC-1** — BFF 가 받은 요청에 `origin` 헤더가 있어도, 백엔드로 보내는 요청에는
      `origin` 헤더가 없다 (단위 테스트). **bite 확인**: 수정 전 커밋(impl PR #4172,
      `b6e6659ec`/`ab3917286`)의 CI `Frontend unit tests` 런에서 이 단언이
      `AssertionError: expected 'https://store.hubwang.com' to be null` 로 실제 적색이었고
      (run `37426431100`, job `112147261858`, `bff-proxy.test.ts (14 tests | 1 failed)` —
      실패 테스트는 이 신규 케이스뿐), 수정 커밋(`f548ac8ae`)에서 같은 런이
      `Frontend unit tests` SUCCESS(6m21s, run `37426919727`)로 통과했다.
- [x] **AC-2** — 같은 요청의 평범한 헤더(`content-type`)는 그대로 포워딩된다 (대조군, 같은
      테스트) — 위 CI 런에서 같은 테스트 케이스의 일부로 함께 통과.
- [x] **AC-3** — 기존 `bff-proxy.test.ts` 케이스가 수정 없이 통과한다 — 같은 CI 런에서
      `(14 tests)` 전체 통과(기존 13 + 신규 1).
- [x] **AC-4** — `tsc --noEmit` rc=0 · `next lint` rc=0 로컬 통과. 단위 테스트는 로컬
      vitest 4.x 가 이 호스트(Node 24)에서 기동 자체를 못 하는 환경 한계
      (`env_webstore_vitest4_node24_module_evaluator` 메모리 토픽 — `#module-evaluator`
      기동 오류, CI 는 Node 20 이라 영향 없음)라 CI `Frontend unit tests` 잡을 권위로 썼다
      (위 AC-1 참조). `next build` 는 로컬에서 컴파일·타입체크·정적 페이지 생성까지는
      성공했으나 Windows `output: 'standalone'` 트레이싱 단계의 심볼릭 링크 생성이
      `EPERM`(관리자 권한 필요, 이 호스트의 알려진 제약)으로 실패 — 코드와 무관한 환경
      한계이며, CI `Frontend lint & build` 잡(Linux 러너)이 SUCCESS(2m46s)로 빌드를 쟀다.
- [x] **라이브(23차 창) 재확인** — 배포된 스토어(store.hubwang.com)에서 프로필 수정·배송지
      추가가 200 으로 성공하고, 새로고침 후 값이 유지된다. (오케스트레이터가 측정)

---

# Related Specs

- 없음 (이 결함은 스펙이 아니라 구현 — BFF 가 자신의 설계 의도(동일 오리진 서버-서버
  프록시, 파일 상단 주석)에 맞지 않는 헤더를 포워딩하고 있었다)

# Related Contracts

- 없음 (게이트웨이 CORS 계약·설정 불변 — BFF 쪽만 수정)

---

# Edge Cases

- `Origin` 헤더가 없는 요청(서버 사이드 렌더/서버 액션 — 어차피 BFF 를 안 씀, 또는 GET
  내비게이션): 영향 없음 — 벗길 헤더가 원래 없다.
- 세이프 메서드(GET/HEAD) 재시도 경로(TASK-FE-100): `origin` 제외는 헤더 포워딩 단계에서
  한 번만 일어나므로 재시도 요청에도 동일하게 적용된다 — 별도 처리 불필요.
- 멀티 오리진 데모 호스트(sslip.io IP, localhost:3000 등): 모두 같은 증상이었고, 모두 같은
  수정으로 해소된다(오리진 값과 무관하게 아예 안 넘기므로).

# Failure Scenarios

- **`origin` 을 벗기지 않고 게이트웨이 CORS 허용 목록만 넓힌다** → AMI 재굽기가 필요하고,
  배포된 스토어 호스트가 바뀔 때마다(예: Vercel 프리뷰 URL) 다시 깨진다. 이 티켓이 배제한
  접근.
- **`referer` 까지 같이 벗긴다** → 근거 없는 범위 확장. 이 티켓이 배제.
- **BFF 응답 헤더 쪽(`STRIPPED_RESPONSE_HEADERS`)을 건드린다** → 증상은 요청 포워딩
  단계에서만 발생. 범위 밖.

---

# Test Requirements

- `bff-proxy.test.ts`: `origin` 헤더 미포워딩 + `content-type` 대조군 (AC-1, AC-2)
- 기존 스위트 전체 회귀 없음 (AC-3)

# Definition of Done

- [x] 수정 + 테스트
- [x] 로컬 게이트(`tsc`, `next lint`) 통과, CI `Frontend unit tests` 로 단위 테스트 권위 확보
- [x] Ready for review

---

## 23차 창 라이브 확인 + done 이관 (2026-10-06 UTC)

- 배포 반영 07:31:48Z — `Origin: https://store.hubwang.com` 을 붙인 `GET /api/bff/api/products` 가 403 → **200** 으로 바뀐 순간(30초 폴링).
- 라이브 AC ✅ — 같은 새 계정(`TASK-MONO-764` 흐름 1 에서 가입)으로 브라우저에서: 프로필 `PATCH /api/bff/api/users/me` **200**(«프로필이 수정되었습니다») → 새로고침 뒤 닉네임 «점검닉네임23» · 전화 «010-2345-6789» 유지 · 배송지 `POST …/addresses` **201** → `GET` 에 «회사 · 김점검 · 04524 · 서울특별시청 3층 · 기본» 저장. 수정 전 같은 화면은 두 요청 다 403.
- 연쇄로 풀린 흐름(764): 주문 `POST orders 201` → `payments/confirm 200` · 위시 `DELETE 204` · 리뷰 `POST reviews 201`.
- 4-dim: (a) #4172 MERGED (b) `6fd6a8d76` = origin/main 에 있음 (c) 머지 시점 실패 0 (SUCCESS 18 · SKIPPED 49) (d) AC 섹션 전부 닫힘(위 라이브 AC 포함).
