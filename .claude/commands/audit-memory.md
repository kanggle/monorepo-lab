---
name: audit-memory
description: Audit all auto-memory entries for staleness, contradictions, dangling references, CLAUDE.md duplicates, and common-rule promotion candidates
---

# audit-memory

메모리 전체를 감사하여 문제를 발견·분류하고, 확인 후 수정한다.

## Usage

```
/audit-memory          # 전체 감사 후 보고서 출력 → 확인 → 수정
/audit-memory --dry-run  # 보고서만 출력, 수정하지 않음
```

## Procedure

### Phase 1: 메모리 수집

1. `MEMORY.md` 인덱스를 읽는다.
2. 인덱스에 나열된 모든 메모리 파일을 읽는다.
3. `CLAUDE.md` 전문을 읽는다 (Phase 4·5 비교용).

### Phase 2: 검사 실행

아래 5가지 기준을 **병렬**로 검사한다. 발견된 모든 문제를 수집하되 이 단계에서는 수정하지 않는다.

#### 2-1. Stale (낡은 내용)

- 완료된 작업이 "진행 중" 또는 미래 시제로 남아 있는가?
- 날짜·버전·브랜치명이 현재 git 상태(`git log`, `git branch`)와 일치하는가?
- 해결된 blocker/이슈가 여전히 미해결로 기술되어 있는가?
- 메모리에 언급된 파일·디렉터리가 현재 저장소에 실제로 존재하는가?

> 검증 방법: `git log --oneline -20`, 해당 파일 경로 존재 여부 확인.

#### 2-2. 모순/불일치 (Contradiction)

- 두 메모리 파일이 같은 사실을 서로 다르게 기술하는가?
- `MEMORY.md` 인덱스의 한 줄 요약이 실제 파일 내용과 다른가?
- 날짜·버전·결정 사항 등 사실 정보가 파일 간에 어긋나는가?

#### 2-3. 댕글링 참조 (Dangling Reference)

- `MEMORY.md` 인덱스의 `[Title](file.md)` 링크가 존재하지 않는 파일을 가리키는가?
- 메모리 파일 본문에서 언급한 spec/task/파일 경로가 저장소에 없는가?

> 검증 방법: `Glob` 또는 `Read` 로 각 경로 실재 여부 확인.

#### 2-4. CLAUDE.md 중복 (Duplicate)

- 메모리에 기록된 규칙·정책이 `CLAUDE.md`(또는 `platform/`, `rules/` 등 상위 소스)에 이미 동일하게 명시되어 있는가?
- 중복된 메모리는 삭제 대상이다. 단, 메모리가 CLAUDE.md보다 더 구체적인 예시나 맥락을 추가하는 경우는 중복으로 보지 않는다.

#### 2-5. 공통 규칙 후보 (Promote-to-CLAUDE.md)

- 개인 선호가 아닌 **레포 전체**에 적용할 규칙인데 메모리에만 존재하는가?
- 판단 기준: "이 규칙이 다른 개발자나 다른 AI 세션에도 동일하게 적용되어야 하는가?"
  - Yes → CLAUDE.md 또는 해당 `platform/`/`rules/` 파일 승격 후보
  - No (개인 작업 스타일, 특정 프로젝트 일회성 결정) → 메모리에 유지

### Phase 3: 보고서 출력

검사 결과를 아래 형식으로 출력한다. `--dry-run` 플래그가 있으면 여기서 종료한다.

```
## Memory Audit Report  (YYYY-MM-DD)

### 1. Stale
- [file.md] 설명 — 권장 조치

### 2. 모순/불일치
- [file-a.md ↔ file-b.md] 설명 — 어느 쪽이 우선인지 근거 포함

### 3. 댕글링 참조
- [file.md] 참조 경로 — 존재 여부 확인 결과

### 4. CLAUDE.md 중복
- [file.md] 중복되는 CLAUDE.md 섹션 명시

### 5. 공통 규칙 후보
- [file.md] 승격 대상 규칙 요약 — 승격 위치 제안

### Summary
- 파일 감사: N개
- Stale: N
- 모순: N
- 댕글링 참조: N
- CLAUDE.md 중복: N
- 공통 규칙 후보: N
- 상태: PASS / NEEDS_FIX
```

### Phase 4: 수정 실행

`--dry-run` 이 없으면 보고서를 출력한 뒤 **사용자에게 확인**을 받고 아래를 수행한다.

| 문제 유형 | 조치 |
|---|---|
| Stale | 메모리 파일 내용 업데이트 (날짜·상태·경로 수정) |
| 모순 | 낮은 우선순위 파일 수정, 내용 통일 |
| 댕글링 참조 (인덱스 링크) | `MEMORY.md` 에서 해당 항목 제거 |
| 댕글링 참조 (본문 경로) | 경로 수정 또는 관련 설명 삭제 |
| CLAUDE.md 중복 | 메모리 파일 삭제 + `MEMORY.md` 인덱스 항목 제거 |
| 공통 규칙 후보 | 사용자와 승격 여부 합의 후, 합의 시 CLAUDE.md 해당 섹션에 추가 + 메모리 삭제 |

수정 후 `MEMORY.md` 인덱스가 실제 파일 목록과 일치하는지 재확인한다.

## Rules

- 보고서를 먼저 출력한 다음 사용자 확인을 받고 수정한다 (`--dry-run` 없는 경우).
- 한 번에 여러 문제를 발견해도 보고서는 한 번만 출력한다.
- 메모리 파일 삭제 전 반드시 내용을 읽어 확인한다.
- 공통 규칙 승격은 사용자가 명시적으로 동의한 경우에만 수행한다.
- 판단이 모호한 항목은 보고서에 `[?]` 표시 후 사용자에게 판단을 요청한다.
- 검사는 읽기 전용으로 시작하고, 수정은 Phase 4에서만 수행한다.
