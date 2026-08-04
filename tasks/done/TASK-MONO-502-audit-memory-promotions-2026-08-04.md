# Task ID

TASK-MONO-502

# Title

`/audit-memory` 2026-08-04 세션 — 사용자 승인 6건 공통 규칙 승격 (TEMPLATE.md 3건 + platform/coding-rules.md 1건 + check-index-queue-drift.sh 가드 확장 1건 + 유저레벨 CLAUDE.md 1건)

# Status

done

# Owner

monorepo

# Task Tags

- docs
- ci
- config

---

# Goal

`/audit-memory` 감사(2026-08-04)가 발굴한 "공통 규칙 후보" 9건 중 사용자가 개별 확인 후 승격 승인한 6건을 정경 문서/스크립트에 반영한다. 나머지 3건(저확신)은 메모리에만 유지하기로 사용자가 결정 — 이 태스크의 범위 밖.

이 저장소의 기존 카탈로그+디테일 분리 관행(`feedback_auto_worktree_per_task` 등 다수 선례)을 따라, 각 원본 메모리 파일은 삭제하지 않고 "Promoted" 각주만 추가해 인시던트 디테일 레코드로 유지한다.

---

# Scope

## In Scope

1. **`TEMPLATE.md § Local Network Convention`** — 새 `### Known operational traps` 서브섹션에 3종 추가:
   - Traefik이 non-healthy 컨테이너를 조용히 스킵 + busybox `wget localhost`가 `::1`로 풀려 실패 + Traefik v3.2가 Docker Engine 29와 API 버전 협상 실패.
   - Secure 쿠키가 HTTP(비-`localhost`) 오리진에서 저장 자체가 안 되는 구조적 제약 — `*.local` 컨벤션 전체에 걸림, 기존 문서의 `localhost:<port>` 콜백 등록 관행의 이유를 명시.
   - `NEXT_PUBLIC_*`가 Next.js 빌드타임에 인라인되는 범용 동작 — 3개 프런트(console-web/web-store/fan-platform-web) 전부 영향 + 프리베이크 이미지(AMI) 이식성 함의.
2. **`platform/coding-rules.md § TypeScript Rules`** — BFF/프록시가 204/205/304 응답 재구성 시 Fetch null-body-status 규격을 놓치면 500으로 변질되는 패턴, 1개 규칙 항목 추가.
3. **`scripts/check-index-queue-drift.sh`** — 기존 set-equality 검사(ready/in-progress/review)와 독립된 신규 "중복 헤딩" 검사 추가: 파일 내 동일 `## <name>` 헤딩이 2회 이상 나타나면(예: `## done` 중복) DRIFT로 보고. 헤더 주석·selftest 픽스처·성공 메시지 갱신.
4. **유저레벨 `~/.claude/CLAUDE.md § Windows Shell Environment`** — `cmd | tail` 류 파이프가 마지막 명령(포매터)의 종료코드를 반환해 실패를 성공으로 위장하는 패턴, 1줄 추가. (저장소 외부 파일 — 이 저장소의 커밋/PR 대상 아님, 별도로 직접 반영 완료.)
5. 위 4개 문서/스크립트 반영과 함께, 승격 대상 원본 메모리 6개 파일에 "Promoted" 각주 추가(삭제 아님).
6. **부수 발견**: 신규 가드가 최초 실행에서 `projects/fan-platform/tasks/INDEX.md`의 기존(미검출) 중복 `## done` 헤딩을 실제로 발견 — 같은 PR에서 병합 정리.

## Out of Scope

- 저확신 후보 3건(`feedback_pr_bundling`의 impl-PR 번들링 규칙 / `feedback_verification_depth_scales_with_change_risk`의 검증깊이 차등 / `env_aws_toolchain_windows_host`의 winget PATH 미갱신) — 사용자가 메모리 유지로 결정, 승격 안 함.
- `check-index-queue-drift.sh`가 `done/` 섹션의 **내용**(set-equality)까지 검사하도록 확장하는 것 — 스크립트 자체 헤더 주석이 이미 측정 근거를 들어 명시적으로 배제한 설계 결정이며, 이 태스크는 그 결정을 뒤집지 않는다. 오직 "같은 헤딩이 파일 내 2회 이상"이라는 독립적·구조적 검사만 추가.

---

# Acceptance Criteria

- [x] `TEMPLATE.md`에 3개 트랩이 `### Known operational traps` 서브섹션으로 추가됨.
- [x] `platform/coding-rules.md`에 BFF null-body-status 규칙 1건 추가됨.
- [x] `check-index-queue-drift.sh --selftest`가 신규 DUP 픽스처를 포함해 통과.
- [x] `check-index-queue-drift.sh`(전체 실행)가 현재 저장소 트리에서 0 finding으로 통과(신규 가드가 찾아낸 fan-platform 기존 중복 헤딩을 같은 PR에서 정리했으므로).
- [x] 6개 원본 메모리 파일에 "Promoted" 각주 추가(파일 삭제 없음).
- [ ] CI green, 3-dim 검증 후 머지.

---

# Related Specs

- `TEMPLATE.md § Local Network Convention`
- `platform/coding-rules.md`
- `scripts/check-index-queue-drift.sh` (자체 헤더 주석이 설계 근거의 SoT)

# Related Contracts

없음 — 문서/CI 스크립트/유저레벨 설정 변경만, API·이벤트 계약 무관.

---

# Edge Cases

- 신규 DUP 가드가 `done` 섹션뿐 아니라 파일 내 **임의의** 중복 헤딩을 검사하므로, 향후 다른 섹션 이름이 실수로 중복될 경우도 동일하게 잡는다(설계상 의도, `done` 전용이 아님).
- 승격된 규칙이 원본 메모리의 전부가 아니다 — 인시던트 디테일(정확한 에러 메시지, 재현 절차, 특정 PR 번호 등)은 메모리 파일에만 남는다. 이 태스크는 그 분리를 의도적으로 보존한다.

# Failure Scenarios

- `check-index-queue-drift.sh`의 awk 로직 오류로 기존 `ready`/`in-progress`/`review` 검사가 회귀하면 CI가 대량 오탐/누락을 낼 수 있음 — `--selftest`가 기존 10개 ID 단정 + 파서 픽스처를 전부 유지한 채 신규 단정만 추가했으므로 회귀 시 selftest가 먼저 잡는다.

---

# Test Requirements

- `bash scripts/check-index-queue-drift.sh --selftest` GREEN.
- `bash scripts/check-index-queue-drift.sh`(전체) GREEN.
- CI `INDEX queue drift` 체크 GREEN.

---

# Definition of Done

- [x] 4개 문서/스크립트 변경 완료
- [x] 원본 메모리 6개 "Promoted" 각주 추가
- [x] fan-platform 기존 중복 헤딩 정리
- [ ] PR 머지 + 3-dim 검증 + close-chore
