# Task ID

TASK-FAN-FE-030

# Title

fan-platform-web 에 댓글을 쓰는 화면이 없다 — v1 스펙이 이미 「읍고 전용」으로 명시한 의도적 범위, 지금도 유효한지 소유자 결정 필요

# Status

done

# Owner

fan-platform

# Task Tags

- product-decision
- fan-platform-web

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 이 티켓 자체는 구현이 없다(결정 기록 또는 후속 티켓 분기).

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 11 — 글 쓰기 → 반응/댓글).

---

# 배경 — 23차 창 실측 + 스펙 대조 (2026-10-06 UTC)

fan-platform-web 어디에도 댓글을 **쓰는** UI 가 없다 — 코드에는 `commentCount` 를
표시하는 자리만 있다:

```tsx
// features/post/ui/PostCard.tsx:97
<span>댓글 {item.commentCount}</span>
```

```tsx
// features/post/ui/MyPostList.tsx:52  (같은 모양)
```

🔴 **그런데 이것은 결함이 아니다 — 스펙이 이미 그렇게 적어 뒀다.**
`projects/fan-platform/specs/services/fan-platform-web/overview.md:57`:

```
## Out of scope (v1)
- 댓글 composer UI — community-service 의 comment API 는 backend 존재, 본 frontend 는
  read-only display.
```

즉 "백엔드엔 댓글 API 가 있는데 v1 frontend 는 일부러 읍기 전용으로만 만든다"는 **의도된
결정**이고, 지금 코드는 그 결정을 정확히 구현하고 있다.

## 그럼 왜 이 티켓을 올리는가

- `TASK-FAN-FE-016`(DONE)이 이미 글 **쓰기**(`/compose`) 화면을 v1 에 추가했다 — 즉 "쓰기
  기능 자체는 v1 범위 밖"이라는 전제가 포스트 축에서는 이미 깨졌다. 그 상태에서 "포스트는
  쓰는데 댓글은 못 쓴다"는 비대칭이 방문자에게 **제품이 미완성인 것처럼** 보일 수 있다 —
  v1 경계선이 2026-08 당시의 결정이고, 지금 다시 봐도 같은 결정인지 묻는 것이 이 티켓의
  유일한 목적이다.
- `TASK-MONO-764` 점검표가 이것을 "눌러볼 수 없는 흐름"으로 표면화했다 — 결함인지
  제품 공백인지 가르지 않은 채로 넘기면 다음 점검에서 또 똑같이 발견된다.

---

# 결정 기록 (owner decision, 2026-10-07 UTC)

소유자 발화(verbatim): 「FAN-FE-030(팬 댓글 작성 화면을 넣을지) 넣어줘」

**결정 — AC-2 (전환 결정): 범위에 넣는다.** fan-platform-web 은 댓글 목록 + 작성 + 본인
댓글 삭제 UI 를 받는다. `overview.md:57` 의 "read-only display" 줄은 더 이상 소유자의
의도가 아니다 — 아래 조치 ③에서 고친다.

## 착수 시 다시 측정한 간극

이 티켓이 ready 로 올라온 시점의 전제("backend comment API 는 이미 존재")를 다시 재서
보니 **절반만 사실**이었다: `specs/contracts/http/community-api.md` § Comments (당시
~L353)에는 `POST /api/community/posts/{postId}/comments` 와
`DELETE …/comments/{commentId}` 만 있고 **읽기 경로가 없었다** — post 상세 응답은
`commentCount` 숫자만 들고 있고, `CommentController` 에 `@GetMapping` 자체가 없었다.
목록 없는 composer 는 쓸 수 없는 화면이므로, 프런트 작업 전에 백엔드 읽기 경로가
선행되어야 한다는 결론이 이 결정에서 바로 나온다.

## 분기

- **`TASK-FAN-BE-052`** (backend) — `GET /api/community/posts/{postId}/comments` 페이지
  조회 경로. contract-first로 계약을 먼저 갱신하고 구현.
- **`TASK-FAN-FE-032`** (frontend, BE-052 선행 의존) — post 상세 화면에 댓글 목록 +
  작성 + 본인 댓글 삭제 UI. `overview.md:57` 의 그 줄을 "넣는다" 로 고친다. BE-052 가
  아직 배포되지 않은 데모 백엔드에서도 화면이 깨지지 않도록(404/부재 → 목록만 숨김) 해야
  한다.

양쪽 모두 `projects/fan-platform/tasks/ready/` 에 새로 기안했다(아래 Related Specs 참조).

## AC 닫음

