# Task ID

TASK-MONO-373

# Title

결제 경로 e2e 4개가 **CI 어디에서도 실행되지 않는다** — nightly 두 잡이 스택의 반쪽씩 갖고 한 번도 같이 뜨지 않아서. `frontend-e2e-fullstack` 은 컨테이너 22개를 띄우고 **백엔드를 다 꺼도 통과할 스위트**를 돌린다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- test

---

# Dependency Markers

- **발굴 출처**: 2026-07-12 미티켓 3차원 스윕(code-marker 축).
- **유예를 남긴 선행 task 들 (전부 done, 재티켓팅 없음)**: `TASK-MONO-014`(§ Out of Scope 에서 GAP 컨테이너 유예) → `TASK-MONO-318`(*"기존 `frontend-e2e-fullstack` 잡은 `SKIP_GAP_E2E=1` 유지"*). **유예가 두 task 를 건너 살아남았고 아무도 다시 열지 않았다.**
- **통과 실증**: `TASK-MONO-292` AC-4 — *"web-store wishlist e2e 가 `SKIP_GAP_E2E=0` 에서 **3/3 통과** (federation demo stack 상대)"*. ⇒ **못 만드는 게 아니라 배선이 안 된 것이다.**

---

# Goal

## 착수 전 실측 (2026-07-12, `875e583cd`)

nightly 에 두 잡이 있고, **둘 다 스택의 반쪽만 갖는다**:

| 잡 | 띄우는 것 | 빠진 것 | 실제로 도는 스펙 |
|---|---|---|---|
| `frontend-e2e-fullstack` (nightly-e2e.yml:196) | ecommerce 백엔드 12개 + 인프라 10개, 게이트웨이 health 7분 대기, 60분 예산 | **IAM 없음** | `SKIP_GAP_E2E=1` → `auth-redirect.spec.ts` **하나** |
| `web-store-iam-logout-e2e` (nightly-e2e.yml:390) | IAM(`docker-compose.iam-e2e.yml` = auth-service + mysql + account-mock) | **ecommerce 백엔드 없음** | `rp-initiated-logout.spec.ts` 하나 |

`web-store/e2e/` 의 스펙 6개 중 **5개가 `shouldSkipGap()` 게이트**를 갖는다. 그중 **4개**(`golden-flow` · `cart-management` · `wishlist` · `account-type-guard`)는 **로그인(IAM) + 장바구니/주문 백엔드 둘 다** 필요하다.

**⇒ 두 조건을 동시에 만족하는 잡이 없으므로 이 4개는 어느 잡에서도 실행되지 않는다.** 워크플로 전체 grep 에서 이 스펙들이 등장하는 유일한 곳은 **"skip 한다" 는 주석**이다.

## 그래서 `frontend-e2e-fullstack` 이 실제로 단언하는 것

유일하게 도는 `auth-redirect.spec.ts` 를 전문 읽었다: 비로그인 상태로 보호 라우트에 가서 **307 리다이렉트 + 로그인 버튼 표시**를 단언한다. 전부 **NextAuth 미들웨어와 정적 페이지**가 처리한다 — **백엔드 호출 0건.**

**⇒ 이 잡은 백엔드 12개를 전부 꺼도 똑같이 통과한다.**

**정직하게 — 잡이 아무것도 안 하는 건 아니다.** `Wait for gateway health` 스텝이 `exit 1` 이므로 **"스택이 뜨고 게이트웨이가 healthy 하다"는 단언은 실재**한다. 문제는 **이름(`full-stack`) · 60분 예산 · 컨테이너 22개가 광고하는 커버리지가 없다**는 것이다. 체크아웃 · 장바구니 · 위시리스트 · 크로스-앱 role 가드 회귀는 **이 레인을 초록으로 통과해 나간다.**

## 왜 아무도 못 봤나

