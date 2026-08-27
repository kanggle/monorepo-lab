# Task ID

TASK-MONO-566

# Title

`wms-boot-jars` 만 `retention-days: 7` — 2026-04-19 첫 CI 커밋의 잔재. 살아있는 아티팩트 용량의 **38%** 를 혼자 차지한다.

# Status

done

> **AC-0·1·2·3 완료. AC-4 는 날짜 게이트**(머지 +8일)라 `TASK-MONO-573` 으로 분리했다.
> 아래 § 실측 기록 참조 — **이 티켓의 열거가 두 곳에서 불완전했다.**

# Owner

monorepo

# Task Tags

- ci
- chore

---

# Goal

`ci.yml` 의 `wms-boot-jars` 업로드를 `retention-days: 7 → 1` 로 내려, **2026-04-28 에 정해진 이 레포의 관례**(빌드 산출물=1일 / 진단 리포트=7일)에 뒤늦게 맞춘다. 그리고 같은 드리프트가 다시 생기면 **CI 가 발화하도록** 가드를 남긴다.

정책 변경이 아니다. 관례가 정해지기 **9일 전에** 쓰인 한 줄이 4개월 동안 소급 적용되지 않은 것을 고치는 일이다.

---

# 배경 — 실측 (2026-08-22 UTC)

## ① blame: 이 줄에는 결정이 없다

```
8da4ddd0f5 (kanggle 2026-04-19 1922)           retention-days: 7
```

`8da4ddd0f` = 이 레포의 **CI 최초 커밋**(`ci: add GitHub Actions workflow for build + test + boot-jar artifacts`). 그 한 커밋이 `test-reports: 7` 과 `wms-boot-jars: 7` 을 **함께** 썼다 — 그때는 형제 boot-jars 잡이 하나도 없었고, 태스크 번호도 ADR 도 붙어 있지 않다.

