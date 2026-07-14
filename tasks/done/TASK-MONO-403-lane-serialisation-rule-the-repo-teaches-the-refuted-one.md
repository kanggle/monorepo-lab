# TASK-MONO-403 — 저장소가 가르치는 레인 직렬화 규칙은 MONO-398 이 반증한 그 규칙이다

- **Status**: done
- **Type**: monorepo (shared — `.github/workflows/`, `platform/`)
- **Priority**: P2
- **Origin**: 자진 신고 (TASK-MONO-398 의 정합화 감사에서 발견)

---

## Goal

`_integration.yml` 의 `gradle-args` 입력 문서가 **모듈 수를 트리거로** 가르친다:

> *"Use `--no-parallel` to serialize a caller whose modules each spin a heavy Testcontainers stack … a multi-module caller otherwise boots every module's stack at once and can exhaust the runner."*

**`TASK-MONO-398` 이 실측으로 결론낸 것은 거의 반대다.** 완화 없던 5개 레인의 이력을 전수 조사해 **`ecommerce` 하나만** 실제로 이 병을 앓았고(고갈 지문 151), **`fan`(4모듈)·`erp`(4)·`scm`(3)·`finance`(2) 는 증거 0건이라 의도적으로 건드리지 않았다** — *"증거 없이 바르면 CI 벽시계만 늘고 `wms` 가 겪은 timeout-CANCELLED 를 새로 만든다."*

**그 넷은 전부 위 문서 설명에 문자 그대로 해당한다.** 다음 사람이 이 doc-string 을 따르면 **MONO-398 이 명시적으로 경고한 과잉수정을 한다.** 저장소가 자기 실측과 반대되는 것을 가르치고 있다.

동시에, 같은 근거가 **네 caller 주석에 흩어져 반복**된다(`iam` 25줄 · `ecommerce` 20줄 · `wms` 6줄 · `wms-inv` 7줄). 규칙은 한 번만, 증거는 레인마다.

## Scope

**In:**

- `.github/workflows/_integration.yml` — `gradle-args` 입력 description 정정 (증거 우선).
- `.github/workflows/ci.yml` — 네 개 integration caller 주석 정리 (공통 '왜' 는 한 곳, caller 엔 그 레인의 증거만). `iam` 주석 자족화.
- `platform/testing-strategy.md` — § 신설: IT 레인 직렬화 규칙 (프로젝트 무관 서술).
- `tasks/INDEX.md` — 이 task 행 + 별건 2건.

**Out (non-goals):**

- **레인 구성·타임아웃·`gradle-args` *값* 변경 없음.** 이 task 는 **주석과 문서만** 바꾼다 — AC-3 의 가드가 그것을 증명한다.
- **`iam` 5모듈 재측정 없음** (별건 — 아래 AC-5).
- **`ecommerce` timeout 상향 없음** — 증거가 없다. 관찰만 별건으로 남긴다.

## Acceptance Criteria

