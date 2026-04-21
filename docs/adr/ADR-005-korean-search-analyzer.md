# ADR-005: Elasticsearch 한국어 analyzer로 `nori` 채택

- **Status**: Accepted
- **Date**: 2026-04
- **Tags**: search, elasticsearch, korean, i18n

## Context

search-service 초기 버전은 Elasticsearch `standard` analyzer를 썼다. 이 선택은 공식 이미지를 그대로 쓸 수 있고 매핑이 단순하다는 장점이 있었으나, **한국어 이커머스**에서는 실사용 수준이 아니었다.

`standard`는 공백/구두점 기준으로 토큰화한다. 예를 들어 `"슬림핏 데님 청바지"`는 `["슬림핏", "데님", "청바지"]` 세 토큰으로만 색인된다. 그 결과:

| 쿼리 | standard 결과 | 사용자 기대 |
|---|---|---|
| 티셔츠 | ✓ | ✓ |
| 청바지 | ✓ | ✓ |
| **바지** | **✗ 0건** | 청바지 매칭 기대 |
| **슬림** | **✗ 0건** | 슬림핏 매칭 기대 |
| **견과** | **✗ 0건** | 견과류 매칭 기대 |

한국어 사용자는 상품명을 정확히 기억하기보다 **부분 키워드**로 검색하는 빈도가 높다. 현재 시드는 상품 8개라 영향이 제한적이지만, 이 구조 그대로 확장되면 검색 이탈률이 급격히 늘어난다.

## 고려한 대안

### A. 그대로 `standard` 유지 + 문서화

- **장점**: 설정 변경·이미지 빌드·재색인 없음
- **단점**: 실제 한국어 검색 품질 미달. "알고 있지만 안 함" 포지션
- **평가**: 토이 샘플용 스코프라면 가능하나, "포트폴리오 수준의 한국어 이커머스"를 표방하면서 취하기엔 약함

### B. `ngram` 토크나이저

- **장점**: 플러그인 불필요, 부분 매칭 가능
- **단점**: 인덱스 크기 폭증(상품명 N자 → N×(N+1)/2 토큰), 의미 없는 문자 조각도 토큰화. 한국어 형태소를 모름
- **평가**: 단순 부분 매칭은 되지만 검색 품질은 standard만도 못할 수 있음

### C. `nori` (Elasticsearch 공식 한국어 분석기) — **채택**

- **장점**: 한국어 형태소 분석 + 복합명사 분해(decompound). `"청바지"`를 `"청바지" + "청" + "바지"`로 색인. 조사·어미 제거용 `nori_part_of_speech` 필터 내장
- **단점**: 공식 이미지에 기본 포함 아님 → 커스텀 Dockerfile 필요. 과분해 리스크(짧은 어미 매칭)

## Decision

**`nori`를 채택**하고 다음 구조로 운영한다.

### 1. ES 이미지 커스텀화

[infra/elasticsearch/Dockerfile](../../infra/elasticsearch/Dockerfile):
```dockerfile
FROM elasticsearch:8.15.0
RUN elasticsearch-plugin install --batch analysis-nori
```

[docker-compose.yml](../../docker-compose.yml)은 `image:` 대신 `build:`로 이 Dockerfile을 빌드해 `ecommerce-microservices-platform-elasticsearch:nori`로 태깅. 공식 이미지 업그레이드 시 한 줄만 갱신하면 됨.

### 2. 인덱스 매핑

- **tokenizer**: `nori_tokenizer`, `decompound_mode: mixed`
  - `"청바지"` → `["청바지", "청", "바지"]` 전부 색인 → 부분 매칭 가능
- **analyzer**: custom — `nori_mixed` + `nori_part_of_speech` + `lowercase`
  - 조사·어미 제거, 영문 대소문자 통일
- **적용 필드**: `name`, `description` (keyword 필드에는 적용하지 않음)

### 3. 마이그레이션 정책

기존 `standard` 기반 인덱스와 nori 매핑은 호환되지 않는다. Elasticsearch는 기존 필드의 analyzer 변경을 지원하지 않으므로 **delete-and-recreate**가 불가피.

[IndexInitializer](../../apps/search-service/src/main/java/com/example/search/adapter/outbound/elasticsearch/IndexInitializer.java)가 기동 시:
1. 인덱스 존재 여부 확인
2. 존재하면 `name` 필드의 analyzer가 `nori_korean`인지 점검
3. 아니면 인덱스 삭제 후 최신 스펙으로 재생성

데이터 재구축은 [`POST /api/search/admin/reindex`](../../apps/search-service/src/main/java/com/example/search/adapter/inbound/web/SearchAdminController.java)로 수동 트리거. 프로덕션 무중단 전환이 필요하면 alias 기반 롤오버(기존 인덱스 유지 → 새 인덱스 생성/색인 → alias 교체 → 구 인덱스 삭제)가 더 안전하나, 현 스코프에서는 다루지 않는다.

## Consequences

### Positive

- **실사용 수준의 한국어 검색**:
  | 쿼리 | 결과 |
  |---|---|
  | 바지 | 청바지 매칭 ✓ |
  | 슬림 | 슬림핏 데님 청바지 매칭 ✓ |
  | 견과 | 견과류 선물세트 매칭 ✓ |
  | 와이드 | 와이드핏 슬랙스 매칭 ✓ |
- **ES 공식 플러그인**: 비공식 패키지 의존 없음, 버전 호환성 명시적
- **확장 지점 확보**: 사용자 사전(`user_dictionary`), 동의어(`synonyms`) 필터 추가가 용이한 구조

### Negative

- **빌드 시점 의존**: ES 이미지를 로컬에서 빌드해야 함 (CI에서도 마찬가지). 공식 이미지를 그대로 `pull`만 하는 구성보다 한 단계 추가
- **과분해 가능성**: `"이어"` 같은 짧은 쿼리가 어미로 오분해되면 과매칭(현재 검증에서 관찰됨). 실 서비스에서는 `user_dictionary`로 상품 도메인 어휘를 등록하거나 `min_gram`을 활용해 튜닝 필요
- **Testcontainers 영향 가능성**: search-service의 통합 테스트가 공식 ES 이미지를 쓰는 경우, nori 없이 돌면 매핑 생성 단계에서 실패한다. 테스트 이미지도 동일 플러그인을 포함시키거나 프로필로 분리 필요

## 전환 트리거와 역행 조건

nori의 과분해가 실제 지표에 부정적으로 나타나면 역행도 고려 가능하다. 판단 근거:

- **전환 유지/심화**: 부분 매칭 성공률이 사용자 세션에서 체감적으로 의미 있는 수준이면 유지. 도메인 어휘 사전 추가 검토
- **재검토**: 과매칭(모든 쿼리가 거의 모든 상품을 반환)이 로그에서 반복되면, `decompound_mode: none`으로 약화하거나 특정 어미를 `stoptags`로 배제

## References

- [ES 공식 nori 문서](https://www.elastic.co/guide/en/elasticsearch/plugins/8.15/analysis-nori.html)
- [apps/search-service/.../IndexInitializer.java](../../apps/search-service/src/main/java/com/example/search/adapter/outbound/elasticsearch/IndexInitializer.java)
- [infra/elasticsearch/Dockerfile](../../infra/elasticsearch/Dockerfile)
- 관련 ADR: 없음
