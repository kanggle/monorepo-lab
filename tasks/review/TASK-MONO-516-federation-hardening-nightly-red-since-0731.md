# Task ID

TASK-MONO-516

# Title

Federation Hardening E2E nightly 가 **2026-07-31 이후 매일** 빨갛다 — `scm-inbound-expected-loop` 스펙이 페이지 봉투를 `items` 로 읽는데 계약이 `content` 로 바뀌어 있었다 (**리더 결함**, wms 는 매번 ASN 을 만들고 있었다)

# Status

review

# Task Tags

- bug
- ci
- integration

---

# 배경 — `TASK-ERP-BE-041` 작업 중 main 확인하다 발견

`main` 의 **Federation Hardening E2E (Phase 8 cross-product, nightly)** 워크플로 이력:

| 결과 | commit | 날짜 |
|---|---|---|
| ❌ | `078d230c6` | 2026-08-07 |
| ❌ | `eae894bf7` | 2026-08-05 |
| ❌ | `6d65c43ad` | 2026-08-04 |
| ❌ | `672426d33` | 2026-08-03 |
| ❌ | `672426d33` | 2026-08-02 |
| ❌ | `672426d33` | 2026-08-01 |
| ❌ | `e00236923` | 2026-07-31 |
| ✅ | `7e48b1152` | 2026-07-30 ← **마지막 초록** |

🔴 **7일 연속, 서로 다른 커밋에서, 같은 실패.** 이것은 flake 가 아니라 **결정론적 회귀**다.
그리고 **아무도 티켓을 갖고 있지 않다**(`TASK-MONO-328` 이 파일명만 스쳐 언급하지만 그 티켓은
CI `if:` 리팩터로 무관).

## 실패 지문

```
[chromium] › specs/scm-inbound-expected-loop.spec.ts:91:7 › scm inbound-expected …
Error: wms inbound-service creates an Asn(CREATED, SCM_PROCUREMENT)
       — poNumber=SCM-PO-FED-8E451
1 failed, 20 passed
```

`Build`·`compose up`·서비스 기동은 모두 통과했고 **스펙 하나만** 죽는다. 즉 인프라 실패가
아니라 **동작 실패**다.

## 🔴 대조군이 없는 관찰 — 원인으로 쓰지 말 것

같은 로그에 `SQL Error: 1406, SQLState: 22001`("Data too long for column")이 보인다. **그러나
그것은 `auth-service` 가 낸 것**이고 inbound 경로가 아니다. 착수 시 이것을 원인으로 채택하지
마라 — 나는 그 연결을 세우려다 서비스 이름을 확인하고 **철회했다.** 초록이던 `7e48b1152`
실행에도 같은 1406 이 있는지부터 보라(있으면 잡음이다).

## 🔵 `TASK-SCM-BE-058` 과 **증상 계열이 같다** — 그러나 공통 원인은 미확인