- **AC-0** — 위 발화로 확인됨.
- **AC-1** — 적용 안 됨(유지 결정이 아니었음).
- **AC-2** — 위 "분기" 절 + `TASK-FAN-BE-052`/`TASK-FAN-FE-032` 기안으로 닫힘.
- **AC-3** — 🟡 **부분 이탈, 사유를 기록한다.** 이 AC 는 원래 "이 결정 티켓은 결정만 남기고
  구현 PR 과 분리된다"는 전제로 적혔다. 그런데 오케스트레이터 지시가 이 결정 종결 +
  `TASK-FAN-BE-052` 백엔드 구현 + `TASK-FAN-FE-032` 기안을 **하나의 PR** 로 묶으라고
  명시했다 — 즉 이 PR 에는 코드 변경이 **있다**. 다만 그 코드는 이 티켓 자신이 아니라
  AC-2 가 새로 분기시킨 `TASK-FAN-BE-052` 에 귀속되는 변경이며, 이 티켓 파일 자체는
  결정 기록 + 링크만 담고 있다 — "코드 변경이 없다"의 글자 그대로는 충족하지 못했으므로
  AC-3 를 그대로 체크하지 않고 이 각주로 이탈을 밝힌다.

---

# Goal

fan-platform-web 의 댓글 쓰기 UI가 **지금도** v1 범위 밖인지 소유자가 다시 확인하고,
결정을 기록한다. 범위에 들어온다면 구현 티켓을 별도로 기안한다 — 이 티켓 자신은 구현하지
않는다.

---

# Scope

## In Scope

- `specs/services/fan-platform-web/overview.md` § Out of scope (v1) 의 그 줄이 여전히
  소유자의 의도를 반영하는지 확인(질문).
- 결정이 "그대로 유지"면 그 근거(예: 모더레이션/스팸 표면 확대 우려, 또는 단순히 v1
  범위 밖)를 이 티켓에 기록하고 구현 없이 종결.
- 결정이 "범위에 넣는다"면, 댓글 composer UI + 기존 백엔드 comment API 배선을 위한 **새
  구현 티켓**을 Scope/AC 를 갖춰 기안하고 이 티켓에서 링크한다.

## Out of Scope

- 이 티켓에서 직접 댓글 UI 를 구현하는 것 — 결정이 먼저다.
- `TASK-FAN-FE-016` 의 포스트 작성 화면 변경.

---

# Acceptance Criteria

- [x] **AC-0** — `overview.md:57` 의 그 줄이 여전히 커밋된 의도인지, 소유자에게 확인한다.
      → 위 "결정 기록" 절, 2026-10-07 UTC.
- [ ] **AC-1 (유지 결정)** — 적용 안 됨(소유자가 전환을 결정).
- [x] **AC-2 (전환 결정)** — "범위에 넣는다"면, 이 티켓은 구현하지 않고 **별도 구현
      티켓**(Scope/AC 포함, 기존 `community-api.md` 의 comment 엔드포인트를 인용)을 새로
      기안해 이 티켓에서 링크한다. → `TASK-FAN-BE-052` + `TASK-FAN-FE-032` 기안 완료.
- [x] **AC-3** — 🟡 부분 이탈 — 위 "AC 닫음" 절 참조. 이 PR 은 결정 기록 전용이 아니라
      `TASK-FAN-BE-052` 구현을 함께 묶는다(오케스트레이터 지시).
      → **소유자가 이탈을 수락하고 닫음(2026-10-07 UTC, 발화 「받아들이고 닫기로 결정」).**

---

# Related Specs

- `projects/fan-platform/specs/services/fan-platform-web/overview.md` § Out of scope (v1)
- `projects/fan-platform/specs/contracts/http/community-api.md`(댓글 API — 이미 backend
  존재한다고 overview 가 적음, 정확한 엔드포인트는 착수 시 확인)
- 후속 티켓: `projects/fan-platform/tasks/ready/TASK-FAN-BE-052-community-comment-list-read-endpoint.md`
- 후속 티켓: `projects/fan-platform/tasks/ready/TASK-FAN-FE-032-post-detail-comment-list-compose-delete-ui.md`

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` § Comments — `GET`
  목록 엔드포인트는 이 티켓이 아니라 `TASK-FAN-BE-052` 가 추가한다(이 티켓은 결정 기록).

---

# Edge Cases

- 해당 없음(결정 티켓 — 코드 엣지 케이스는 전환 결정 이후의 구현 티켓이 다룬다).

# Failure Scenarios

- **이 티켓에서 바로 댓글 UI 를 구현한다** — 이 티켓이 강제하려는 소유자 판단을 건너뛴다.
- **"백엔드에 API 가 있다"는 사실만으로 프런트 공백을 자동으로 결함이라 단정한다** — 스펙이
  명시적으로 범위 밖으로 적어 뒀다. 소유자가 다시 그렇다고 확인할 때까지는 결함이 아니다.

---

# 닫기 기록 (2026-10-07 UTC, 4차원 검증)

- (a) PR #4195 `state=MERGED`, squash `b675955d2`.
- (b) `origin/main` 이 `b675955d2` 를 포함(현재 tip `f02660e1b`).
- (c) 머지된 PR 의 `statusCheckRollup` 실패 0 · 대기 0.
- (d) AC 절을 열어 읽음: AC-0 ✅ · AC-1 해당 없음(전환 결정) · AC-2 ✅(BE-052 #4195 · FE-032 #4197 머지) · AC-3 🟡 이탈 — 소유자 수락.
