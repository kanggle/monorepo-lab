# Task ID

TASK-MONO-844

# Title

⏳ SCHEDULED — DO NOT START before 2026-12-02 UTC · 레인별 gradle-home 키(`TASK-MONO-843`) 머지 뒤 7일 동안 Maven Central 429 실패가 줄었는지 다시 센다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- measurement

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (측정 · 기록 — 코드 변경 없음이 기본)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-843` AC-5(«머지 뒤 7일 동안 429 실패 잡 수를 다시 센다»)의 이관.

# Dependency Markers

- 선행: `TASK-MONO-843`(done — impl `4c93b21e1`, 머지 2026-11-25T14:13:19Z).

# Goal

843 은 재사용 워크플로 레인들이 gradle-home 캐시 키 하나를 공유해 자기 의존성을 매번 다시 받다가 Maven Central 429 를 맞는 것을 고쳤다. 머지 당일 같은 sha 재실행에서는 «새 의존성 항목을 저장한 레인» 이 14/14 → 0/14 로 줄었다. 그러나 그것은 **정확 키 복원**으로 잰 값이다. 평소 실행(새 sha → 같은 레인의 최신 항목을 접두어로 복원)에서도 그런지, 그리고 429 자체가 줄었는지는 아직 아무도 재지 않았다.

기준: 2026-11-23 21:01Z ~ 11-25 사흘에 429 실패 잡 **11**(`main` 최종 실패 6), CI · Nightly 224 런.

# Scope

## In Scope

1. **AC-0 — 날짜 게이트 + 전제 확인(검증 후 실행):**
   - 오늘(UTC, `date -u +%F`)이 2026-12-02 이상인가. 아니면 멈춘다(소유자가 조기 착수를 요청하면 예외).
   - `_integration.yml` · `_platform-e2e.yml` 에 `GRADLE_BUILD_ACTION_CACHE_KEY_JOB_INSTANCE` 가 아직 있는가(되돌려졌으면 측정 대상이 없다 — 그 사실을 적고 닫는다).
2. 2026-11-25T14:13Z ~ 측정 시점의 CI · Nightly 런 전부에서:
   - (a) `status code 429` 를 포함한 실패 잡 수(최종 실패 · 재실행으로 통과 구분). 런 수와 함께.
   - (b) 재사용 레인 잡이 **복원한 것과 다른** `gradle-dependencies-v1-*` 항목을 저장한 비율(843 의 단위). 의존성 버전을 올린 커밋 직후는 정당하게 새 항목이 생기므로 그 런은 따로 표시한다.
   - (c) gradle-home 복원이 OS 단계까지 내려간 잡 수(= 레인 자기 항목도, 같은 job id 의 형제도 없었다 — 캐시 축출 신호). `actions/cache/usage` 값.
3. 결과에 따라:
   - 429 0 이고 (b) 가 낮다 → 기록하고 닫는다.
   - 429 가 남는다 → 그 수치로 **미러 저장소 추가 여부를 소유자에게 묻는다**(공급망 결정 — 에이전트가 구현하지 않는다).
   - (c) 가 0 이 아니다 → 캐시 한도 축출 후속 티켓.

## Out of Scope

- 미러 추가 구현(소유자 결정 전).
- 재사용 워크플로가 아닌 잡(`build-and-test` · `*-boot-jars`) — 843 실측에서 429 0. 여기서 429 가 보이면 수치만 기록.

# Acceptance Criteria

- [ ] AC-0: 날짜 게이트(UTC)와 설정 존속을 확인해 기록한다.
- [ ] AC-1: In Scope 2 의 (a)(b)(c) 를 숫자 · 런 범위 · 명령과 함께 Implementation Evidence 에 적는다. (b) 의 단위는 843 과 같게(«레인 수» — 파일 수 아님).
- [ ] AC-2: In Scope 3 의 분기 중 하나를 실행한다 — 닫음 / 소유자 질문(질문한 사실과 답) / 후속 티켓 번호.

# Related Specs

- `tasks/done/TASK-MONO-843-shared-gradle-home-key-sends-lanes-to-maven-central-429.md`(기전 · 기준값 · 단위 정정)

# Related Contracts

- 없음.

# Edge Cases

- 측정 창에 레인이 돈 런이 적으면(문서만 바뀐 push 는 레인을 건너뛴다) 숫자가 작다 — 런 수를 함께 적고 «0 = 개선» 으로 바로 읽지 않는다(843 이 본 창은 하루 127~163 런이었다).
- `gh run list` 는 기본 20개 — `--limit` · `--created` 를 명시한다.
- 날짜는 UTC 로 적는다(KST 호스트).

# Failure Scenarios

- 429 0 을 «고쳐졌다» 로 읽는데 실제로는 Maven Central 쪽 한도 정책이 풀린 것일 수 있다 — (b) 가 그 둘을 가른다: 우리 쪽 다운로드가 줄었는지는 (b) 로만 보인다.

# Test Requirements

- 측정 · 기록.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
