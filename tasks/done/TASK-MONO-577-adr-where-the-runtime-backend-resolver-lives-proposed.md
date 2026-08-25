# Task ID

TASK-MONO-577

# Title

런타임 백엔드 주소 해석기를 **어디에 둘 것인가** — 세 앱이 서로 다른 JS 의존 세계에 살아서 둘 데가 없다. **PROPOSED ADR** 로 결정을 세운다.

# Status

done

# Owner

monorepo

# Task Tags

- adr
- structure
- decision

---

# Goal

`ADR-MONO-067` D2(*"주소는 빌드 산출물이 아니라 런타임 조회 결과여야 한다"*)를 구현하려면 세 앱이
같은 조회 로직을 써야 한다. **그 코드가 살 자리가 지금 없다.** 어디에 둘지를 ADR 로 정한다.

🔴 이 티켓은 `ADR-MONO-067` 이 다루지 않은 축이다. 067 은 *무엇을* 할지 정했고, 이건 *어디에
둘지*이며, **구조 결정이라 ADR 사안**이다(`platform/architecture-decision-rule.md`).

---

# Context — 실측 (2026-08-23)

## 세 앱은 서로 다른 JS 세계에 산다

| 앱 | 워크스페이스 | 공용 패키지 |
|---|---|---|
| web-store | `projects/ecommerce-microservices-platform/pnpm-workspace.yaml` | `@repo/api-client`·`@repo/types`·`@repo/ui`·`@repo/utils` |
| fan | `projects/fan-platform/pnpm-workspace.yaml` | **없음** |
| console | **워크스페이스 없음** — 자기 `pnpm-lock.yaml` 단독 | **없음** |

그리고 루트 `package.json` 이 스스로 못 박는다:

> `"description": "Monorepo root — thin shortcut scripts that delegate runtimes to the owning
> project. **No workspace / no dependencies at this level.**"`

⇒ **프로젝트를 가로지르는 JS 코드를 둘 자리가 오늘 존재하지 않는다.**

## 왜 repo-root `libs/` 가 답이 아닌가

`CLAUDE.md` § Repository Layout: repo-root `libs/` 는 **Java 공유 라이브러리**이고
**project-agnostic 이어야 한다**(HARDSTOP-03). 새 JS 워크스페이스를 루트에 만드는 것은
루트 `package.json` 의 자기 선언을 뒤집는 **구조 변경**이다.

## 무엇을 공유해야 하는가 — 작다

`GET {DEMO_API_BASE}/status` → `{state, ip, used_minutes, budget_minutes}` 를 부르고
TTL 캐시하는 ~30줄. 🔵 조회 소스는 **이미 있다**(론처가 쓰는 그 엔드포인트) — 새 인프라 0.

🔴 그런데 **세 앱의 fetch 스택이 다르다**: web-store 는 axios(`@repo/api-client`),
fan 은 네이티브 fetch(`gatewayFetch`), console 은 159개 route handler. 공유 단위를
*"주소를 돌려주는 함수"* 로 좁힐지, *"클라이언트째"* 로 넓힐지가 선택지를 가른다.

---

# Acceptance Criteria

## AC-0 — 재측정: 위 표가 여전히 참인가

```bash
git ls-files | grep pnpm-workspace          # 워크스페이스 개수
head -5 package.json                        # 루트의 자기 선언
git ls-files | grep -E "packages/.*/package.json"
```

어긋나면 STOP — 누군가 이미 공용 자리를 만들었을 수 있고, 그러면 이 결정의 전제가 다르다.

## AC-1 — 선택지를 **최소 3개**, 각각 실패 모드와 함께

| # | 안 | 대표 실패 모드 |
|---|---|---|
| A | **3벌 복사** | 🔴 한 벌만 고쳐진다 — `CLAUDE.md` 가 project-scoped libs 를 도입한 이유가 정확히 이 실패다 |
| B | **새 repo-root JS 패키지** | 루트 `package.json` 의 선언을 뒤집는다. 세 워크스페이스가 어떻게 참조하나(console 은 워크스페이스도 없다) |
| C | **복사 + 동일성 가드** | 드리프트를 *막지는* 못하고 *잡는다*. 가드가 세 사본을 비교 — 싸지만 정직하다 |

각각 **되돌리기 비용**과 **console 을 어떻게 다루는지**를 반드시 적는다(console 만 워크스페이스가
없어서 A·B·C 의 난이도가 앱마다 다르다).

## 🔴🔴 AC-1.5 — **"해석기가 아예 필요 없다"** 를 선택지에 넣어라 (2026-08-25 추가)

이 티켓은 *어디에 둘까* 를 묻는다. 그 전에 **왜 필요한가**를 한 번 봐야 한다.

해석기가 필요한 이유는 하나뿐이다 — **데모 주소가 부팅마다 바뀐다.** 그런데
`ADR-MONO-067` 의 선택지 A/B/C 는 **`sslip.io` 를 전제로 깔고** 시작하며,
**"주소를 안 바뀌게 만든다"** 는 축을 열거하지 않았다.

**실측 (2026-08-25)**: 저장소 전체에 `Elastic IP` · `aws_eip` · `Route53` · "고정 IP" 언급이
**0건**이다. `infra/demo/aws/terraform/` 에 그 리소스가 없고, **기각된 기록도 없다.**
⇒ 검토하고 버린 것이 아니라 **선택지에 오른 적이 없다.**

### 움직이는 주소가 지금 물리고 있는 것 (전수)

