# Task ID

TASK-MONO-686

# Title

⏳ 모든 실제 화면에 샘플이 찬 뒤 **콘솔 둘러보기(`/demo`)를 은퇴**시킨다 — `(demo)` 그룹 · `features/demo-tour` · `console-sample` 데이터셋 (`ADR-MONO-074` 실행 8/8)

# Status

done

# Owner

monorepo

# Task Tags

- platform-console
- demo
- cleanup

---

# ⏳ SCHEDULED — DO NOT START before `TASK-PC-FE-282` ~ `TASK-PC-FE-288` 전부 `done/`

**AC-0 verify-then-act 게이트**: ① 일곱 티켓이 `projects/platform-console/tasks/done/` 에 있는가 ② 샘플 원장
(`shared/sample/coverage`)의 `pending` 이 **0** 인가 — 🔴 둘 다 **재서** 확인한다(INDEX 행이 아니라 파일과 원장). 하나라도
아니면 **STOP**. 🔴 원장이 0 이 아닌데 지우면 그 도메인의 방문자에게 «준비 중» 만 남는다(`ADR-MONO-074` § Roadmap 8 의 이유).

---

# Goal

`ADR-MONO-074` ACCEPTED(A · R1ⓐ · R2ⓐ · R3ⓐ) 의 마지막 단계. 익명 방문자가 실제 콘솔 화면을 샘플로 보게 된 뒤 남는
**두 번째 UI**(`/demo`)와 그것만 먹이던 데이터셋을 걷어 낸다.

