# Task ID

TASK-MONO-758

# Title

`ADR-MONO-081` 단계 6 — 재굽기 창에서 **Vercel 콘솔의 세 화면이 실제 데이터로** 뜨는지 라이브 확인

# Status

done (2026-10-04 UTC — 19차 창: AC-0·1·3·4·5 닫힘, AC-2 는 화면만 — 다음 창에서 라우트 로그와 함께 재측정)

# Owner

monorepo

# Task Tags

- demo
- verification
- platform-console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 측정 티켓. 🔴 데모 기동·재굽기·`terraform apply` 는 소유자 승인 대상.
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.**

---

# Dependency Markers

- **선행**: `TASK-MONO-757` 머지. 🔴 그리고 **757 이후 커밋으로 구운 AMI** — 데모 백엔드는 구워진 클론에서 돈다(재굽기 전까지 데모에 없다). 콘솔 자체는 Vercel 이라 302·303 머지 시점에 이미 배포된다.
- **알림**: `TASK-MONO-648`(포트폴리오 캡처, in-progress) — 저하 화면 셋이 실제 화면이 되면 다시 찍을 수 있다(AC-4).

# Goal

`console.hubwang.com` 에서 운영 개요(`/dashboards/overview`) · 도메인 상태(`/dashboards/health`, `/`) · 알림 벨이 `bffUnavailable` / 502 없이 실제 도메인 데이터로 뜨는 것을 결과 상태로 확인한다.

# Scope

## In Scope

- 데모 기동 1회(다른 재굽기 창과 합칠 수 있으면 합친다) · 화면 확인 · 도메인 하나를 내린 대조군

## Out of Scope

- 코드 변경(발견은 새 티켓)

# Acceptance Criteria

