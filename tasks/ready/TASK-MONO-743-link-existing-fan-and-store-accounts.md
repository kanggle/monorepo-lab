# Task ID

TASK-MONO-743

# Title

전역 소비자 계정 5단계 — 기존 팬·스토어 계정 **묶기** + id 이전 (`ADR-MONO-078` A · D2)

# Status

ready

# Owner

monorepo

# Task Tags

- security
- identity
- data-migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (계정 탈취 경로가 될 수 있는 유일한 단계 — 대조군이 본체)
>
> ⏳ **DO NOT START — 보류(소유자 결정 2026-10-02 UTC «보류 + 744에서 시드 해결»).** 날짜 조건이 아니다. 착수 전 **AC-00** 을 재서 참일 때만 시작하고, 거짓이면 이 파일 끝에 측정값과 날짜(UTC)를 덧붙이고 `ready/` 에 그대로 둔다.

---

# Dependency Markers

- **선행**: `TASK-MONO-742`(계약) · `TASK-BE-614`(풀 모델) · `TASK-BE-615`(인증 흐름)
- **후속**: ~~`TASK-MONO-744`(데모 시드가 묶인 계정을 쓴다)~~ — 2026-10-02: 744 가 데모 계정을 처음부터 풀 계정 하나로 시드하므로 더는 이 티켓을 기다리지 않는다

# Goal

이미 팬과 스토어에 **따로** 계정이 있는 사람이 **본인 확인 한 번**으로 두 계정을 풀 계정 하나로 묶고, 사라지는 쪽 id 로 저장돼 있던 데이터가 살아남는 id 로 보이게 한다. 이메일 일치만으로는 절대 묶지 않는다(`ADR-MONO-034` § 1.3).

# Scope

## In Scope

- IAM: 묶기 흐름(로그인한 계정 + 다른 쪽 계정의 본인 확인) · 묶인 계정의 자격·역할·소셜 신원 정리
- id 이전: 팬 11 컬럼/10 테이블 · 이커머스 약 11 테이블(user·order·payment·shipping·review·coupon·notification 등) · 운영자 매핑(`ADR-MONO-044` D5 — 운영자 권한이 소비자 계정에 붙는다)
- 이전 방식(즉시 재기록 / 이벤트로 각 서비스가 재기록 / 별칭 표)의 결정과 구현

## Out of Scope

- 새 가입자(처음부터 풀 계정 — 이전 없음)
- 콘솔 운영자 모델 자체(D1)

# Acceptance Criteria

