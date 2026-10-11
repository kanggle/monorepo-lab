# Task ID

TASK-MONO-788

# Status

done — 구현 완료(2026-10-11 UTC). AC-1~5 ✅. ⏳ `done/` 전 남은 것 = **AC-6**(소유자 `terraform apply` 뒤 라이브 확인).

# Title

꺼진 인스턴스에서도 론처 고급 영역의 도메인 「시작」이 되게 하고, 「▶ 전체 스택 시작」 을 동작에 맞는 이름으로 바꾼다

# Owner

monorepo

# Task Tags

- demo
- launcher
- lambda

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 기존 묶음 경로(`bundle_start`)로의 위임 + 문구. 단 가드 (z40) 의 «거절이 돈보다 앞» 을 콜드 경로에도 걸어야 한다.

---

# Dependency Markers

- 선행: `TASK-MONO-787`(꺼진 동안 도메인 목록을 «꺼짐» · 잠긴 버튼으로 표시 — 그 티켓의 Scope Out 이 이 티켓이다: *"람다 변경 — 꺼져 있을 때 `/domain/start` 가 409 를 내는 동작은 그대로 둔다. 바꾸려면 `terraform apply` 가 필요하다"*).
- 관련: `TASK-MONO-634` / `ADR-MONO-071`(부팅 선택 · 묶음) · `TASK-MONO-647`(구세대 AMI 거절, 가드 (z40)) · `TASK-MONO-685`(세션 경계) · `TASK-MONO-477`(도메인별 제어).

# Goal

소유자 요청(2026-10-11): ① 꺼진 인스턴스에서도 도메인 「시작」을 누르면 서버를 켜고 그 도메인만 올린다(787 이 잠가 둔 버튼). ② 「▶ 전체 스택 시작」 이 실제 동작과 다르다는 지적에 대해 **이름을 동작에 맞춘다**(권고안 A).

② 의 사실관계(실측): `start()` 는 인스턴스만 켜고 선택을 건드리지 않는다. 부팅 유닛은 `DEMO_PROFILE=selection`(demo-stack.service:50)이라 **저장된 선택**으로 뜨고, 비어 있으면 `demo-core`(iam · ecommerce · wms, 67 컨테이너)로 떨어진다(demo-selection.sh § 폴백 결정). 그런데 버튼은 「전체 스택 시작」, 힌트는 «데모 도메인을 전부 올립니다(약 10분)», 람다 문구는 «전체 데모 도메인 웜업까지 약 10분» 이었다. 🔴 동작을 «전부» 로 바꾸는 안(B)은 `ADR-MONO-071` 의 출발점(*버튼만 나누고 내부적으로 전체 스택을 시작하는 구현 금지*)과 정면으로 부딪혀 기각 — 전부 올리는 길은 「모든 도메인 시작」(`/domain/start full`)이고, ① 로 꺼진 상태에서도 된다.

# Scope

## In

- `infra/demo/aws/terraform/lambda/handler.py`
  - `DOMAIN_BUNDLE` / `COLD_START_BUNDLES` — 도메인 → «그 도메인 하나만 푸는 묶음» 을 `BUNDLES`/`BUNDLE_ADDONS` 에서 **파생**(세 번째 표를 손으로 적지 않는다). `full` = 모든 짝. `demo-core` 는 넣지 않는다(정본은 `projects.sh` CORE).
  - `domain_start` — `stopped` + 이름이 `COLD_START_BUNDLES` 안이면 `bundle_start(None, names=…, label=…)` 로 **위임**. 세대 거절 · 예산 · 세션 경계 · 선택 영속화를 그 함수에서 물려받는다. `pending`/`stopping`/`demo-core` 는 사유별 409.
  - `bundle_start(event, names=None, label=None)` — 위임용 인자 둘(메시지에 `console-wms` 대신 `wms`).
  - `status()` 에 `domain_cold_start`(= 세대 판정) 추가 — 론처가 이 값이 참일 때만 꺼진 동안의 버튼을 연다.
  - `start()` 문구 — «고른 화면 · 없으면 핵심 3도메인 · 약 4~10분».
- `infra/demo/aws/site/index.html` — 「▶ 데모 서버 켜기」 · 힌트 · summary · 꺼진 동안 `domain_cold_start` 면 도메인 「시작」 · 「모든 도메인 시작」 열기 · 콜드 클릭 즉시 잠금 · 액션 뒤 `poll`(빈 `/domains` 로 꺼진 행이 풀리지 않게).
- `infra/demo/aws/tests/test_handler.py` — `DomainColdStartTest` 10칸 + 기존 `requires_running_vm` → `while_booting_is_refused`.
- `infra/demo/verify-demo-wrapper.sh` (z40) — `domain_start` 는 `bundle_start` 로 위임해야 하고 `start_instances`·`_add_to_selection`·`_reset_selection`·`_write_selection` 을 직접 부르면 FAIL + **bite-4**(위임을 직접 기동으로 바꾸면 무는가).