루트 티켓인 이유: `infra/demo/public-data/**` 가 `projects/` 밖이다(`tasks/INDEX.md` § When to Use Root vs Project Tasks).

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/src/app/(demo)/**` · `src/features/demo-tour/**` 삭제
- `/demo` → `/dashboards/overview`, `/demo/<domain>` → 그 도메인의 실제 경로 **308**
- 둘러보기 전용 테스트 삭제: `tests/unit/demo-tour-*` (🔴 `demo-tour-console-guard-regression` 은 282 가 기대값을 바꿔 **살아 있는 가드**다 — 이름만 옮기고 지우지 않는다) · `e2e-smoke/demo-tour.spec.ts`
- `infra/demo/public-data` 의 `console-sample` 데이터셋 은퇴(픽스처 · 스냅샷 · 데이터셋 선언 · 소비자 마커)
- 저장소 안에서 콘솔 `/demo` 를 가리키는 링크·문구(론처 `infra/demo/aws/site/index.html` · README · `docs/portfolio.md` · `vercel-ignore.sh` 등) 갱신

## Out of Scope

- 팬·스토어 공개 봉투(`ADR-MONO-070` D1~D5) — 그대로
- 샘플 모드 자체 — 282 가 소유

---

# Acceptance Criteria

- [x] **AC-0** 위 게이트. — § Implementation notes AC-0 (7 티켓 done 확인 + 원장 재측정, `pending` 0).
- [x] **AC-1** 삭제 전 **소비자 grep** — `demo-tour` · `console-sample` · `DEMO-PUBLIC-DATA-CONSUMER: console-web` · `'/demo` 를 저장소 전체에서 세고, 남는 참조가 0 이 될 때까지 표로 이 파일에 적는다(삭제가 남긴 것의 소비자). — § Implementation notes AC-1 표. 실물(코드·마커) 잔존 0, 라이브 참조는 전부 설명됨, 동결 기록은 안 건드림.
- [x] **AC-2** 308 매핑 테스트: `/demo` 와 삭제 시점 데이터셋의 **모든** 도메인 키가 실제 경로로 간다(🔴 목록을 손으로 적지 말고 삭제 직전 픽스처에서 뽑아 고정). — § Implementation notes AC-2. `next.config.mjs` `DEMO_TOUR_DOMAIN_REDIRECTS`(7 키, 픽스처에서 옮김) + 유닛·e2e 이중 증명.
- [x] **AC-3** `public-data` 패키지 테스트·발행 CLI 가 `console-sample` 없이 초록(데이터셋 목록을 세는 곳이 있으면 그 수도). — § Implementation notes AC-3. `node --test` 41/41 · `--check` 2 데이터셋 드리프트 없음 · CLI 거부 확인.
- [x] **AC-4** 🔴 `scripts/` 에 추가·삭제가 생기면 **가드 전수** 실행(부분 선택 금지 — `CLAUDE.md` § Task Rules). — `scripts/` 무변경, 해당 없음(§ Implementation notes AC-4).
- [x] **AC-5** `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(console-web) 각각 독립 `rc=0`. — § Implementation notes AC-5 게이트 표, 전부 rc=0.
- [ ] **AC-6** 머지 후 첫 `nightly-e2e.yml` 콘솔 스펙 1회 확인. — ⚪ 코디네이터 몫(과업 지시 § 6), 이 PR 은 `tests/e2e/**` 무변경.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` § Roadmap 8
- `docs/adr/ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md` (D6 — 부분 대체됨)

# Related Contracts

- 없음(공개 봉투 계약에서 콘솔 데이터셋이 빠질 뿐 팬·스토어 계약 불변)

# Edge Cases

- 외부에 `/demo` 링크가 이미 퍼져 있다(포트폴리오·이력서) → 308 이 그 링크를 살린다. 지우지 않는다.
- `TASK-MONO-680` 이 바꾼 `/demo` 문구·`sample-actions.ts` 는 여기서 함께 사라진다(의도).

# Failure Scenarios

- 원장 `pending` 이 0 이 아닌데 착수 ⇒ AC-0 STOP.
- 데이터셋을 세는 가드가 수 불일치로 빨강 ⇒ AC-4 전수 실행에서 드러난다.

# Test Requirements

- AC-5 · AC-4

# Definition of Done

- [x] `(demo)` · `features/demo-tour` · `console-sample` 0 — 삭제 확인(§ AC-1 표).
- [x] `/demo/**` 외부 링크가 실제 화면으로 간다 — 308, § AC-2.

분석=Opus 5 / 구현 권장=Sonnet 5.

---

# Implementation notes (구현 에이전트, 2026-09-17 UTC)

## AC-0 — verify-then-act 게이트 재확인 (이 워크트리에서 직접 재측정)

① `projects/platform-console/tasks/done/`에 `TASK-PC-FE-282`~`288` 7개 파일 전부 존재 확인(`ls` 로 재확인, INDEX 행이 아니다).
② `shared/sample/coverage.ts`를 코드로 다시 셌다(문자열 리터럴 카운트, INDEX 문장 인용 아님):

| | `SURFACE_COVERAGE` | `SCREEN_COVERAGE` |
|---|---:|---:|
| `ready` | 33 | 58 |
| `static` | — | 6 |
| `pending` | **0** | **0** |

`status: 'pending'`/`'pending'` 문자열 리터럴 전수 grep 도 0건(코드의 `screenStatusFor()` 기본 반환값 `'pending'` 한 곳 제외 — 그것은 데이터 행이 아니라 "원장에 없는 화면"의 안전한 기본값이다). ⇒ 게이트 통과, STOP 아님.

## AC-1 — 삭제 전 소비자 grep (before/after)

**BEFORE(코디네이터 측정, `origin/main` `4d273a826`, `tasks/done` · `projects/*/tasks/done` · `docs/adr` 제외)**: `demo-tour` 17 파일 · `console-sample` 14 · `DEMO-PUBLIC-DATA-CONSUMER: console-web` 2 · `'/demo` 5 · `"/demo` 3.

**AFTER(이 워크트리, 전체 저장소 — 제외 없이 다시 셌다)**:

