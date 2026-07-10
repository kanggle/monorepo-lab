# Task ID

TASK-MONO-345

# Title

`docs/project-overview.md` + 루트 `README.md` 의 서비스 맵이 `settings.gradle` 실체와 어긋남 — 유령 서비스 2 · 누락 서비스 5 · 폐기 서비스 1 · 도메인 수 3중 모순. 문서를 실체에 맞추고, **재발을 CI 가드로 차단**

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- ci
- chore

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능.
- **관련 (비차단)**: `TASK-MONO-343`(push 폴백이 `code-changed` 무시). 본 task 의 Facet A 는 **doc-only** 이므로, 343 이 먼저 머지되면 close chore 의 main CI 가 Testcontainers 레인을 skip 한다. Facet B 는 `scripts/` + `.github/workflows/**` 를 건드리므로 `workflows` 플래그로 전량 실행된다 — 343 과 **무관**하게 동작한다. 순서 무관.
- **관련 (선례, 비차단)**: `tasks/done/TASK-MONO-341-demo-wrapper-ci-smoke.md` — "커버리지 드리프트" 가드(모든 `projects/*/docker-compose.yml` 이 래퍼 맵에 등록되어야 함)의 **직접적 형태 선례**. 본 task 의 Facet B 는 같은 발상을 `settings.gradle` ↔ 문서 축으로 옮긴 것. 구현 시 그 스크립트의 네거티브 테스트 패턴을 답습할 것.

---

# Goal

`docs/project-overview.md` 는 이 저장소의 **단일 진입 스냅샷**이고, 루트 `README.md` 는 포트폴리오 허브다. 두 문서 모두 각 프로젝트의 **service map 표**를 싣는다. 그런데 이 표들이 `settings.gradle`(빌드가 실제로 아는 유일한 진실)과 **양방향으로** 어긋나 있다.

- **유령(문서에만 존재)**: `finance:gateway-service`, `erp:gateway-service` — Gradle include 에 없다. 두 도메인은 게이트웨이 서비스 없이 Traefik 이 경로를 보고 서비스로 직접 라우팅한다.
- **누락(코드에만 존재)**: `scm:demand-planning-service`, `finance:ledger-service`, `fan:membership-service`, `fan:notification-service` — 넷 다 문서에는 **"v2 deferred"** 로 적혀 있는데 이미 살아 있다. `ecommerce:settlement-service` 는 문서에 **아예 없다**.
- **폐기(문서가 살아있다고 주장)**: `ecommerce:auth-service` — `settings.gradle` 주석이 *"excluded from build by TASK-BE-132 (decommissioned) … Replaced by IAM OIDC"* 라고 명시하는데, 문서 § 2.3 는 여전히 *"IAM migration: 향후 (TASK-MONO-020) — 현재 자체 auth-service"* 로 적는다.
- **자기모순**: 같은 파일 안에서 도메인 수가 **3중으로** 갈린다 — 머리말 "5 프로젝트" / § 1 "5 도메인 프로젝트" / § 2 제목 "7 도메인 + platform-console". 또 "5/5 backend domains … federated" 라 쓰지만 콘솔 사이드바에는 **E-Commerce 를 포함해 6 도메인**이 있고 `console-bff` 도 6개를 집계한다.

이 문서가 낡았다는 사실 자체보다 **낡았다는 걸 아무도 알 수 없다는 점**이 문제다. 문서를 읽고 카탈로그를 만들면 사실이 아닌 산출물이 나오고(2026-07-10 실제로 발생), 어긋남을 검출할 기계적 수단이 없어 다음 서비스가 추가될 때 같은 일이 반복된다. `TASK-MONO-341` 이 데모 래퍼에 대해 이미 내린 결론 — *"등록 누락 시 신규 프로젝트가 데모에서 조용히 사라진다"* — 와 정확히 같은 종류의 실패이며, 그때의 해법(커버리지 가드)이 여기서도 유효하다.

**목표**: 두 문서를 실체에 맞추고(Facet A), `settings.gradle` 과 문서 서비스 맵의 드리프트를 CI 가 검출하게 한다(Facet B). 문서를 고치기만 하면 6개월 뒤 같은 task 를 다시 쓰게 된다.

---

# Scope

## In Scope — Facet A (문서 정합, doc-only)

