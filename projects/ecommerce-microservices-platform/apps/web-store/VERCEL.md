# Vercel 배선 — web-store (`TASK-MONO-582`)

`vercel.json` 옆의 이 파일이 그 JSON 이 담을 수 없는 것을 담는다. **JSON 에는 주석이 없고,
`vercel.json` 은 스키마가 엄격하다** — 이 저장소는 그 문으로 두 번 죽었다(§ 아래).

| | |
|---|---|
| Vercel 프로젝트 | **⏳ 아직 없다 — 소유자가 만들어야 한다** (아래 § 소유자 절차) |
| Root Directory | `projects/ecommerce-microservices-platform/apps/web-store` |
| 프레임워크 | Next.js 15 App Router — **감지는 대시보드에 맡긴다** |
| `vercel.json` 이 선언하는 것 | `installCommand` + `ignoreCommand` 둘뿐 |
| 무시 규칙의 경로 목록 | **[`vercel-ignore.sh`](./vercel-ignore.sh)** — JSON 안이 아니다 |

---

## 🔴 소유자 절차 — 저장소가 못 하는 일

1. Vercel 대시보드에서 **새 프로젝트**를 만들고 이 저장소를 연결한다.
2. **Root Directory** 를 `projects/ecommerce-microservices-platform/apps/web-store` 로 지정한다.
3. **환경변수 `DEMO_API_BASE`** 를 넣는다:

   ```
   cd infra/demo/aws/terraform && terraform output api_base_url
   ```

   🔴 이 값은 **손으로 하는 배선**이다. 예전에는 terraform 이 S3 사본의 `config.js` 를 자기
   상태에서 렌더했으므로 *"배포된 페이지가 자기 API 와 어긋나는 것"* 이 표현 불가능했는데,
   `TASK-MONO-579` 가 그 사본을 폐기하면서 그 성질이 사라졌다. **API 를 재생성하면 사람이
   두 자리를 맞춰야 한다.** *없는 것* 은 앱이 잡지만(해석기가 아예 호출하지 않는다),
   ***낡은 것*** 은 아직 아무도 안 잡는다.

4. 첫 배포 로그를 확인하고 **§ 첫 배포에서 깨질 수 있는 지점** 을 이 문서에 회신한다.

🔵 `DEMO_API_BASE` 가 **없으면** 앱은 조용히 정상 동작한다 — 데모가 아닌 환경이라고 보고
기존 env 사슬(`API_URL_INTERNAL` → `NEXT_PUBLIC_API_URL` → `localhost:8080`)로 간다.
그래서 **환경변수를 빠뜨린 배포는 초록으로 뜨고 데이터만 안 나온다.** 3번을 건너뛰지 마라.

---

## 🔴 첫 배포에서 깨질 수 있는 지점 — **저장소에서 판정 불가**

아래 둘은 로컬에서 재현할 수 없다. *"아마 괜찮다"* 라고 적지 않는다 — **판정 출처는 소유자의
첫 배포 로그 하나뿐**이고, 그 로그를 본 뒤 이 절을 사실로 갱신한다.

### 1. `output: 'standalone'`

`next.config.ts` 에 있고 **Docker 용**이다(`Dockerfile` 이 그 산출물을 쓴다). Vercel 은 자기
빌드 출력을 쓰므로 충돌하면 **첫 배포에서** 드러난다.

🔵 **지우지 마라.** 그 값은 데모 호스트의 컨테이너 빌드가 쓰고 있고, 지우면 이쪽이 아니라
**저쪽이** 죽는다. 충돌이 실제로 확인되면 환경 분기로 가른다.

### 2. `next-auth` v5 의 `NEXTAUTH_SECRET`

`src/shared/auth/auth.ts:69` 가 모듈 최상위에서 `NextAuth({ secret: process.env.NEXTAUTH_SECRET })`
를 부른다. 빌드 중 정적 생성이 그 경로를 타면 **값 부재가 빌드를 죽일 수 있다.**

🔴 OIDC 축 전체(`OIDC_ISSUER_URL` · 클라이언트 시크릿 · 콜백 URL)는 **`ADR-MONO-067` 이 D4
로 떼어 낸 별도 결정**이고 `TASK-MONO-576` 이 그 ADR 을 맡는다. **여기서 즉흥으로 정하지
마라** — HTTPS 프런트 ↔ 평문 IdP 는 이 저장소가 두 방향 모두 데인 축이다.
빌드만 통과시키면 되는 경우라도, 넣은 값과 그 이유를 여기에 적는다.

---

## 무시 규칙 — 왜 목록이 JSON 밖에 있는가

**Vercel 스키마는 명령 문자열에 `maxLength: 256` 을 건다.** `TASK-MONO-562` 가 형제
프로젝트(`kanggle-fan`)의 `ignoreCommand` 에 pathspec 5개를 직접 넣어 **261자**(+5)가 됐고,
그래서 `vercel.json` 이 거부되어 **모든 배포가 0초에 죽었다** — 빌드 로그조차 남지 않았다.

| 파일 | `ignoreCommand` 길이 | 한도 256 |
|---|---:|---|
| `kanggle-fan` (562 판 — 죽은 쪽) | **261** | ❌ |
| `kanggle-fan` (563 이 고친 판) | 99 | ✅ |
| **이 파일** | **113** | ✅ |

목록은 [`vercel-ignore.sh`](./vercel-ignore.sh) 에 있고 거기엔 길이 제한이 없다. 제한 자체는
`scripts/check-vercel-build-triggers.sh` 의 **칸 (5)** 가 지킨다 — 그 칸은 원문이 아니라
**디코드된 값**의 길이를 잰다(`\"` 는 원문 2자 · 값 1자라 grep 으로 세면 틀린다).

---

## 이 프로젝트를 만들면 Vercel 프로젝트가 **셋**이 된다

`scripts/check-vercel-build-triggers.sh` 의 하한(`FLOOR`)이 **3** 으로 올라간다. 그 가드는
모집단을 `git ls-files '*vercel.json'` 로 **발견**하므로, 이 파일을 놓는 것만으로 새 무시
규칙을 **실제로 실행해서** 대조한다(문자열 grep 이 아니다).

### 🔴🔴 배포 압력 — 잰 축과 **안 잰 축**

최근 30일(`origin/main` 커밋 **479개**) 중 각 프로젝트의 트리거 경로를 건드린 커밋:

| 프로젝트 | 빌드 트리거 커밋 | 비율 |
|---|---:|---:|
| `kanggle-portfolio` (론처) | 10 | 2.1% |
| `kanggle-fan` | 14 | 2.9% |
| **web-store** | **10** | **2.1%** |

**이 표는 「빌드」를 세었다.** Vercel 은 푸시마다 **배포를 먼저 만들고** `ignoreCommand` 는
그 뒤에 *빌드할지*를 정한다 — `Canceled by Ignored Build Step` 은 **배포가 생긴 뒤의 상태**다.
즉 세 번째 프로젝트는 **푸시마다 배포 1건을 무조건 더 만든다.**

🔴 **그리고 푸시 수는 저장소에서 잴 수 없다** — `git log` 는 커밋을 세지 푸시를 세지 않고,
PR 한 건의 5커밋 푸시는 배포 1건이다. **그러니 위 표를 "한도에 여유가 있다"의 근거로 쓰지
마라.** 그 판정의 출처는 소유자 대시보드의 사용량 하나뿐이다
(`ADR-MONO-067` AC-0 ④ 가 아직 미측정으로 남아 있는 항목이 정확히 이것이다).