| 패턴 | AFTER 파일 수 | 남은 참조 분류 |
|---|---:|---|
| `demo-tour` | 13 | **살아 있는 코드/문서, 전부 설명됨**: 이 티켓 파일 자신 · 루트 `tasks/INDEX.md`(review 행, 이 티켓의 요약) · `console-guard-idle-refresh.test.tsx`(옛 파일명 → 새 이름 포인터 주석) · `e2e-smoke/root-redirect.spec.ts`(같은 포인터) · `e2e-smoke/legacy-demo-redirects.spec.ts`(자기 docstring 이 새 유닛 테스트 파일명 `legacy-demo-tour-redirects.test.ts` 를 인용 — 파일명 자체에 그 문자열이 들어 있다) · `next.config.mjs`(`DEMO_TOUR_DOMAIN_REDIRECTS` 상수명 + 은퇴 배경 주석). **다른 세션 소유(수정 안 함)**: `tasks/in-progress/TASK-MONO-674`(리네임 사실만 여기 기록, § 아래). **동결된 기록(수정 안 함)**: `tasks/done/{680,682,655,638}` · `projects/platform-console/tasks/done/TASK-PC-FE-282` · `docs/adr/ADR-MONO-074` — 전부 이미 닫힌 티켓/ADR 이고 그 시점의 사실을 기록한다. |
| `console-sample` | 13 | **살아 있는 코드, 전부 설명됨**: `tests/unit/legacy-demo-tour-redirects.test.ts`(삭제 직전 픽스처 출처 주석) · 이 티켓 파일 · 루트 `tasks/INDEX.md` · `vercel-ignore.sh`(제거 사유 주석) · `next.config.mjs`(출처 주석) · `infra/demo/public-data/src/datasets.ts`(타입 제거 사유 주석) · `infra/demo/public-data/bin/publish-public-data.mjs`(플래그 설명에 "은퇴됨" 명시). **동결된 기록**: `tasks/done/{680,655,638,640}` · `projects/platform-console/tasks/done/TASK-PC-FE-282` · `docs/adr/ADR-MONO-074`. |
| `DEMO-PUBLIC-DATA-CONSUMER: console-web` | 1 | **0 — 실제 마커는 삭제됐다.** 유일한 잔존은 이 티켓 파일 자신이 "이 마커가 있던 파일을 지웠다"고 설명하는 문장. |
| `'/demo` | 6 | **전부 이 은퇴 작업의 산물, 설명됨**: `legacy-demo-tour-redirects.test.ts` · 이 티켓 파일 · `console-guard-idle-refresh.test.tsx` · `root-redirect.test.ts` · `legacy-demo-redirects.spec.ts` · `next.config.mjs` — 전부 "옛 `/demo` 경로가 이제 308 로 어디로 가는지"를 검증·문서화하는 코드다. |
| `"/demo` | 0 | 변동 없음(BEFORE 3 도 `tasks/done`/`docs/adr` 안이었다 — 이번 셈은 그 디렉터리를 포함했는데도 0 — BEFORE 표본이 이미 그 디렉터리 안의 문자열이었다는 뜻). |

🔴 **BEFORE 와 AFTER 는 모집단이 다르다** — 코디네이터의 BEFORE 는 `tasks/done` · `projects/*/tasks/done` · `docs/adr` 를 제외했고, 위 AFTER 표는 **제외 없이** 저장소 전체를 다시 세어 "동결된 기록"까지 눈으로 분류했다(제외 규칙을 그대로 물려받으면 그 규칙 자체가 맞는지 검증할 수 없다 — 실제로 위 표의 "동결된 기록" 행들이 정확히 그 세 디렉터리 안에 있다는 것을 눈으로 확인했다). **라이브 코드/문서 안의 참조는 전부 이 티켓이 설명 가능한 이유로 남긴 것**이고, 삭제된 실물(`(demo)` 그룹 · `features/demo-tour` · `console-sample.mjs`/`.json` · `read-console-sample.ts` 의 `DEMO-PUBLIC-DATA-CONSUMER` 마커)은 0건이다.

## AC-2 — 308 매핑 (도메인 키는 손으로 안 지었다)

삭제 직전에 읽은 `infra/demo/public-data/fixtures/console-sample.mjs`(`CONSOLE_SAMPLE_DOMAINS`, 이 커밋이 함께 지웠다)의 `key`·`liveHref` 필드에서 **그대로** 옮겼다:

| `/demo/<key>` | → 실제 경로 | 출처 |
|---|---|---|
| `/demo` (맨살) | `/dashboards/overview` | 폴백 규칙(아래) |
| `/demo/overview` | `/dashboards/overview` | `CONSOLE_SAMPLE_DOMAINS[0]` |
| `/demo/ecommerce` | `/ecommerce/orders` | 〃 |
| `/demo/wms` | `/wms/inventory` | 〃 |
| `/demo/scm` | `/scm/procurement` | 〃 |
| `/demo/erp` | `/erp/approval` | 〃 |
| `/demo/finance` | `/finance/accounts` | 〃 |
| `/demo/iam` | `/iam` | 〃 |
| `/demo/<모르는 키>` | `/dashboards/overview` | 폴백(아래) |

구현: `next.config.mjs` 의 `DEMO_TOUR_DOMAIN_REDIRECTS`(동결 배열, 7개 도메인) + `redirects()`가 각 키를 개별 규칙(구체적 → 배열 앞쪽)으로 펴고, 마지막에 `source: '/demo/:path*'`(세그먼트 0개도 매칭 — 맨살 `/demo` 도 이 한 줄로 덮인다) 폴백을 `/dashboards/overview` 로 둔다. 전부 `permanent: true`(308).

**증명 두 겹**:
- 유닛(`tests/unit/legacy-demo-tour-redirects.test.ts`): ① `DEMO_TOUR_DOMAIN_REDIRECTS` 의 키 집합이 이 파일에 동결해 둔 `FROZEN_TOUR_DOMAIN_KEYS`(위 표의 7개, 그 문구 그대로 삭제 전 픽스처에서 옮겼다는 주석 포함)와 **정확히** 같다(개수·중복 없음) ② `redirects()` 실제 반환값에서 7개 전부의 destination·permanent 확인 ③ 폴백 규칙 존재 + destination ④ 구체적 규칙이 배열에서 폴백보다 앞선다(Next 는 첫 매치를 쓴다).
- e2e-smoke(`legacy-demo-redirects.spec.ts`, 실제 프로덕션 빌드): 맨살 `/demo`·`/demo/ecommerce`·모르는 `/demo/no-such-domain` 세 칸을 라이브로 확인(전수는 유닛 몫, e2e 는 "배선이 실제로 켜졌는지"만 증명).

## AC-3 — `public-data` 패키지 · 발행 CLI

`console-sample`을 `PUBLIC_DATASETS`(`fan`·`store` 만 남음) · `PublicDataByDataset` · `validateDatasetData` · `index.ts` 재수출 · `build-bundled-snapshots.mjs` 의 `BUILDERS`/`assertNoLeak` 분기에서 제거. 발행 CLI 는 `console-sample`을 주면 이제 `--dataset 은 fan | store 중 하나여야 합니다` 로 거부(라이브 확인, 아래 게이트 표).

데이터셋 수를 세는 곳: `PUBLIC_DATASETS.length`(코드가 스스로 세므로 별도 갱신 불필요) · `README.md`(발행 예시의 `--dataset console-sample --seed` → `--dataset fan --seed`, "아티스트·상품·콘솔" → "아티스트·상품") · `tests/public-data.test.mjs` 의 `for (const ds of ['fan', 'store', 'console-sample'])` → `['fan', 'store']`.

## AC-4 — `scripts/` 변경 여부

`scripts/` 에 파일을 추가하거나 지우지 않았다(이 커밋의 `git status` 에 `scripts/` 항목 0건) ⇒ 가드 전수 실행 의무 **해당 없음**. 그래도 요구된 세 필수 가드는 § 게이트 표에서 돌렸다.

## AC-5 — 로컬 게이트 (각각 독립 statement, 명시 `rc`)

BEFORE 는 이 워크트리를 `git stash push -u` 로 되돌린(분기점 `origin/main` = `4d273a826`) 상태에서, AFTER 는 최종 트리에서 쟀다.