**1일 관례는 9일 뒤 `df67721d7`(2026-04-28, #83, TASK-MONO-014)** 이 `ecommerce-boot-jars` 를 `retention-days: 1` 로 추가하면서 생겼고, 이후 모든 boot-jars 잡이 1을 따랐다. `wms` 만 소급되지 않았다. 그 줄은 그 뒤로 한 번도 수정된 적이 없다(주변 두 줄만 서비스 추가로 변경).

## ② 축은 「빌드 산출물 vs 진단 리포트」다 — 워크플로 3파일 전수

| | `retention-days: 7` | `retention-days: 1` |
|---|---|---|
| **진단·리포트** | 8곳 **전부** (`test-reports`, `playwright-report-smoke`, `iam-platform-e2e-smoke-test-reports`, `observability-footprint`, nightly 리포트 4곳, `_platform-e2e.yml:214`) | 0 |
| **빌드 산출물(jar)** | **`wms-boot-jars` 1곳** | `ecommerce-boot-jars`, `fan-platform-boot-jars`, `scm-platform-boot-jars`, `ecommerce-boot-jars-nightly`, `platform-console-e2e-boot-jars-nightly` — **5곳** |

7일은 *사람이 실패를 나중에 열어보는 물건*의 방침이고, 그 8곳 중 3곳은 `if: failure()` 라 실패했을 때만 올라온다. `wms-boot-jars` 는 **조건 없이 매 런 올라오는 빌드 산출물**이다.

## ③ 소비 경로: 같은 런 안에서 끝난다

```
ci.yml:2900   needs: [changes, boot-jars]
ci.yml:2919   boot-jars-artifact: wms-boot-jars
              → _platform-e2e.yml:115  actions/download-artifact@v4  (boot-jars-mode: download)
```

같은 워크플로, 같은 런, 업로드 몇 분 뒤 다운로드. **런 경계를 넘어 이 아티팩트를 읽는 경로는 없다** — nightly 는 `boot-jars-mode: build` 로 그 자리서 다시 빌드하며, `_platform-e2e.yml` 헤더가 그 이유를 명시한다(*"no cross-workflow artifact reuse"*). 런이 끝난 뒤 이것을 읽는 것은 아무것도 없다.

⇒ **7일이 사주는 것이 없다.** 1일이 부족해지는 유일한 경우는 「다른 런/다른 워크플로가 이 아티팩트를 가져간다」인데, 그 경로가 실재하지 않음을 위 세 줄이 보인다.

## ④ 비용: 살아있는 59.7 GB 중 22.8 GB

`gh api --paginate .../actions/artifacts` 전량 페이지네이션, `expired==false` 만:

| 이름 | 개수 | 용량 | retention |
|---|---:|---:|---:|
| **`wms-boot-jars`** | **78** | **22.76 GB** | **7** |
| `ecommerce-boot-jars-nightly` | 16 | 14.04 GB | 1 |
| `ecommerce-boot-jars` | 9 | 7.90 GB | 1 |
| `platform-console-e2e-boot-jars-nightly` | 16 | 5.83 GB | 1 |
| `fan-platform-boot-jars` | 12 | 3.50 GB | 1 |
| `scm-platform-boot-jars` | 9 | 3.33 GB | 1 |
| 나머지 14종 | 224 | 2.35 GB | 1·7 혼재 |
| **합계** | **364** | **59.71 GB** | |

`wms-boot-jars` = 전체의 **38.1 %**. 개당 0.29 GB × 78벌.

만료일이 정책대로 붙어 있음을 확인했다(같은 날 업로드분 대조): `wms-boot-jars` `2026-08-22 → 2026-08-29`(+7), 나머지 boot-jars 전부 `→ 2026-08-23`(+1). **`retention-days` 는 정상 작동 중이고, 문제는 값 하나다.**

---

# Scope

## In Scope

1. `.github/workflows/ci.yml` L1922 — `retention-days: 7` → `1` (한 줄).
2. `scripts/check-artifact-retention.sh` — 형제 가드(`check-libs-ci-coverage.sh` 등)와 같은 모양의 신규 가드 + `--self-test`.
3. `ci.yml` 배선 — paths-filter 엔트리 + `bash -n` / `--self-test` / 본 실행 3스텝(형제 가드와 동일 패턴).
4. `tasks/INDEX.md` 행 이동.

## Out of Scope

- **기존 78벌 삭제** — 되돌릴 수 없고, 소유자 결정 사항이며, **애초에 필요 없다**: 변경 후 7일 안에 전부 자연 만료한다.
- **다른 아티팩트의 보존 기간** — 7일짜리 8곳은 전부 진단물이고 관례에 맞다. 건드리지 않는다.
- **`*-nightly` 이름이 붙었는데 하루 16번 도는 것**(`ecommerce-boot-jars-nightly` · `platform-console-e2e-boot-jars-nightly`) — 실측으로 드러난 별건이다. 이미 1일이라 스스로 사라지므로 이 태스크의 결함이 아니다. **관측만 기록하고 손대지 않는다.**
- **캐시 998개 / 10.6 GB** — 아티팩트와 다른 축이고 다른 만료 규칙을 따른다.

---

# Acceptance Criteria

## AC-0 — 착수 시점 재측정 (verify-then-act)

착수하는 사람은 **아래 셋을 다시 재고**, 어긋나면 STOP 하고 티켓을 갱신한다. 위 숫자는 2026-08-22 UTC **한 시점의 스냅샷**이다.

```bash
git blame -L 1913,1923 -- .github/workflows/ci.yml            # ① 여전히 8da4ddd0f 인가
grep -n "boot-jars-artifact:" .github/workflows/ci.yml         # ② 소비자가 여전히 같은 런인가
gh api --paginate "repos/kanggle/monorepo-lab/actions/artifacts?per_page=100" \
  --jq '.artifacts[] | select(.expired==false) | [.name,.size_in_bytes] | @tsv'   # ③ 점유율
```

**③ 이 바뀌어 있어도 AC-1 은 그대로 진행한다** — 근거는 점유율이 아니라 ①②(결정 부재 + 소비 경로 없음)다. 점유율은 우선순위를 정할 뿐이다.

🔴 `git blame` 의 줄 번호는 위 서비스가 추가되면 밀린다. **줄 번호가 아니라 `name: wms-boot-jars` 블록을 찾아 그 아래 `retention-days` 를 본다.**

## AC-1 — 변경

- `ci.yml` 의 `name: wms-boot-jars` 업로드 스텝이 `retention-days: 1` 을 갖는다.
- 워크플로 3파일에 **`retention-days` 를 가진 boot-jars 업로드는 전부 1** 이고, **7은 하나도 없다.**
- `ci.yml` 의 다른 어떤 `retention-days` 도 바뀌지 않았다 (`git diff --stat` = 1 file, 1 insertion, 1 deletion).

## AC-2 — 가드: 같은 드리프트가 다시 생기면 발화한다

`scripts/check-artifact-retention.sh`:

- **모집단을 워크플로 파일에서 유도한다.** `.github/workflows/*.yml` 의 `actions/upload-artifact` 블록을 전부 파싱해 `name:` 과 `retention-days:` 를 뽑는다. 🔴 **아티팩트 이름을 손으로 열거하지 않는다** — 하드코딩 목록은 새 잡이 추가돼도 조용히 통과한다(이 레포가 여러 번 밟은 모양).
- **술어**: 이름이 `boot-jar` 을 포함하는 업로드는 `retention-days: 1` 이어야 한다. 그 외 업로드는 검사하지 않는다(진단물의 7일은 정당하다).
- **모집단 하한 단언**: 추출된 업로드 블록이 **12개 미만이면 실패**한다. 파서가 죽었는데 「위반 0건」으로 초록이 되는 것을 막는다. 🔴 추출 0건은 통과가 아니라 **판정 불가**다.
- **`--self-test`**: (a) 위반을 주입한 fixture 에서 **실제로 물고**, (b) 현재 트리에서 통과하며, (c) `retention-days` 를 아예 생략한 업로드도 물어야 한다 — **생략은 저장소 기본값(90일) 상속**이라 7보다 나쁘다. 세 케이스 모두 **주입이 실제로 일어났는지 먼저 단언**한다.

## AC-3 — 배선: 가드가 실제로 CI 에서 돈다

형제 가드와 동일한 3스텝(`bash -n` → `--self-test` → 본 실행) + `changes` 잡 paths-filter 에 `scripts/check-artifact-retention.sh` 와 `.github/workflows/**` 엔트리.

🔴 **가드를 쓰고 배선을 빠뜨리면 아무것도 지키지 않는다.** PR 의 실제 체크 목록에서 이 스텝이 **돌았음을 확인**한다 — 워크플로에 적혀 있는 것과 돈 것은 다르다.

## AC-4 — 효과 확인 (머지 후 8일)

머지 8일 뒤 `expired==false` 인 `wms-boot-jars` 가 **하루치(≈11벌 / ≈3.2 GB)** 만 남는다.

🔴 **11 은 예측이지 측정이 아니다** — 78벌 ÷ 7일의 평균에서 나온 값이고, wms 잡은 path-filter 로 게이팅되므로 하루 실행 횟수는 변동한다. **판정은 「≈11 인가」가 아니라 「가장 오래된 것이 24시간 이내인가」로 한다.**

---

# Related Specs

- `docs/guides/monorepo-workflow.md` — CI 워크플로 관례 (인간 참조용)
- `.github/workflows/README.md` — 잡 구성 설명
- `scripts/check-libs-ci-coverage.sh` — 가드 형태·`--self-test`·배선의 참조 구현

# Related Contracts

없음. 아티팩트 보존은 CI 내부 사정이고 서비스 간 계약을 건드리지 않는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 런이 **1일 안에** 끝나지 않는다 | 아티팩트 만료는 **업로드 시각** 기준이고, e2e 소비는 업로드 몇 분 뒤다. 잡 `timeout-minutes` 가 훨씬 짧아 구조적으로 불가능. |
| 실패한 e2e 를 **다음날 재현**하려고 jar 이 필요하다 | 재현은 `re-run job` 으로 하며, 그것은 jar 을 **다시 빌드**한다(`boot-jars` 잡이 함께 돈다). 다른 boot-jars 5곳이 이미 1일로 이 상태에서 4개월 운영됐다. |
| 새 프로젝트가 boot-jars 잡을 추가한다 | AC-2 가드가 모집단을 파일에서 유도하므로 **자동으로 포함**된다. 이것이 이 태스크가 가드를 남기는 이유다. |
| `retention-days` 를 **생략**한 업로드가 새로 들어온다 | 저장소 기본값(90일)을 상속하므로 7보다 나쁘다. AC-2 `--self-test` (c) 가 이 케이스를 명시적으로 문다. |
| `_platform-e2e.yml` 이 나중에 **cross-run 재사용**으로 바뀐다 | 그 순간 이 결정의 전제가 깨진다. 그 변경을 하는 사람이 여기를 다시 읽도록, `ci.yml` 의 바뀐 줄 옆에 **왜 1인지**(같은 런 소비) 한 줄 주석을 남긴다. |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 소비 경로 판독이 틀렸고 실제로 다른 런이 가져간다 | 머지 후 e2e 잡이 `download-artifact` 에서 **404/artifact not found** | 즉시 revert(한 줄). 그 다음 **그 경로를 티켓에 적고** 7일을 정당화한다 — 지금은 정당화가 없다는 것이 이 티켓의 핵심이므로, 발견되면 그 자체가 성과다. |
| 가드가 물지 않는다 | `--self-test` 의 주입 케이스가 초록 | **가드가 아니라 술어를 의심한다.** 주입이 실제로 파일에 들어갔는지부터 단언(이 레포가 반복해서 밟은 모양: *"안 물었다" 보다 "주입됐나" 를 먼저*). |
| 가드가 건강한 상태에 빨간불 | 진단물 업로드가 `boot-jar` 을 이름에 포함 | 술어를 **이름 부분문자열**에서 **잡 성격**으로 좁히거나, 해당 업로드 이름을 바꾼다. **기대를 낮춰 통과시키지 않는다.** |
| 배선 누락 | PR 체크 목록에 스텝이 없음 | AC-3 이 이것만 본다. 워크플로 파일에 적힌 것으로 만족하지 않는다. |
| 파서가 죽어 조용히 통과 | 위반을 넣어도 초록 | AC-2 의 **모집단 하한 12** 가 이것을 판정 불가로 만든다. |

---

# 참고 — 함께 드러났으나 이 티켓 밖인 것

- **`*-nightly` 이름의 아티팩트 2종이 하루 16번 만들어진다**(`ecommerce-boot-jars-nightly` 14.0 GB · `platform-console-e2e-boot-jars-nightly` 5.8 GB — 같은 날 12:09·13:44·14:23·15:15 …). 이름과 실제 주기가 어긋나 있다. 둘 다 1일이라 스스로 사라지므로 용량 결함은 아니지만, **이름이 거짓**이라 다음 사람의 판독을 오도한다. 별건 티켓 후보.
- **캐시 998개 / 10.6 GB** — 아티팩트 59.7 GB 와 별개 축.

분석=**Opus 5** / 구현 권장=**Haiku** (설정 한 줄) — 단 **AC-2 가드는 Sonnet 이상**(파서 + self-test + 배선).

---

# 실측 기록 (2026-08-23 UTC) — 구현=Opus 5 직접

## ✅ AC-0 — 세 축 재측정, 전부 일치

| 축 | 티켓(08-22) | 재측정(08-23) | |
|---|---|---|---|
| blame | `8da4ddd0f` | `8da4ddd0f` | ✅ 여전히 **결정이 없다** |
| 소비 경로 | 같은 런 | 같은 런 | ✅ `needs: [changes, boot-jars]` → `_platform-e2e.yml` |
| 점유 | 78벌 / 22.76 GB (38.1%) | **79벌 / 23.05 GB (38.2%)** | ✅ |

🔵 티켓이 경고한 대로 **줄 번호가 밀렸다**(1922 → 1956). `name: wms-boot-jars` 블록으로 찾았다.

## 🔴 이 티켓의 열거가 두 곳에서 불완전했다 — 결론은 안 바뀐다

| | 티켓 | 실측 |
|---|---:|---:|
| boot-jar 업로드 | 6 | **8** (`fan-platform-iam-boot-jar` · `federation-hardening-e2e-boot-jars` 누락) |
| 진단 업로드 | 8 | **12** |
| 합계 | 14 | **20** |

빠진 둘은 **이미 `1`** 이라 *"wms 가 유일한 이탈"* 은 그대로다. 그러나 **이것이 AC-2 가 하드코딩
목록을 금지하는 이유의 실증**이다 — 사람이 손으로 센 목록은 이 티켓처럼 조용히 모자란다.
가드는 모집단을 파일에서 유도하므로 8곳을 전부 본다.

## 🔴 술어를 한 번 좁혔다 — 기대를 낮춘 게 아니라

첫 술어는 *"이름에 `${{` 가 있으면 판정 불가"* 였고, **건강한 업로드에 즉시 빨간불**을 켰다:
`observability-footprint-${{ github.run_id }}` 는 템플릿이지만 **고정 stem 이 이름을 정하고
있어** run_id 를 무엇으로 치환해도 boot-jars 가 될 수 없다.

⇒ 술어를 *"`${{ … }}` 를 제거한 뒤 **고정 stem 이 남는가**"* 로 좁혔다. 남으면 이 파일이 이름을
정한 것이고, 아무것도 안 남으면(`${{ inputs.artifact-name }}`) **호출자가 통째로 정하므로 이
파일은 판정할 수 없다.** 판정 불가 2건은 **세어서 핀으로 고정**했다 — 새 템플릿 업로드가 생기면
가드가 실패하고 사람이 "거기로 boot jar 가 흐르는가"를 확인하게 된다. **못 세는 몫을 판정 옆에
적는 것**이 가드가 할 수 있는 유일한 정직한 처리다.

## 🔴 self-test 가 제 주입 실패를 두 번 잡았다

| 증상 | 진짜 원인 |
|---|---|
| "injection did not land" ×3 | **`extract_uploads \| grep -q` 가 pipefail 에 걸림** — `grep -q` 가 첫 매치에서 종료 → 상류 SIGPIPE → 매치했는데 파이프라인은 실패. **주입은 됐는데 안 됐다고 보고**됐다. 캡처 후 비교로 교체 |
| 잔여 1건 | **CRLF** — `retention-days: 1\n` 패턴이 실제 `1\r\n` 에 안 맞아 perl 이 조용히 no-op. `\r?` 추가 |

🔵 **self-test 가 "안 물었다"로 오독하지 않고 "주입이 안 됐다"로 보고한 덕에** 두 번 다
계측기를 고쳤다. 주입 여부를 먼저 단언하지 않았다면 술어를 의심하며 헤맸을 것이다.

## ✅ AC-1 — 한 줄 + 이유 주석

`retention-days` **키 개수 9 → 9**(추가·삭제 0), 값 하나만 `7 → 1`. 워크플로 전체 분포
**1:8 / 7:12** = 가드가 센 20블록(boot-jar 8 + 진단 12)과 일치.

바뀐 줄 옆에 **왜 1인지**(같은 런 소비 + nightly 는 `build` 모드) 주석을 남겼다 — Edge Case 의
*"cross-run 재사용으로 바뀌면 전제가 깨진다"* 에 대한 대응.

## ✅ AC-2 — `scripts/check-artifact-retention.sh`

모집단 파일에서 유도(20블록, grep 대조군 20과 일치) · 하한 12(추출 0 = 통과 아닌 **판정 불가**) ·
self-test **6/6**(주입 landed 단언 포함), 그중 **대조군 2칸**:
- 진단 업로드의 retention 을 바꿔도 **물지 않는다** — 없으면 *"1 아닌 건 다 잡아라"* 라는 오답이
  전 케이스를 통과하고 실패 리포트에 1일을 요구하게 된다.
- 새 템플릿 이름이 들어오면 **핀이 깨진다**.

## ✅ AC-3 — 배선

`changes.outputs` + paths-filter + 3스텝 잡. **grep 이 아니라 YAML 파싱으로** 세 지점 전부 확인했다
(출력 선언을 빠뜨리면 `if:` 가 영원히 거짓이라 **가드가 한 번도 안 돈다**). 필터는
`code-changed` 와 **AND 하지 않는다** — 두 도착 경로가 모두 워크플로 전용 diff 라 AND 하면
정확히 이 결함 클래스에서 꺼진다.

## ⏳ AC-4 — 분리됨

머지 +8일 검증은 `TASK-MONO-573`(날짜 게이트).
