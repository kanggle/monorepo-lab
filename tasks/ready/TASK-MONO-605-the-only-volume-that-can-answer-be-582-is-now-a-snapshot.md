# Task ID

TASK-MONO-605

# Title

`TASK-BE-582` 의 「기존 볼륨」 판정 — 그 볼륨은 이제 **스냅샷 하나**다. 기동은 필요 없다.

# Status

ready

# Owner

monorepo

# Task Tags

- verification
- demo
- iam

---

# 배경 — `TASK-MONO-581` ⑥ 에서 분리됐다 (2026-08-29)

`TASK-BE-582`(`V0033`)의 AC-4 는 두 칸이다:

| 칸 | 상태 |
|---|---|
| **신선 볼륨** 판정 | ✅ CI `Integration (iam …, Testcontainers)` — 실제 MySQL 에 Flyway 를 돌려 DB 에서 읽었다 |
| **기존 볼륨** 판정 | ⏳ **미수행** |

그 이유를 582 가 이렇게 적었다:

> *"CI 는 항상 신선 볼륨이라 **마이그레이션 순서 결함에 영구히 초록**이다. 데모 호스트는
> 기존 볼륨을 쓴다."*

## 🔴🔴 그런데 데모 호스트로는 못 잰다 — 재굽기 당일에 드러났다

`TASK-MONO-581` 이 ⑥ 으로 묶어 「다음 기동에서 잰다」고 계획했으나, 그 계획이 성립하지 않는다:

