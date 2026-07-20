# ADR-003: web-store / admin-dashboard 아키텍처 이원화

- **Status**: Superseded by ADR-MONO-031
- **Date**: 2026-03
- **Tags**: frontend, architecture, nextjs

> **부분 supersede 안내 (2026-07-20).** 이 ADR은 두 가지를 결정했다: ① `web-store` → FSD, ② `admin-dashboard` → Layered by
> Feature. [`ADR-MONO-031`](../../../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md)
> (repo-root, **ACCEPTED 2026-06-13**)이 그중 **②만** 뒤집었다 — 독립 `admin-dashboard` 앱을 폐기하고 운영자 UI를
> platform-console로 흡수하는 결정으로, `TASK-MONO-259`에서 이미 실행 완료됐다.
> `specs/services/admin-dashboard/architecture.md`는 그 결과로 **RETIRED** 헤더("Absorbed into platform-console… Do not
> implement new features against this spec")를 달고 있다. **①(web-store → FSD)은 이 supersede의 영향을 받지 않고
> 그대로 유효하다** — ADR-MONO-031은 web-store를 다루지 않았으며, web-store는 여전히 독립 스토어프런트로 남아
> FSD 아키텍처를 따른다. 아래 본문의 admin-dashboard 관련 서술(§ Decision 후반부, § Consequences 일부)은 역사적
> 기록으로만 읽을 것 — 새 작업의 근거로 삼지 말 것.

## Context

프론트엔드 2개를 가진다:

| 앱 | 성격 | 주 사용자 |
|---|---|---|
| `web-store` | 고객향 스토어 | 일반 구매자 |
| `admin-dashboard` | 운영 관리자 | 내부 직원 |

두 앱은 **복잡도·다양성·성능 요구**가 근본적으로 다르다:

- **web-store**: SEO·LCP·CLS·SSR/SSG 중요. 상품 상세·검색·장바구니·결제 각 피처가 상이한 상태 모델과 비즈니스 로직을 가짐. 복잡성·다양성 큼.
- **admin-dashboard**: 인증 뒤 클라이언트 렌더. 대부분 **동일한 CRUD 패턴**(목록·상세·생성·수정)의 반복. 도메인 복잡도 낮음, 일관성·개발 속도 중요.

"한 가지 아키텍처로 통일하자"는 유혹이 강하다. 하지만 두 앱의 제약이 다르다.

## Decision

**두 앱에 서로 다른 아키텍처를 명시적으로 적용**한다:

### web-store → Feature-Sliced Design (FSD)
- 레이어: `app` → `widgets` → `features` → `entities` → `shared`
- 의존 방향 엄격: 상위만 하위를 참조
- 피처 간 직접 참조 금지 → widgets 레이어에서만 조합
- 참조: [specs/services/web-store/architecture.md](../../specs/services/web-store/architecture.md)

### admin-dashboard → Layered by Feature
- 레이어: `app` → `features` → `shared`
- 각 피처 폴더는 자기 components/hooks/api/types를 소유
- 공통 패턴(DataTable, FormField, PageLayout)은 `shared/`에 위치
- 참조: [specs/services/admin-dashboard/architecture.md](../../specs/services/admin-dashboard/architecture.md)

두 앱 모두 Next.js App Router + TypeScript + 공유 packages(@repo/ui, @repo/api-client, @repo/types, @repo/utils)를 사용하되, **앱 내부 구조는 갈린다.**

## Consequences

### Positive
- **web-store**: 피처가 커져도 FSD의 엄격한 의존 방향 덕분에 피처 간 오염이 어려움. 팀이 나뉘어 병렬 개발 가능.
- **admin-dashboard**: CRUD 보일러플레이트 반복을 `shared/hooks/useInvalidatingMutation`([참조](../../apps/admin-dashboard/src/shared/hooks/use-invalidating-mutation.ts)) 같은 팩토리로 공격적으로 중앙화 → 신규 피처 추가 비용 최소
- **규칙의 비대칭이 오히려 건강함**: 같은 `features/` 단어라도 두 앱에서 의미가 다름을 명시적으로 선언

### Negative
- 신규 멤버 온보딩 비용: 두 앱 중 어느 쪽부터 보느냐에 따라 학습 곡선 꺾임. 각 앱의 `architecture.md`를 먼저 읽도록 워크플로 강제.
- 공통 훅·유틸을 꺼낼 때 **어느 앱에 맞춰야 하나** 판단 필요. 원칙: "두 앱에 모두 맞지 않으면 공용화하지 않는다"

### 버린 대안: 두 앱 모두 FSD 통일
- **왜 안 택했나**: admin-dashboard의 반복 CRUD에 FSD 5계층은 과잉. `widgets/`가 거의 비어있게 되고, `entities/`·`features/` 구분이 억지스러워짐. 한 달 안에 규칙이 흐려질 가능성 큼.

### 버린 대안: 두 앱 모두 Layered by Feature
- **왜 안 택했나**: web-store의 결제·장바구니·검색처럼 **서로 조합되는** 피처가 많은데, Layered-by-Feature는 피처 간 조합 레이어가 없어 widgets 역할을 어디에 둘지 애매. 피처 간 직접 import가 쉽게 섞여 들어감.

## 검증: 실제로 규칙이 지켜지고 있는가

최근 admin-dashboard 리팩토링([ADR 미발행, commit 2ee626d](https://github.com/kanggle/ecommerce-microservices-platform/commit/2ee626d))에서 12개 뮤테이션 훅을 공용 팩토리로 통합할 때, **어떤 훅도 `features/` 간 직접 참조를 만들지 않았음**이 확인됐다. 공용화는 전부 `shared/hooks/`로 올라갔다.

반대로 web-store의 `HeroBanner` 이미지 최적화([b60ea50](https://github.com/kanggle/ecommerce-microservices-platform/commit/b60ea50))는 `widgets/hero/`에 국한됐다 — FSD 경계가 지켜짐.

## References

- [specs/services/web-store/architecture.md](../../specs/services/web-store/architecture.md)
- [specs/services/admin-dashboard/architecture.md](../../specs/services/admin-dashboard/architecture.md)
- [README.md § Code Quality: 리팩토링 사례](../../README.md#code-quality-리팩토링-사례)
