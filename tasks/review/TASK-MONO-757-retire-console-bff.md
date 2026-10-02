# Task ID

TASK-MONO-757

# Title

`ADR-MONO-081` 단계 5 — **console-bff 삭제** 와 정리

# Status

review (2026-10-02 UTC — AC-6 은 머지 뒤 첫 nightly)

# Owner

monorepo

# Task Tags

- cleanup
- ci
- infra

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 삭제와 참조 정리. 🔴 `scripts/` 파일을 지우거나 고치면 **모든 가드**를 돌린다(`CLAUDE.md` — 개수가 다른 가드의 입력이다).
>
> ⏳ **DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.**

---

# Dependency Markers

- **선행**: `TASK-PC-FE-302` · `TASK-PC-FE-303` · `TASK-MONO-756` 머지 **그리고** 그 뒤 첫 `nightly-e2e.yml` 런이 초록(라이더 R3).
- **후속**: `TASK-MONO-758`(라이브 확인).

# Goal

console-bff 를 저장소에서 지우고, 그것을 전제로 한 설정·가드·문서를 정리한다(`ADR-MONO-081` D4 목록).

# Scope

## In Scope

- `projects/platform-console/apps/console-bff/**` · `settings.gradle` include
- compose: `projects/platform-console/docker-compose.yml` · `docker-compose.e2e.yml` · `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` · `infra/demo/*`(`demo.env` 의 `CONSOLE_BFF_*` · `projects.sh` · `iam-traefik.override.yml` · `console-vercel.override.yml` «알려진 한계» 절) · `infra/traefik/docker-compose.yml` 주석
- CI: `.github/workflows/ci.yml`(경로 필터 · `:console-bff:check` · 통합 시험 잡) · `nightly-e2e.yml` · `federation-hardening-e2e.yml`
- 가드: `scripts/check-gateway-drift.sh` · `check-service-map-drift.sh` · `infra/demo/verify-demo-wrapper.sh`
- 데모 AMI 서비스 수(`infra/demo/aws/packer/demo-ami.pkr.hcl`)
- 계약: `console-integration-contract.md` § 2.4.9 의 **⏳ console-bff-era 표시 17곳**(`TASK-MONO-755` 가 달았다 — 각 절을 § 2.4.9.0 규칙으로 다시 쓰거나 지우고, § 2.4.9.0 머리의 «현재 생산자» 문단도 정리) · `notification-inbox-contract.md` § 4 의 «처음엔 console-bff 에 지었다» 이력 문장 · `console-web/architecture.md` 의 «console-bff fan-out 과 달리» 대비 서술
- 문서: `PROJECT.md`(Service Map 행 삭제 · `service_types` 에서 `rest-api` — ADR-013 § D5 가 넣은 근거가 사라졌음을 적는다) · `console-bff/architecture.md` 삭제 · `jwt-standard-claims.md` · `libs/java-security` `AllowedAudiencesValidator` javadoc 의 console-bff 사례 · `README.md` · `docs/project-overview.md` · `TEMPLATE.md` · `scripts/dev-setup.*`

## Out of Scope

- `AllowedAudiencesValidator` 자체(다른 서비스가 쓴다) · 도메인 서비스의 `ServiceLevelOAuth2Config` 동작(주석만)

# Acceptance Criteria