| 게이트 | 트리 | 결과 |
|---|---|---|
| `pnpm test`(console-web) | BEFORE | rc=0 · **315 files / 3518 tests passed** |
| `node --test tests/public-data.test.mjs` | BEFORE | rc=0 · **42/42** |
| `node bin/build-bundled-snapshots.mjs --check` | BEFORE | rc=0 · fan·store·console-sample 드리프트 없음 |
| `pnpm lint`(console-web) | AFTER | rc=0 · «No ESLint warnings or errors» |
| `npx tsc --noEmit`(console-web) | AFTER | rc=0(1차 시도에서 신규 테스트 파일의 `possibly undefined` 2건 발견 → `if (!x) throw` 로 좁혀 재실행 rc=0) |
| `pnpm test`(console-web) | AFTER | rc=0 · **313 files / 3499 tests passed** — Δ파일 −2 · Δ테스트 −19, 산술: 삭제 3파일(`demo-tour-{disabled-controls(4)·filter(7)·pages(13, `it.each` 6행 포함)}`=24 테스트) − 리네임 2파일(순변화 0) + 신규 1파일(`legacy-demo-tour-redirects.test.ts`=5 테스트) ⇒ 파일 −3+1=−2 · 테스트 −24+5=−19(정확히 일치) |
| `pnpm build`(console-web) | AFTER | rc=0 · `(demo)`/`/demo/*` 라우트 0개, 유지된 `/api/demo/heartbeat` 만 "demo" 문자열로 남음 |
| `pnpm e2e:smoke`(console-web) | AFTER | rc=0 · **22 passed** — BEFORE(TASK-PC-FE-288 기록) 24 passed − `demo-tour.spec.ts` 삭제(5) + `legacy-demo-redirects.spec.ts` 신규(3) = 22(정확히 일치) |
| `node --test tests/public-data.test.mjs` | AFTER | rc=0 · **41/41**(console-sample 계약 테스트 1건 감소) |
| `node bin/build-bundled-snapshots.mjs --check` | AFTER | rc=0 · fan·store 만, 드리프트 없음 |
| `node bin/publish-public-data.mjs --dataset console-sample --seed` | AFTER | rc=1 · `--dataset 은 fan \| store 중 하나여야 합니다` (의도된 거부 — AC-3 라이브 확인) |

## AC-6 — 머지 후 nightly 확인

⚪ **코디네이터 몫으로 남긴다**(과업 지시 § 6). `tests/e2e/**`(nightly full-stack)은 이 커밋에서 **건드리지 않았다** — grep 확인: 그 트리 안에 `/demo`·`demo-tour`·`console-sample` 참조 0건. 콘솔 헤딩·testid 변경도 이번 커밋엔 없다(순수 삭제 + `next.config.mjs` redirects + 테스트 파일명 변경뿐). 그래도 `/demo` 경로가 사라지는 변경이라 다음 nightly 콘솔 스펙 1회는 코디네이터가 확인해야 한다.

## 다른 세션과의 경계

- **`TASK-MONO-648`(다른 세션, in-progress)** — 론처 `infra/demo/aws/site/index.html` 의 콘솔 카드 `<dt>로그인 없이</dt>` 항목이 `<code>/demo</code>` 를 가리키던 문장을 "실제 콘솔 화면을 샘플 데이터로(«(샘플)» 표기 + 배너) — 대시보드·주문·재고·조직·권한 등 64개 화면"으로 바꿨다. **648 이 이미 결정한 것(캐러셀 사진 2장·"🔒 로그인 후 화면" 라벨·"로그인 후" `<dt>` 문구·`needs-boot` 배지)은 한 글자도 안 건드렸다** — h2 의 판정 링크(`data-url="https://console.hubwang.com/"`)는 원래도 `/demo` 를 가리키지 않았으므로(루트 도메인이었다) 이 변경은 순수하게 `<dd>` 텍스트 하나다. 648 이 이 파일에 남긴 "다음 데모 창 묶음"(사진 교체) 계획과 충돌하지 않는다.
- **`TASK-MONO-674`(다른 세션, in-progress)** — 그 파일은 편집하지 않았다. 그 파일이 인용하는 `tests/unit/demo-tour-console-guard-regression.test.tsx:94`·`demo-tour-console-guard-regression`(파일 전체 실행 인자로 여러 곳) 은 이 커밋에서 **`tests/unit/console-shell-sample-visitor-guard.test.tsx`** 로 이름만 옮겼다(내용·줄 번호 불변 — git mv, 바이트 동일). 674 owner 가 `npx vitest run tests/unit/{...,demo-tour-console-guard-regression,...}` 류 명령을 다시 실행하려면 그 자리에 새 이름을 넣어야 한다.