`TASK-SCM-BE-058`(scm e2e `InboundExpectedLoopE2ETest` — "기다리던 inbound-expected 이벤트가
안 왔다")과 이 티켓(federation — "wms 가 ASN 을 안 만든다")은 **같은 scm → wms inbound-expected
경로**다. 한쪽이 다른 쪽의 결과일 가능성이 높다.

🔴 **그러나 그것은 아직 추론이다.** 결정적 차이가 하나 있다:

| | `SCM-BE-058` | 이 티켓 |
|---|---|---|
| 성격 | **간헐적**(같은 날 초록 2회) | **결정론적**(7일 연속) |

간헐과 결정론이 같은 원인일 수는 있지만(자원 압박이 간헐을 만들고 스키마/계약 결함이
결정론을 만드는 식으로 **둘일 수도** 있다), **같다고 가정하고 하나만 고치면 나머지가 남는다.**
AC-2 가 이것을 가르라고 요구한다.

# Goal

Federation Hardening nightly 가 `main` 에서 초록이다. 그리고 **7일간 빨갰던 이유**가 기록된다.

# Scope

## In Scope

- `tests/federation/specs/scm-inbound-expected-loop.spec.ts` 와 그것이 검증하는 경로
- wms `inbound-service` 의 ASN 생성 (scm `inbound-expected` 소비)
- 필요 시 `federation-hardening-e2e.yml`

## Out of Scope

- scm 단독 e2e 의 간헐 실패 → **`TASK-SCM-BE-058`**(공통 원인으로 밝혀지면 그때 병합)
- GitHub Actions 장애로 인한 `Set up job` 실패

# 🔴 이 티켓의 제목이 틀려 있었다 — 착수 첫 발견

원래 제목은 *"wms 가 ASN 을 만들지 않는다"* 였다. **그건 관측이 아니라 테스트의 assertion 라벨이다.**
실패 실행(31135504068)의 컴포즈 로그가 정반대를 말한다:

```
00:55:32 CreateScmInboundExpectationService - scm_inbound_expected_created asnNo=ASN-20260807-0001 poNumber=SCM-PO-FED-8E451970
00:56:42 CreateScmInboundExpectationService - scm_inbound_expected_created asnNo=ASN-20260807-0002 poNumber=SCM-PO-FED-3A030585
00:57:49 CreateScmInboundExpectationService - scm_inbound_expected_created asnNo=ASN-20260807-0003 poNumber=SCM-PO-FED-330AE23A
```

세 poNumber 는 **테스트가 쓴 세 개와 정확히 일치**한다(초기 시도 + 재시도 2회). wms 는 매번,
즉시(1초 내) ASN 을 만들었다. **7일 내내 고장나 있던 것은 읽는 쪽이다.**

⇒ 티켓을 쓸 때 나는 실패 메시지를 증상으로 옮겨 적었고, 그 메시지는 **생산자의 이름을 달고 있었다.**
[[feedback_a_warning_can_be_a_proxy_for_a_bigger_problem]] 의 반대 방향 — 경고가 엉뚱한 주체를 지목했다.

# 근본 원인 (확정)

`TASK-BE-568`(ADR-MONO-058 D3, PR #3147)이 wms inbound-service 의 목록 봉투를 교체했다:

| | 이전 | 이후 |
|---|---|---|
| 타입 | `AsnController.PagedResponse<T>`(수제) | 공유 `PageResult` 기반 `PageResponse<T>` |
| 배열 필드 | **`items`** | **`content`** |
| 페이지 필드 | `page`,`size`,`total` 평면 | `page:{number,size,totalElements,totalPages}` + `sort` |

`scm-inbound-expected-loop.spec.ts:167` 은 `body.items` 를 계속 읽었다 → `undefined` → `[] `
→ `.some(...)` 이 **60초 내내 false** → 타임아웃. 매 실행 동일하므로 **결정론적**이다.

**🔵 BE-568 은 잘못하지 않았다.** 계약(`inbound-service-api.md §Pagination`)도, 생산자측 가드
(`AsnControllerSliceTest` 의 `$.content`/`$.page`/`$.sort`)도 같이 옮겼고 PR CI 는 정당하게 초록이었다.
**옮겨지지 않은 유일한 소비자가 이 nightly 전용 스펙**이었고, 그래서 7일·7커밋 뒤에 드러났다.

**소비자 전수 조사 결과 — 반경은 이 스펙 하나다**: `.items` 로 이 목록을 읽는 곳은 저장소 전체에서
`scm-inbound-expected-loop.spec.ts:167` 뿐. 콘솔의 `listAsns` 는 **admin-service** `/dashboard/asns`
를 부르고(별개 계약) 이미 `content` 를 읽는다. `seed-wms.sh` 는 원문 body 를 `grep -F` 해서
필드명에 무관하다.

# Acceptance Criteria

- [x] **AC-0 (모집단 재확인)** — 재계수 완료. 실패 **7건**(07-31~08-07), 마지막 초록
      `7e48b1152`(07-30), 그 앞은 **13연속 초록**(07-18~07-30). 실패 단계로 갈라 세니
      **7/7 이 `Run Playwright federation hardening e2e` 단계**이고 `Set up job` 인프라 실패는
      **0건**이다 ⇒ 전부 이 결함
- [x] **AC-1 (마지막 초록 → 첫 빨강 사이의 diff)** — 창은 716파일/29k줄(하루치 머지 전량)이라
      눈으로 볼 크기가 아니었다. **좁히는 축은 "무엇이 바뀌었나"가 아니라 "실패가 무엇을
      만지나"였다** — federation 하네스는 창 안에서 **0줄** 바뀌었고(테스트는 그대로 = 제품 회귀),
      wms inbound-service 의 바뀐 11파일 중 `AsnController`·`AsnQueryService`·`QueryAsnUseCase`
      + **신규 `PageResponse`** 가 정확히 목록 응답 경로였다. 원인 = `TASK-BE-568`(PR #3147)
- [x] **AC-2 (SCM-BE-058 과의 관계 확정) — 별개다. 병합하지 말 것.**
      수치 근거: `InboundExpectedLoopE2ETest`(scm) 안에 `inbound/asns`·`items`·`PagedResponse`·
      `SCM_PROCUREMENT` 참조가 **0건**이다. 그 테스트는 **Kafka 봉투**(`scm.procurement.inbound-expected.v1`)
      를 `await` 하는 **생산자측** 검증이고 wms HTTP 목록을 **아예 읽지 않는다** ⇒ 이 티켓의
      원인(HTTP 봉투 필드명)이 그것의 원인일 수 **없다**. 성격도 갈린다(간헐 vs 결정론).
      `TASK-SCM-BE-058` 은 자기 원인을 따로 가진다
- [x] **AC-3 (1406 배제/채택) — 로그 대조로는 답할 수 없다(계측 부재). 그리고 무관하다.**
      🔴 초록 실행 로그에 1406 이 0건이라 "빨강에만 있다=신호" 로 승격시킬 뻔했다. 확인해 보니
      **초록 로그엔 컨테이너 로그가 통째로 0줄**이다(`auth-service-1|` 0건 / 빨강 97건,
      `wms-inbound-service-1|` 0건 / 빨강 500건) — 컴포즈 로그 덤프는 **실패 시에만** 돈다.
      대조군이 계측기에 오염돼 있었다([[feedback_control_set_contaminated_by_the_instrument]]).
      별개로 1406 은 `auth-service` 가 내는 것이라 ASN 읽기 경로와 무관하다
- [ ] **AC-4 (수정 + 연속 초록)** — 수정 후 nightly **연속 2회** 초록. 결정론적 결함이므로
      1회로도 강한 증거지만, 2회가 인프라 잡음과 갈라 준다 ⇒ **머지 후 확인 필요**
- [x] **AC-5 (재발 방지 — PR 타임 가드)** *(착수 중 추가)* — nightly 전용 소비자가 계약을
      읽는데 PR 타임에 그걸 묶어 두는 것이 없었다. `AsnControllerSliceTest` 에
      **federation 리더가 매칭하는 세 이름**(`$.content`, 행의 `source`/`status`)을 고정하는
      테스트 추가. **물기 실측**: `AsnSummaryResponse.source → sourceType` 변형 시
      **17건 중 1건만** RED(=새 테스트). 나머지 16건은 초록 ⇒ **그 틈이 실재했음의 증명**
      (`$.content[0].id` 만 보던 기존 단언들은 필드명 변경을 그대로 통과시켰다). 복원 후
      17/0/0 초록

# Related Specs

- `projects/wms-platform/specs/services/inbound-service/architecture.md`
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- ADR-MONO-050 (scm → wms inbound-expected 컨슈머)

# Edge Cases

- federation 스택은 여러 프로젝트를 한 compose 로 띄운다 — 한 서비스의 스키마/계약 변경이
  **다른 프로젝트의 nightly** 만 깨뜨릴 수 있다(그래서 PR-time CI 는 초록이었다)
- 🔴 `nightly-e2e.yml` 과 `federation-hardening-e2e.yml` 은 **다른 워크플로**다. 하나가 초록이라고
  다른 하나를 추정하지 마라 — 이 세션에서 둘 다 빨갰고 실패 스펙이 서로 달랐다

# Failure Scenarios

- **간헐로 오해하고 재실행** → 7일 연속 실패는 재실행으로 안 사라진다. AC-1 이 diff 를 요구한다
- **`SCM-BE-058` 과 묶어서 하나만 고침** → 나머지가 남는다. AC-2 가 막는다
- **PR-time CI 가 초록이라 안심** → 이 경로는 **nightly 에서만** 실행된다
  (CLAUDE.md § "Post-merge nightly check")

# 구현 기록

## 고친 것 (2파일)

1. **`tests/federation-hardening-e2e/specs/scm-inbound-expected-loop.spec.ts`**
   - `body.items` → `body.content` (계약 `inbound-service-api.md §Pagination`)
   - 🔴 **그리고 술어가 실패를 삼키던 것을 고쳤다.** 원래는
     `if (resp.status() !== 200) return false;` 였다 — **401/403/계약드리프트/진짜 부재가
     한 문장을 공유**했고, 그 문장이 하필 생산자를 지목했다. 이제 `lastDiagnostic` 이
     비-200(상태+본문), `content` 부재(실제 봉투 키 목록), 행 부재(행 수 + 관측된 source 목록)
     를 갈라 기록하고 타임아웃 시 그걸 보고한다. 메시지에 *"이게 봉투 이름이나 비-200 을
     보고하면 컨슈머는 멀쩡하고 이 리더가 결함일 수 있다 — 탓하기 전에 inbound-service 로그의
     `scm_inbound_expected_created` 를 보라"* 를 넣었다. 이번에 내가 7일치를 잘못 읽은
     경로를 다음 사람에게는 막아 준다
2. **`projects/wms-platform/apps/inbound-service/.../AsnControllerSliceTest.java`** — AC-5 가드

## 검증

- 슬라이스 테스트 **17/0/0** (`--rerun-tasks` 로 강제 실행, XML 리포트에서 확인 —
  UP-TO-DATE 캐시가 아니다)
- 물기 실측: 변형 시 1건만 RED(위 AC-5)
- e2e 스펙 `tsc --noEmit`: 에러 2건인데 **둘 다 `kafkajs` 모듈 부재**이고 **손대지 않은
  `scm-replenishment-loop.spec.ts` 에도 동일**하게 난다(대조군) ⇒ 내 편집이 만든 타입 에러 **0건**
- 🔵 **로컬에서 federation 스택을 띄워 실증하지는 못했다** — 44컨테이너 + 호스트 여유 4.4GB.
  대신 증거는 ① 실패 실행의 컴포즈 로그가 ASN 3건 생성을 poNumber 일치로 보여 준 것
  ② 생산자측 슬라이스 테스트가 실제 직렬화기로 `content`/`source`/`status` 를 찍는 것.
  **최종 판정은 AC-4(nightly 2연속 초록)** 이며 그 전까지 이 수정은 미검증이다

# 🔴 남은 체계적 구멍 (이 티켓이 닫아도 남는다)

nightly 전용 스펙이 계약을 읽는데 **PR 타임에 그 둘을 묶어 두는 장치가 일반적으로는 없다.**
이번엔 `AsnControllerSliceTest` 에 수동으로 이름 세 개를 박아 막았지만, 그건 **이 한 경로에만**
적용된 처방이다. federation/nightly 스펙이 읽는 다른 계약들도 같은 상태일 수 있다.
⇒ 후속 티켓 후보(이 티켓 범위 아님): nightly 전용 리더가 의존하는 응답 필드를 열거하고
PR 타임 가드 유무를 세는 sweep. **숫자를 먼저 세고 나서 판단할 일이지 지금 추정으로 만들 티켓이 아니다.**

# Definition of Done

- [x] AC-0~AC-3, AC-5 충족
- [ ] **AC-4 — Federation Hardening E2E nightly 연속 2회 GREEN** (머지 후 확인)
- [x] Ready for review
