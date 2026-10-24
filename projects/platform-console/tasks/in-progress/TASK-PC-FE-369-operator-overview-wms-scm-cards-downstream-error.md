# Task ID

TASK-PC-FE-369

# Title

운영자 통합 개요의 «WMS 출고» · «SCM 발주» 카드가 `DOWNSTREAM_ERROR` 이고 도메인 상태 요약이 «WMS 점검 불가» — 같은 `WMS_ADMIN_BASE_URL` 을 쓰는 `/wms` 화면은 정상, 서비스 로그엔 그 요청이 없다

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (원인 미확정 — 콘솔 서버 합성 · 배포 환경 변수 · 게이트웨이 경로 중 어디인지 측정부터, 콘솔 서버 로그를 못 보는 환경)
> 기안 2026-10-23 UTC — `TASK-PC-FE-364` AC-3 라이브(33차 창)의 ❌. 측정 먼저(AC-0).

# Dependency Markers

- 출처: `TASK-PC-FE-364` § 라이브 확인 (2026-10-23 UTC · 33차 창) — AC-3 ❌.
- 후속: 고친 뒤 `TASK-PC-FE-364` AC-3(권한 세트별 «권한 없음» 표 포함)을 다음 창에서 다시 잰다.
- 관련 · 직렬: `TASK-PC-FE-368`(ready — 같은 `shared/composition/` 합성에 지표 칸을 더한다). 둘이 같은 파일을 고치면 공유 파일 시리즈 = 한 worktree · 직렬.
- 관련: `TASK-PC-FE-364`(데이터 다리 정의) · `TASK-PC-FE-310`(도메인 상태 요약).
- 선행: 없음.

# Goal

Fix issue found in TASK-PC-FE-364. 33차 창(2026-10-23 UTC), 로그인(`demo-corp`) 운영자 통합 개요에서:

- «WMS 출고» · «SCM 발주» 카드가 «하위 서비스에서 오류가 발생했습니다 · 사유: DOWNSTREAM_ERROR»(두 번 로드, 수 분 간격, 같음).
- 도메인 상태 요약 «WMS 점검 불가».
- 나머지 IAM 0 · Finance 0 · ERP 2 · ecommerce 0 은 표시.
- 해당 서비스 로그(wms-admin-service · scm procurement)에 그 요청의 오류가 없다 → 요청이 서비스에 닿지 않은 것으로 보인다.
- 같은 `WMS_ADMIN_BASE_URL` 을 쓰는 `/wms` 화면은 정상.
- 콘솔 서버(Vercel) 로그는 볼 수 없었다(CLI 없음).

다리 정의(`apps/console-web/src/shared/composition/console-composition.ts`): 데이터 `:381-394` — wms `${WMS_ADMIN_BASE_URL}/dashboard/orders?status=RECEIVED&page=0&size=1` · scm `${SCM_GATEWAY_BASE_URL}/api/v1/procurement/po?status=DRAFT&page=0&size=1`(둘 다 `domainHeaders`), 상태 `:416-433` — `${originOf(base)}/actuator/health`(자격 없음, `originOf` = `:308`). 🔵 기안 때 코드 읽기(측정 아님): 실패 분류 `:192-248` 은 fetch 예외 · 2xx 인데 JSON 아님 · health 503 인데 health 문서 아님 · 401/403 밖의 모든 상태(404 포함)를 **같은** `DOWNSTREAM_ERROR` 로 접는다 — 화면 문구만으로는 «서비스가 오류를 냈다» 와 «요청이 서비스에 안 닿았다» 를 구분할 수 없다. 상태 다리는 base 의 origin 만 쓰므로 base 에 경로 접두가 있으면 데이터 다리와 다른 곳을 친다.

목표: 두 카드가 실제 값(또는 그 도메인의 정당한 «권한 없음»)을 그리고, 도메인 상태 요약이 WMS 를 실제 상태로 보인다.

# Scope

## In Scope