1. `docs/project-overview.md`
   - § 2.3 `ecommerce` service map — `auth-service` 를 **폐기 표기**(iam 의 `~~admin-web~~` 선례를 따를 것: 취소선 + RETIRED 사유 + 근거 task). `settlement-service` 행 **추가**(Commerce 레이어). `IAM migration: 향후` 문장을 **완료**로 정정.
   - § 2.4 `scm` — `demand-planning-service` 행 추가, v2 deferred 목록에서 제거.
   - § 2.5 `fan` — `membership-service` · `notification-service` 행 추가, v2 deferred 목록에서 제거.
   - § 2.7 `finance` — `gateway-service` 행 **삭제**(유령), `ledger-service` 행 추가, v2 deferred 목록에서 `ledger-service` 제거.
   - § 2.8 `erp` — `gateway-service` 행 **삭제**(유령).
   - **게이트웨이 부재의 사유를 명시**: finance · erp 는 게이트웨이 모듈 없이 Traefik 이 라우팅한다는 사실을 각 § 에 한 줄로 남긴다. 단순 삭제만 하면 다음 독자가 "빠뜨린 것"으로 오해하고 되살린다.
   - 머리말 / § 1 / § 2 제목의 **프로젝트 수를 하나로** 통일(7 도메인 + platform-console = 8 프로젝트).
   - "5/5 federated" → **6/6**(ecommerce 포함). § 2.6 platform-console 상태 문장도 동반 수정.
2. `README.md`(루트) — **⛔ 착수 중 차단됨. 아래 § Blocker 참조. 훅 패치가 선행되어야 함(사용자 소관).**
   - Projects 표가 **4행뿐**이다(wms · ecommerce · iam · fan). scm · finance · erp · platform-console **4개 프로젝트가 통째로 없다**.
   - wms 행의 *"admin / notification: bootstrap pending"* → 둘 다 존재하므로 정정.
   - fan 행의 *"🚧 Bootstrapping"* → v1 종결(2026-05-03).
   - Phases 표가 *"Phase 2. Second Project 🔜 Next"* 에 멈춰 있다. Phase 5 LAUNCHED / Phase 6~8 COMPLETE 를 반영.
   - Key Documents 표의 *"5 projects"* 문구 정정.

## In Scope — Facet B (재발 차단 가드)

3. `scripts/check-service-map-drift.sh` 신규 — `settings.gradle` 의 `projects:<project>:apps:<service>` include 를 열거하고, `docs/project-overview.md` 의 **해당 프로젝트 섹션 안에** 각 `<service>` 가 등장하는지 단언. 누락 시 비-0 종료 + **누락 서비스명 출력**(이름을 찍지 않으면 CI 로그에서 무용지물 — MONO-339 의 교훈).
4. **역방향 검사(유령 검출)** — 각 프로젝트 service map 표의 백틱 서비스명 중 `settings.gradle` 에 없고 폐기 마커(`~~취소선~~` / `RETIRED` / `FROZEN`)도 없는 것을 실패로 처리. 이 방향이 없으면 이번의 `finance:gateway-service` 유령을 못 잡는다.
5. `.github/workflows/ci.yml` — 가드를 잡으로 배선. **순수-positive 필터**(negation 금지 — MONO-074/075 quirk). 트리거 경로 = `settings.gradle` · `docs/project-overview.md` · `scripts/check-service-map-drift.sh`. 필터를 `code-changed` 와 **AND 하지 말 것** — 이 가드가 잡는 드리프트는 markdown-only 편집으로 도착하므로 AND 하면 존재 이유인 바로 그 변경에서 꺼진다.

## In Scope — Facet C (착수 중 편입, 2026-07-10)

6. `.claude/hooks/hardstop-detect.ps1` — `$sharedPathPattern`(L110)에서 `README\.md` 제거. Facet A 의 README 파트가 이 훅에 막혀 **구현 불가**였다(§ Blocker). 훅이 `CLAUDE.md` 의 문서화된 공유 집합보다 넓었고, **훅 자신이 출력하는 `[WHY]` 스탠자조차 README 를 공유 경로로 열거하지 않는다**(L228) — 즉 패턴만 어긋나 있었다. 에이전트는 `.claude/` 를 스테이징할 수 없어 **사용자가 직접 적용·커밋**했다(`chore(hooks):` 커밋).