## 이름 바꾼 파일 (지우지 않음 — 살아 있는 가드/회귀)

| 옛 이름 | 새 이름 | 왜 지우지 않았나 |
|---|---|---|
| `tests/unit/demo-tour-console-guard-regression.test.tsx` | `tests/unit/console-shell-sample-visitor-guard.test.tsx` | 티켓 지시대로 — `TASK-PC-FE-282` 가 기대값을 바꾼 살아 있는 가드(`(console)` 셸의 익명/샘플 방문자 분기). 둘러보기 UI 를 임포트하지 않는다. 단언 바이트 동일. |
| `tests/unit/demo-tour-root-redirect.test.ts` | `tests/unit/root-redirect.test.ts` | 🔴 **티켓 Scope 의 "demo-tour-\* 삭제" 문언의 명시적 예외 목록엔 없지만, 내용을 읽고 판단을 벗어났다.** 이 파일은 `/demo` UI 를 임포트하지 않고 `app/page.tsx`(루트 리다이렉트)의 인증/익명 두 갈래 + "세션을 안 읽는다"를 잰다 — 대체 커버리지가 **없다**(e2e-smoke 는 인증 세션을 못 만들어 그 갈래를 못 잰다, 자기 헤더가 이미 그렇게 적어 뒀다). 삭제하면 회귀 커버리지가 순감소한다. 단언 바이트 동일, 이름만 이동. |

## 편차 / 구현자 선택

- **D1 — `@demo/public-data` 를 console-web 의 `package.json`/`pnpm-lock.yaml`/`Dockerfile`/`docker-compose*.yml` 에서 빼지 않았다.** `features/demo-tour` 삭제로 이 앱은 그 패키지를 임포트하는 코드가 0 이 됐지만(`next.config.mjs` 의 `transpilePackages` 에서는 뺐다 — 그건 lockfile 과 무관), 의존성 자체를 빼려면 이 앱 전용(`pnpm-workspace.yaml` 없음) `pnpm-lock.yaml` 을 갱신해야 하고 그건 `pnpm install` 없이 손으로 하기엔 손상 위험이 크다(이 워크트리에서 `pnpm install` 은 금지). 이 의존은 이제 **죽은 참조**(빌드가 그 내용을 안 읽는다 — transpilePackages 에서 이미 뺐으므로)이지만 해롭지 않다. 대신 실제로 안전하게 고칠 수 있는 것(빌드가 실제로 그 디렉터리를 못 읽게 하는 `vercel-ignore.sh` 의 SPECS)은 고쳤다. 후속 후보로 남긴다.
- **D2 — `redirects()` 의 폴백을 `/demo/:path*` 하나로 통합.** `/demo`(맨살)에 별도 규칙을 안 만들었다 — Next 의 `:path*` 는 세그먼트 0개도 매칭하므로 폴백 한 줄이 맨살 `/demo` 와 모르는 `/demo/<x>` 를 함께 덮는다(중복 규칙을 피했다). 유닛 테스트가 그 폴백이 실제로 존재/정확한지 확인한다.
- **D3 — `console-guard-idle-refresh.test.tsx`·`e2e-smoke/console-guard.spec.ts`·`e2e-smoke/root-redirect.spec.ts` 의 파일명 인용을 갱신했다** — 렌섬 파일이 아니라 살아 있는 코드/문서라, 리네임 뒤 그대로 두면 "그 파일을 열어 보면 없다"는 깨진 포인터가 된다.
- **D4 — `app/page.tsx`·`e2e-smoke/root-redirect.spec.ts` 의 "`/demo` 는 아직 안 지워졌다/남아 있다" 류 문장을 "은퇴했다/308 로 되돌아온다" 로 고쳤다** — 이 커밋 자체가 그 문장들이 가리키던 미래이므로, 안 고치면 이 커밋 이후에도 거짓을 말한다.
- **D5 — `docs/portfolio.md`·`projects/platform-console/README.md`·페이지 수(68→66)를 갱신했다.** README 의 페이지 집계 관례(루트 `page.tsx` 리다이렉트는 "화면"으로 안 센다)를 그대로 따라 68(64 셸 + 둘러보기 2 + 온보딩 1 + 로그인 1) → 66(64 + 온보딩 1 + 로그인 1)로 고쳤다 — 손으로 지어낸 수가 아니라 삭제한 파일 2개(`(demo)/demo/page.tsx`·`(demo)/demo/[domain]/page.tsx`)를 뺀 값이다.
- **D6 — "둘러보기"(한국어, tour/browse 의 뜻)라는 낱말 자체는 AC-1 의 4개 grep 패턴에 없다** — 팬·스토어의 일반적인 "둘러보다" 용법과 섞여 있어(36 파일, 대부분 무관) 기계적으로 전수 census 하지 않았다. 콘솔 둘러보기 UI 를 실제로 가리키던 자리(README·portfolio·launcher 카드)는 Scope 가 명시한 대로 손으로 찾아 고쳤다.