`nightly-e2e.yml:219` 주석이 *"the smoke job (`frontend-e2e-smoke` in ci.yml) covers them instead"* 라고 말한다. **성립하지 않는다** — `playwright.smoke.config.ts:14` 의 `testDir` 은 **`./e2e-smoke`** 로 **다른 디렉터리**이고 그 안엔 `smoke.spec.ts` 하나뿐이다. 이 4개 스펙은 `./e2e` 에 있고 **아무 데서도 안 돈다.**

---

# Scope

## In Scope

1. **한 잡에서 두 반쪽을 함께 띄운다** — ecommerce 백엔드 + IAM. 조각은 이미 다 있다:
   - `projects/ecommerce-microservices-platform/docker-compose.yml`(50 서비스, web-store + gateway + cart/order/user 포함, **IAM 없음**)
   - `projects/ecommerce-microservices-platform/docker-compose.iam-e2e.yml`(auth-service + mysql + redis + kafka + account-mock, **ecommerce 없음**)
   - 둘 다 이미 nightly 에서 **따로** 쓰인다.
2. **`SKIP_GAP_E2E=0`** 으로 4개 스펙을 실제로 실행.
3. **스펙이 실제로 실행됐음을 증명** — Playwright 리포트의 **실행 건수**를 확인한다. `passed` 문구가 아니라 **skipped=0 + 실행 카운트**. (`BUILD SUCCESSFUL` 이 전건 SKIPPED 를 못 거르는 것과 같은 함정.)
4. **주석 교정** — `nightly-e2e.yml:219` 의 *"smoke job covers them"* 은 거짓이다. 지우거나 사실로 고친다.

## Out of Scope

- **`auth-redirect.spec.ts` 를 없애기** — 미들웨어 회귀 가드로서 정당하다. 백엔드를 안 건드릴 뿐.
- **스펙 자체를 다시 쓰기** — `MONO-292` 가 **3/3 통과**를 실증했다. 스펙은 멀쩡하다. **배선만 하라.**
- **`web-store-iam-logout-e2e` 잡 제거** — 그 잡의 로그아웃 스펙은 ecommerce 백엔드가 필요 없다. 통합할지는 구현자 판단이되, **없애서 커버리지를 잃지 말 것.**
- **fed-e2e compose 재사용** — 실측: `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` 에는 **ecommerce 백엔드가 없다**(wms/scm/finance/erp + console). 이 스택으로는 안 된다.

---

# ⚠️ 착수 전 결정해야 할 것 (구현자가 판단)

**어느 IAM 을 띄울 것인가.**

- **A) `docker-compose.iam-e2e.yml` 오버레이** — 가볍다. 단 **`account-mock`** 을 쓴다(실제 account-service 가 아님). 로그인이 되는지, 그리고 `sub` 가 UUID 로 나오는지(**`MONO-291` 이 정확히 이걸로 물렸다** — 하드시드된 `sub` 가 이메일이라 wishlist 쓰기가 막혔다) **먼저 확인할 것.**
- **B) iam-platform 실제 compose** — 무겁지만 실물. `MONO-292` 가 3/3 통과를 본 스택에 가깝다.

**추측하지 말고 실측할 것.** 잘못 고르면 *"스택은 healthy 한데 로그인이 안 되는"* 상태가 되고, 그건 `MONO-358` 이 통째로 겪은 자리다.

---

# Acceptance Criteria

- [ ] **AC-1** — 4개 스펙(`golden-flow` · `cart-management` · `wishlist` · `account-type-guard`)이 CI 잡에서 **실제로 실행**된다.
- [ ] **AC-2 (헛된 초록 배제)** — 리포트에서 **실행 건수 + skipped=0** 을 확인한다. **"잡이 초록이다" 는 증거가 아니다** — 지금 상태도 초록이고 아무것도 안 돌고 있다.
- [ ] **AC-3 (가드가 실제로 무는가)** — 백엔드 하나(예: cart-service)를 의도적으로 깨뜨리거나 스텁 응답을 바꾼 **mutation 에서 이 잡이 RED** 가 되는지 확인. **지금은 백엔드를 통째로 꺼도 초록이다.** 이 mutation 이 통과하지 못하면 배선이 여전히 vacuous 하다.
- [ ] **AC-4** — `nightly-e2e.yml:219` 의 거짓 주석("smoke job covers them") 제거/교정.
- [ ] **AC-5** — 기존 커버리지 무손실: `auth-redirect` · `rp-initiated-logout` 는 계속 돈다.