## Out

- AMI 재굽기 — 불필요. 부팅은 이미 묶음 선택을 읽는다(`demo-boot.sh selection`, 634 이후 모든 AMI).
- `terraform apply` — 소유자 실행(분류기가 에이전트의 apply 를 막는다). 런처는 머지로 배포되고 람다는 apply 로 배포되므로 그 사이의 창은 `domain_cold_start` 키 부재 → 잠금으로 덮는다.
- 켜진 인스턴스의 `/domain/start` 동작 — 무변경(SSM `demo-boot.sh <name>`).

## projects/ 영향

없음.

# Acceptance Criteria

- [x] **AC-1** — 꺼진 인스턴스에서 `/domain/start <d>`(d ∈ 7도메인)는 200 · 인스턴스 기동 1회 · 저장된 선택이 **d 하나만** 푼다 · SSM 명령 0. 실측: `test_each_domain_starts_the_instance_with_only_that_domain_selected` 7 서브테스트 + `test_every_domain_has_a_cold_start_bundle`.
- [x] **AC-2** — `full` 은 7도메인 전부를 푸는 선택을 저장한다. `demo-core` · `pending` · `stopping` 은 409 이고 아무것도 안 켠다. 실측: `test_full_selects_every_domain` · `test_demo_core_is_refused_while_off` · `test_domain_start_while_booting_is_refused`. ⚪ `stopping` 은 단위시험 칸이 없다 — 분기는 `pending` 과 같은 `state != "running"` 아래 문구만 다르다.
- [x] **AC-3** — 콜드 경로도 세대 거절(no · unknown → 409, 선택 미저장 · 기동 0) · 예산 429 · 세션 경계(지난 선택 비움)를 지난다. 실측: 단위시험 3칸 + `verify-demo-wrapper.sh` (z40) ok — 구조 판정 + **bite-4 물림**.
- [x] **AC-4** — 런처: `domain_cold_start` 가 참이고 stopped 일 때만 꺼진 동안의 「시작」 · 「모든 도메인 시작」 이 열린다. 실측(Playwright + 모의 제어 API, 4상태): 꺼짐+참 → 행 버튼 **7/7 열림** · 「모든 도메인 시작」 열림 · 「모든 도메인 종료」 잠김 → `wms` 클릭 → 즉시 **0/7**, POST 1건 `{"name":"wms"}` → 폴링(pending) 뒤에도 잠김 · 응답 문구 유지 / 꺼짐+키 없음 · 꺼짐+거짓 · pending → 전부 **0/7**. 페이지 오류 4상태 모두 0.
- [x] **AC-5** — 「전체」 라는 말이 `start()` 응답과 `#start` 라벨 · 힌트에서 사라지고 «고른 화면 · 없으면 핵심 3도메인» 을 말한다. 실측: `test_start_no_longer_claims_every_domain` · 브라우저에서 `#start` = 「▶ 데모 서버 켜기」.
- [ ] **AC-6** — ⏳ 소유자 `terraform apply` 뒤 라이브: `GET /status` 에 `domain_cold_start:true` · 꺼진 상태에서 도메인 하나 「시작」 → `GET /bundles` 의 `selection` 이 그 짝 묶음 하나. (읽기 두 번 · 창은 그 클릭이 연다 — 확인 뒤 「데모 종료」.)

# Related Specs

- `infra/demo/demo-stack.service`(`DEMO_PROFILE=selection`) · `infra/demo/demo-selection.sh`(폴백 `demo-core`) · `infra/demo/projects.sh`(`BUNDLES` · `resolve_bundles` · `resolve_deps` 가 FULL 순서로 정렬).
- `ADR-MONO-071`(화면 묶음 · 부팅 선택).

# Related Contracts

- 제어 API(`/status`)에 **추가 필드** `domain_cold_start` — 옛 소비자는 모르는 키를 무시한다. `/domain/start` 는 꺼진 상태의 409 가 200 으로 바뀌는 것 외에 응답 모양 무변경.

# Edge Cases

| 상황 | 기대 |
|---|---|
| 런처는 배포됐고 람다는 apply 전 | `/status` 에 키 없음 → 꺼진 동안 버튼 잠금(787 동작) |
| 콜드 클릭 직후 다른 도메인 클릭 | 런처가 클릭 즉시 잠근다. API 직접 호출이면 `pending` → 409 «켜지는 중» |
| 구세대 AMI(`BUNDLE_SELECTION_CAPABLE` ≠ yes) | `/status` `domain_cold_start:false` → 잠금; API 직접 호출도 409 `bundle-boot-not-deployed` |
| 예산 소진 | 429, 기동 0 |
| ecommerce 를 푸는 묶음이 둘(`store` · `console-ecommerce`) | `BUNDLES` 쪽(`store`)을 먼저 본다 — 푸는 도메인은 같다 |

