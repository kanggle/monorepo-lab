# GitHub Repository Setup Guide

GitHub에 레포를 올린 후 포트폴리오 노출을 위해 설정할 항목들입니다.

---

## 1. Repository 생성 & Push

```bash
# GitHub에서 레포 생성 후
git remote add origin https://github.com/{username}/ecommerce-microservices-platform.git
git branch -M main
git push -u origin main
```

---

## 2. Repository Description

Settings 또는 레포 메인 페이지에서 설정:

**Description:**
```
도메인 기반 마이크로서비스 이커머스 플랫폼 — Spring Boot, Next.js, Kafka, K8s
```

**Website:** (배포 후 URL 입력)

---

## 3. Repository Topics

레포 메인 페이지 About 섹션의 톱니바퀴 아이콘을 클릭하여 추가:

```
java spring-boot microservices nextjs react typescript
kafka postgresql redis elasticsearch kubernetes docker
ddd hexagonal-architecture event-driven ecommerce monorepo
```

---

## 4. CI 뱃지 활성화

README.md 상단의 주석 처리된 CI 뱃지를 해제합니다:

```markdown
![Backend CI](https://github.com/{username}/{repo}/actions/workflows/backend-ci.yml/badge.svg)
![Frontend CI](https://github.com/{username}/{repo}/actions/workflows/frontend-ci.yml/badge.svg)
```

`{username}/{repo}`를 실제 값으로 변경하세요.

---

## 5. Social Preview Image (선택)

Settings → Social preview에서 이미지를 업로드하면 SNS 공유 시 미리보기로 표시됩니다.

**권장 크기:** 1280 x 640px

포함할 내용:
- 프로젝트 이름
- 핵심 기술 스택 아이콘 (Spring Boot, Next.js, Kafka, K8s)
- 간단한 아키텍처 다이어그램

---

## 6. GitHub Profile README 연동 (선택)

GitHub 프로필 README에서 이 프로젝트를 핀 고정하고 링크:

```markdown
### Projects
- [E-Commerce Microservices Platform](https://github.com/{username}/{repo})
  — 12개 마이크로서비스 + 2개 프론트엔드, DDD/Hexagonal/Layered 아키텍처
```
