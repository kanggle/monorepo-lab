# Task ID

TASK-MONO-725

# Title

🔴 팬 `/membership` 한 화면이 **두 가격**을 보인다 — 공개 요금제 안내(4,900 / 12,900)가 백엔드가 청구하는 값(7,900 / 17,900)과 아무 연결이 없었다

# Status

review (2026-09-23 UTC — AC-0~AC-4 닫힘 · 🔴 AC-5(라이브 발행)는 소유자 · AC-6(재촬영)은 창)

# Owner

monorepo

# Task Tags

- fan
- demo-public-data
- content-drift
- portfolio

---

# Goal

`TASK-MONO-648` 의 README 큐레이션에서 후보 이미지를 열다가 보였다: 팬 `/membership` 의 **위 절**(가입 카드)은
«월 7,900원 / 월 17,900원», **아래 «요금제» 절**은 «4,900원 / 12,900원». 이름(«멤버스 전용» ↔ «멤버스»)과 혜택 목록도 달랐다.
이미 README·포트폴리오 문서에 실린 `04-fan-membership.jpg` 도 같은 화면이었다.

방문자는 안내 가격을 보고 들어와 **다른 금액을 결제**한다. 데모에서는 «이 숫자는 믿을 수 없다» 를 보여 주는 장이다.

---

# Scope

## 포함

- 공개 안내 fixture 를 정본에 맞추고, 두 정본과의 대조를 테스트로 박는다.
- 번들 스냅샷 재생성 · 팬 웹 테스트 기대값 · 이미 실린 모순 장(README · 포트폴리오 문서) 처리.

## 제외

- 가격 자체의 재설계(7,900 / 17,900 은 계약 `membership-api.md` 가 정한 값이다).
- Blob 발행 실행 — 이 저장소에 토큰이 없다(`TASK-MONO-575`). 소유자 몫으로 AC-5 에 적는다.

---

# Acceptance Criteria

- [x] 🟢 **AC-0 — 두 값의 출처를 지목.** 아래 § 원인.
- [x] 🟢 **AC-1 — 정본 결정 → fixture 정렬.** 소유자 결정(2026-09-23): **가입 카드가 정본**(이름·혜택·가격). `infra/demo/public-data/fixtures/membership-plans.mjs` 를 그대로 맞췄다.
- [x] 🟢 **AC-2 — 드리프트를 무는 대조 + bite.** `tests/public-data.test.mjs` 에 2칸 — 정본 **소스 파일을 직접 읽는다**(가격 = `MembershipPricing.java` 상수, 이름·혜택·표시가격 = `SubscribePanel.tsx` `TIERS`). 비공허성: 파서가 두 티어를 못 읽으면 빨강. bite: fixture 를 main 판으로 되돌리면 **새 2칸만** 빨강(41 pass / 2 fail), 고친 판 43/43.
- [x] 🟢 **AC-3 — 산출물 정합.** `build-bundled-snapshots.mjs` 재생성 → `--check` rc=0(재생성 전 rc=1). `fan.json` diff 는 `membershipPlans` 필드뿐. 팬 웹 `public-pages.test.tsx` 기대값 갱신(옛 값 부재까지 단언) — vitest **35 files / 302 tests** 통과.
- [x] 🟢 **AC-4 — 이미 실린 모순 장.** 소유자 결정: **지금 내리고 재촬영 때 다시 싣는다.** `fan-platform/README.md` 에서 04 제거 + 파일 삭제. 🔴 `docs/portfolio.md:67` 도 그 장을 참조하고 있었다 — 파일만 지웠으면 포트폴리오 문서의 이미지가 깨졌다 ⇒ 검증된 `03-fan-artist-profile.jpg` 로 교체.
- [ ] **AC-5 — 라이브 반영 (소유자).** 저장소는 번들 스냅샷을 고쳤다. 🔴 라이브 팬 웹이 **Blob 발행본**을 읽는 설정이면(`DEMO_PUBLIC_DATA_BASE_URL`), 그 발행본은 여전히 옛 값이다 ⇒ `BLOB_READ_WRITE_TOKEN=… node infra/demo/public-data/bin/publish-public-data.mjs --dataset fan --seed` 가 필요하다(README § 발행). 🔴 라이브가 어느 쪽을 읽는지는 **안 쟀다** — 판정 = 머지·배포 뒤 공개 `/membership` 을 열어 «7,900원» 이 보이는가.
- [ ] **AC-6 — 재촬영 (창).** 고친 판이 배포된 뒤 로그인 상태의 `/membership` 을 찍어 README 04(와 원하면 포트폴리오 문서)에 다시 싣는다 — 🔴 이미지를 **열어** 위·아래 가격이 같은지 본다.

---

# 원인

| 절 | 출처 | 들어온 날 |
|---|---|---|
| 위 (가입 카드) | `SubscribePanel.tsx` `TIERS` 표시 문구 · 결제액 = 백엔드 `MembershipPricing`(`MEMBERS_ONLY_MONTHLY_MINOR = 7_900L` · `PREMIUM_MONTHLY_MINOR = 17_900L`), 계약 `membership-api.md:95` | 2026-07-24 (`25a225702`, TASK-FAN-BE-032) |
| 아래 (공개 요금제 안내) | `infra/demo/public-data/fixtures/membership-plans.mjs` — 저장소가 쓴 `authored` 문구 | 2026-09-08 (`9f0fcd2d6`, ADR-MONO-070/071) |

