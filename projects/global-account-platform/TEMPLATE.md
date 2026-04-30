# Template Guide

이 저장소는 **살아있는 백엔드 포트폴리오 템플릿**입니다. 현재 활성 프로젝트는 [PROJECT.md](PROJECT.md)에 선언된 `global-account-platform`이지만, 저장소 구조 자체는 새 프로젝트를 시작하는 출발점으로 재사용되도록 설계되었습니다.

---

## Template vs Project Content

저장소의 모든 것은 두 부류로 나뉩니다:

### 재사용 인프라 (템플릿이 제공, 새 프로젝트에 그대로 복사)

| 경로 | 역할 |
|---|---|
| [.claude/config/](.claude/config/) | domain·trait·service-type 카탈로그 (short) |
| [rules/](rules/) | 선언된 domain·trait의 상세 규칙 (long) |
| [platform/](platform/) | 기술 수준 공통 규칙 — 아키텍처·코딩·테스트·보안·관측성 |
| [.claude/skills/](.claude/skills/) | 구현 패턴과 체크리스트 |
| [.claude/agents/](.claude/agents/) | AI 에이전트 역할 정의 |
| [.claude/commands/](.claude/commands/) | 슬래시 명령어 |
| [.claude/workflows/](.claude/workflows/) | 작업 흐름 가이드 |
| [tasks/templates/](tasks/templates/) | 백엔드·프론트엔드·통합 태스크 템플릿 |
| [libs/](libs/) | 도메인 중립 Java 공용 라이브러리 |
| [infra/](infra/) | Prometheus·Grafana·Loki 기본 설정 |
| [scripts/](scripts/) | 빌드·테스트·배포·검증 스크립트 |
| [docs/onboarding/](docs/onboarding/) | 온보딩 문서 |
| [knowledge/](knowledge/) | 설계 참조 자료 |
| [CLAUDE.md](CLAUDE.md) | AI 에이전트·개발자 최소 운영 규칙 |
| `build.gradle`, `settings.gradle`, `gradle/`, `gradlew*` | Gradle 구성 |
| `.gitignore`, `.gitattributes`, `.editorconfig` | 저장소 메타 |

### 프로젝트 콘텐츠 (새 프로젝트는 빈 상태로 시작)

| 경로 | 역할 |
|---|---|
| [PROJECT.md](PROJECT.md) | 프로젝트 분류 선언 (name / domain / traits / ...) |
| [README.md](README.md) | 프로젝트 소개 |
| [specs/contracts/](specs/contracts/) | HTTP·이벤트 컨트랙트 |
| [specs/services/](specs/services/) | 서비스별 스펙 (architecture.md 등) |
| [specs/features/](specs/features/) | 기능 스펙 |
| [specs/use-cases/](specs/use-cases/) | 유스케이스 스펙 |
| `apps/` | 서비스 구현체 (Spring Boot 모듈들) |
| [tasks/ready/](tasks/ready/), [backlog/](tasks/backlog/), [in-progress/](tasks/in-progress/), [review/](tasks/review/), [done/](tasks/done/), [archive/](tasks/archive/) | 태스크 생명주기 상태 |
| `.env` | 로컬 시크릿 (gitignored) |

---

## Starting a New Project

### 1. Clone to a new location

```bash
cp -r /path/to/this-repo /path/to/new-project
cd /path/to/new-project
rm -rf .git .gradle build
find libs -type d -name build -prune -exec rm -rf {} +
```

> `git clone`이 아닌 `cp -r`을 쓰는 이유: 새 프로젝트는 템플릿의 git history와 무관하게 시작합니다.

### 2. Run the init script

플래그 기반:

```bash
./scripts/init-project.sh \
  --name my-platform \
  --domain saas \
  --traits transactional,regulated,audit-heavy \
  --service-types rest-api,event-consumer
```

또는 대화형 (플래그 생략):

```bash
./scripts/init-project.sh
```

스크립트가 수행하는 일:

1. `--name`, `--domain`, `--traits`를 [.claude/config/domains.md](.claude/config/domains.md)·[.claude/config/traits.md](.claude/config/traits.md)와 대조 검증
2. 선언된 domain·trait에 해당하는 [rules/domains/](rules/domains/)·[rules/traits/](rules/traits/) 파일 존재 확인 → 없으면 **경고** (블로킹은 하지 않음)
3. [PROJECT.md](PROJECT.md) frontmatter와 본문 스켈레톤 재작성
4. [settings.gradle](settings.gradle)의 `rootProject.name` 교체
5. [README.md](README.md) 첫 줄 제목 교체
6. `.env.example` → `.env` 복사 (`.env`가 없을 때만)

### 3. Post-init 필수 작업

1. `PROJECT.md`의 TODO 섹션(Purpose · Domain Rationale · Trait Rationale · Out of Scope)을 실제 텍스트로 채우기
2. `.env` 값 채우기 (JWT_SECRET, DB passwords, OAuth keys 등)
3. `.env.example` 자체도 새 프로젝트에 맞게 수정 (현재 파일은 global-account-platform의 변수 집합)
4. **선언한 domain/trait의 규칙 파일이 없으면 직접 작성** — on-demand 정책상 필수 (아래 섹션 참조)
5. `specs/services/<service>/architecture.md` 작성 → 첫 태스크(`tasks/ready/TASK-BE-001-<service>-bootstrap.md`) 생성
6. Git 초기화:
   ```bash
   git init && git add -A && git commit -m "init <project-name>"
   ```