- [ ] **AC-1 — `_integration.yml` 이 옳은 규칙을 가르친다.** `gradle-args` description 이 (a) **모듈 수를 트리거로 제시하지 않고**, (b) 발동 근거는 **그 레인의 고갈 지문**(커넥션 절단 로그)임을 말하고, (c) **대가**(직렬화 = 벽시계 ~2배, 이미 timeout-CANCELLED 전과 있음)를 말한다.
- [ ] **AC-2 — 규칙이 저장소에 canonical 하게 존재한다.** `platform/testing-strategy.md` 에 § 신설: **언제** 직렬화하나(증거) / **증거를 어떻게 얻나**(⚠️ 재실행에 가려진 실패 — `gh run list` 는 최종 attempt 의 결론만 보고한다) / **대가**. **서비스·프로젝트 이름 0개** (HARDSTOP-03 — 레인별 사실은 `ci.yml` 이 갖는다).
- [ ] **AC-3 — 리팩토링은 무동작변경이다.** 네 caller 의 공통 '왜' 를 한 번만 말하고 각 caller 엔 **그 레인의 증거만** 남긴다. **가드(술어) = 주석·빈줄을 제거한 두 워크플로 파일이 before/after 바이트 동일**. diff 줄 수 같은 대리 지표 금지 — **커밋될 아티팩트의 성질 자체**를 묻는다.
- [ ] **AC-4 — `iam` 주석이 자족적이다.** *"TASK-MONO-398 owns that question, and its lane table still says seven"* 라는 **DONE 티켓을 향한 미해결 질문 포인터**를 제거한다(그 티켓은 종결됐고 재측정을 범위 밖이라 명시했다 ⇒ 질문이 무주공산이다). 주석엔 사실만: 지문은 **7모듈 시절** 측정치 · 현재 레인은 **5모듈** · **유지는 보수적 기본값이지 실측 결론이 아니다**.
- [ ] **AC-5 — 무주공산 질문 2건에 주인을 준다.** `tasks/INDEX.md` 별건 행으로 명시: ① `iam` 이 5모듈에서도 `--no-parallel` 이 필요한가(미측정) ② `ecommerce` 는 이제 저장소 최장 직렬 레인(16분 08초 / timeout 30분, 여유 47%)이고 **13번째 모듈이 붙을 때 아무도 경고하지 않는다**.

## Related Specs

- `platform/testing-strategy.md` (§ Testcontainers Conventions — 이웃 절)
- `tasks/done/TASK-MONO-398-*.md` (실측 출처), `tasks/done/TASK-MONO-393-*.md`, `tasks/done/TASK-MONO-331-*.md`

## Related Contracts

없음 (CI 워크플로 주석 + 플랫폼 문서).

## Edge Cases

- **주석만 고치는 PR 은 CI 를 발동시킨다** (`workflows` 필터). 정상 — 무동작변경이므로 전 레인 GREEN 이 기대값이고, 그것이 AC-3 의 **런타임** 확인이다(정적 가드와 별개).
- `platform/` 에 프로젝트 이름을 쓰면 hook 이 HARDSTOP-03 을 발화한다 — 규칙은 무관하게 서술한다.
- `_integration.yml` 은 **모든** integration caller 가 공유한다. description 변경은 동작에 영향 없지만(문서 필드), YAML 블록 스칼라(`>-`) 들여쓰기를 깨면 워크플로가 파싱 불가가 된다.

## Failure Scenarios

- **F1 (가장 위험) — 주석 재작성이 조용히 *입력*을 바꾼다.** 블록 스칼라(`>-`) 안에서 들여쓰기를 한 칸 틀리면 `gradle-tasks` 나 `report-paths` 가 달라지고 CI 는 초록으로 보일 수 있다(그 레인이 skip 되면). **⇒ AC-3 의 가드가 이 시나리오의 술어다: 주석 제거 후 바이트 동일.** 이 저장소가 세 번 물린 실패 모드(대리 지표를 검증) 를 여기서 반복하지 않는다.
- **F2 — 정정문이 거짓 문장을 *인용*하면 grep 가드가 그것을 잔존으로 센다.** MONO-398 이 이미 겪었다(가드 술어는 "인용"과 "주장"을 구분 못 한다). ⇒ 인용하지 말고 풀어쓴다.
- **F3 — 과잉 정리.** 레인별 증거(job id, 지문 수, 날짜)를 지우면 다음 사람이 **재검증할 수 없고**, 그때 이 레인이 왜 직렬인지 아무도 모르게 된다. **공통 '왜' 만 hoist하고 증거는 caller 에 남긴다.**
- **F4 — 이 티켓이 자기 병에 걸린다.** "규칙을 한 곳에 적자" 가 **다섯 번째 사본**을 만드는 것으로 끝날 수 있다(문서 + doc-string + 주석 4개). ⇒ caller 주석에서 공통 서술을 **실제로 제거**했는지 줄 수로 확인한다(순증이면 실패).