- [ ] **AC-00 (보류 게이트 — 2026-10-02 추가)** — 팬·스토어에 같은 이메일로 **따로** 계정이 있는 사람이 데모 시드 밖에 실제로 있다(실사용자 · 운영 데이터). 잰 방법: account_db 에서 같은 `email` 이 `fan-platform` 과 `ecommerce` 두 테넌트에 모두 있는 행 수 — 데모 시드 계정(`demo@demo.com`, `TASK-MONO-744` 가 풀 계정 하나로 바꾼다)은 빼고 센다. 0 이면 착수하지 않는다.
- [ ] **AC-0 (미결 — 구현 전에 답하고 기록)** — 🔴 **어느 id 가 살아남는가.** `ADR-MONO-078` § 라이더 대조가 라이더 표에 없던 질문으로 명명했다. 후보: 먼저 만들어진 계정 · 로그인한 쪽 · 데이터가 많은 쪽. 고르고 근거를 이 파일에 적는다.
- [ ] **AC-1 (R1 — 구현자 기본값)** — 본인 확인 = 다른 쪽 계정 **비밀번호 1회**. 소셜 전용 계정(비밀번호 없음)의 본인 확인 수단을 따로 정한다. 소유자가 「이메일 인증으로」 로 뒤집을 수 있다.
- [ ] **AC-2 (R2 — 구현자 기본값)** — 묶기는 **사용자가 원할 때만**(로그인 뒤 안내). 강제하지 않는다.
- [ ] **AC-3** — 🔴 **대조군**: 남의 이메일로 팬에 가입한 사람이 그 이메일의 **기존 스토어 계정**과 묶이지 않는다(스토어 비밀번호를 모르면 실패). 시험이 **실패 쪽을 먼저** 단언하고, 같은 시험에서 올바른 비밀번호일 때만 묶이는 것을 단언한다.
- [ ] **AC-4** — 묶은 뒤 팬의 팔로우·멤버십·게시글, 스토어의 주문·배송·리뷰가 살아남는 id 로 조회된다(서비스별 시험).
- [ ] **AC-5** — 묶은 사람이 운영자이기도 하면 콘솔 운영자 교환이 살아남는 id 로 성공한다.
- [ ] **AC-6** — 묶기 중 실패(한 서비스의 이전 실패)가 계정을 «반쯤 묶인» 상태로 남기지 않는다 — 재시도 가능하거나 되돌려진다.
- [ ] **AC-7** — 감사 로그: 누가 언제 어떤 두 계정을 묶었는가.

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md` D2 · § Verification
- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` § 1.3
- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` D5

# Related Contracts

- `TASK-MONO-742` 이 갱신한 `jwt-standard-claims.md` · `account-events.md`

# Edge Cases

- 같은 이메일, **다른 사람**이 팬·스토어에 각각 가입 — 묶기가 일어나면 안 된다(AC-3).
- 한 사람이 묶기 도중 다른 기기에서 로그인 중 — 토큰의 `sub` 가 바뀐다. 기존 토큰 처리를 정한다.
- 묶인 두 계정이 둘 다 운영자 매핑을 가진 경우.

# Failure Scenarios

1. 이메일 일치로 묶는 지름길 — 계정 탈취.
2. 한 프로젝트의 테이블만 이전 — 팬 데이터는 보이는데 스토어 주문이 사라진다(«부분 삭제가 전체 삭제로 읽힌다»).
3. 대조군이 «묶였다» 만 단언하고 «안 묶여야 할 때 안 묶인다» 를 안 잰다.

---

# 보류 기록 (2026-10-02 UTC) — 소유자 결정 «보류 + 744에서 시드 해결»

착수 전 측정(코드·시드 census):

| 잰 것 | 결과 |
|---|---|
| 팬·스토어에 같은 이메일 계정이 따로 있는 사람 | **1명** — `demo@demo.com`(ecommerce `…ec01` · fan-platform `…fa02`, `account-service` `R__05` · `auth-service` `R__01`). 아티스트 6명은 팬에만, e2e `e2e-consumer@example.com` 은 스토어에만 |
| 묶기 때 id 를 재기록할 곳 | 팬 **10 테이블 / 11 컬럼**(artist · community · membership · notification) · 이커머스 **11 테이블 / 11 컬럼**(user · order · payment · promotion · notification · review · shipping). 그중 UNIQUE/PK 에 걸린 것 7곳 — 재기록이 충돌할 수 있다(`follows` PK · `reactions` PK · `artists` UNIQUE · `reviews` UNIQUE(user, product) · `wishlist_items` UNIQUE · `user_profiles` UNIQUE · `user_notification_preferences` PK) |
| 외부 결제사로 나가는 계정 id | 없음(Toss `customerKey='ANONYMOUS'` · PortOne 은 paymentId/amount 만) |
| 데모 계정의 팬·스토어 데이터 | `infra/demo/seed/seed-ecommerce.sh` · `seed-fan.sh` 가 **로그인해 API 로** 만든다 → 계정을 풀 계정 하나로 시드하면 데이터가 그 id 하나로 저절로 쌓인다 |

⇒ 묶기 기능(본인 확인 화면 + 두 프로젝트 21 테이블 재기록 + 충돌 처리 + 중간 실패 복구)의 대상은 데모 계정 한 명이고, 그 한 명은 시드 변경으로 끝난다. 묶지 않은 두 사이트 계정은 지금처럼 따로 동작한다(`multi-tenancy.md` § 4 «사이트별 계정» 행) — 막히는 것은 그 이메일로의 **새** 풀 가입뿐이다(§ 2).

- 데모 계정 시드 → `TASK-MONO-744` 로 넘겼다(그 티켓 «743 보류 인계» 절).
- `TASK-BE-617` 의 선행에서 이 티켓을 뺐다(소셜 전용 계정의 본인 확인 수단은 묶기를 만들 때 다시 정한다).
- AC-0(어느 id 가 살아남는가)은 **미결 그대로** 둔다 — 착수 시 첫 질문이다. 위 표의 «재기록 위치 수» 가 그 판단의 입력이다(한 프로젝트 쪽 id 를 항상 남기면 재기록 코드는 반대 프로젝트 하나에만 생긴다).