- [x] **AC-0 (게이트)** — 302·303·756 이 `origin/main` 에 있고, 그 뒤 첫 nightly 런 id 와 결론(`success`)을 이 파일에 적었다. 아니면 착수하지 않는다.
- [x] **AC-1** — `git grep -n "console-bff"` 의 남은 줄이 전부 **역사 기록**(ADR · done/review 티켓 · CHANGELOG 류)이고, 그 목록을 이 파일 § 결과에 붙인다. 활성 코드·설정·가드·활성 티켓에는 0.
- [x] **AC-2** — 🔴 `bff_*` 지표 소비자 grep(`infra/` · 대시보드 JSON · 알림 규칙 · `.claude/skills/**/observability-query`) = 0. 남아 있으면 지우기 전에 R1 로그로 옮긴다.
- [x] **AC-3** — `scripts/` 가 바뀌었으므로 **모든 가드**를 스테이지 뒤 돌려 rc 를 적는다(필수 4종만이 아니다 — `check-ls-files-guard-count.sh` 류가 개수로 문다).
- [x] **AC-4** — `./gradlew projects` 에 console-bff 가 없고 `./gradlew check` 의 platform-console 범위가 초록.
- [x] **AC-5** — 다른 티켓의 의무(`ADR-MONO-081` § ACCEPT 가 만든 새 의무): `TASK-MONO-672` 의 «console-bff `aud`(712)» 측정 행에 «대상 은퇴 — `TASK-MONO-757`» · `TASK-MONO-697` Out of Scope 의 console-bff 항목 한 줄 정리. 🔴 그 티켓이 그 사이 in-progress 면 소유 세션에 남길 문장만 적는다.
- [ ] **AC-6** — 머지 뒤 다음 nightly 초록 확인.

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md` D4 · R1 · R3
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D5
- `projects/platform-console/PROJECT.md`

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

# Edge Cases

- `docker-compose.e2e.yml` 의 `CONSOLE_BFF_URL` 을 console-web 이 아직 읽고 있으면 302·303 이 덜 끝난 것이다 — 이 티켓에서 고치지 말고 되돌아간다.
- 데모 AMI 는 재굽기 전까지 옛 compose 를 들고 있다 — 삭제가 데모에 닿는 것은 758.

# Failure Scenarios

1. 필수 가드 4종만 돌리고 푸시해, 가드 개수 가드가 main 을 빨갛게 만든다(`TASK-MONO-650` 선례).
2. CI 경로 필터에서 console-bff 항목만 지우고 잡은 남겨, 존재하지 않는 모듈의 `:check` 가 실패한다.
3. `service_types` 를 그대로 두어 분류(`rest-api`)가 없는 서비스를 가리킨다.

# 결과 (2026-10-02 UTC)

## AC 판정

| AC | 판정 | 근거 |
|---|---|---|
| AC-0 | ✅ | 302(#4118 `f98ece821`) · 303(#4122 `bbdd3990f`) · 756(#4118 에 흡수) 모두 `origin/main`. 그 뒤 첫 nightly = **`37024617764`**(303 머지 커밋의 main push 런) **success** — `Platform Console E2E full-stack` 가 실행되어 success |
| AC-1 | ✅ (분류) | 아래 § 남은 `console-bff` 줄. **코드·설정·가드·계약·문서·데모 인프라·스킬 = 0**. 남은 줄은 전부 `tasks/` 와 `docs/adr/` — 역사 기록이거나, 활성 티켓 안의 **그때의 측정 기록**(고쳐 쓰면 기록 위조), 또는 «없음» 을 재는 AC |
| AC-2 | ✅ | `bff_*` 소비자 grep(`infra/` · `scripts/observability` · 대시보드 · 알림 규칙 · `.claude/skills`) = 0. 유일한 소비자였던 `observability-query` SKILL 의 지표 행을 지우고 트레이스 트리를 콘솔 서버 기준(루트 → `console.composition.leg` → 생산자)으로 고쳤다. 대체물 = R1 구조화 로그 `console_composition_leg` |
| AC-3 | ✅ | 스테이지 뒤 **모든 가드** — ci.yml 의 가드 호출 전부 + `scripts/check-*` 전부(인자 없이), 총 71건. 아래 § 가드 |
| AC-4 | ✅ | `./gradlew projects` rc=0 — platform-console 모듈 **0**(console-bff 없음). platform-console 에는 이제 Gradle 범위가 없다(console-web 은 pnpm). 주석을 고친 14 모듈 compileJava/compileTestJava rc=0 |
| AC-5 | ✅ | `TASK-MONO-672` 측정 행(712 `aud`) → «대상 은퇴 — `TASK-MONO-757`» · `TASK-MONO-697` Out of Scope 의 그 항목 정리. 둘 다 `ready`(소유 세션 없음) |
| AC-6 | ⏳ | 머지 뒤 첫 nightly |

## 바뀐 것 (요약)

- **삭제**: `apps/console-bff/**` · `specs/services/console-bff/architecture.md` · `settings.gradle` include · `infra/demo/console-vercel.override.yml`.
- **CI**: `ci.yml`(경로 필터 항목 · `:console-bff:check` 단계 · 통합 시험 잡 · 요약 단계 참조 · backend 잡의 platform-console 트리거) · `nightly-e2e.yml`(bootJar · 업로드/복원 · 기동 목록) · `federation-hardening-e2e.yml`(같은 것 + 안 쓰이던 `E2E_CONSOLE_BFF_URL`). 업로드 공통 접두사(`projects/`)가 그대로라 복원 경로는 안 바뀐다.
- **compose**: 본체 · e2e · federation 에서 서비스 · `CONSOLE_BFF_*` · `CONSOLE_BFF_URL` 제거. federation 의 console-web 은 BFF 를 통해 간접으로 받던 생산자 기동 의존을 직접 선언했다. 셋 다 `docker compose config` rc=0.
- **콘솔 코드**: 샘플 코어 이름 `'console-bff'` → **`'console-composition'`**(ADR-081 D1 이 «단계 티켓이 정한다» 로 남긴 결정 — 존재하지 않는 서비스 이름을 원장에 남기지 않는다). 원장 · 픽스처 키 · 4 라우트 · 시험을 함께. 거짓이 된 서술(«Vercel 은 이 패널에 영원히 못 닿는다») 정리 — Vercel 실측은 758.
- **계약**: `console-integration-contract.md` § 2.4.9 — § 2.4.9.0 을 본문으로 접고 ⏳ 표시 **17곳 전부** 처리(재작성 14 · 삭제 3). `notification-inbox-contract.md` · `jwt-standard-claims.md` · `error-handling.md` · `console-web/architecture.md`.
- **`PROJECT.md`**: `service_types: [frontend-app]` — `rest-api` 는 ADR-013 § D5 가 «BFF 가 오면» 넣은 값이고 그 근거가 사라졌다(본문에 적었다). Service Map 행 삭제.
- **가드 · 스크립트 · 스킬**: `check-gateway-drift.sh` · `check-service-map-drift.sh` 주석/안내문, `dev-setup.*`, `console-demo-up.ps1`, observability 스킬.

## 🔴 티켓이 예상하지 못한 것 — 데모의 `console` 도메인 (소유자 결정 2026-10-02)

console-bff 를 지우면 `projects/platform-console/docker-compose.yml` 의 서비스는 `console-web` 하나뿐이고, 데모 체인은 그것을 Vercel 로 억제한다 ⇒ **데모의 `console` 도메인 = 서비스 0개.** 실측: `docker compose … --dry-run up -d` → rc=1 **`no service selected`**. 재굽기 뒤 데모 부팅의 console 단계가 실패하고, `demo-status.sh` 가 console 을 영원히 `down` 으로 내 론처의 콘솔 묶음이 준비되지 않는다.

소유자 선택: **`console` 을 데모 도메인에서 뺀다.**
- `projects.sh`: COMPOSE · FULL · CORE · DOWN_ORDER · DEPS 에서 `console` 제거 (FULL 7 · CORE `iam ecommerce wms`). **방문자 묶음 `console` 은 남고 `iam` 으로 풀린다** — Vercel 콘솔이 데모 호스트에서 쓰는 것은 로그인 홉뿐이다. 업무 화면은 `console-*` 애드온 그대로.
- Lambda `handler.py`: `DOMAINS` 에서 `console` 제거 · `BUNDLES["console"]=("iam",)`. 시험 112 OK(새 시험 포함). 🔴 **실 Lambda 반영 = `terraform apply` — 소유자 몫.**
- 론처 `index.html`: 콘솔 카드의 도메인 = `iam`, 고급 영역 `DOMAINS` 에서 console 제거.
- `verify-demo-wrapper.sh`: (u) 은퇴(읽던 BFF `application.yml` 이 사라졌고, console-web 은 이제 어떤 데모 체인에도 없다 — demo.env 의 console 키도 함께 제거, 사유는 그 자리에 기록) · (z31) 은퇴(억제할 것이 없다) → 대신 «console compose 가 `NOT_DEMO_COMPOSE` 에 사유와 함께 있고 어느 데모 체인에도 없다» 칸 · 억제 축 바닥 3→2. 전체 **rc=0**(정적 60칸).
- AMI: 부트 jar 42→41(유도값 — 다음 굽기의 `expected=` 가 실측).
- 사용 예의 `demo-up.sh … console` 을 활성 문서 5곳 + 가드 안내문 1곳에서 고쳤다(`console` 은 이제 «알 수 없는 도메인» 으로 거부된다).
- 🔴 루트 디스크를 재사용하는 재굽기라면 옛 `console` 프로젝트 컨테이너가 남아도 이제 아무것도 그것을 내리거나 재지 않는다 → `TASK-MONO-758` AC-3 에 확인 항목으로 덧붙였다.

## 가드 (AC-3)

ci.yml 의 가드 호출 전부 + `scripts/check-*` 전부(인자 없이), 스테이지 뒤 실행 — **71건**.

- 1차: rc≠0 **4건**.
  - `check-walkthrough-ledger-drift.sh`(+ `--self-test`) — 🔴 **이 PR 이 만든 드리프트**: § 6 의 «Vercel 콘솔 세 패널» 행을 «열림(🟡) — 758 대기» 로 고쳐 쓰면서 이미 done 인 585·627 을 인용 칸에 남겼다. 인용을 열린 `TASK-MONO-758` 하나로 줄이자 **둘 다 rc=0**(자체 시험의 실패 3칸은 실제 원장 드리프트가 원인이었다).
  - `check-erp-single-tenant-ratchet.sh` — 떠 있는 erp MySQL 이 필요한 라이브 래칫(ci.yml 이 안 부른다). **main 에서도 같은 사유로 rc=1**. 다만 그 안내문이 `demo-up.sh iam erp console` 을 권하고 있어(이제 `console` 은 «알 수 없는 도메인») 고쳤다.
  - `check-prerendered-demo-verdict.sh` — `DEMO_API_BASE` + web-store 빌드가 전제(CI 의 frontend-checks 가 그 env 로 부른다). 이 PR 과 무관.
- 수정 뒤 필수 4종 재실행: index-queue-drift 0 · task-id-collision 0 · walkthrough-ledger-drift 0(+self-test 0) · `changes` 는 CI.
- 그 밖 67건 rc=0 — `check-gateway-drift` · `check-service-map-drift` · `check-service-type-drift` · `check-ls-files-guard-count`(+self-test) · `check-required-check-names` · `check-libs-ci-coverage` · `check-fetch-resolution` 포함.
- 데모: `bash infra/demo/verify-demo-wrapper.sh` rc=0(정적 60칸).

## 남은 `console-bff` 줄 (AC-1)

`git grep -n -iE "console-bff|console_bff|CONSOLE_BFF|consolebff"` — `docs/adr/**` 와 `tasks/done/**` 를 빼면 남는 것은 **전부 `tasks/` 안**이다:

| 위치 | 분류 |
|---|---|
| `tasks/INDEX.md` · `projects/*/tasks/INDEX.md` | done/archive 행 = 역사. 활성 구역의 3줄(platform-console backlog 메모 · «직전 착수» 메모, iam ready 머리 메모)도 날짜 박힌 과거 서술 |
| `tasks/ready/TASK-MONO-672` | 과거 창의 **측정 기록**(AC-5 의 행은 «대상 은퇴» 로 닫았다) |
| `tasks/in-progress/TASK-MONO-648` | 다른 세션 소유의 **측정 기록** — 손대지 않았다 |
| `tasks/ready/TASK-MONO-758` AC-3 | «console-bff 컨테이너가 없다» 를 **재는** 조건 — 이름이 있어야 한다 |
| `tasks/ready/TASK-MONO-751` | 755 가 남긴 정정 이력 한 줄 |
| 이 티켓 | 자기 범위 서술 |

## 대조군 · 플레이크 기록

- console-web vitest 를 기본 병렬로 두 번 돌리면 **서로 다른** 시험 1~5개가 5 s 타임아웃으로 실패(`LedgerOpsScreen` · `OperatorsScreen` — 이 PR 이 안 건드린 파일). 단독 실행 67/67 · 워커 축소 전체 328 파일 / 3,690 시험 rc=0. 이 호스트의 알려진 부하 플레이크 축이다(`env_console_web_vitest_flake_unhandled_error`). PR CI 가 판정한다.
- 이 PR 범위 밖으로 본 것(고치지 않음): 방문자 가이드 문구 «개요 = 5개 도메인 요약» 은 실제 카드 6개(iam 포함)와 다르다 · `demo-backend.ts` 가 없는 `scripts/check-console-backend-urls.sh` 를 인용한다 · 로컬 데모 오버레이(`console-demo-up`)의 `scm-gateway` 가 302 이후 base 의 `scm-gateway-service` 와 중복 기동된다.

## 머지 전 e2e dispatch 실측 (2026-10-02 UTC, 브랜치 `mono-757-retire-console-bff`)

| 커밋 | 워크플로 | 런 | 결과 |
|---|---|---|---|
| `ba688eb53` | federation-hardening-e2e | `37048625934` | ✅ success |
| `ba688eb53` | nightly-e2e | `37048619631` | ❌ **Launcher freshness 만** failure — 나머지 잡(Platform Console E2E full-stack 포함) success |
| `950eabf88` (런처 판정기 수정 + main 병합) | nightly-e2e | `37050938638` | ✅ success |
| `950eabf88` | federation-hardening-e2e | `37050942420` | ✅ success |

🔴 1차 Launcher freshness 실패의 원인 — **판정기의 ref 엇갈림**(이 PR 이 런처 `index.html` 을 바꿔서 드러났다): `check-launcher-fresh.sh --self-test` 가 «현재 판» 은 `origin/main` 에서, «이전 판» 은 `HEAD` 에서 구했다. 런처를 바꾼 브랜치로 dispatch 하면 HEAD 의 직전 판이 main 의 현재 판과 같아 «이전 커밋 없음»(exit 2). main 예약 런은 HEAD=REF 라 안 보였다. «이전 판» 도 `$REF` 에서 구하게 고쳤다(로컬 self-test rc=0, 위 2차 런 success).

PR CI(`950eabf88`): 68 개, pending 0, fail 0.