## ⚪ 측정하지 못한 것 / 넘길 의무

- **AC-6(머지 후 nightly 1회 확인)** — 코디네이터 몫(과업 지시가 그렇게 정했다).
- **실제 Vercel 배포에서 `/demo` 308**(로컬 production build + e2e:smoke 까지만 쟀다) — 283~288 과 같은 한계.
- 넘길 의무: 없음(다른 세션들의 진행 중 티켓에 아무것도 얹지 않았다 — 위 § 다른 세션과의 경계 참조).

## CORRECTION — 닫기 판정 (조정자, 2026-09-17 UTC)

🔴 위 AC 절의 AC-6 체크박스는 `[ ]` 로 남아 있다 — `review/` 파일은 동결이다. **체크박스가 아니라 아래 표가 판정이다.**

머지 검증 4차원: (a) PR [#3895](https://github.com/kanggle/monorepo-lab/pull/3895) `state=MERGED` 2026-09-17T10:34:20Z ·
(b) squash `e74191a74` 가 `origin/main` 조상(머지 시점의 끝) · (c) 머지 전 롤업 **62 체크 · FAILURE 0**(`4d60318ad` 기준 — 아래 수정 후),
필수 4 + 프런트 unit · E2E smoke · console-bff IT · `Vercel build triggers` · `Demo wrapper smoke` **실제 실행** · (d) 아래 표.

| AC | 닫힘 | 증거 |
|---|---|---|
| AC-0 | ✅ | 조정자가 착수 전 `origin/main` `4d273a826` 에서 실측(282~288 일곱 파일 `done/` · 원장 표면/화면 pending 0), 에이전트가 worktree 에서 재측정 일치 |
| AC-1 | ✅ | 조정자 기준값(BEFORE: `demo-tour` 17 · `console-sample` 14 · 소비자 마커 2 · `'/demo` 5 · `"/demo` 3 파일) 대비 AFTER 남은 참조를 **한 줄씩 열어** 분류: 은퇴 설명 주석 · 새 308 테스트 · 「이전 이름」 역사 표기 · 동결 기록 — 살아 있는 코드/설정 0. 🔴 존재하지 않는 파일을 가리키던 주석 1곳(`next.config.mjs`: `demo-tour-redirects.test.ts` → 실제 `legacy-demo-tour-redirects.test.ts`)은 조정자가 고쳤다 |
| AC-2 | ✅ | `/demo` · `/demo/overview` · 모르는 `/demo/<x>` → `/dashboards/overview`, `ecommerce/wms/scm/erp/finance/iam` → `/ecommerce/orders` · `/wms/inventory` · `/scm/procurement` · `/erp/approval` · `/finance/accounts` · `/iam`. 키는 삭제 직전 `console-sample.mjs` 에서 추출(손으로 안 적음). **조정자 대조**: 목적지 7곳 전부 샘플 원장의 실제 화면. 단위 테스트 + e2e-smoke(프로덕션 빌드) |
| AC-3 | ✅ | `infra/demo/public-data`: `node --test` rc=0 · `build-bundled-snapshots --check` rc=0(조정자 재실행) · 발행 CLI 가 `--dataset console-sample` 을 거부 |
| AC-4 | ✅ | `scripts/` 추가·삭제 없음 → 전수 실행 조건 미발생 |
| AC-5 | ✅ | 조정자 재실행(최종 트리): lint rc=0 · tsc rc=0 · vitest **313 files / 3499 tests** 전부 통과(315/3518 에서 삭제한 둘러보기 테스트만큼 감소) · 에이전트: build rc=0 · e2e-smoke 22 passed · PR CI E2E smoke SUCCESS |
| AC-6 | ✅ | 머지 커밋 `e74191a74` 의 `nightly-e2e.yml` push 런 [35211186289](https://github.com/kanggle/monorepo-lab/actions/runs/35211186289): `Platform Console E2E full-stack (Playwright + docker compose)` **success**, 런 전체 success |
| DoD | ✅ | `(demo)` · `features/demo-tour` · `console-sample` 파일 0(조정자 `git ls-files` 실측) · `/demo/**` → 실제 화면 308 |

🔴 **PR CI 가 잡은 결함 — 반쪽 은퇴** (머지 전 수정, 커밋 `4d60318ad`):
에이전트가 `console-web/vercel-ignore.sh` 에서 `:/infra/demo/public-data` 트리거를 뺐다(근거: 둘러보기가 그 패키지를 읽던 유일한 자리). 그러나 D1 에 따라
`package.json` 은 `@demo/public-data` 를 `link:` 로 **여전히 선언**한다. 선언된 로컬 의존이 트리거에 없으면 그 패키지만 바뀐 커밋의 배포가 **조용히 건너뛰어진다** —
`scripts/check-vercel-build-triggers.sh` 칸 (12)가 CI 에서 물었다(자기시험의 «망가뜨리지 않은 사본은 통과» 칸이 rc=1). ⇒ 트리거를 되돌리고 주석의 근거를
«의존 선언이 남아 있어서» 로 고쳤다(가드 rc=0 · 자기시험 전 칸 ok, 로컬). 의존 선언이 사라지는 날 트리거도 같이 뺀다.
🔴 **조정자 자기 정정**: 로컬에서 **필수 가드 3종만** 돌리고 PR 본문에 «가드 통과» 라고 적었다 — 이 PR 이 건드린 경로(`vercel-ignore.sh`)를 직접 재는 비필수 가드를
돌리지 않아 CI 가 먼저 잡았다.

🔵 **남는 것**:
- console-web 이 이제 **읽지 않는** `@demo/public-data` 의존(`package.json` `link:` · `Dockerfile` 복사 · compose) — 정리하면 lockfile · 이미지 빌드를 함께 건드린다(D1).
  정리하는 날 `vercel-ignore.sh` 트리거도 함께 뺀다. 현재 비용은 그 패키지만 바뀐 커밋에서의 불필요한 콘솔 재배포뿐(안전한 방향).
- 실제 Vercel 배포의 `/demo` 308 은 미측정(로컬 production build + smoke + nightly full-stack 까지).
- `TASK-MONO-674`(다른 세션, in-progress)의 본문이 인용하는 `demo-tour-console-guard-regression.test.tsx` 는 `console-shell-sample-visitor-guard.test.tsx` 로 이름이 바뀌었다(내용 R100) — 그 티켓은 편집하지 않았다.

🔵 **`ADR-MONO-074` 로드맵 완료**: 실행 1/8(282) · 2~7/8(283~288) · 8/8(이 티켓) 전부 done. 넘길 의무 0건.
