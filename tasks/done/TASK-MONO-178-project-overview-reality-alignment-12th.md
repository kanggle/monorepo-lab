# Task ID

TASK-MONO-178

# Title

**`docs/project-overview.md` reality-alignment 12회째 — erp 결재 워크플로 도메인 완성 반영 (MONO-141/148/168/172/177 cadence).** MONO-177(11회, 2026-06-05) snapshot 이후 이번 세션에 출하된 **erp approval 도메인 전체**(백엔드 BE-009/010/011/012/013 + 콘솔 PC-FE-051~054)를 평가자-노출 SoT 에 정합. 핵심: §2.8 v2-deferred 가 `approval-service`/`notification-service` 를 아직 deferred 로 잘못 분류하던 **사실오류 교정**(MONO-177 의 read-model 교정의 approval/notification 판).

# Status

done

# Owner

monorepo (docs-only; 분석=Opus 4.8 / 구현=Opus 직접 — surgical docs edit, no code/spec/ADR 변경)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Dependency Markers

- **선행 (반영 대상, 모두 main 머지됨)**: ERP-BE-009 `575bc9bf`(approval 단일단계) / ERP-BE-010 `d1a9d83f`(read-model approval-fact) / ERP-BE-011 `7fd1b0eb`(notification) / ERP-BE-012 `b749c1f9`(다단계+IN_REVIEW) / ERP-BE-013 `650ae89c`(대결/위임) + PC-FE-051 `9f04b977`(결재함) / PC-FE-052 `3b2f8e74`(notification bell) / PC-FE-053 `49e85e87`(다단계 UI) / PC-FE-054 `939027b4`(위임 관리).
- **cadence**: MONO-141(6회)/148(7회)/168(9회)/172(10회)/177(11회) reality-alignment 패턴. 큰 build wave 직후 SoT 정합.
- **decision (user, 2026-06-05)**: 다음 작업 = project-overview reality-alignment 12회.

# Goal

`docs/project-overview.md`(평가자-노출 SoT)가 이번 세션의 erp 결재 워크플로 도메인 완성(백엔드 3-증분 단계/대결/위임 + read-model approval-fact + notification + 콘솔 결재 4-슬라이스)을 정확히 반영한다. 특히 §2.8 v2-deferred 의 approval/notification 사실오류를 교정한다.

# Scope

## In Scope (surgical docs edits, `docs/project-overview.md` only)

- **header 갱신 narrative** (line 4): erp 결재 워크플로 도메인 완성 arc 추가.
- **§2.8 erp**: ① 결재 워크플로 도메인 라이브 bullet 추가(approval-service BE-009 단일→BE-012 다단계+IN_REVIEW→BE-013 대결/위임 + read-model approval-fact BE-010 + notification BE-011) ② service map 에 `approval-service`(rest-api) + `notification-service`(event-consumer+rest-api) 행 추가 + read-model 행에 approval-fact 투영 보강 ③ **v2-deferred 사실오류 교정** — approval-service/notification-service 제거(라이브), permission-service/admin-service 만 잔존.
- **§2.6 console**: erp 결재 surface bullet 추가(PC-FE-051 결재함/052 notification bell/053 다단계 UI/054 위임 관리).
- **§7 ADR table**: ADR-MONO-016 행에 §D3 forward-declaration 집행(read-model + approval 3-증분 + notification) 보강.
- **§9 roadmap**: 8+ 행에 erp 결재 도메인 완성 + 잔여 deferred(approval v2.2/notification 외부채널/permission·admin) 갱신.

## Out of Scope

- 코드/스펙/ADR 변경 0 (순수 docs 스냅샷 정합).
- 다른 도메인 섹션(wms/scm/finance/gap) — 변화 없음.
- MEMORY.md / 토픽 파일 — 별도(세션 메모리는 각 task close 시 갱신됨).

# Acceptance Criteria

- [ ] **AC-1** §2.8 v2-deferred 가 approval-service/notification-service 를 더 이상 deferred 로 표기하지 않음(라이브 반영); permission-service/admin-service 만 잔존.
- [ ] **AC-2** §2.8 service map 에 approval-service + notification-service 행; read-model 행에 approval-fact 투영 반영.
- [ ] **AC-3** §2.6 console 에 erp 결재 surface(PC-FE-051~054) bullet.
- [ ] **AC-4** §7 ADR-MONO-016 행 + §9 roadmap 행 + header narrative 정합.
- [ ] **AC-5** diff 가 `docs/project-overview.md` + task lifecycle(tasks/) 에 국한; 코드/스펙/ADR 0.

# Related Specs

- align target: `docs/project-overview.md`. 반영 출처: ADR-MONO-016 §D3 amendments(ERP-BE-009~013), approval-service/architecture.md §v2.0/v2.1 amendments, 각 task done 기록.

# Related Contracts

- N/A (docs-only).

# Edge Cases

- 사실오류 교정 우선(approval/notification deferred→live) — MONO-177 의 read-model 교정 선례.
- header/§2.8/§2.6/§7/§9 간 일관성(approval 3-증분·notification·콘솔 4-슬라이스 동일 표기).

# Failure Scenarios

- N/A (docs).

# Test Requirements

- docs lint/렌더 자체검토. CI = docs fast-lane(`changes` pass + heavy jobs skip).

# Definition of Done

- [ ] §2.8/§2.6/§7/§9/header surgical edits; v2-deferred 사실오류 교정.
- [ ] diff confined to docs/project-overview.md + tasks/.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현=Opus 직접 (docs-only surgical, no dispatch). 사용자 "project-overview reality-alignment 12회" 선택. 메타: MONO-177(11회) 이후 erp approval 도메인 전체(BE-009~013 + PC-FE-051~054)가 출하돼 §2.8 v2-deferred 가 approval/notification 을 deferred 로 잘못 분류 — 명시적 갱신 시점에만 변경되는 docs 스냅샷이 누적 arc 와 벌어진 회차(MONO-177 read-model 교정의 approval/notification 판). [[project_refactor_sweep_status]] [[project_platform_console_adr_013]] [[project_monorepo_template_strategy]]