## Out of Scope

- **프로젝트별 `projects/<name>/README.md`** — 같은 드리프트가 있을 가능성이 높으나 프로젝트-내부 경로다. 발견 시 각 프로젝트 `tasks/ready/` 로 spawn 하고 본 task 에서 고치지 말 것(shared/project 경계).
- **`docs/adr/` 본문** — ADR 은 **결정 시점의 기록**이며 사후 사실과 어긋나는 것이 정상이다(append-only). 절대 수정하지 말 것.
- **프론트엔드 3종**(`console-web` · `web-store` · `fan-platform-web`) — Gradle 모듈이 아니므로 Facet B 가드의 대상이 아니다. 문서에는 남긴다.
- **서비스 코드 변경 일체** — 본 task 는 문서와 가드뿐이다. 유령 게이트웨이를 "실제로 만들어" 문서에 맞추는 방향은 **명시적으로 금지**(finance/erp 의 Traefik 직접 라우팅은 의도된 설계).
- `TEMPLATE.md` · `rules/` · `platform/` — 서비스 맵을 싣지 않는다.

---

# Acceptance Criteria

- [ ] AC-1 — `docs/project-overview.md` 의 모든 § 2.x service map 표가 `settings.gradle` include 집합과 일치한다. 유령 2건 삭제, 누락 5건 추가, 폐기 1건 표기.
- [ ] AC-2 — finance · erp 의 **게이트웨이 부재 사유**가 각 § 에 문장으로 남는다(삭제만 하고 사유를 안 남기면 미달).
- [ ] AC-3 — 프로젝트 수가 문서 전체에서 **하나로** 통일된다(머리말 · § 1 · § 2 제목). federated 도메인 수는 **6/6**.
- [x] AC-4 — 루트 `README.md` Projects 표에 **8개 프로젝트 전부**가 있고, wms · fan 의 상태 문구와 Phases 표가 실제 단계를 반영한다. *(훅 패치 후 달성 — § Blocker 의 해소 노트 참조. 8/8 프로젝트 디렉터리 + 8/8 ADR 링크 실재 확인.)*
- [ ] AC-5 — `scripts/check-service-map-drift.sh` 가 **현재 트리에서 exit 0**(= Facet A 가 실제로 정합함을 스크립트가 증명). Facet A 없이 AC-5 는 성립할 수 없다 — 두 Facet 은 서로의 검증이다.
- [ ] AC-6 (**네거티브 테스트, 양성만으론 불충분**) — ① `settings.gradle` 에 더미 include 를 임시 추가 → 가드 exit 1 + 그 서비스명 출력. ② `project-overview.md` 표에 유령 행 임시 추가 → 가드 exit 1 + 그 이름 출력. 두 로그를 PR 본문에 첨부. (MONO-339 에서 `comm` 인자순서 실수를 양성 테스트가 못 잡은 전례.)
- [ ] AC-7 — CI 잡이 `settings.gradle` 변경 PR 에서 **실행**되고, 무관 코드 PR 에서 **skip** 된다. 필터는 순수-positive.
- [ ] AC-8 — 가드 스크립트가 **자신의 한계를 주석으로 명시**한다(프론트엔드 3종 미대상, 역방향 검사의 마커 목록).

---

# Related Specs