- **AC-0 측정** (file:line · 실측):
  - ① 다리 URL 을 실제 배포 값으로 풀어 적는다(Vercel Production env 의 `WMS_ADMIN_BASE_URL` · `SCM_GATEWAY_BASE_URL` — 비밀이 아닌지 확인 후, 아니면 모양만) · `/wms` 화면이 부르는 URL · 헤더 · 토큰과 무엇이 다른가.
  - ② 같은 요청을 콘솔 서버 밖에서 재현(같은 URL · 같은 헤더 모양 · 운영자 토큰) — 게이트웨이 · Traefik 앞에서 무엇을 받는가(상태 · 본문 · `Content-Type`). `/actuator/health` 가 그 origin 에서 무엇을 받는가.
  - ③ 위 `:192-248` 분류 중 어느 갈래였는가를 보이게 할 수단 — 콘솔 서버 로그가 없는 환경에서 원인을 남길 길(`logLeg` 의 출력 위치 · 응답 `reason` 세분 · span).
  - ④ 시험 · e2e(federation · nightly)가 이 두 다리를 실제 URL 로 돌렸는가 — 그쪽은 초록인데 라이브가 빨간 이유.
- 수선: AC-0 이 정한다(URL 조립 · env · 게이트웨이 경로 · 오류 분류). 계약(`console-integration-contract.md` § 2.4.9.1 · § 2.4.9.2)이 바뀌면 계약 먼저. 사용자에게 보이는 문구가 바뀌면 소유자 승인.

## Out of Scope