# Failure Scenarios

1. `domain_start` 가 꺼진 인스턴스를 **직접** 켠다 → 세대 거절을 건너뛰어 구세대 AMI 가 선택을 모른 채 `full` 로 뜬다(647 의 결함). (z40) + bite-4 가 문다.
2. 도메인 → 묶음 표를 손으로 적는다 → 묶음이 바뀌는 날 혼자 낡아 «wms 를 눌렀더니 다른 것이 떴다». 파생 + `test_every_domain_has_a_cold_start_bundle` 이 문다.
3. 런처가 키 없이 버튼을 연다 → apply 전 창에서 누른 방문자가 409 를 본다(787 의 Failure 1).
4. 액션 뒤 `pollDomains` 를 그대로 둔다 → 꺼진 인스턴스의 빈 스냅샷으로 `renderDomains` 가 돌아 «확인 중» 버튼이 풀린다.

# Implementation Notes

- 람다 단위시험: 기준선 112 → **122 통과**(`python infra/demo/aws/tests/test_handler.py`, rc=0).
- 🔴 **bite (단위시험)**: 복사본에서 콜드 위임 두 줄을 지우면 새 칸 **12개**가 빨강. 같은 복사본 배치의 **대조군**(원본 람다)도 3칸이 빨강이었다 — `projects.sh`·`main.tf` 를 상대경로로 읽는 시험이라 복사 위치의 산물이다. ⇒ 주입에 귀속되는 빨강은 정확히 그 12칸.
- 🔴 내 시험이 처음에 **다른 클래스를 깼다**: 서브테스트마다 `setUp()` 을 다시 불러 직전 칸의 `BUNDLE_CAPABLE="no"` 를 «원래 값» 으로 저장했고, tearDown 이 그것을 되살려 뒤에 도는 `MaxRuntimeResetTest` 가 409 로 떨어졌다. 저장(setUp 1회)과 초기화(`_reset`)를 갈라 고쳤다.
- 가드: `verify-demo-wrapper.sh` 정적 **PASS (rc=0)**. ⚪ (h) 이미지 실재 검증은 이 호스트에서 `docker manifest inspect` 가 매달려 **docker 심(shim)으로 manifest 만 즉시 실패**시켜 돌렸다 → **0/18 측정(skip 18)** — 이 변경과 무관한 축이고 CI 가 잰다.
- 런처: 첫 브라우저 실측에서 클릭 직후 폴링(pending)이 응답 문구를 지웠다 — 무엇을 요청했는지가 그 줄에만 있으므로 pending·starting 에서는 남기게 고쳤다(재실측 `dmsg="mock: 기동 시작"`).
- `infra/demo/aws/README.md` 의 `/status` 필드 목록에 `domain_cold_start` 한 줄.

---

## CORRECTION

(2026-10-11 UTC, close chore) 위 Status 와 AC-6 의 «⏳ 소유자 `terraform apply` 뒤 라이브 확인» 은 **더 이상 남은 일이 아니다** — 아래가 지금의 사실이다. 위의 기록은 그대로 둔다.

- **4차원 검증**: (a) #4302 `MERGED` · 머지 커밋 `5ad677ce8` (b) `origin/main` tip 일치 (c) 머지 시점 실패 check **0**(8 pass · 필수 4종 포함) (d) AC-1~5 는 review 시점에 닫혔고 AC-6 은 아래로 닫힘.
- **AC-6 라이브 실측** — 소유자 `terraform apply`(`aws_lambda_function.control` in-place 1건, plan `0 add / 1 change / 0 destroy`, `source_code_hash` 만 변경) 뒤:
  - `GET /status` → `"domain_cold_start": true`, `state: stopped`.
  - 기준선: `GET /bundles` 의 `selection` = 지난 세션의 **5묶음** `console` · `console-scm` · `console-wms` · `store` · `store-fulfillment`.
  - 꺼진 인스턴스에서 `POST /domain/start {"name":"finance"}` → **200** «기동 시작 - finance 웜업까지 약 4~10분». 3초 뒤 `selection` = **`["console-finance"]`** — 합집합이 아니라 **교체**(세션 경계 상속 확인).
  - 약 2분 반 뒤 `console-finance` = `ready`. `/domains`: iam **up 16/16** · finance **up 7/7** · traefik 1/1 · wms · scm · erp · ecommerce · fan **down 0/0** ⇒ **24 컨테이너, 고른 도메인 + 로그인 의존만**.
  - 확인 뒤 `POST /stop` → `stopping`, `used_minutes` 1258/1800.
- ⚪ 안 잰 것: 런처 화면에서의 클릭(위 실측은 API 직접 호출). 화면 쪽은 AC-4 의 브라우저 실측(모의 API)이 덮고, 이번 라이브는 그 버튼이 부르는 같은 엔드포인트를 쟀다.