---

## On-Demand Rule Policy (중요)

[rules/domains/](rules/domains/)와 [rules/traits/](rules/traits/)에는 **지금까지 선언된 것만** 파일이 존재합니다. 현재 라이브러리:

- **domains**: `saas`
- **traits**: `transactional`, `regulated`, `audit-heavy`, `integration-heavy`

새 프로젝트가 카탈로그에 있는 다른 값을 선언하면 (예: `domain: ecommerce`, `traits: [content-heavy, real-time]`), 해당 규칙 파일은 **그 프로젝트 PR에서 직접 작성**해야 합니다. Init 스크립트는 경고만 띄우고 진행합니다.

규칙 파일 작성 시 참고:

- 포맷: [rules/traits/transactional.md](rules/traits/transactional.md) 또는 [rules/domains/saas.md](rules/domains/saas.md)를 템플릿으로 사용
- 필수 섹션: Scope / Mandatory Rules / Forbidden Patterns / Required Artifacts / Interaction with Common Rules / Checklist
- **프로젝트 특화 경로 금지**: 본문에 `apps/my-service/` 같은 구체 경로를 쓰지 말 것. 라이브러리 재사용성을 해침. 프로젝트별 적용 범위는 별도 섹션 또는 파일로 분리.

정책 근거: [rules/README.md §On-Demand Generation Policy](rules/README.md#on-demand-generation-policy)

---

## Back-porting Improvements

한 프로젝트에서 템플릿 인프라(rules/, platform/, libs/, .claude/, scripts/, tasks/templates/)를 개선했다면, 다른 프로젝트가 혜택을 받으려면 수동 역이식이 필요합니다.

**권장 절차:**

1. 개선 사항을 diff로 추출: `git diff <previous-ref> <current-ref> -- rules/ platform/ libs/ .claude/ scripts/ tasks/templates/`
2. 원본 템플릿 저장소(이 저장소)에 같은 변경을 반영
3. 다른 프로젝트에서 필요 시 수동으로 sync:
   ```bash
   cd /path/to/other-project
   # 비교
   diff -r ../this-template/rules/ rules/
   # 선택적으로 복사
   cp -r ../this-template/rules/traits/new-trait.md rules/traits/
   ```

중앙 sync 스크립트는 현재 없습니다. 각 프로젝트는 **템플릿의 독립 스냅샷**을 소유하고, 개선은 수동 전파합니다. 이 트레이드오프는 의도적입니다 — 자동화된 sync는 drift를 숨기고 프로젝트별 커스터마이징을 어렵게 만듭니다.

---

## Validation

Init 후 선언과 라이브러리의 일치를 확인:

```bash
# PROJECT.md의 선언 값
grep -E "^(name|domain|traits|service_types):" PROJECT.md

# 카탈로그 값
grep '^- ' .claude/config/domains.md
grep '^- ' .claude/config/traits.md

# 실제 존재하는 규칙 파일
ls rules/domains/ rules/traits/
```

AI 기반 검증은 [.claude/commands/validate-rules.md](.claude/commands/validate-rules.md) 스킬 사용.

---

## FAQ

**Q: 템플릿의 `rules/domains/saas.md`를 쓰는데 내 프로젝트는 `fintech`인데요?**
A: 선언되지 않은 규칙 파일은 로드되지 않습니다. `PROJECT.md`에 `domain: fintech`만 선언하면 `saas.md`는 읽히지 않습니다. 정리 차원에서 `rm rules/domains/saas.md` 해도 무방하고, 나중에 saas를 추가할 때 참고용으로 남겨둬도 됩니다.

**Q: `.claude/config/` 카탈로그도 수정해야 하나요?**
A: 보통은 아닙니다. 카탈로그는 모든 가능한 domain/trait 값의 목록으로 범용입니다. 카탈로그에 없는 값을 선언하면 [CLAUDE.md](CLAUDE.md)의 Hard Stop Rules에 걸립니다. 새 domain/trait을 추가하려면 카탈로그 + `rules/` 상세 파일 + `rules/taxonomy.md`를 같은 PR에서 갱신하세요.

**Q: `platform/`의 파일을 프로젝트 특화로 수정해도 되나요?**
A: 일부는 가능하지만 주의가 필요합니다. `platform/error-handling.md`의 도메인별 에러 코드 섹션이나 `platform/architecture.md`의 예시 다이어그램은 프로젝트에 맞게 조정하는 것이 정상입니다. 반면 `platform/testing-strategy.md`, `platform/shared-library-policy.md` 같은 원칙 수준 규칙은 건드리지 말 것 — 템플릿 전반에 drift를 초래합니다.

**Q: `apps/`는 템플릿에 있어야 하나요?**
A: 아닙니다. `apps/`는 프로젝트 콘텐츠이고, 새 프로젝트는 빈 상태에서 시작합니다. 첫 서비스는 `tasks/ready/TASK-BE-001-*-bootstrap.md`를 통해 생성합니다.

**Q: Init 스크립트가 만든 PROJECT.md의 TODO는 꼭 채워야 하나요?**
A: 네. Purpose·Domain Rationale·Trait Rationale·Out of Scope가 비어 있으면 나중에 "왜 이 도메인/trait을 골랐는가"를 되찾기 어렵습니다. 한두 문단이면 충분합니다.