- `settings.gradle` — **본 task 의 권위 소스**. 문서가 여기에 맞춰야지 그 반대가 아니다.
- `tasks/done/TASK-MONO-341-demo-wrapper-ci-smoke.md` — 커버리지 드리프트 가드 선례 + 네거티브 테스트 패턴.
- `tasks/done/TASK-MONO-339-fed-e2e-bringup-completeness-guard.md` — "손-나열 목록이 선언과 어긋나도 절차가 성공으로 끝나면 아무도 모른다" 의 원형. **실패 시 이름을 출력**해야 한다는 규칙의 출처.
- `tasks/done/TASK-MONO-074-*.md` / `TASK-MONO-075-*.md` — `paths-filter@v3` negation quirk. Facet B 의 필터는 순수-positive 여야 한다.
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` · `ADR-MONO-008-finance-platform-bootstrap.md` — 두 부트스트랩 ADR 이 `gateway` 를 "v2 deferred" 로 적었고, 그 문구가 overview 의 service map 표에 **행으로** 옮겨 앉으면서 유령이 됐다. **읽되 수정 금지**(Out of Scope).
- Memory `project_ci_path_filter_074_075_quirk`.

# Related Contracts

None — 문서 + CI 설정. API/이벤트 계약 무변경.

---

# Evidence (착수 전 검증 완료 — 2026-07-10)

`settings.gradle`(권위) 을 직접 열거해 문서와 대조한 결과.

| # | 프로젝트 | `settings.gradle` 실체 | 문서 주장 | 판정 |
|---|---|---|---|---|
| 1 | finance | `account-service`, `ledger-service` (2) | `gateway-service` + `account-service`; ledger = "v2 deferred" | 유령 1 · 누락 1 |
| 2 | erp | `masterdata-service`, `read-model-service`, `approval-service`, `notification-service` (4) | 위 4 + `gateway-service` | 유령 1 |
| 3 | scm | `gateway`, `procurement`, `inventory-visibility`, `demand-planning` (4) | 앞 3개만. `demand-planning` 언급 **전무** | 누락 1 |
| 4 | fan | `gateway`, `community`, `artist`, `membership`, `notification` (5) | 앞 3개. membership · notification = "v2 deferred" | 누락 2 |
| 5 | ecommerce | `settlement-service` 포함 12 모듈. `auth-service` 는 **include 에서 제외**(BE-132 폐기, IAM OIDC 대체) | `settlement` 부재. `auth-service` 를 현역으로 서술 + "IAM migration 향후" | 누락 1 · 폐기 1 |
| 6 | wms · iam · console | 일치 | 일치 | ✅ |

**자기모순(같은 파일 내부)**

| 위치 | 문구 |
|---|---|
| 머리말 | "5 프로젝트" |
| § 1 한 줄 요약 | "5 도메인 프로젝트" |
| § 2 제목 | "7 도메인 + platform-console" |
| § 1 현재 단계 | "5/5 backend domains (iam·wms·scm·finance·erp) federated" |

마지막 항은 콘솔 사이드바(`ConsoleSidebarNav.tsx` § 도메인 운영)에 **E-Commerce 드릴인 부모가 존재**하고 `console-bff` 가 ecommerce leg 를 집계한다는 사실과 어긋난다 → **6/6** 이 맞다.

**루트 `README.md`**: Projects 표에 4행(wms · ecommerce · iam · fan). scm · finance · erp · platform-console **부재**. wms 행 *"admin / notification: bootstrap pending"* — 둘 다 `settings.gradle` 에 있다. fan 행 *"🚧 Bootstrapping"* — v1 종결. Phases 표 *"Phase 2 🔜 Next"* — 실제 Phase 8 COMPLETE.

**발견 경위**: 2026-07-10 사용자의 플랫폼 개요 요청에 `docs/project-overview.md` 를 근거로 서비스 카탈로그를 만들었고, 콘솔 메뉴↔서비스 매핑을 위해 `settings.gradle` 을 열어보고서야 산출물이 **틀렸음**을 알았다. 문서만 읽는 한 검출 불가능했다는 점이 Facet B 의 근거다.

---

# Blocker — README.md 는 훅에 의해 에이전트 편집 불가 (2026-07-10 발견 → **당일 해소**)

> **해소 (2026-07-10)**: 사용자가 아래 1안을 직접 적용 — `hardstop-detect.ps1` L110 의 `$sharedPathPattern` 에서 `README\.md` 제거. 이후 README Projects 표(8 프로젝트 전량) + Phases 표 + Key Documents 정정 완료 → **AC-4 달성**. 훅 파일 자체의 커밋은 `.claude/` 분류기 하드블록으로 에이전트가 스테이징할 수 없어 **사용자 소관**으로 남는다(§ 후속 참조).
>
> 아래는 발견 시점의 기록(재발 시 진단용).

Facet A 의 `README.md` 파트는 **구현 불가 상태**다. 문서 문제가 아니라 훅 구성 문제다.

- `.claude/hooks/hardstop-detect.ps1` **L110** 의 `$sharedPathPattern` 이 `README\.md` 를 **project-agnostic 공유 파일**로 포함한다. 따라서 README 에 `wms-platform` 같은 프로젝트 경로 토큰을 쓰는 모든 편집이 `HARDSTOP-03` 으로 차단된다.
- 그런데 **`CLAUDE.md` 의 공유 목록에는 `README.md` 가 없다** — 명시된 집합은 `platform/` · `rules/` · `.claude/` · `libs/` · `tasks/templates/` · `tasks/INDEX.md` + 루트 `tasks/` · `docs/guides/` · `CLAUDE.md` · `TEMPLATE.md` 다. 훅이 문서화된 규칙보다 **넓다**. (`build.gradle` · `settings.gradle` 도 동일하게 훅에만 있다.)
- 게다가 **`origin/main` 의 README 는 이미 4개 프로젝트를 이름으로 나열한다.** 즉 파일은 이미 "위반" 상태이고, 훅은 그것을 고치는 것만 막는다. README 는 포트폴리오 허브이며 **프로젝트를 호명하는 것이 존재 이유**다 — Template 추출 대상도 아니다.
- 훅이 제공하는 정식 escape (`<!-- hardstop-allow: … -->`) 는 **바로 앞 줄**만 인정한다(**L213**, `$lines[$i-1]`). Projects 표는 마크다운 표이고 **표의 행 사이에는 주석을 넣을 수 없다**(표가 깨진다). 행마다 annotation 을 다는 것이 구조적으로 불가능하므로 escape 경로가 닫혀 있다.

**해소 방법(사용자 소관)** — `.claude/hooks/` 편집·커밋은 분류기에 하드블록되어 에이전트가 적용할 수 없다. 다음 중 하나:

1. **(권장)** L110 의 `$sharedPathPattern` 에서 `README\.md` 를 제거해 훅을 `CLAUDE.md` 의 문서화된 공유 집합과 일치시킨다. `docs/guides/` 와 달리 README 는 프로젝트-불가지가 아니어야 한다.
2. L213 근처를 고쳐 **파일 상단의 단일 `<!-- hardstop-allow-file: … -->`** 를 인정하게 한다. 표 친화적이며 다른 마크다운 표에도 재사용된다.

둘 중 하나가 적용되기 전까지 AC-4 는 **달성 불가**하며, 본 task 는 **Facet A(project-overview.md) + Facet B(가드+CI) 로 종결**한다. README 정정은 훅 패치 직후 **후속 doc-only task** 로 분리한다.

> 이 Blocker 자체가 본 task 의 논지를 재확인한다 — 손으로 유지되는 목록(훅의 `$sharedPathPattern`)이 문서화된 규칙(`CLAUDE.md`)과 갈라졌고, 아무 절차도 실패하지 않았다.

## 훅 조사 부산물 — 후속 티켓 **불필요** 판정 (2026-07-10)

`$sharedPathPattern` 에는 `README\.md` 외에도 `build\.gradle` · `settings\.gradle` 이 있고 이 둘 역시 `CLAUDE.md` 의 공유 집합에 없다. 착수 중 "동일 드리프트 2건 잔존" 으로 의심했으나, **소스 확인 결과 무해**하므로 티켓을 끊지 않는다.

- HARDSTOP-03 의 유일한 탐지 형태는 **슬래시 경로 토큰** `(?:projects|apps)/<project>/` 다(L216). 다른 형태는 없다.
- `settings.gradle` 은 프로젝트를 **콜론 형태** `projects:wms-platform:apps:…` 로만 참조한다. 실측: `settings.gradle` · `build.gradle` 모두 슬래시 형태 **0건** → 정규식이 **원리적으로 발화 불가**. 두 항목은 패턴에 있으나 **동작상 inert** 하다.
- 반면 `README.md` 는 Projects 표가 `[wms-platform](projects/wms-platform/)` 형태의 **슬래시 링크**를 쓰므로 매번 발화했다. README 만 실제 피해자였다.

**부수 관찰 2건**(수정하지 않음 — `.claude/` 하드블록 + 본 task 범위 밖):

1. L179~180 주석은 탐지 형태를 *"path token **or as a code-fenced identifier**"* 라 적지만 **코드-펜스 탐지 분기는 존재하지 않는다**. 주석이 구현보다 앞서 있다(또는 제거된 기능의 잔재).
2. `$shortAliases`(L192~198)에 `finance-platform` · `erp-platform` · `platform-console` 이 없다. 세 프로젝트는 정식명으로만 탐지되고 축약형(`apps/erp/` 등)은 통과한다.

둘 다 실질 위험이 낮고 관측된 피해가 없다 — **AC-0 미충족(트리거 미관측)** 이므로 MONO-328/330 과 같은 판단으로 **백로그화하지 않는다.** 재발 신호가 관측되면 그때 티켓을 끊는다.

---

# Edge Cases

- **ADR 을 "고치려는" 충동 (최상위 함정)** — `ADR-MONO-008` / `-016` 이 `gateway` 를 v2 deferred 로 적은 것은 **결정 시점에 사실**이었다. ADR 은 append-only 기록이지 현재 상태 문서가 아니다. 유령의 원인은 ADR 이 아니라 그 문구를 service map **표의 행**으로 옮겨 적은 overview 다. ADR 을 건드리면 `HARDSTOP-04` 인접 영역 + 감사 추적 파괴.
- **`~~취소선~~` 마커 파싱** — iam § 2.2 는 `~~admin-web~~` · `~~community-service~~` · `~~membership-service~~` 를 취소선으로 적는데, `community-service` 와 `membership-service` 는 **`settings.gradle` 에 실재**한다(FROZEN 이지 미존재가 아님). 역방향 검사가 "취소선 = 없어야 함" 으로 단정하면 **거짓 양성**이 난다. 마커의 의미는 *"현역 아님"* 이지 *"모듈 없음"* 이 아니다 — 가드는 **순방향(gradle→doc)** 에서 취소선 항목도 "등장함" 으로 인정해야 하고, **역방향**에서는 마커 있는 항목을 면제해야 한다.
- **fan 의 `membership-service` 는 iam 에도 있다** — 같은 이름의 모듈이 두 프로젝트에 존재한다(`iam:membership-service` FROZEN, `fan:membership-service` 현역). 가드가 파일 전체를 `grep` 하면 **엉뚱한 섹션의 등장으로 통과**한다. 반드시 **프로젝트 섹션 범위 안에서** 검사할 것. `notification-service` 는 wms · erp · fan · ecommerce **4곳**에 있어 더 심하다.
- **역방향 검사가 브리틀할 경우** — 마크다운 표 파싱이 불안정하면 **순방향만 배선하고 역방향은 보류**하되, 그 사유와 놓치는 케이스(= 이번 유령 2건)를 스크립트 주석 + close chore 에 **명시**할 것. 조용히 빼면 다음 유령을 못 잡는다.
- **`tests:e2e` include** — `settings.gradle` 은 `projects:<p>:tests:e2e` 도 include 한다. `apps:` 만 열거할 것(`tests:` 는 서비스가 아니다).
- **ecommerce `auth-service` 디렉터리는 남아 있다** — `apps/auth-service/build.gradle` 파일은 존재하고 include 만 빠졌다. 따라서 **디렉터리 glob 이 아니라 `settings.gradle` include 를 파싱**해야 한다. glob 을 쓰면 폐기 서비스가 되살아난다.
- **`libs:` include** — 라이브러리는 서비스가 아니다. `projects:` 접두만 볼 것.
- **Windows 개행** — 저장소는 Windows 호스트에서도 편집된다. 가드가 `\r` 에 걸려 서비스명 끝에 캐리지리턴이 붙으면 비교가 항상 실패한다.

---

# Failure Scenarios

| # | 시나리오 | 기대 동작 / 완화 |
|---|---|---|
| 1 | 문서만 고치고 가드를 안 넣음 | 6개월 뒤 동일 task 재발. Facet B 는 **본 task 의 존재 이유** — 분리 머지 금지 |
| 2 | 가드가 파일 전체 `grep` | `notification-service`(4곳) · `membership-service`(2곳) 때문에 **모든 프로젝트가 통과** → 가드가 아무것도 안 함. AC-6 ①의 더미 include 는 반드시 **기존에 없는 이름**으로 |
| 3 | 역방향 검사가 취소선 항목을 유령으로 오판 | `iam:community-service` 등에 거짓 양성 → 개발자가 가드를 신뢰하지 않고 꺼버림. Edge Cases 의 마커 규칙 준수 |
| 4 | `apps:` 대신 디렉터리 glob 사용 | 폐기된 `ecommerce:auth-service` 가 "누락" 으로 잡혀 문서에 되살아남 |
| 5 | 유령 게이트웨이를 실제 모듈로 생성해 문서에 맞춤 | 설계 역행. Out of Scope 명시. finance/erp 의 Traefik 직접 라우팅은 의도 |
| 6 | ADR 본문 수정 | 감사 추적 파괴. Out of Scope + Edge Cases |
| 7 | CI 필터에 negation 사용 | MONO-074/075 quirk 재현 → 잡이 안 돌거나 항상 돎. 순수-positive 강제 |
| 8 | 네거티브 테스트 생략 | 인자 순서 / 정규식 방향 실수가 양성 테스트를 통과함(MONO-339 실증). AC-6 필수 |

---

# Test Requirements

- `bash -n scripts/check-service-map-drift.sh` — 문법.
- **양성**: 현재 트리에서 exit 0 (AC-5).
- **네거티브 ×2**: 더미 include 주입 → exit 1 + 이름 출력 / 유령 행 주입 → exit 1 + 이름 출력 (AC-6). 두 로그를 PR 본문에 첨부.
- **CI 배선 확인**: `settings.gradle` 을 건드리는 본 PR 자체에서 가드 잡이 실행(AC-7 전반). skip 측은 직전 doc-only PR 과 대조.
- YAML 파싱: `npx --yes js-yaml .github/workflows/ci.yml > /dev/null` (python·jq 부재).
- **Java 빌드 불필요** — 코드 변경 0. `./gradlew check` 는 본 task 의 게이트가 아니다.

---

# Definition of Done

- [x] `docs/project-overview.md` + 루트 `README.md` 가 `settings.gradle` 과 정합(AC-1~4).
- [x] `scripts/check-service-map-drift.sh` 신규 + CI 배선 + 양성/네거티브 로그(AC-5~8).
- [x] **`.claude/hooks/hardstop-detect.ps1` 의 `README\.md` 제거를 본 PR 에 포함**(Facet C) — 사용자가 직접 `git add` + `git commit`(`.claude/` 스테이징은 분류기 하드블록). 브랜치에 안착 확인.
- [ ] 프로젝트-내부 `README.md` 드리프트를 발견했다면 각 프로젝트 `tasks/ready/` 로 spawn(고치지 말 것).
- [ ] `tasks/INDEX.md` done entry — **역방향 검사를 보류했다면 그 사유와 놓치는 케이스를 여기 기록**. 역방향은 **보류하지 않고 배선함**(네거티브 테스트 통과).

---

# Provenance

Surfaced 2026-07-10 — 사용자의 *"내 플랫폼에 대해서 말해봐"* → *"각 플랫폼 서비스/기능 표"* → *"콘솔 메뉴별 동작 서비스"* 로 이어진 요청을 처리하며 발견. 첫 두 산출물은 `docs/project-overview.md` 를 근거로 만들었고 **틀렸다**. 세 번째 요청이 `settings.gradle` 과 콘솔 소스를 읽게 만들었고 그제서야 드리프트가 드러났다.

즉 이 결함은 **문서만 읽는 독자에게는 원리적으로 비가시**다. `TASK-MONO-339`(fed-e2e 손-나열 목록 ↔ compose 선언) · `TASK-MONO-341`(데모 래퍼 맵 ↔ `projects/*/docker-compose.yml`)와 **동일 계열**의 세 번째 사례 — "사람이 손으로 유지하는 목록"이 "기계가 아는 진실"과 갈라지고, 절차가 성공으로 끝나기 때문에 아무도 모른다. 앞선 두 task 는 각각 가드로 봉했다. 본 task 는 그 계열의 마지막 손-나열 목록을 봉한다.

분석=Opus 4.8 / 구현 권장=**Sonnet** (Facet A 는 기계적 문서 정정, Facet B 는 단일 셸 스크립트 + CI 잡 1개. 상태기계·트랜잭션 설계 없음. 단, **Edge Cases 의 "섹션 범위 내 검사" 와 "취소선 마커 의미" 두 함정은 반드시 읽고 착수할 것** — 이 둘을 놓치면 가드가 조용히 무력화된다).
