# TASK-MONO-017 — Import global-account-platform into monorepo

## Goal

`c:/Users/kangdow/dev/project/ai-project/global-account-platform` (standalone repo)를
`projects/global-account-platform/`으로 모노레포에 이관한다.
ecommerce-microservices-platform 이관(TASK-MONO Phase 2~8) 과 동일한 `direct-include` 패턴을 따른다.

## Scope

**In scope:**

1. **Phase 1 — Rules 승격**: global-account-platform 고유 rules 파일 3개를 모노레포 공용층으로 승격
   - `rules/domains/saas.md`
   - `rules/traits/regulated.md`
   - `rules/traits/audit-heavy.md`

2. **Phase 2 — Libs 승격**: global-account-platform libs에서 모노레포 공용 libs보다 앞선 항목 병합
   - `libs/java-common/resilience/ResilienceClientFactory` (+1 class)
   - `libs/java-messaging/event/BaseEventPublisher`, `EventSerializationException` (+2 class)
   - `libs/java-messaging/outbox/OutboxFailureHandler`, `OutboxMetricsAutoConfiguration`,
     `OutboxProperties`, `OutboxSchedulerConfig` (+4 class, TASK-BE-211 결과물)
   - `libs/java-security/pii/PiiMaskingUtils` (+1 class)
   - 위 항목들의 단위 테스트 포함

3. **Phase 3 — git subtree add + settings.gradle**
   - `git subtree add --prefix=projects/global-account-platform <local-path> HEAD --squash`
   - root `settings.gradle`에 global-account-platform 서비스 7개 + tests:e2e include 추가

4. **Phase 4 — 중첩 shared 콘텐츠 정리**
   - `projects/global-account-platform/platform/` 삭제
   - `projects/global-account-platform/rules/` 삭제
   - `projects/global-account-platform/.claude/` 삭제
   - `projects/global-account-platform/libs/` 삭제 (루트 shared libs로 대체)
   - `projects/global-account-platform/scripts/` 삭제 (standalone repo 전용)
   - `projects/global-account-platform/.github/` 삭제
   - `projects/global-account-platform/CLAUDE.md` → `docs/migration-notes.md`로 rename + 이관 이력 추가
   - `projects/global-account-platform/TEMPLATE.md` 삭제
   - `projects/global-account-platform/settings.gradle` 삭제 (root settings가 대체)
   - `node_modules/`, `build/` 등 gitignore 대상 잔재 확인 및 제거

5. **Phase 5 — 통합 배선**
   - root `package.json` — `gap:up/down/ps/logs/docker` 단축 스크립트 추가
   - `docker-compose.yml`에 `${PORT_PREFIX:-3}XXXX` 패턴 적용 (PORT_PREFIX=3 예약됨)
   - `scripts/sync-portfolio.sh` — `PROJECT_REMOTES`에 `global-account-platform → kanggle/global-account-platform` 추가, `PROJECT_TYPES["global-account-platform"]="direct-include"` 추가

**Out of scope:**

- CI (`.github/workflows/`) global-account-platform backend 서비스 추가 — 별도 TASK-MONO-018
- global-account-platform standalone repo (`kanggle/global-account-platform`) 생성·초기화 — Phase 5 sync-portfolio.sh 추가 후 최초 sync 시 수행
- admin-web (Next.js) CI 추가 — 별도

## Acceptance Criteria

- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:compileJava` PASS
- [ ] `./gradlew :projects:global-account-platform:apps:account-service:compileJava` PASS
- [ ] `./gradlew :projects:wms-platform:apps:master-service:compileJava` PASS (wms 회귀 없음)
- [ ] `./gradlew :projects:ecommerce-microservices-platform:apps:auth-service:compileJava` PASS (ecommerce 회귀 없음)
- [x] `projects/global-account-platform/` 하위에 `platform/`, `rules/`, `.claude/`, `libs/`, `scripts/`, `.github/` 디렉토리 없음
- [x] root `rules/domains/saas.md`, `rules/traits/regulated.md`, `rules/traits/audit-heavy.md` 존재
- [x] `scripts/sync-portfolio.sh`에 global-account-platform remote 등록됨

## Related Specs

- `CLAUDE.md` § "Cross-Project Changes"
- `CLAUDE.md` § "Port Namespace Convention"
- `TEMPLATE.md` § "direct-include 통합 절차"
- `tasks/INDEX.md` § "When to Use Root vs Project Tasks"
- `projects/global-account-platform/PROJECT.md`

## Related Contracts

- 없음 (구조 이관, API 계약 변경 없음)

## Edge Cases

- global-account-platform의 `tests:e2e` 모듈은 Testcontainers를 사용할 가능성이 높음 — CI 추가 시 `@Tag("integration")` 필터 확인 필요 (이번 태스크 out of scope)
- `admin-web`은 Gradle이 아닌 pnpm/Next.js — root `settings.gradle`에 포함하지 않음
- `apps/bin/` 은 IntelliJ/Eclipse build 산출물 — .gitignore 확인 후 삭제

## Failure Scenarios

- subtree add 충돌: root `settings.gradle`, `CLAUDE.md` 등이 global-account에도 존재 — Phase 3 이전에 Phase 1·2에서 rule 파일 변경 없이 경로 충돌 없으므로 안전
- libs 충돌: global-account libs 내 java.class 파일이 이미 mono libs에 있는 경우 — Phase 2에서 내용 기반 비교 후 신규 추가분만 복사

## Definition of Done

- [x] Implementation completed
- [ ] Compile checks passing (Acceptance Criteria)
- [ ] PR created
- [x] docs/migration-notes.md 작성됨
