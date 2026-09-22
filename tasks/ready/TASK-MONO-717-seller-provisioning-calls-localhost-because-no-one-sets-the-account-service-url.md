# Task ID

TASK-MONO-717

# Title

🔴 셀러 프로비저닝이 `localhost:8081` 을 불러 매번 실패하는데 **아무 화면도 빨개지지 않는다**

# Status

ready (2026-09-22 UTC)

# Owner

미지정

# Task Tags

demo, ecommerce, iam, fail-soft, observability

---

# 배경 — 이것은 추론이 아니라 **데모 창 실측**이다

`TASK-MONO-713` 이 곁발견으로 *"`ACCOUNT_SERVICE_BASE_URL` 을 설정하는 compose / env / override 가
저장소에 하나도 없다"* 를 적었고, 판정에 창이 필요해 `TASK-MONO-672` 항목 7 로 갔다.
**2026-09-22 데모 창에서 쟀다** (`TASK-MONO-672` § 14차 창 수확):

```
docker inspect ecommerce-product-service … | grep -i account   →  (없음)

{"level":"WARN","logger":"com.example.product.infrastructure.client.AccountServiceSellerProvisioner",
 "message":"seller provisioning failed (fail-soft, seller stays PENDING) tenant=ecommerce
            seller=demo-seller: I/O error on POST request for
            \"http://localhost:8081/oauth2/token\": Connection refused"}
{"level":"WARN","logger":"com.example.product.application.service.RegisterSellerService",
 "message":"seller left PENDING_PROVISIONING (IAM unavailable, retryable)
            tenant=ecommerce seller=demo-seller"}
```

부팅 시드 1회 + 재시드 1회 = **네 번 다 같은 줄**. 🔵 예상(«localhost 는 컨테이너 자기 자신이니
refused 일 것이다»)이 **측정이 됐다.** 🔴 그리고 예상보다 한 단계 **더 앞에서** 죽는다 —
실패하는 호출은 accounts 엔드포인트가 아니라 **IAM 토큰 엔드포인트**(`/oauth2/token`)다.
프로비저닝은 **자격증명을 얻는 단계에서** 끝난다.

## 왜 조용한가

이 호출은 **fail-soft** 다(`ADR-MONO-042` D3 — account-service 가 응답하지 않아도 셀러 등록 자체는
진행된다). ⇒ 실패해도 아무 화면도 빨개지지 않는다. 🔴 **그러나 결과는 무해하지 않다**:
셀러가 `PENDING_PROVISIONING` 에 남는다. 데모 시드의 「셀러 활성화」 줄은 **다른 경로**(상태 PATCH)라
화면상으로는 활성으로 보이고, **프로비저닝이 안 됐다는 사실만 사라진다.**

---

# Goal

`ACCOUNT_SERVICE_BASE_URL`(그리고 그 클라이언트가 읽는 IAM 토큰 주소)이 **어디에서 와야 하는지**를
정하고, 「설정이 없으면 조용히 localhost 로 떨어진다」를 끝낸다.

# Scope

## 포함

- `AccountServiceSellerProvisioner` 가 읽는 주소들의 **출처 확정**(compose / override / 기본값).
- 데모 체인(`infra/demo/*.override.yml`)에 그 값을 싣는 것.
- 「설정이 없다」를 **조용하지 않게** 만드는 판정 — 아래 AC-3.

## 제외

- fail-soft 정책 자체를 바꾸는 것(`ADR-MONO-042` D3). 🔴 그것은 ADR 결정이다.
- `TASK-MONO-713` 갈래 ⓑ(`/internal/tenants/**` 를 게이트웨이 뒤로 넣기) — 그 배선은 별건이고,
  이 티켓이 그 면제의 만료 경로이기도 하다(672 항목 7 § 곁가지).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (전제부터 다시 재라)

- [ ] 🔴 **주소가 정말 둘인지 하나인지부터 읽어라.** 로그의 실패 대상은 `/oauth2/token` 인데
      티켓(713)이 적은 기본값은 `${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}` 이다.
      **같은 상수를 토큰과 accounts 양쪽에 쓰는지**, 아니면 IAM 주소가 따로 있는지 코드에서
      확인하고 그 답을 여기 적어라. 🔵 답에 따라 고칠 값이 하나가 아니라 둘일 수 있다.
- [ ] 🔴 **운영/로컬에서도 같은 상태인지** 확인하라. 데모에서만 빠진 것인지, 어디에서도 설정된
      적이 없는 것인지는 다른 문제다(713 의 전수 grep 은 후자를 가리킨다).

## AC-1 — 고친다

- [ ] 데모 체인에 값을 싣고, **창에서** 그 두 WARN 줄이 사라지는 것을 본다.
- [ ] 🔴 판정은 로그 부재가 아니라 **결과 상태**다: 셀러가 `PENDING_PROVISIONING` 에서
      벗어나는가 · `account_db` 에 그 셀러-운영자 계정 행이 **생기는가**.
      🔵 로그가 조용해도 행이 없으면 고쳐진 것이 아니다(672 항목 7 이 그 술어를 못박았다).

## AC-2 — bite

- [ ] 🔴 **「설정이 없으면 조용히 localhost」를 무는 술어**를 놓아라. 날짜나 로그 문구로 재지 마라.
      후보: 기동 시 그 주소가 `localhost` 이고 프로파일이 데모/운영이면 **WARN 이 아니라 실패**,
      또는 compose 렌더에서 그 env 의 부재를 세는 가드.
      **bite**: 그 설정을 지우면 빨개진다.
- [ ] 🔴 fail-soft 를 fail-closed 로 바꾸는 것이 아님을 분명히 하라 — 무는 것은 **설정 부재**이지
      account-service 의 일시 장애가 아니다. 둘을 같은 술어로 묶으면 장애 때 셀러 등록이 죽는다.

## AC-3 — 한계를 적는다

- [ ] 이 수정으로도 **안 보이는 채로 남는** fail-soft 경로가 또 있는지 훑고, 있으면 목록을 적어라.
      🔴 「이제 다 보인다」로 적지 마라.

---

# Related Specs / Contracts

- `ADR-MONO-042` D3 — 셀러 프로비저닝의 fail-soft 정책.
- `projects/ecommerce-microservices-platform/apps/product-service/.../AccountServiceSellerProvisioner.java`
- `tasks/done/TASK-MONO-713-…` § AC-0 곁발견 · `tasks/ready/TASK-MONO-672-…` § 항목 7 (실측)

# Edge Cases

- **데모 IP 가 매 부팅 바뀐다** — 값을 리터럴 IP 로 박으면 다음 창에 낡는다. 형제들이 쓰는
  `${DEMO_DOMAIN}` 파생 또는 컨테이너 이름(`iam-auth-service:8081`)을 따라야 한다.
- **로컬(`DEMO_DOMAIN=local`)에서도 성립해야 한다** — 데모만 고치면 로컬이 다시 조용히 깨진다.

# Failure Scenarios

1. 주소만 채우고 **결과 상태를 안 재면** 「로그가 조용해졌다」를 고쳐진 것으로 읽는다 (AC-1 이 막는다).
2. bite 를 로그 문구로 만들면 문구가 바뀔 때 조용히 죽는다 (AC-2 가 막는다).

# 분석 / 구현 권장

(분석=Opus 5 / 구현 권장=Sonnet — 배선과 기본값 문제이고 도메인 결정이 아니다. 단 AC-2 의 술어 설계는 Opus)
