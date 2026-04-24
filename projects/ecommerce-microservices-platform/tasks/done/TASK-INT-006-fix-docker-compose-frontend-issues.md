# Task ID

TASK-INT-006-fix

# Title

TASK-INT-006 리뷰 이슈 수정 — 프론트엔드 Dockerfile 빌드 실패, restart 정책 누락, NEXT_PUBLIC 환경변수

# Status

done

# Owner

backend

# Task Tags

- deploy
- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

TASK-INT-006 리뷰에서 발견된 3건의 이슈를 수정한다.

---

# Scope

## In Scope

### 이슈 1: 프론트엔드 Dockerfile `public/` 디렉토리 미존재로 빌드 실패

- web-store, admin-dashboard 모두 `public/` 디렉토리가 존재하지 않음
- Dockerfile의 `COPY --from=builder /app/apps/web-store/public ./public` 구문이 빌드 시 실패함
- `public/` 디렉토리가 없어도 빌드가 성공하도록 수정 필요

### 이슈 2: `restart` 정책 미설정

- 태스크 Failure Scenario에 "OOM Kill 시 자동 재시작 (restart: unless-stopped)"이 명시되어 있음
- 현재 구현에 어떤 서비스에도 `restart` 정책이 설정되지 않음
- 모든 서비스에 `restart: unless-stopped` 추가 필요

### 이슈 3: `NEXT_PUBLIC_API_URL` 환경변수 비효과적

- Next.js에서 `NEXT_PUBLIC_*` 접두어 환경변수는 **빌드 타임**에 치환됨
- docker-compose의 `environment`에 설정해도 런타임 클라이언트 사이드 코드에 반영되지 않음
- 빌드 타임 ARG로 전달하거나, 런타임에 서버 사이드에서 사용할 변수는 `NEXT_PUBLIC_` 접두어 없이 설정해야 함

## Out of Scope

- 리소스 제한 수치 조정
- 기타 TASK-INT-006 변경 사항 재작업

---

# Acceptance Criteria

- [ ] `public/` 디렉토리가 없어도 프론트엔드 Dockerfile 빌드가 실패하지 않는다
- [ ] 모든 서비스 컨테이너에 `restart: unless-stopped`가 설정된다
- [ ] `NEXT_PUBLIC_API_URL`이 빌드 타임에 올바르게 주입된다
- [ ] `docker compose config` 문법 검증을 통과한다

---

# Related Specs

- `specs/platform/testing-strategy.md`

# Related Skills

_(없음)_

---

# Related Contracts

_(없음)_

---

# Target Service

- `docker-compose.yml`
- `apps/web-store/Dockerfile`
- `apps/admin-dashboard/Dockerfile`

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- `public/` 디렉토리가 나중에 생성되었을 때도 정상 동작해야 함

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- `docker compose config` 로 문법 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