---

# Related Specs

- `.github/workflows/nightly-e2e.yml` L196(`frontend-e2e-fullstack`) · L390(`web-store-iam-logout-e2e`)
- `projects/ecommerce-microservices-platform/apps/web-store/e2e/` — 스펙 6개 + `helpers/auth.ts`(`shouldSkipGap()`)
- `projects/ecommerce-microservices-platform/apps/web-store/e2e/CI-IAM-E2E-HANDOFF.md` — **이 유예를 문서화한 핸드오프 노트**(읽을 것)
- `tasks/done/TASK-MONO-292-*.md` AC-4 — 3/3 통과 실증
- `tasks/done/TASK-MONO-291-*.md` — `sub` 가 UUID 가 아니면 wishlist 쓰기가 막힌다(IAM 선택 시 함정)

# Related Contracts

없음 — CI 배선.

---

# Edge Cases

- **`account-type-guard.spec.ts:21` 의 `test.skip(shouldSkipGap, …)`** — 형제 3개는 `shouldSkipGap()` **호출**인데 여기만 **함수 참조**다. 항상-skip 버그처럼 보이지만 **아니다**(Playwright 의 `test.skip(callback, desc)` 오버로드가 콜백을 호출한다). **고치려 들지 말 것 — 동작 동일하다.** 스윕이 이걸 결함으로 오인할 뻔했다.
- **부팅 시간** — 백엔드 12개 + IAM + 인프라. 60분 예산 안에 드는지 실측. 안 되면 스펙을 나누기보다 **잡을 나누는 것**을 검토(단, 4개 다 돌아야 한다).

# Failure Scenarios

- **F1 — 배선했는데 스펙이 여전히 skip 된다** → `SKIP_GAP_E2E` 가 **Playwright 러너와 `webServer` env 양쪽**에 전달돼야 한다(`playwright.config.ts:70` 이 `webServer.env` 로도 넘긴다). AC-2 의 실행-건수 확인이 이걸 잡는다.
- **F2 — 잡이 초록인데 아무것도 안 돈다**(현 상태의 재현) → AC-2 + AC-3.
- **F3 — IAM 을 잘못 골라 로그인이 안 된다** → 스택은 healthy, 스펙은 전부 실패. § "착수 전 결정" 참조.

---

# Test Requirements

- 실제 CI 런에서 4개 스펙 실행 + **skipped=0**.
- **mutation: 백엔드를 깨뜨리면 이 잡이 RED** (AC-3). 이게 없으면 배선을 증명하지 못한다.

---

# Definition of Done

- [ ] AC-1 ~ AC-5.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-12 — 미티켓 3차원 스윕(code-marker 축).

**이 결함의 모양이 이 저장소가 반복해서 배운 것과 정확히 같다**: **가드가 있다고 믿었는데, 그 가드는 물 기회를 얻지 못하고 있었다**(`MONO-359`/`360`). 다만 여기선 트리거가 아니라 **스택의 절반이 빠져 있어서**다. 그리고 **skip 은 초록으로 보고된다.**

**유예가 두 task(MONO-014 → MONO-318)를 건너 살아남았고 아무도 다시 열지 않았다** — 티켓 없는 유예는 썩는다(`MONO-369` 가 같은 이유로 만들어졌다).

분석=Opus 4.8 / 구현 권장=**Opus**(IAM 선택 판단 + 부팅 오케스트레이션. 잘못하면 "healthy 한데 로그인 안 됨" = MONO-358 이 통째로 겪은 자리).
