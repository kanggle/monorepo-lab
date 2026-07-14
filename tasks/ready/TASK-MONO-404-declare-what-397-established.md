# Task ID

TASK-MONO-404

# Title

저장소가 **자기가 아는 것을 선언하지 않는다** — `MONO-397`/`399` 가 확립한 사실과 문서의 정합화, 그리고 가드 규칙의 정경 홈

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- docs
- platform

# Goal

`TASK-MONO-397`(kafka 512M→1G)과 `TASK-MONO-399`(AC-6, 재굽기)를 하면서 **저장소가 참이라고 아는 것과 저장소가 적어 둔 것이 갈라져 있음**을 발견했다. 이 task 는 그 간극을 메운다.

**두 축이고, 뿌리는 하나다: 내가(그리고 앞선 task 들이) 확립한 사실이 어디에도 선언돼 있지 않다.**

## 축 A — 데모 서브시스템 문서가 독자를 잘못된 결론으로 이끈다

**가장 심각한 것부터**: `infra/demo/aws/README.md` 는 **"`terraform apply` 2분이면 되고 AMI 를 다시 구울 필요가 없다"** 고 적는다. 그 문장은 *destroy 후 복구*에 대해서만 참인데, **어디에도 그 한정이 없다.**

**저장소 어디에도 배포 층이 둘이라는 사실이 적혀 있지 않다:**

| 고친 것 | 도달 경로 | 재굽기 |
|---|---|---|
| `terraform/**` · `lambda/handler.py` · `site/index.html` · `ec2/user-data.sh` | `terraform apply` 가 저장소에서 그때 읽는다 | ❌ |
| `infra/demo/*.sh` · `demo-stack.service` · `projects/*/docker-compose.yml` · 앱 소스 | **AMI 에 구워져 있다**(bake 시 `git clone`, 부팅 시 `git pull` **없음**) | ✅ ~55분 |

⇒ **`docker-compose.yml` 을 고친 사람은 `terraform apply` 로 충분하다고 믿는다. 아니다.** 그리고 실제로 그 상태다 — **`MONO-397` 의 kafka 1G 는 `main` 에 있지만 현재 AMI 에는 없다. 지금 데모를 켜면 512 MiB kafka 가 뜬다.**

**나 자신이 이 문장에 속았다.** PR 을 머지하고 *"397 완전 종료"* 라고 보고했다. 사용자가 *"코드 수정하면 재빌드 하고 시작해?"* 라고 묻지 않았다면 그대로 넘어갔다.

## 축 B — 가드 규칙에 정경 홈이 없다

*"첫날 RED 인 가드는 꺼지고, 꺼진 잡의 skip 은 초록으로 보고된다"*(`MONO-360`)는 저장소 **14곳**에 각자 복사돼 있다(`scripts/check-*-drift.sh` · `.github/workflows/ci.yml` · `libs/*/build.gradle` · `verify-demo-wrapper.sh` · ADR-049). **전수 대조 결과 갈라지지는 않았다** — 즉 **`ADR-MONO-049` 의 병(사본이 갈라진다)이 아니다.** 중복 제거는 정당화되지 않는다.

**진짜 갭은 선언의 부재다.** `platform/testing-strategy.md`(= `CLAUDE.md` § Layer Rules 가 Testing 의 정경으로 가리키는 파일)에는 **가드 이야기가 한 줄도 없다.** `rules/`·`.claude/skills/`·`docs/adr/` 에도 없다. ⇒ **새 가드를 처음 쓰는 사람은 이 규칙들을 발견할 경로가 없다.**

그리고 **가장 최신 규칙(G4: 내 호스트에서 보정한 임계는 러너에서 안 문다)은 저장소에 단 한 곳**(`verify-demo-wrapper.sh`)에만 있다. 나는 그것을 모른 채 **하루에 두 번** 그 함정에 빠졌고, 두 번 다 **측정 전용 PR 이 아니었으면 "가드를 넣었다" 고 보고하고 머지**했을 것이다.