- [x] **AC-0 (게이트)** — 데모 AMI 의 구운 커밋이 757 머지 커밋의 자손이다(`tfvars` 의 AMI → 그 AMI 의 커밋을 **다시 읽는다** — 목록을 물려받지 않는다). 아니면 착수하지 않는다.
- [x] **AC-1** — 운영자 로그인 뒤 세 화면이 실제 값으로 뜬다 — 화면 글자 + 같은 시각의 console-web 라우트 응답(200, 카드 `status` 값)을 함께 적는다. 🔴 클라이언트 렌더 화면은 SSR HTML 로 판정하지 않는다.
- [ ] **AC-2** — 🔴 대조군: 도메인 하나(예: scm)를 내린 상태에서 운영 개요는 200 이고 그 카드만 열화.
- [x] **AC-3** — 데모 호스트에 console-bff 컨테이너가 없다(`docker ps`). 🔵 2026-10-02 UTC (757 이 덧붙임): 757 이 `console` 을 데모 **도메인** 목록에서 뺐다(FULL·CORE·COMPOSE·DOWN_ORDER — 그 compose 엔 데모가 띄울 서비스가 하나도 남지 않아 `up -d` 가 `no service selected` 로 실패한다는 것을 실측했다). 그래서 `console` 프로젝트 컨테이너 자체가 **0개**여야 한다. 루트 디스크를 재사용한 경우 옛 `console` 프로젝트 컨테이너가 남아 있으면 이제 아무것도 그것을 내리거나 재지 않는다 — 있으면 적고 수동으로 지운다.
- [x] **AC-4** — `TASK-MONO-648` 에 «저하 화면 셋이 실제 화면이 됐다 — 다시 찍을 수 있다» 를 알린다(그 티켓이 in-progress 면 소유 세션에 남길 문장).
- [x] **AC-5** — 🔵 2026-10-02 UTC 고쳐 씀(757): `infra/demo/console-vercel.override.yml` 은 757 이 **파일째** 지웠다(억제할 서비스가 남지 않았다). 대신 확인할 것: 론처의 콘솔 카드가 iam 기동만으로 «준비됨» 이 되는가(`BUNDLES[console]=iam` — `projects.sh` 와 Lambda `handler.py` 둘 다. Lambda 쪽은 **`terraform apply` 가 선행**이다 — 소유자 몫).

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md` § Verification
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9

# Edge Cases

- Vercel 배포가 302·303 이후 커밋인지 먼저 확인한다(머지 + CI 초록 ≠ 배포됨).
- 데모 주소는 부팅마다 바뀐다 — console-web 서버의 백엔드 해석기가 새 주소를 쓰는지(`@demo/backend-resolver`).

# Failure Scenarios

1. 옛 AMI 로 띄워 «안 고쳐졌다» 와 «아직 안 실렸다» 를 구별하지 못한다(AC-0).
2. 화면 HTML 만 보고 판정해, 클라이언트에서 열화로 바뀌는 것을 놓친다.

# 19차 창 (2026-10-04 UTC · 인스턴스 i-08d452973ebf789be · AMI ami-00815e1f9614cda90 · 커밋 2a49dfb48)

| AC | 판정 | 근거 |
|---|---|---|
| AC-0 | ✅ | AMI 태그 `RepoCommit=2a49dfb48`(AWS 되읽음) — `git merge-base --is-ancestor 108c26461(757) 2a49dfb48` 참. tfvars `ami_id` = 핀 = 인스턴스가 무는 AMI(`check-ami-generation.sh --with-aws` rc=0) |
| AC-1 | ✅ | 운영자(`demo-corp`) 로그인. **overview** 화면(06:34Z): IAM 0 · WMS 1 · SCM 0 · ERP 3 · ecommerce 0 · Finance `MISSING_PREREQUISITE`(기본 finance 계정 미설정 — 원래 있는 안내) · 상태 요약 «정상 6 / 6». 같은 창 라우트 로그(Vercel, `console_composition_leg`): `operator-overview` 06:38:11Z 요청 `07c23af0` — erp·scm·wms·iam·ecommerce `status=ok`(378–397ms) · finance `forbidden/MISSING_PREREQUISITE`. **health** 화면(06:35Z) 6 도메인 `UP` · 라우트 `domain-health` 6 leg `ok`. **알림함**(상단 바 종): 목록 열림 · 알림 클릭 → 배지 감소(소유자 관찰). 라우트: `notifications-inbox` erp `ok` → `notifications-read` erp `ok`(06:37:17Z `bd06d218`, 06:37:22Z) → 다시 inbox `ok` |
| AC-2 | ⚪ 화면만 | 06:21:03Z(기준 시각) ecommerce 가 아직 `booting` 일 때 overview 가 그 카드만 `DOWNSTREAM_ERROR` · 상태 요약 «점검 불가 1» · 나머지 다섯 카드 정상(소유자 화면 붙여넣기). 🔴 그 요청의 라우트 로그는 Vercel 에서 **찾지 못했다**(06:19–06:24 조회에 06:22:24Z 부터만 있음) ⇒ AC 가 요구하는 «같은 시각의 라우트 응답 200» 이 없다. 다음 창에서 scm 하나를 내리고 화면+라우트 로그를 함께 잰다 |
| AC-3 | ✅ | SSM 읽기(05:1xZ): `docker ps --filter label=com.docker.compose.project=console` → **0** · 이름에 console 포함 0 · 실행 96 · unhealthy 0. 인스턴스가 교체돼 루트 디스크 재사용 없음 |
| AC-4 | ✅ | `TASK-MONO-648`(in-progress)에 알림 문장을 남겼다(그 파일 끝 «TASK-MONO-758 알림») |
| AC-5 | ✅ | `/bundles` 폴링 05:09:12Z: `console=ready`(domains `["iam"]`) — 같은 시각 ecommerce·erp·finance·scm·fan 은 `booting`. Lambda `handler.py` 는 apply 로 실림(`AMI_REPO_COMMIT=2a49dfb48`) |

🔵 곁관찰 (AC 밖, 판정에 안 씀):
- IAM 카드 «전체 계정 0» 은 IAM 이 `X-Tenant-Id: demo-corp` 로 `GET /api/admin/accounts?page=0&size=1` 에 `totalElements=0` 을 답한 값이다(화면 파서 정상). 데모 계정이 소비자 풀에 있다면 0 이 맞을 수 있다. DB 읽기(`account_db.accounts` 테넌트별 집계)는 분류기에 막혀 못 쟀다 — 다음 창에서 같은 질문.
- `platform@demo.com`(테넌트 `fan-platform`)으로 들어간 세션에서 `notifications-inbox` erp leg 가 `degraded/PERMISSION_DENIED` → `notification_inbox_degraded` — 303 의 설계대로(401 아닌 도메인 실패 = 저하).

---

## CORRECTION (2026-10-05 UTC) — 20차 창 판정 (2026-10-04 UTC · i-0c4859442f56d70e0 · ami-0d78d476824493d77 · f0927bcd0)

> 위 AC 목록의 «AC-2 `[ ]`» 와 Status 괄호의 «AC-2 는 화면만 — 다음 창에서 재측정» 은 **이제 사실이 아니다.** 동결 파일이라 체크박스는 고치지 않고 여기서 닫는다. 이 절이 현재 상태다. 분석=Opus 5.5.

**창.** 20차 AMI 창(2026-10-04 UTC 14:5x–15:49), 인스턴스 `i-0c4859442f56d70e0`, AMI `ami-0d78d476824493d77`(구운 커밋 `f0927bcd0`, 핀 #4151, provenance `ami-tag`). 콘솔 = Vercel `console.hubwang.com`, main ≥ `60b97d178`. AC-0 의 술어(구운 커밋이 757 머지 `8e8feacf1` 의 자손)는 이 AMI 에서도 참이다(`git merge-base --is-ancestor 8e8feacf1 f0927bcd0`).

### AC-2 — 도메인 하나(scm)를 내린 대조군

| 시각 (UTC) | 무엇 | 관측 |
|---|---|---|
| 15:28:28 | scm 묶음 정지 | `POST /bundle/stop {"bundles":["console-scm"]}` → 15:28:46 scm 컨테이너 **0** |
| 15:33:58–15:34:00 | 도메인 상태 라우트 `domain-health` (requestId `0a13b147-9613-4e49-b6a7-d776e7b74e76`) | scm `degraded(DOWNSTREAM_ERROR)` · iam · ecommerce · wms · erp · finance `ok` |
| 15:34:00 | (무효) `platform@demo.com` 세션으로 연 첫 시도 | 카드가 `forbidden` — **잘못된 계정**(팬 전용으로 묶인 데모 플랫폼 운영자)이라 판정에 쓰지 않았다. 그 화면의 배너 문제는 새 티켓으로 뺐다(아래 곁발견) |
| 15:36:07.080Z | 운영 개요 화면 — `demo@demo.com` / 테넌트 `demo-corp` | IAM 회원 계정 0 · WMS 재고 행 수 1 · **SCM «하위 서비스에서 오류가 발생했습니다 / 사유: DOWNSTREAM_ERROR / 다시 시도»** · Finance `MISSING_PREREQUISITE`(19차 AC-1 에도 있던 기존 안내) · ERP 활성 부서 3 · ecommerce 상품 0 · 상태 요약 **«정상 5 · 주의 0 · 점검 불가 1»** |
| 같은 시각 | 같은 요청의 Vercel 라우트 로그 `operator-overview` (requestId `8582d51c-51c0-4644-9737-c8df0e7eecd0`) | finance `forbidden(MISSING_PREREQUISITE)` · **scm `degraded(DOWNSTREAM_ERROR)`** · ecommerce · iam · wms · erp `ok` |
| 15:36–15:38 | scm 묶음 재기동 | (`TASK-MONO-697` 의 scm 요약 카운터가 여기서 0 부터 다시 센다) |

⇒ **AC-2 ✅.** 도메인 하나가 내려간 상태에서 운영 개요는 응답했고(라우트가 6 leg 의 합성 본문을 냈고 화면이 그것을 정상 카드 다섯 + 열화 카드 하나로 그렸다), **열화는 scm 카드 하나뿐**이다. 19차의 공백(«같은 시각의 라우트 응답을 Vercel 에서 못 찾음»)은 같은 requestId 의 화면 + 라우트 로그 짝으로 메워졌다.
- 🔵 finance 의 `forbidden(MISSING_PREREQUISITE)` 은 scm 을 내리기 전부터 있던 상태이고(19차 AC-1 «정상 6 / 6» 에서도 같은 값), 상태 요약도 그것을 «점검 불가» 로 세지 않는다 ⇒ «그 카드만 열화» 를 깨지 않는다.
- ⚪ 라우트 로그 줄에 HTTP 상태코드가 따로 찍혔는지는 전달받은 기록에 없다. 200 의 근거는 «라우트가 leg 별 결과를 담은 합성 본문을 냈고 화면이 그 본문을 카드로 그렸다» 이다(계약 § 2.4.9 — all-down 도 200 이고, 합성 라우트가 실패하면 화면은 카드 대신 전면 오류를 그린다).

### 곁발견 (판정에 안 씀 → 새 티켓)

- 15:34:00 무효 시도에서, 모든 카드가 `forbidden` 일 때 상단 배너 «모든 도메인의 개요 정보를 일시적으로 불러올 수 없습니다 … 잠시 후 다시 시도»(로그 `console_composition_all_down`)가 떴다 — 원인은 권한인데 장애처럼 읽힌다 → **`TASK-PC-FE-307`**.
- SCM «스냅샷 행 수 0»(카드는 `ok`)은 19·20차 모두 같다 — 재고 가시성 투영이 비어 있다 → **`TASK-MONO-760`**.
- scm 재기동(15:36–15:38) 동안 web-store 에 «데모 서버가 켜지는 중입니다…» 배너가 떴다(스토어 묶음은 ready) → **`TASK-FE-104`**(ecommerce).

### 4차원 (close chore)

이 티켓은 **측정 티켓**이다(`ADR-MONO-081` 단계 6) — **자기 구현 PR 이 없다.** (a)–(c) 는 이 티켓이 재는 단계 PR 에 대해 적는다. 이 티켓의 PR 을 지어내지 않는다.

| 차원 | 결과 |
|---|---|
| (a) MERGED | `TASK-PC-FE-302` #4118 `f98ece821` · `TASK-PC-FE-303` #4122 `bbdd3990f` · `TASK-MONO-757` #4127 `8e8feacf1` — 셋 다 `state=MERGED` |
| (b) origin/main 조상 | 셋 다 `git merge-base --is-ancestor <sha> origin/main` 참(origin/main `92a6320eb`) · 셋 다 20차 AMI 커밋 `f0927bcd0` 에도 포함 |
| (c) 실패 체크 | #4118 69건 · #4122 66건 · #4127 68건 — FAILURE/CANCELLED/TIMED_OUT **각 0** |
| (d) `# Acceptance Criteria` | AC-0 · AC-1 · AC-3 · AC-4 · AC-5 `[x]`(19차) · **AC-2 = 이 절에서 닫힘** — 동사 «도메인 하나를 내린 상태에서 운영 개요는 200 이고 그 카드만 열화» 를 화면 + 같은 requestId 라우트 로그로 확인 |

⇒ **`review/` → `done/`.**
