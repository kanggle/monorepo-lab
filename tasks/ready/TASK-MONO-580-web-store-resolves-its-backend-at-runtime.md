# Task ID

TASK-MONO-580

# Title

`ADR-MONO-067` **단계 2** — web-store 가 백엔드 주소를 **런타임에** 얻고, 방문자를 Vercel 판으로 보낸다. **승인 불필요분 전체.**

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- frontend
- demo

---

# ⏳ 선행 — **없다. 지금 착수 가능하다.**

| # | 선행 | 상태 |
|---|---|---|
| 1 | `ADR-MONO-067` AC-0 ①② | ✅ `TASK-MONO-565` · `571` |
| 2 | 해석기를 **어디 둘지** | ✅ `ADR-MONO-068` ACCEPTED — **앱 안에** (`TASK-MONO-577`) |
| 3 | 론처의 집 | ✅ Vercel 하나 (`TASK-MONO-579`, apply 만 소유자 대기) |

🔵 **AC-0 ③④(OIDC·한도)는 이 티켓의 선행이 아니다** — web-store 는 **D4 축에 안 걸린다**.
그것이 067 이 web-store 를 **파일럿**으로 고른 이유다.

🔴 **이 티켓은 AMI 재굽기를 필요로 하지 않는다.** 데모 호스트의 옛 사본을 치우는 일은
`TASK-MONO-581` 로 분리했다(재굽기 승인 대기). 이 티켓의 산출물은 **전부 Vercel 배포**로 반영된다.

---

# Goal

**(B) 를 처음으로 실증한다** — 브라우저는 Vercel 만 부르고, Vercel 서버가 평문 HTTP 데모 백엔드로
프록시하며, 그 **주소를 런타임에** 얻는다. 성공하면 단계 3·4 의 근거가 된다.

---

# Context — 실측

## 고칠 지점은 **2곳**, 둘 다 같은 env 쌍을 읽는다

| 파일 | 줄 | 지금 |
|---|---|---|
| `apps/web-store/src/shared/config/api.ts` | 34 | `isServer ? (API_URL_INTERNAL ?? NEXT_PUBLIC_API_URL ?? 'http://localhost:8080') : '/api/bff'` |
| `apps/web-store/src/app/api/bff/[...path]/route.ts` | 28-34 | `backendBaseUrl()` — 같은 폴백 사슬 |

🔵 **클라이언트 분기는 이미 상대경로 리터럴 `/api/bff`** 라 D1 을 이미 만족한다(`TASK-MONO-565`
산출물 실측: 클라 번들의 백엔드 오리진 **0건**). **고칠 것은 서버 쪽 두 지점뿐이다.**

🔴 **두 지점은 한 앱 안이므로 `ADR-MONO-068` 위반이 아니다** — 그 ADR 이 세는 단위는 **앱**이다.
다만 **둘 다 마커를 달아야** 하고(가드가 마커 없는 구현을 RED 로 잡는다), 두 지점이 **같은 함수**를
쓰는 것이 옳다(같은 사실이 두 곳에 있으면 한쪽만 고쳐진다).

## 론처의 링크는 조립된다 — 그리고 산문 불변식이 하나 있다

`infra/demo/aws/site/index.html`:

