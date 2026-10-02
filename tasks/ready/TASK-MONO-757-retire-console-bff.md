# Task ID

TASK-MONO-757

# Title

`ADR-MONO-081` 단계 5 — **console-bff 삭제** 와 정리

# Status

ready

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
- 문서: `PROJECT.md`(Service Map 행 삭제 · `service_types` 에서 `rest-api` — ADR-013 § D5 가 넣은 근거가 사라졌음을 적는다) · `console-bff/architecture.md` 삭제 · `jwt-standard-claims.md` · `libs/java-security` `AllowedAudiencesValidator` javadoc 의 console-bff 사례 · `README.md` · `docs/project-overview.md` · `TEMPLATE.md` · `scripts/dev-setup.*`

## Out of Scope

- `AllowedAudiencesValidator` 자체(다른 서비스가 쓴다) · 도메인 서비스의 `ServiceLevelOAuth2Config` 동작(주석만)

# Acceptance Criteria

- [ ] **AC-0 (게이트)** — 302·303·756 이 `origin/main` 에 있고, 그 뒤 첫 nightly 런 id 와 결론(`success`)을 이 파일에 적었다. 아니면 착수하지 않는다.
- [ ] **AC-1** — `git grep -n "console-bff"` 의 남은 줄이 전부 **역사 기록**(ADR · done/review 티켓 · CHANGELOG 류)이고, 그 목록을 이 파일 § 결과에 붙인다. 활성 코드·설정·가드·활성 티켓에는 0.
- [ ] **AC-2** — 🔴 `bff_*` 지표 소비자 grep(`infra/` · 대시보드 JSON · 알림 규칙 · `.claude/skills/**/observability-query`) = 0. 남아 있으면 지우기 전에 R1 로그로 옮긴다.
- [ ] **AC-3** — `scripts/` 가 바뀌었으므로 **모든 가드**를 스테이지 뒤 돌려 rc 를 적는다(필수 4종만이 아니다 — `check-ls-files-guard-count.sh` 류가 개수로 문다).
- [ ] **AC-4** — `./gradlew projects` 에 console-bff 가 없고 `./gradlew check` 의 platform-console 범위가 초록.
- [ ] **AC-5** — 다른 티켓의 의무(`ADR-MONO-081` § ACCEPT 가 만든 새 의무): `TASK-MONO-672` 의 «console-bff `aud`(712)» 측정 행에 «대상 은퇴 — `TASK-MONO-757`» · `TASK-MONO-697` Out of Scope 의 console-bff 항목 한 줄 정리. 🔴 그 티켓이 그 사이 in-progress 면 소유 세션에 남길 문장만 적는다.
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