- 카드 정의 · 문구(`TASK-PC-FE-364`) · 실적 지표 칸(`TASK-PC-FE-368`).
- 서버 쪽 변경 — AC-0 에서 서버 · 게이트웨이 · 데모 배선 결함으로 판명되면 그 프로젝트에 별도 티켓.

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④ 를 file:line · 실측 출력으로. 원인이 콘솔 밖(게이트웨이 · 서버 · 데모 배선)이면 STOP 하고 해당 프로젝트 티켓을 기안한다.
  - → **닫힘 (2026-10-24 UTC 기록 정리)** — ①~④ 는 아래 § 기전 (e) · § ② · § ③ · § ④ · § 측정(2026-10-24 UTC, CloudWatch 1분 해상도)에 있다. 원인은 콘솔 밖이었다(`infra/demo/` 공유 해석기, 동시 `/status` 가 람다 한도 10 을 넘음). AC-0 이 요구한 대로 해당 티켓 `TASK-MONO-814` 를 기안했다. 🔵 이 칸은 수선 PR(#4508)이 머지될 때 체크되지 않은 채 남아 있었다 — 본문은 이미 닫힌 상태를 적고 있었다.
- [x] **AC-1** — 수선 + 시험(원인을 재현하는 고정값 · 실패 분류) · `tsc --noEmit` · `next lint` · vitest(`--maxWorkers=4 --minWorkers=2`) · Client graph 가드 둘 · 큐 가드 셋(스테이지 뒤) rc=0. SSR · 합성이 바뀌면 머지 전 브랜치 nightly 콘솔 잡 · federation e2e.
  - → **닫힘 — `TASK-MONO-814` AC-1~4 로** (같은 PR #4508, `45fa46428`; 814 는 `review/` 에서 AC-1~4 체크 확인). 본문 § 수선의 자리: «AC-1 은 814 의 AC-1~4 로 닫힌다». 남는 것은 AC-2(라이브) 하나다 — 다음 창, `TASK-MONO-672` 목록(814 AC-6 과 같이).
- [ ] **AC-2** — 배포(Vercel Production 기록) · 라이브: 로그인(`demo-corp`) 개요의 «WMS 출고» · «SCM 발주» 카드가 값을 그리고 도메인 상태 요약에 «WMS 점검 불가» 가 없다(다음 데모 창 — wms · scm 묶음이 켜져 있을 때).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (데이터 다리) · § 2.4.9.2 (상태 다리)
- wms `projects/wms-platform/specs/contracts/http/admin-service-api.md` (`/dashboard/orders`) · scm procurement (`/api/v1/procurement/po`)

# Edge Cases

- 묶음(bundle)이 꺼진 도메인은 정당한 저하다 — 이 결함과 구분돼야 한다(30차 창의 카드 `DOWNSTREAM_ERROR` 는 `ecommerce` 묶음이 꺼진 경우였다 — `TASK-MONO-672` § Order 3). 33차 창은 9묶음 전부 ready 였다.
- 운영자가 그 도메인 권한이 없을 때의 «권한 없음» 은 바뀌면 안 된다.

# Failure Scenarios

1. 단위 시험의 URL 모의값으로 초록 — 실제 배포 env 에서만 나는 결함일 수 있다 → AC-0 ① · ②.
2. 묶음이 꺼진 창에서 «재현» 하고 원인으로 오인 → 창의 묶음 상태를 같이 적는다.

# AC-0 — 코드 · 계약 판독 (2026-10-24 UTC, 브랜치 `task/pc-fe-369-overview-cards` = origin/main `2b16bd674`)

> 🔴 **라이브 측정 0** — 데모 인스턴스가 꺼져 있다. 아래는 전부 코드 · 계약 · 앞선 티켓 기록 판독이다. ① 의 «실제 배포 값» 은 `TASK-PC-FE-352` § Vercel 확인 결과(소유자, 2026-10-20 UTC)에서 가져왔다 — 이 티켓이 다시 잰 값이 아니다.
> 🔴 **결론: 원인을 코드로 증명하지 못했다 ⇒ 수선 보류(AC-1 열림).** 의심 넷은 배제됐고, 남은 기전 하나는 «가능하다» 까지만 증명된다(검증 가능한 기전 ≠ 원인).

## ① 다리 요청 vs 동작하는 화면 요청 — 바이트 대조

| 축 | 개요 WMS 다리 | `/wms` «미출고 주문» 칸 | 개요 SCM 다리 | `/scm` 조달 화면 |
|---|---|---|---|---|
| 조립 | `console-composition.ts:384-388` `${WMS_ADMIN_BASE_URL}/dashboard/orders?status=RECEIVED&page=0&size=1` | `wms-ops/api/overview-state.ts:204` → `wms-inventory-api.ts:80-90` 같은 경로 · 같은 질의(`status=RECEIVED&page=0&size=1`) | `console-composition.ts:390-394` `${SCM_GATEWAY_BASE_URL}/api/v1/procurement/po?status=DRAFT&page=0&size=1` | `scm-procurement-api.ts:20-33` 같은 경로 |
| 주소 해석 | `operator-overview-composition.ts:85` `fetch(await resolveBackendUrl(url))` | `shared/api/wms-gateway.ts:172` 같은 함수 | 같음(`:85`) | `shared/api/flat-envelope-gateway.ts:252` 같은 함수 |
| 헤더 | `console-composition.ts:341-347` `Accept` · `Authorization: Bearer <domainFacingToken>` · `X-Request-Id`, `X-Tenant-Id` 없음 | `wms-gateway.ts:283-290` 같은 셋, `X-Tenant-Id` 없음 | 같음 | `flat-envelope-gateway.ts:414-418` 같은 셋 |
| 토큰 | `operator-overview-composition.ts:69` `getDomainFacingToken()` | `wms-gateway.ts:276` 같은 함수 | 같음 | 같음 |
| 시한 | `LEG_TIMEOUT_MS` 4000(`:69`) — 넘으면 `TIMEOUT`, `DOWNSTREAM_ERROR` 아님 | `WMS_TIMEOUT_MS` 5000 | 4000 | `SCM_TIMEOUT_MS` 5000 |

- Vercel Production 의 두 env 는 **비어 있다** ⇒ 코드 기본값 `http://wms.local/api/v1/admin` · `http://scm.local`(`env.ts:182-185` · `:217`), 런타임에 `resolveBackendUrl` 이 `.local` → `.<ip-대시>.sslip.io` 로 바꾼다(`demo-backend.ts:103-127`). 출처 `TASK-PC-FE-352` § Vercel 확인 결과 ②. 비밀 아님.
- ⇒ **개요 WMS 다리와 `/wms` 칸은 같은 URL · 같은 헤더 · 같은 토큰 · 같은 해석 함수**다. 다른 것은 시한(4 s vs 5 s — 넘으면 사유가 `TIMEOUT` 이라 관측과 안 맞는다)과 **같은 프로세스에서 동시에 도는 호출 수**뿐이다.

## 의심 (a)~(d) — 판정

| 의심 | 판정 | 근거 (file:line) |
|---|---|---|
| (a) 생산자가 거부하는 `status` 값 → 400 | ❌ **배제(증명)** | WMS `OrderDashboardController.java:47` `@RequestParam(required = false) String status` — 자유 문자열, 검증 없음, JPQL `OrderSummaryRepository.java:27` 의 등호 비교(없는 값이면 0 건 200). SCM `PurchaseOrderController.java:119` `PoStatus status` — `PoStatus.java:15` 에 `DRAFT` 가 있다. 게다가 WMS 는 `/wms` 칸이 **같은 값**으로 성공했다. |
| (b) 응답 모양 불일치 → 파싱 실패가 `DOWNSTREAM_ERROR` 로 | ❌ **배제(증명)** | 합성은 본문을 **모양 검사 없이** `res.json()` 만 한다(`console-composition.ts:209-217`) — `page.totalElements` 중첩 여부와 무관. 카드별 좁히기(`operator-overview-types.ts:189-218`)는 렌더 쪽이고 실패해도 카드는 `ok` 로 남아 «—» 를 그린다(같은 파일 `:155-157`) — `DOWNSTREAM_ERROR` 를 만들 수 없다. 남는 길은 2xx 인데 JSON 이 아닌 본문뿐인데, 그것은 생산자 DTO 가 아니라 «다른 곳이 대답했다» 의 경우다(아래 기전). |
| (c) 화면 클라이언트가 보내는 헤더 누락 | ❌ **배제(증명)** | 위 표 — 세 헤더가 같고 둘 다 `X-Tenant-Id` 를 **일부러** 안 보낸다(`wms-gateway.ts:289`, `flat-envelope-gateway.ts:418`). 역할은 토큰 안에 있다. |
| (d) 상태 다리 `originOf(base)` 가 경로 접두를 떨어뜨려 `/actuator/health` 가 빗나감 | ❌ **배제(증명)** | `originOf('http://wms.local/api/v1/admin')` = `http://wms.local` → `/actuator/health` 는 데모 Traefik `Host(wms.<도메인>)` 전체 → wms 게이트웨이 8080(`projects/wms-platform/docker-compose.e2e.yml:108-110`) — 게이트웨이 자신의 actuator(`gateway-service/.../application.yml:125-134`), 공개 경로(`GatewayRoutingAuthIntegrationTest.java:153` `healthEndpointIsPublic`). SCM 도 같은 모양(`GatewayHealthCheckIntegrationTest.java:11`)이고 33차 창에서 **성공**했다. 20차 창에서는 같은 코드의 WMS 상태 다리가 정상이었다(`tasks/done/TASK-MONO-758-…md:108` «정상 5 · 점검 불가 1» = 내린 scm 만). |

## 남는 기전 (e) — 다리마다 따로 도는 데모 주소 해석 · 🔵 «가능» 은 증명, «일어났다» 는 추론

증명된 사실(코드):

1. **같은 화면 렌더에서 해석 호출이 13개 이상 동시에 난다.** 개요 페이지가 도메인 상태(6 다리) · 레지스트리 · 개요(6 다리)를 한 프로세스에서 동시에 띄운다(`app/(console)/dashboards/overview/page.tsx:91` · `:95` · `:102`, `TASK-PC-FE-350` 부터 SSR 이 HTTP 를 안 거치고 같은 프로세스에서 직접 합성). 다리마다 `resolveBackendUrl` → `resolveDemoBackend` 를 부른다(`operator-overview-composition.ts:85`, `domain-health-composition.ts:58`).
2. **해석기에는 진행 중 요청 공유가 없다.** 캐시가 비었거나 15 s 가 지났으면 호출마다 컨트롤 플레인 `/status` 를 **따로** 친다(`infra/demo/backend-resolver/src/index.ts:206-212`, TTL `:142`). 한 합성 안의 여섯 다리는 같은 틱에 캐시를 보므로(`console-composition.ts:275` 동기 `map` → `:199` 첫 `await` 전까지 동기), 캐시가 차 있으면 여섯이 **같은 주소**를 쓰고, 비어 있으면 **여섯이 각자** `/status` 를 친다 — 한 합성 안에서 «둘만 실패» 가 나오는 것은 후자뿐이다(같은 주소면 `/wms` 와 같은 요청이다).
3. **`/status` 하나가 실패하면 그 다리만 설정값 `*.local` 로 간다.** `fetchStatus` 는 비-2xx · 2 s 시한 · 네트워크 실패를 전부 `null` 로 돌린다(`index.ts:174-190`, `STATUS_TIMEOUT_MS` 2000 = `:132`) → `resolveBackendUrl` 은 설정값을 그대로 돌린다(`demo-backend.ts:124-127`) → Vercel 에서 `http://wms.local/…` 은 닿지 않는다 → `fetch` 가 던지고 시한 전이면 `DOWNSTREAM_ERROR`(`console-composition.ts:205-206`). **그 요청은 데모 호스트에 안 간다** — 서비스 로그가 비어 있다는 관측과 맞는다. 실패한 결과는 캐시도 덮어쓴다(`index.ts:231`).
4. **`/status` 는 동시 13개를 받을 여유가 없다.** API Gateway 스테이지 스로틀 `rate 5 · burst 10`(`infra/demo/aws/terraform/main.tf:515-518`) — 그 버킷을 론처 폴링 · heartbeat 도 같이 쓴다. 스로틀을 넘기면 429(비-2xx). 넘기지 않아도 동시 요청마다 람다 인스턴스가 따로 뜨고 `/status` 는 EC2 `describe_instances` + SSM 읽기를 한다(`lambda/handler.py:895-906` · `:835-892`) — 차가운 시작이 2 s 해석 시한을 넘을 수 있다.
5. `TASK-PC-FE-352` 가 이 길을 이미 «가장자리 (b) — 데모 on 인데 `/status` 가 실패해 설정값을 그대로 쓰는 경우» 로 적었다(그 티켓 § Vercel 확인 결과 ④ 판독). 그때는 «일시적» 으로 봤다 — 위 1·4 는 그것이 **매 차가운 렌더마다** 일어날 수 있다는 근거다.

추론으로만 남는 것(증명 못 함):

- 33차 창의 그 렌더에서 실제로 `/status` 가 실패했는가 — Vercel 로그(`console_composition_leg`)도 API Gateway · 람다 지표도 이 티켓은 못 봤다.
- 왜 **같은 둘**(WMS · SCM 데이터 + WMS 상태)이 두 번 다 실패했는가 — 이 기전은 «어느 다리가 지는가» 를 도착 순서에 맡긴다. 같은 코드 경로라 순서가 비슷할 수는 있으나 같은 집합이 두 번 나온 것은 이 기전으로 **설명되지 않은 채** 남는다.
- `/wms` 는 같은 렌더에서 11 개를 동시에 띄운다(`overview-state.ts:164-208`) — 기전 (e) 라면 그쪽도 차가운 렌더에서 칸 일부가 저하됐어야 한다. 소유자는 «미출고 주문 0» 만 보고했다(다른 칸 상태 · 캐시가 따뜻했는지는 기록 없음).

## ② 콘솔 밖 재현 — ⚪ 못 쟀다

- 데모 인스턴스가 꺼져 있어 게이트웨이 · Traefik 앞에서 같은 요청을 칠 수 없다.

## ③ 어느 갈래였는지 보이게 할 수단 — 현재는 **없다**

- `logLeg`(`console-composition.ts:121-139`)는 `reason` 만 남긴다. `DOWNSTREAM_ERROR` 는 다섯 갈래(`:206` fetch 예외 · `:216` 2xx 비-JSON · `:237` 503 비-health · `:248` 그 밖 모든 상태 · `compose` `:282` 다리 거부)에서 같은 값이다 — 로그로도 화면으로도 갈래를 못 가른다. 주소가 데모로 바뀌었는지(`resolveBackendUrl` 이 설정값을 그대로 돌렸는지)도 안 남는다.

## ④ 시험 · e2e 가 이 다리를 실제 URL 로 돌렸는가

- 단위 시험은 `fetchLeg` 모의(`tests/unit/shared/console-composition.test.ts`) — `.local` 고정값, 해석기 없음.
- federation-hardening e2e · nightly 콘솔 잡은 **컨테이너 DNS 주소**(`http://wms-admin-service:8086/api/v1/admin` 등, `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml:787-788`)이고 `DEMO_API_BASE` 가 없다 ⇒ `resolveBackendUrl` 은 아무것도 안 한다(`demo-backend.ts:116`). **기전 (e) 는 어느 시험의 모집단에도 없다** — 그쪽이 초록이고 라이브만 빨간 것과 맞는다(그러나 그것이 원인의 증거는 아니다).

## 다음 창 · 그 전에 잴 것 (제안 — 소유자 결정 필요)

데모를 켜지 않고 **지금** 잴 수 있는 것(AWS 콘솔, 33차 창 시각 기준 — CloudWatch 1 분 지표는 15 일 보존):

1. API Gateway HTTP API `$default` 스테이지의 `4xx` · `Count` (1 분 단위) — 개요를 연 두 시각에 `/status` 429 가 있었는가.
2. 람다 `ConcurrentExecutions` · `Throttles` · 로그 그룹의 `REPORT` 줄 `Duration` · `Init Duration` — 같은 분에 동시 실행이 10 을 넘었는가, 2000 ms 를 넘긴 `/status` 가 있었는가.

다음 데모 창:

3. 개요를 **두 번 연달아**(15 s 안 — 캐시가 따뜻한 렌더) 열어 본다. 기전 (e) 면 두 번째는 여섯 카드가 다 산다.
4. 차가운 첫 렌더에서 `/wms` 의 11 칸 전부의 상태.

가장 작은 진단 변경(코드 — 사용자 문구 변경 없음, 로그만):

5. `logLeg` 에 `cause` 한 칸 — `fetch_threw:<err.cause.code>`(`ENOTFOUND` · `ECONNREFUSED` · `UND_ERR_*`) · `http_<status>` · `body_not_json` · `health_503_not_document` · `rejected` 와 `addr: demo | configured`(해석 결과가 설정값과 같았는가). 콘솔 로그를 볼 수 있는 창에서 다섯 갈래와 기전 (e) 를 가른다. 🔴 Vercel 로그를 못 보는 창에서는 이것도 안 보인다 — 그래서 1·2 가 먼저다.

기전 (e) 가 측정으로 확인됐을 때의 수선 후보(지금 구현하지 않음):

- `@demo/backend-resolver` 의 `resolveSnapshot` 에 진행 중 약속 공유(동시 호출은 `/status` 하나를 기다린다) + 실패 결과가 좋은 캐시를 덮어쓰지 않게. 🔴 `infra/demo/` 는 공유 경로 — 루트 티켓(세 앱이 같은 해석기를 쓴다). 콘솔 쪽만 고치려 `demo-backend.ts` 에 캐시를 두면 `scripts/check-demo-resolver-copies.sh` 의 «앱 안의 구현» 지문에 걸릴 수 있다(그 파일 헤더 `:11-14`).

# 측정 (2026-10-24 UTC)

> 위 «다음 창 · 그 전에 잴 것» 1 · 2 를 메인 세션이 쟀다 — 읽기 전용 CloudWatch, 33차 창 2026-10-23 13:25–13:45 UTC. 🔵 기전 (e) 가 **1 분 해상도로 확인**됐다.

| 지표 (분당) | 13:32 | 13:33 | 13:35 | 13:37 | 그 밖의 분 |
|---|---|---|---|---|---|
| 람다 `portfolio-demo-control` `Throttles` (Sum) | 4 | 4 | 12 | 4 | 0 |
| 람다 `ConcurrentExecutions` (Max) | 10 | 10 | 10 | 10 | 2 |
| API Gateway `$default` `5xx` (Sum) | 4 | 4 | 12 | 4 | 0 |
| API Gateway `$default` `4xx` (Sum) | 0 | 0 | 0 | 0 | 0 |

- `aws lambda get-account-settings`: 계정 `ConcurrentExecutions` 한도 **10** · 미예약 10 · 이 함수의 예약 동시성 없음. 스테이지 스로틀 rate 5 / burst 10(`infra/demo/aws/terraform/main.tf:516-517`) 은 **발화하지 않았다**(4xx 0) — 걸린 것은 계정 동시 실행 한도다. 위 «남는 기전» 4 의 «스로틀을 넘기면 429» 추정은 틀린 갈래였고, 실제 갈래는 람다 스로틀 → 5xx 다(어느 쪽이든 `fetchStatus` 는 `null` 이라 결과는 같다).
- 그 분들은 로그인 개요를 연 때(13:33 · ~13:36)와 그 사이 점검들이다.
- ⇒ 동시 `/status` 가 10 을 넘는다 → 스로틀 → 5xx → `resolveSnapshot` 이 `null` 을 캐시(좋은 스냅샷을 덮는다) → 다리가 `*.local` 로 → fetch 가 던진다 → `DOWNSTREAM_ERROR`. 🔵 **어느 다리가** 스로틀된 조회를 받았는지는 여전히 추론이다(지표는 요청을 세지 호출자를 세지 않는다).

## 소유자 결정 (2026-10-24 선택 창)

«주소 조회를 한 번으로 묶기» — 코드로 고친다. 공유 `@demo/backend-resolver` 가 동시 조회를 진행 중 `/status` **하나**로 묶고, 실패한 조회가 좋은 캐시를 덮지 않는다. AWS 무변경.

## 수선의 자리 — `TASK-MONO-814` (같은 PR)

- `infra/demo/` 는 공유 경로라 수선은 루트 티켓 `tasks/in-progress/TASK-MONO-814-demo-backend-resolver-single-flight.md` 이 싣는다. 이 티켓의 콘솔 쪽 변경은 그 수선의 시험(`tests/unit/demo-backend.test.ts`)뿐이고 같은 PR 에 탄다.
- 🔴 **이 티켓은 열어 둔다.** AC-1 은 814 의 AC-1~4 로 닫히지만 AC-2(라이브)는 다음 창 — 차가운 개요의 여섯 카드 + 그 분 람다 `Throttles` 0(814 AC-6) — 를 기다린다.