```
83  data-host="console"          85  data-host="web.fan-platform"
84  data-host="web.ecommerce"
186 const demoHost = (ip) => `${ip.replace(/\./g, "-")}.sslip.io`; // GUARD-T-ANCHOR
195 const surfaceUrl = (ip, prefix) => `http://${prefix}.${demoHost(ip)}/`;
```

191번 줄 주석: *"🔴 **모든 링크는 반드시 `demoHost()` 를 통과한다.**"*

🔵 **가드 (t) 는 영향을 받지 않는다 — 확인함.** (t) 는 `GUARD-T-ANCHOR` 의 **표현식**을 떠서 부팅의
파생과 대조할 뿐, *어느 링크가 그것을 쓰는지* 는 열거하지 않는다. ⇒ 스토어 링크를 Vercel 주소로
바꿔도 (t) 는 안 문다. **그래서 191번 산문이 조용히 거짓이 된다** — 그것을 고치는 것이 AC-3 이다.

## 🔴 데모가 꺼져 있을 때 — **새 요구다**

`ADR-MONO-067` § Consequences: *"데모가 꺼져 있어도 **화면 자체는 뜬다**(백엔드 없는 상태를 앱이
표현해야 한다 — **새 요구다**)."* 지금 론처는 도메인이 down 이면 **링크의 `href` 를 아예 없앤다**
(215번 줄). Vercel 판은 백엔드 없이도 뜨므로 그 규칙이 그대로 맞지 않는다.

---

# Scope

**하는 것**

- `apps/web-store/` — 서버 업스트림 2지점을 **런타임 해석**으로 (+ `DEMO-RESOLVER:` 마커)
- `infra/demo/aws/site/index.html` — 스토어 링크를 Vercel 판으로 + down 상태 표현 조정
- 위 두 곳의 **산문 불변식** 정정 (191번 줄)
- 테스트 (기존 `api.test.ts` · `bff-proxy.test.ts` 옆)

**안 하는 것** (🔴 명시)

- **데모 호스트의 `web-store` 컨테이너를 지우지 않는다** → `TASK-MONO-581` (재굽기 필요)
- console·fan 은 건드리지 않는다 (단계 3·4)
- **기동하지 않는다** — 라이브 확인은 581 의 재굽기 번들에 묶는다

---

# Acceptance Criteria

## AC-0 — 전제 재측정

```bash
sed -n '30,36p' projects/ecommerce-microservices-platform/apps/web-store/src/shared/config/api.ts
sed -n '26,36p' 'projects/ecommerce-microservices-platform/apps/web-store/src/app/api/bff/[...path]/route.ts'
bash scripts/check-demo-resolver-copies.sh          # 착수 전 = 앱 0개
```

어긋나면 STOP. 🔴 마지막 줄이 **rc=2(판정 불가)** 를 내면 그것도 STOP 이다 — 가드가 눈을 잃은 상태에서
구현하면 마커 실수를 아무도 못 잡는다.

## AC-1 — 해석기: **한 곳에** 구현하고 두 지점이 그것을 쓴다

- `GET {DEMO_API_BASE}/status` → `{state, ip, …}` → `DEMO_DOMAIN = <ip-대시>.sslip.io` → 업스트림.
- **짧은 TTL 캐시**(요청마다 컨트롤 API 를 때리지 않는다). TTL 값과 **왜 그 값인지**를 적는다.
- 🔴 **`DEMO_API_BASE` 가 없으면**? 폴백은 **기존 env 사슬**(`API_URL_INTERNAL` → `NEXT_PUBLIC_API_URL`)이다.
  로컬 개발과 CI 는 데모 컨트롤 플레인이 없다 — 거기서 죽으면 이 변경이 **로컬을 깬다**.
- 🔴 **`state != running` 이면**? 업스트림을 **만들지 않고** 호출자가 *"데모 꺼짐"* 을 표현할 수 있게 한다.
  조용히 옛 IP 로 붙는 것이 가장 나쁘다(그 IP 는 **남의 인스턴스**일 수 있다).

## AC-2 — 마커 (`ADR-MONO-068` D2)

두 지점(또는 공통 모듈)에 `// DEMO-RESOLVER: web-store` 를 단다.

**판정**: `bash scripts/check-demo-resolver-copies.sh` → **앱 1개**, rc=0.
🔴 **마커를 빼고 한 번 돌려 RED 를 확인**하라 — 가드가 이 구현을 **실제로 보는지**를 증명하는 유일한 방법이다.

## AC-3 — 론처: 스토어 링크를 Vercel 로 + **산문 불변식 정정**

- `data-host="web.ecommerce"` 조립 대신 **Vercel 스토어 주소**로.
- 🔴 **191번 줄 주석을 같이 고쳐라** — *"모든 링크는 `demoHost()` 를 통과한다"* 가 거짓이 된다.
  가드 (t) 는 이것을 **안 잡는다**(확인함) ⇒ 안 고치면 **아무도 모르는 거짓**이 남는다.
- 🔵 새 사실을 **한 곳에** 적어라: *어느 화면이 Vercel 이고 어느 화면이 데모 호스트인가.*
  단계 3·4 가 그 목록을 하나씩 옮길 것이다.

## AC-4 — 데모 꺼짐 상태

- 스토어 링크는 **데모가 꺼져 있어도 살아 있다**(화면은 뜬다).
- 그 화면이 *"데모 백엔드가 꺼져 있다"* 를 **표현**한다 — 빈 목록이나 스피너 무한이 아니라.
- 🔴 **판정은 렌더된 출력으로** 한다. 모델 속성이나 SSR grep 이 아니다.