# Scope

**In scope**

- `infra/demo/aws/README.md` — 배포 층 표 + 재굽기 경고 + 사이징 재프레이밍 + 프로젝트 수
- `infra/demo/README.md` — scratchpad 포인터 → `aws/`, 인스턴스 타입 모순
- `infra/demo/demo-stack.service` — 프로젝트 수
- `infra/demo/aws/terraform/terraform.tfvars.example` — 사이징 출처 오기 + 재프레이밍
- `platform/testing-strategy.md` — **§ CI Guards / Drift Detectors 신설**(G1~G9)
- `infra/demo/verify-demo-wrapper.sh` — 397 교훈 블록에 정경 포인터 한 줄

**Out of scope**

- **14곳의 인라인 가드 주석을 지우는 것.** **갈라지지 않았고**, 쓰는 자리에 있는 것이 옳다. 지우면 *"이 가드가 왜 이렇게 생겼는가"* 를 읽을 자리가 사라진다. **없는 것은 사본이 아니라 선언이다.**
- **사이징 숫자(26GB/5.5GB)를 새로 재는 것** — `TASK-MONO-399` AC-2 의 일이다. 이 task 는 **그 숫자가 강제되지 않는 관측치임을 밝히기만** 한다.
- **AMI 재굽기** — `TASK-MONO-399` AC-6.
- **다른 프로젝트에 메모리 리밋 추가** — `MONO-397` D3(리밋은 상한이 아니라 설정).

# Acceptance Criteria

**AC-0 — 인계된 발견을 재측정한다 (verify-then-act).**
정찰이 보고한 드리프트를 **믿지 말고 직접 확인**한다. (수행함: 8건 중 **5건 직접 재확인**, 1건(terraform 리소스 수)은 **드리프트 아님으로 기각**.)

**AC-1 — 배포 층을 선언한다.**
`infra/demo/aws/README.md` 에 **"코드를 고쳤다 — 그게 데모에 도달하는가?"** 절. 표 + `main` 초록 ≠ 데모 고쳐짐 + **현재 데모가 512M kafka 로 돈다는 사실** + 실험용 인스턴스-내 수정은 재굽기 불필요 + **구운 것을 런타임에서 확인하라**. *"AMI 를 다시 구울 필요가 없다"* 문장에 **한정어**를 붙인다.

**AC-2 — 사이징 주장을 예산이 아니라 관측치로 재프레이밍한다.**
`README.md` + `tfvars.example` 둘 다. **107개 서비스 키 중 34개만 리밋 선언, 전부 ecommerce** 를 명시. `tfvars.example` 의 출처 오기 **`TASK-MONO-353` → `TASK-MONO-366`** 정정(353 은 bitnami kafka 삭제 티켓이다).

**AC-3 — 사실 모순을 제거한다.**
① 프로젝트 수 **9 → 8**(`aws/README.md`, `demo-stack.service`; 출처는 `projects.sh` 의 `FULL` 배열 — **숫자를 다시 적지 말고 그 파일을 세라**고 명시)
② 인스턴스 타입 **m6i.4xlarge → m6i.2xlarge**(`infra/demo/README.md` 2곳; `variables.tf` 기본값이 권위)
③ **scratchpad 포인터 제거** — `infra/demo/README.md` 가 AWS 재현 경로로 `ondemand-demo/`(세션 스코프, **남에게는 존재하지 않는 경로**)를 가리킨다. **`aws/README.md` 자신이 "이전에는 scratchpad 에만 있어서 검증 불가능한 주장이었다" 고 적어 둔 그 결함이 바로 옆 파일에 살아 있었다.**