🔴 fixture 는 백엔드 가격이 정해진 지 **6주 뒤**에 쓰였고 **아무것도 대조하지 않았다.** 그 파일의 주석은 *"로그인 후 실제 가입 화면과 어휘가 어긋나면 방문자가 다른 상품으로 읽는다"* 라고 적으면서 티어 **키**만 맞췄다 — 이름·가격·혜택은 대조 대상이 아니었다. `note`(«표시 가격은 안내용입니다») 가 있어도 같은 화면 바로 위의 가격과 다르면 그 문장은 모순을 설명하지 못한다.

🔵 앱의 나머지 화면(`RenewPanel` · `MembershipStatusCard` · `MembershipHistoryList` · `AutoRenewToggle`)은 전부 «멤버스 전용» — 어긋난 것은 공개 안내 하나였다.

🔵 재촬영 의무를 `TASK-MONO-672` 로 옮기지 않은 이유: 그 집은 **닫히는 티켓**의 의무만 받는다. 이 티켓은 AC-6 이 열린 채 review 에 있다.

---

# Related Specs

- `projects/fan-platform/specs/contracts/http/membership-api.md` §95 (티어 가격)
- `projects/fan-platform/specs/services/membership-service/architecture.md` §189
- `infra/demo/public-data/README.md` § 발행
- `docs/adr/ADR-MONO-070` (공개 저장본)

# Related Contracts

- 위 membership 계약 — **안 바꿨다**(가격의 정본이다).

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 백엔드 가격이 바뀐다 | 대조 테스트가 빨개진다 → fixture·가입 카드를 함께 고친다 |
| 가입 카드 문구만 바뀐다 | 같은 테스트의 이름·혜택 칸이 빨개진다 |
| 가입 카드의 표시 가격 문자열만 바뀐다 | «가입 카드 표시 가격이 청구액과 다르다» 로 빨개진다 — 두 정본끼리의 어긋남도 문다 |
| `SubscribePanel` 의 `TIERS` 모양이 바뀌어 파서가 못 읽는다 | 비공허성 단언으로 빨강(초록으로 공허해지지 않는다) |

# Failure Scenarios

1. **fixture 숫자만 고치고 대조를 안 둔다** → 다음 가격 변경 때 같은 모순이 조용히 돌아온다.
2. **테스트에 7900 리터럴을 복사해 둔다** → 그것이 셋째 출처가 되어 같은 드리프트를 다시 만든다(그래서 소스 파일을 읽는다).
3. **README 파일만 지운다** → `docs/portfolio.md` 의 이미지가 깨진다(실제로 참조가 있었다).

---

# 분석 / 구현 권장

분석=Opus 5.5 / 구현 권장=Sonnet (값 교정 + 대조 테스트)

---

## CORRECTION (2026-09-23 UTC) — AC-5 는 **실측으로 닫혔다**: 발행이 필요 없었다

🔴 위 AC-5 는 *"라이브 팬 웹이 Blob 발행본을 읽는 설정이면 … 발행이 필요하다 … 라이브가 어느 쪽을 읽는지는 안 쟀다"* 라고 적었다. **그 질문의 답은 이미 저장소에 있었다** — `TASK-MONO-678`(done) 이 *"Vercel Blob 저장본 — 안 쓰인다 (`DEMO_PUBLIC_DATA_BASE_URL` 미설정)"* 로 기록해 두었다. 그것을 안 읽고 소유자에게 토큰·store URL 을 찾게 했고, 소유자가 두 번 실행해 두 번 다 `@vercel/blob` 로딩에서 멈췄다(네트워크 요청 0 — 부작용 없음).

**실측 (2026-09-23 ≈17:40Z, #3974 머지 `be31b4eae` 뒤)** — `curl https://fan.hubwang.com/membership` → HTTP **200**, 원문에서:

| 문자열 | 횟수 |
|---|---|
| `17,900` | 2 |
| `7,900` | 4 (그중 2 는 `17,900` 의 부분) |
| `4,900` · `12,900` | **0** · **0** |
| `멤버스 전용` | 2 |
| `아직 발행 전` (번들 봉투 배너) | 2 |

⇒ 라이브는 **번들 스냅샷**을 읽고, 머지 뒤 Vercel 재배포로 이미 고친 값을 보인다. **AC-5 = 닫힘(발행 불필요).**

🔴 위 AC-5 의 발행 명령도 **틀렸다** — `DEMO_PUBLIC_DATA_BASE_URL` 이 빠졌다(정본은 `infra/demo/public-data/README.md` § 발행). 그 명령을 따라 하지 마라. Blob 발행이 필요해지는 것은 `DEMO_PUBLIC_DATA_BASE_URL` 이 Vercel 에 **설정된 뒤**뿐이다.

🔵 남은 것 = **AC-6 재촬영(창)** 하나.