## AC-5 — 테스트

기존 `shared/config/__tests__/api.test.ts` · `__tests__/bff-proxy.test.ts` 와 **같은 자리**에 붙인다.

| 칸 | 기대 |
|---|---|
| `DEMO_API_BASE` 있음 + `state=running` | 업스트림이 `<ip-대시>.sslip.io` 로 조립됨 |
| `DEMO_API_BASE` 있음 + `state=stopped` | 업스트림 없음 → "꺼짐" 경로 |
| `DEMO_API_BASE` **없음** (= 로컬/CI) | **기존 env 사슬** 그대로 (회귀 방지) |
| `/status` 가 5xx·타임아웃 | 🔴 안전한 쪽 = **기존 사슬**. 조용히 옛 IP 로 가지 않는다 |
| TTL 캐시 | 연속 호출이 컨트롤 API 를 **한 번만** 때린다 |

🔴 **네 번째 칸이 이 표의 핵심이다** — 그것이 없으면 *"데모 컨트롤 플레인이 잠깐 죽으면 스토어도
죽는다"* 를 아무도 모른다.

## AC-6 — 검증

| 무엇 | 어떻게 |
|---|---|
| 단위 | `pnpm --dir projects/ecommerce-microservices-platform test` (web-store) |
| 린트 | 🔴 프런트는 **lint 가 필수 게이트**다 |
| 빌드 | 🔴 **이 호스트에서 `output: standalone` 이 `EPERM` 으로 죽는다**(067 § 부수 발견) ⇒ **CI 가 권위** |
| 해석기 가드 | `check-demo-resolver-copies.sh` = 앱 1개 · **마커 뺀 RED 도 확인** |
| 래퍼 | `verify-demo-wrapper.sh` (t) — 🔵 로컬 Docker 가 내려가 있으면 앞쪽 셀에서 멈춘다. **CI 가 권위** |

---

# Related Specs

- `docs/adr/ADR-MONO-067-…md` § 단계 2 · D1 · D2 · Consequences
- `docs/adr/ADR-MONO-068-…md` — 해석기는 앱 안에, 마커 필수
- `tasks/done/TASK-MONO-565-…md` — 산출물 실측(클라 번들 0건)
- `tasks/done/TASK-MONO-571-…md` — 평문 HTTP 이그레스 성립

# Related Contracts

없음 — 데모 배선이고 서비스 간 계약을 바꾸지 않는다. `/status` 는 이미 존재하는 컨트롤 API 다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 로컬 개발(`*.local`) | `DEMO_API_BASE` 가 없다 ⇒ **기존 사슬**. AC-5 세 번째 칸이 이것을 고정한다 |
| CI | 같음. 🔴 CI 가 컨트롤 API 를 때리면 안 된다 |
| `/status` 가 `ip` 없이 `state=running` | 방어적으로 **꺼짐 취급**. 반쪽 응답으로 업스트림을 만들지 않는다 |
| 데모 호스트의 옛 사본이 여전히 뜬다 | **의도된 상태**다(581 대기). 🔴 그동안 `web.ecommerce.<domain>` 은 **주소가 살아 있다** — 링크만 없앤 것이지 |
| 방문자가 옛 주소를 북마크했다 | 못 잰다. 581 에서 사본이 사라지면 404 가 된다 — 그때가 진짜 전환 시점이다 |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| `DEMO_API_BASE` 부재 시 죽는다 | 로컬·CI 전부 빨강 | AC-1 폴백 + AC-5 세 번째 칸 |
| `/status` 실패 시 옛 IP 로 붙는다 | 🔴 **남의 인스턴스에 붙을 수 있다** | AC-5 네 번째 칸이 안전한 쪽을 고정 |
| 마커를 안 단다 | 가드가 이 구현을 **안 셈** → 단계 3에서 트리거가 안 울림 | AC-2 의 "마커 빼고 RED 확인" |
| 두 지점이 서로 다른 해석을 쓴다 | 한쪽만 고쳐진다 | AC-1: **한 곳에 구현**하고 둘이 그것을 쓴다 |
| 191번 산문을 안 고친다 | 아무 가드도 안 물고 거짓이 남는다 | AC-3. (t) 가 이것을 **안 잡는다는 것이 확인된 사실**이다 |
| 데모 꺼짐이 무한 스피너로 보인다 | 방문자가 "고장" 으로 읽는다 | AC-4 — 표현은 **새 요구**다 |