**AC-4 — 가드 규칙에 정경 홈을 준다.**
`platform/testing-strategy.md` 에 **§ CI Guards / Drift Detectors — Authoring Rules**(G1~G9), 각 규칙마다 **그것을 낳은 인시던트 번호**를 붙인다. `# Change Rule` 을 확장해 *"한 가드의 주석에만 사는 규칙은 다음 가드의 저자가 찾지 못하는 규칙"* 임을 명시.
**인라인 주석은 지우지 않는다.** `verify-demo-wrapper.sh` 의 397 블록에는 **정경을 가리키는 한 줄**만 얹는다(실측 수치는 규칙의 *증거*로 그 자리에 남는다).

**AC-5 — 문서 task 의 검증이란 무엇인가.**
탐지식을 **아는 답에 먼저 돌린다**: 수정 **전** blob 에서 각 드리프트가 정확히 검출되고, 수정 **후** 0건인가. **빈 출력을 부재로 읽지 않는다**(이 세션에서만 그 함정을 **세 번** 밟았다).
`bash -n infra/demo/verify-demo-wrapper.sh` 통과. `demo-wrapper-smoke` GREEN(가드 (a)~(u) 무손상).

# Related Specs

- [`platform/testing-strategy.md`](../../platform/testing-strategy.md) — 정경 홈
- [`infra/demo/aws/README.md`](../../infra/demo/aws/README.md) — 재현 계약
- [`infra/demo/projects.sh`](../../infra/demo/projects.sh) — 프로젝트 맵 단일 출처

# Related Contracts

없음 (문서·정책 전용, 코드 동작 무변경).

# Edge Cases

- **`verify-demo-wrapper.sh` 를 건드리므로 `demo-wrapper-smoke` 가 실제로 돈다** — 주석 한 줄이라도 `bash -n` + 스모크로 확인한다.
- **`platform/` 은 공유 경로다** — 프로젝트 이름·서비스명·도메인 엔티티를 넣으면 **HARDSTOP-03**. G1~G9 의 예시는 **task 번호와 일반적 실패 모드**로만 쓴다(kafka/ecommerce 를 규칙 본문의 주어로 삼지 않는다).
- **문서만 고치는 PR 은 대부분의 CI 잡이 SKIP 된다** — `demo-wrapper` 필터는 `code-changed` AND 가 **제거돼 있으므로**(MONO-389) markdown 변경에도 발화한다. 그게 이 PR 에서 실제로 확인된다.

# Failure Scenarios

- **가장 위험: 14곳의 인라인 주석을 "중복이니까" 지우는 것.** 갈라지지 않았고, 각자 **그 자리의 구체(왜 이 술어인가)** 를 담고 있다. 지우면 규칙은 남고 **근거가 사라진다** — 그러면 다음 사람이 규칙을 이해하지 못한 채 우회한다. **중복이 곧 병은 아니다. `ADR-MONO-049` 의 병은 *사본이 갈라진 것*이었고, 여기선 갈라지지 않았다.**
- **정경 문서를 만들어 놓고 아무도 안 읽는 것.** 그래서 `platform/testing-strategy.md` 를 골랐다 — `CLAUDE.md` § Layer Rules 가 **Testing 의 정경으로 이미 가리키고 있는 파일**이라 새 발견 경로를 만들 필요가 없다. 새 문서를 만들면 **그것 자체가 아무도 안 읽는 14번째 자리**가 된다.
- **문서를 고치고 "정합화 완료" 라고 적는 것.** 데모는 여전히 512M kafka 로 돈다. **이 task 는 그것을 *선언*할 뿐 *고치지* 않는다** — 고치는 것은 `TASK-MONO-399` AC-6 이다. 두 개를 섞으면 이 task 가 자기 결론에 대해 거짓말하게 된다.

# Notes

- 분석·구현 = **Opus 4.8**.
- 선행: `TASK-MONO-366`(데모 승격) · `TASK-MONO-389`(정문) · `TASK-MONO-397`(kafka) · `TASK-MONO-399`(잔여 + AC-6).
- 이 task 는 **코드 동작을 바꾸지 않는다.** 바꾸는 것은 *저장소가 자기에 대해 말하는 것*이다.