| 사실 | 근거 |
|---|---|
| 데모에 **새 코드**를 올리는 경로는 「AMI 재굽기 → 인스턴스 교체」뿐 | 부팅은 `git pull` 을 하지 않고(`demo-boot.sh` → `demo-up.sh`), `DEMO_BUILD=1` 도 **AMI 안의 클론**을 다시 빌드할 뿐이다 |
| 인스턴스 교체는 루트 볼륨을 **파괴**한다 | `DeleteOnTermination=true`, 별도 데이터 볼륨 없음 ⇒ **도커 볼륨 전부가 루트에 있다** |
| 교체 전에 먼저 기동해서 재는 것도 **불가** | 옛 AMI 는 `2026-08-22`, `V0033` 은 `2026-08-26`(PR #3477) ⇒ **그 이미지에 마이그레이션이 없다** |

⇒ **「새 코드 × 기존 이력」을 만드는 경로가 그 호스트에 없다.**
교체 후에 얻는 것은 «새 코드 × 신선 볼륨» 이고, 그것이 바로 582 가 *"이걸로는 못 잡는다"* 고
적은 조건이다. [[env_fresh_volume_ci_is_permanently_green_on_migration_order]]

## ✅ 그래서 파괴 전에 보존했다 (소유자 판단, 2026-08-29)

| | |
|---|---|
| 스냅샷 | **`snap-09449008990589c36`** · `completed` · 100 GB |
| 원본 | `vol-06bcb08734707ca76` — 인스턴스 `i-033a3820845432e16` 의 루트 |
| 시점 | 인스턴스 `stopped` ⇒ **정합적** |
| 태그 | `Name=portfolio-demo-pre-581-rebake` · `Purpose=TASK-BE-582-AC4-existing-volume-verdict` · `Task=TASK-MONO-581` |

🔴 **이 스냅샷을 지우면 `V0033` 에 대한 「기존 볼륨」 판정은 영원히 불가능해진다.**
그 마이그레이션 **이전**의 이력을 가진 볼륨은 이제 이것 하나뿐이다.

🔵 **그리고 이 티켓은 기동이 필요 없다** — 그래서 다음 재굽기 창을 기다릴 이유가 없다.

---

# Goal

`V0033` 이 **이미 이력이 있는 볼륨**에서도 의도한 행을 만드는지 판정한다.
판정은 **파일이 아니라 행**으로 한다.

---

# Scope

**In:**

- 스냅샷 → 볼륨 복원 → iam MySQL 데이터 디렉터리에서 **기존 이력**을 얻는다
- 그 위에 **현재 코드**의 Flyway 를 돌린다
- `oauth_clients` 행을 읽어 판정하고, 결과를 `TASK-BE-582` AC-4 에 기록한다

**Out:**

- 데모 인스턴스 기동 — **필요 없다.** 열지 마라(예산 축)
- `V0033` 자체의 수정 — 판정이 먼저다
- 신선 볼륨 판정 — CI 가 이미 덮는다

---

# Acceptance Criteria

## AC-0 — 전제 재측정 (**착수 전**)

1. **스냅샷이 아직 있는가.**
   ```bash
   aws ec2 describe-snapshots --snapshot-ids snap-09449008990589c36 \
     --query 'Snapshots[0].{state:State,size:VolumeSize,started:StartTime}'
   ```
   🔴 없으면 **STOP** — 이 티켓의 전제가 사라진 것이고, 그 사실 자체를 `TASK-BE-582` 에
   적어야 한다(«못 잰다» 도 결과다).
2. **그 볼륨이 정말 기존 이력을 담는가** — 복원 후 `flyway_schema_history` 의 최대 version 이
   `V0033` **미만**인지 확인한다. 🔴 이것이 참이 아니면 이 판정은 **신선 볼륨 판정의 재탕**이고
   CI 가 이미 답한 것을 다시 하는 것이다. [[feedback_measurement_needs_a_validity_predicate]]

## AC-1 — 판정은 **행**으로 한다

```sql
SELECT client_id, redirect_uris FROM oauth_clients
WHERE client_id = 'fan-platform-user-flow-client';
```

- `https://fan.hubwang.com/api/auth/callback/iam` 이 **실제로 있는가**
- 🔴 **마이그레이션 파일을 grep 하지 마라.** 파일에 있는 것과 행에 들어간 것은 다른 축이고,
  그 차이가 이 판정의 존재 이유다.

## AC-2 — 대조군

- **신선 볼륨**에 같은 코드를 돌리면 같은 행이 나오는가 (CI 와 일치하는지)
- 🔴 두 판이 **다르면** 그것이 순서/멱등성 결함이고, `TASK-BE-582` 는 다시 열려야 한다
- 🔵 같으면 그 사실을 적고 닫는다 — *"결함이 없었다"* 도 측정 결과다

## AC-3 — 결과를 **`TASK-BE-582` 에** 기록한다

- AC-4 의 「기존 볼륨」 칸을 채운다 (`review/` 이므로 append-only `## CORRECTION`)
- 🔴 **판정 후 스냅샷 처분을 명시한다** — 남길지 지울지. 남긴다면 **왜** 남기는지 적어라
  (보관료가 계속 나가므로, 이유 없이 남은 스냅샷은 다음 사람이 지운다).

---

# Related Specs

- `projects/iam-platform/tasks/review/TASK-BE-582-…md` § AC-4
- `TASK-MONO-581` § ⑥ — 이 티켓이 그 분리분이다
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0033__*.sql`

# Related Contracts

없음.

---

# Edge Cases

- 🔴 **복원한 볼륨을 어디서 돌릴 것인가.** EC2 를 새로 띄우면 그것도 비용이고, 로컬로
  가져오려면 100 GB 다. 🔵 **도커 볼륨 디렉터리만 뽑아내는 편이 싸다** — 필요한 것은
  iam MySQL 의 데이터 디렉터리 하나다.
- 🔴 그 MySQL 데이터 디렉터리는 **버전이 묶여 있다.** 복원한 데이터를 다른 메이저 버전으로
  띄우면 기동 자체가 실패하고, 그 실패는 **마이그레이션 판정처럼 보인다.** compose 의
  이미지 태그를 맞춰라.
- 🔴 스냅샷은 **교체 직전**의 상태다 — 그 인스턴스가 마지막으로 돈 시점 이후의 어떤 변경도
  담고 있지 않다. 「기존 이력」의 정확한 시점을 AC-0 ②로 확인하는 이유다.
- 🔵 이 판정이 참이든 거짓이든 **CI 는 안 바뀐다** — CI 는 구조적으로 신선 볼륨이다.
  결함이 나오면 필요한 것은 CI 수정이 아니라 **마이그레이션 수정**이다.

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 스냅샷이 지워짐 | 판정 영구 불가 | AC-0 ① · 태그에 목적 명시 |
| 신선 볼륨을 기존 볼륨으로 착각 | CI 재탕을 **새 증거로** 보고 | AC-0 ② — `flyway_schema_history` 최대 version |
| 파일 grep 으로 판정 | 파일엔 있는데 행엔 없는 경우를 못 잡는다 | AC-1 |
| 대조군 없이 한 판만 | 「다르다」를 판정할 기준이 없다 | AC-2 |
| 판정만 하고 스냅샷 방치 | 보관료가 계속 나가고 이유를 아무도 모른다 | AC-3 |