| # | 무엇 | 존재 이유 |
|---|---|---|
| ① | `infra/demo/check-label-drift.sh` | Traefik 라벨에 sslip 호스트명이 **각인**되어 재시작 뒤 어긋난다 (`TASK-MONO-553`) |
| ② | `demo-boot.sh` 의 `DEMO_DOMAIN` 파생 | 부팅마다 IMDS 로 IP 를 읽어 도메인을 만든다 |
| ③ | 론처의 `demoHost()` 동적 링크 | 화면 링크를 IP 에서 조립한다 |
| ④ | `CONSOLE_PUBLIC_ORIGIN` (`TASK-MONO-358`) | 콜백이 `console.local` 로 302 하던 것을 EC2 에서 실측해 고친 것 |
| ⑤ | **이 티켓의 해석기 자체** | 세 앱이 런타임에 주소를 물어야 하는 이유 |
| ⑥ | `TASK-MONO-576` **AC-1.5** — 움직이는 `issuer` | next-auth 는 `issuer` 를 **설정값**으로 받는다 |

🔵 **주소가 고정되면 ①~⑥ 이 전부 사라지거나 크게 줄어든다.** 이건 "해석기를 어디 둘까" 보다
**한 층 위의 질문**이고, 그래서 이 티켓이 답해야 한다.

### 그래서 선택지에 이것을 추가한다

| # | 안 | 대가 |
|---|---|---|
| **D** | **주소를 고정한다** (Elastic IP / 안정 DNS) | 🔴 **월 과금**. 데모는 예산이 600분/월 = **한 달의 약 1.4%** 만 켜져 있으므로, 유휴 상태의 요금이 그대로 비용이다 |

🔴 **금액을 여기 적지 마라 — 계정에서 확인하라.** AWS 의 퍼블릭 IPv4 과금 정책은 바뀌었고,
"유휴 EIP 만 과금" 이라는 옛 상식은 더 이상 정확하지 않을 수 있다. `TASK-MONO-575` 가 이미
소유자 대시보드 확인을 대기 중이므로 **같이 확인하면 된다.**

🔵 **D 를 고르면 이 티켓의 나머지(A/B/C = 어디에 둘까)는 통째로 불필요해진다.** 그것이
"공유하지 않는다"(AC-3)보다 더 강한 결론이고, **그 결론이 가능하다는 것을 적지 않는 티켓은
결정하는 티켓이 아니다.**

⚠️ **범위 주의**: D 는 `ADR-MONO-067` 의 선택지 공간을 건드리므로, 이 티켓이 단독으로 채택할 수
없다. **관측과 대가를 적어 067 로 올리는 것**까지가 이 티켓의 몫이다.

## AC-2 — 공유 **단위**를 정한다

*"주소 문자열을 돌려주는 함수"* 인가, *"조회+캐시+폴백 정책"* 인가, *"HTTP 클라이언트째"* 인가.
🔴 **좁을수록 세 앱에 잘 맞고, 넓을수록 중복이 줄지만 스택 차이에 걸린다.** 어느 쪽이든
**왜 그 선을 그었는지**를 적는다.

## AC-3 — 🔵 "공유하지 않는다"도 정당한 결론이다

30줄짜리를 위해 새 구조를 만드는 비용이 드리프트 위험보다 클 수 있다. **A(복사)를 고르는 것도
결정**이고, 그 경우 AC-1 의 실패 모드를 어떻게 다룰지(C 의 가드)를 함께 적어야 한다.
[[feedback_repo_knows_what_it_does_not_say]]

## AC-4 — PROPOSED 로 남긴다

- Status **PROPOSED**, `## Decision` 은 소유자 지정 대기.
- 🔴 **self-ACCEPT 0** 을 본문에 명시.
- `docs/adr/INDEX.md` 행 추가(인덱스 드리프트 가드 있음).

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § D2
- `CLAUDE.md` § Repository Layout — 특히 project-scoped `libs/` 절(같은 문제의 Java 판 선례)
- `platform/shared-library-policy.md`
- `platform/architecture-decision-rule.md`

# Related Contracts

없음 — 코드 배치 결정이고 서비스 간 계약을 바꾸지 않는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 한 앱만 이관하기로 축소된다 | 공유 문제가 **사라진다**. 이 ADR 은 그때 불필요해지므로, 범위 축소를 먼저 확인한다 |
| console 이 워크스페이스로 편입된다 | 별도의 큰 변경이다. 이 ADR 의 **선행이지 일부가 아니다** — 그렇게 적는다 |
| `@repo/*` 를 프로젝트 밖에서 재사용하려 한다 | ecommerce **프로젝트 소유**다. 다른 프로젝트가 쓰려면 승격이고, 그건 별도 ADR 이다(`CLAUDE.md` 명시) |
| D2 가 필요 없어진다 | `TASK-MONO-576`(D4)이 **EC2 TLS 종단**으로 가면 주소 축이 달라질 수 있다. 🔴 **576 결과를 먼저 본다** |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 복사를 골랐는데 가드가 없다 | 한 사본만 수정된 커밋 | AC-3 위반. C 의 가드가 A 의 필수 짝이다 |
| 새 패키지를 만들었는데 console 이 못 쓴다 | console 빌드 실패 | AC-1 이 console 을 따로 다루라고 요구하는 이유 |
| 30줄을 위해 구조를 바꿨다 | 리뷰에서 "이게 이만한 일인가" | AC-3 이 이것을 막는다 — **"공유하지 않는다"가 후보에 있어야 한다** |
| 576 보다 먼저 정해 버린다 | D4 가 TLS 축으로 가서 전제가 바뀜 | Edge Case 마지막 항목. **576 을 먼저 본다** |
