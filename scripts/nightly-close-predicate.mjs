#!/usr/bin/env node
/**
 * nightly 감시 이슈를 **닫아도 되는가** — `TASK-MONO-661`.
 *
 * =============================================================================
 * 🔴🔴 왜 이 파일이 생겼나 — 감시자가 아직 살아 있는 빨강을 스스로 닫았다
 * =============================================================================
 * `TASK-MONO-655` 가 만든 감시 잡은 이렇게 닫았다:
 *
 *     red = needs 중 result 가 failure|cancelled 인 잡
 *     if (red 가 비었다) → 이슈를 닫는다
 *
 * `skipped` 는 `red` 에 안 들어간다. **그리고 그것은 탐지 축에서는 옳다** — watch 잡
 * 셋(`fan-surface-watch` · `launcher-freshness-watch` · `ami-generation-watch`)은
 * `if: github.event_name != 'push'` 라 **push 런에서 정상적으로 skip** 되고, 그것을
 * 빨강으로 세면 **매 머지마다 거짓 이슈**가 열린다. 655 의 self-test (4)가 바로 그 대조군이다.
 *
 * 🔴 틀린 것은 그 판단이 아니라 **같은 술어를 「닫을 때」도 쓴 것**이다:
 *
 * | | 질문 | `skipped` 는 |
 * |---|---|---|
 * | 탐지 | *"이 런에 새 빨강이 있나"* | **빨강 아님** (655 가 맞다) |
 * | 종료 | *"아까 그 빨강이 회수됐나"* | 🔴 **«안 잼» 이지 «초록» 이 아니다** |
 *
 * 실측(2026-09-10, 이슈 #3724 의 자기 기록):
 *
 *     빨간 잡: ami-generation-watch, fan-surface-watch   (런 …798, schedule)
 *     🟢 다시 초록이다 — 1bfc0c5a9… (push)               (런 …683)
 *
 * 그 push 런에서 두 잡은 **돌지 않았다**(skipped). 빨강은 그대로였고 — 같은 날
 * `check-fan-guard-live.sh` 를 라이브에 대고 돌려 rc=1 을 봤다 — 이슈만 사라졌다.
 *
 * 🔵 이것은 이 저장소가 이미 이름 붙인 함정의 **이벤트 필터 판**이다(경로 필터 판은
 * *"main tip 초록 ≠ 그 사이 빨강이 회수됨"* 으로 기록돼 있다). 대가는 더 크다 —
 * **감시자 자신이** 그 빨강을 지운다.
 *
 * =============================================================================
 * 술어
 * =============================================================================
 * **닫는다** = 이슈 본문이 기억하는 «그때 빨갰던 잡» 이 **전부 이번 런에서 실제로 돌아
 * `success`** 였다. 그 외는 전부 **hold**:
 *
 * - `skipped`      → 안 잼 (이 결함의 본체)
 * - `failure`/`cancelled` → 아직 빨강
 * - needs 에 **없다** → 이름이 바뀌었거나 잡이 사라졌다 ⇒ 안 잼
 * - prev 를 못 읽었다 → 무엇이 빨갰는지 모른다 ⇒ 안 잼
 *
 * 🔴🔴 **그러나 «안 닫히게만» 만들면 안 된다** — 영구히 열린 경보는 꺼진 경보와 같다.
 * 그래서 술어는 **prev 에 있는 잡만** 본다. prev 밖의 잡이 skip 됐다는 이유로 붙잡지
 * 않는다(그 대조군이 self-test (5)다). cron 은 하루 1회 도므로, 회수는 **다음 cron
 * 에서** 확정된다 — 이슈가 최대 하루 더 열려 있는 것은 **설계이지 결함이 아니다.**
 *
 * =============================================================================
 * 사용법
 * =============================================================================
 *     node scripts/nightly-close-predicate.mjs --needs '<json>' --prev 'a, b'
 *       → stdout 에 `close` 또는 `hold` 한 줄. 사유는 stderr. exit 0.
 *     node scripts/nightly-close-predicate.mjs --self-test
 *       → 고정 입력으로 양방향 증명. 실패 시 exit 1.
 *
 * 🔵 **bash+jq 가 아니라 node 인 이유**: 이 저장소의 개발 호스트에 외부 `jq` 가 없어서
 * bash 판은 **로컬에서 self-test 를 못 돌린다**. 그러면 테스트가 워크플로 안에만 살고,
 * 로컬 검증본은 «사본» 이 된다 — `TASK-PC-FE-279` 가 바로 그 결함을 고쳤다.
 * `scripts/` 에는 `.mjs` 선례가 이미 여럿 있다.
 */

/** @typedef {Record<string, {result?: string}>} Needs */

/**
 * @param {Needs} needs  워크플로의 `toJSON(needs)`
 * @param {string} prev  이슈 본문 첫 줄이 기억하는 빨간 잡 (`", "` 구분)
 * @returns {{decision: 'close'|'hold', reasons: string[]}}
 */
export function decide(needs, prev) {
  const names = String(prev ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  if (names.length === 0) {
    return {
      decision: 'hold',
      reasons: [
        '이슈가 기억하는 «빨간 잡» 을 못 읽었다 ⇒ 무엇이 회수돼야 하는지 모른다.',
        '  (본문 첫 줄이 `**빨간 잡**: a, b` 형태여야 한다 — 이 스크립트가 아니라 그 본문을 보라.)',
      ],
    };
  }

  const reasons = [];
  let hold = false;
  for (const name of names) {
    const result = needs?.[name]?.result;
    if (result === 'success') {
      reasons.push(`  ✔ ${name} — 이번 런에서 실제로 돌았고 success`);
      continue;
    }
    hold = true;
    if (result === undefined || result === null || result === '') {
      reasons.push(`  ✖ ${name} — 이번 런의 needs 에 **없다**(이름이 바뀌었거나 사라졌다) ⇒ 안 잼`);
    } else if (result === 'skipped') {
      reasons.push(`  ✖ ${name} — **skipped** ⇒ «초록» 이 아니라 «안 잼»`);
    } else {
      reasons.push(`  ✖ ${name} — ${result} ⇒ 아직 빨강`);
    }
  }

  return {
    decision: hold ? 'hold' : 'close',
    reasons: hold
      ? ['그때 빨갰던 잡이 전부 회수됐다고 말할 수 없다:', ...reasons]
      : ['그때 빨갰던 잡이 전부 이번 런에서 돌았고 success 다:', ...reasons],
  };
}

// ---------------------------------------------------------------------------
// self-test — 고정 입력, 양방향
// ---------------------------------------------------------------------------
function selfTest() {
  /** @type {[string, Needs, string, 'close'|'hold'][]} */
  const cases = [
    [
      '(1) 그때 빨갰던 둘이 이번에 돌아서 success → close',
      { a: { result: 'success' }, b: { result: 'success' } },
      'a, b',
      'close',
    ],
    [
      '(2) 🔴 하나가 skipped → hold  (이슈 #3724 가 밟은 그 칸)',
      { a: { result: 'success' }, b: { result: 'skipped' } },
      'a, b',
      'hold',
    ],
    [
      '(3) 아직 failure → hold',
      { a: { result: 'failure' } },
      'a',
      'hold',
    ],
    [
      '(4) needs 에 이름이 없다(개명·삭제) → hold',
      { other: { result: 'success' } },
      'a',
      'hold',
    ],
    [
      '(5) 🔵 대조군 — prev 밖의 잡이 skipped 인 것은 붙잡지 않는다 → close',
      { a: { result: 'success' }, unrelated: { result: 'skipped' } },
      'a',
      'close',
    ],
    [
      '(6) prev 를 못 읽었다 → hold',
      { a: { result: 'success' } },
      '',
      'hold',
    ],
    [
      '(7) 🔵 대조군 — 전부 success 면 여러 개여도 close (영구 hold 가 아니다)',
      { a: { result: 'success' }, b: { result: 'success' }, c: { result: 'success' } },
      'a, b, c',
      'close',
    ],
  ];

  let failed = 0;
  for (const [label, needs, prev, expected] of cases) {
    const { decision } = decide(needs, prev);
    if (decision === expected) {
      console.log(`  ok   ${label}`);
    } else {
      console.error(`  FAIL ${label} — 기대 ${expected}, 실제 ${decision}`);
      failed += 1;
    }
  }

  // 🔴 «양방향이 실제로 다르다» 를 따로 단언한다 — 전부 hold 를 내는 술어도 위 칸들
  //    중 일부는 통과시킨다. close 가 최소 한 번, hold 가 최소 한 번 나와야 한다.
  const decisions = new Set(cases.map(([, n, p]) => decide(n, p).decision));
  if (!(decisions.has('close') && decisions.has('hold'))) {
    console.error('  FAIL 술어가 한 방향만 낸다 — 판정기가 아니라 상수다.');
    failed += 1;
  }

  if (failed > 0) {
    console.error(`self-test 실패 ${failed}칸`);
    process.exit(1);
  }
  console.log(`self-test ${cases.length}칸 + 양방향 대조 통과.`);
}

// ---------------------------------------------------------------------------
function main() {
  const argv = process.argv.slice(2);
  if (argv.includes('--self-test')) return selfTest();

  const arg = (flag) => {
    const i = argv.indexOf(flag);
    return i >= 0 ? argv[i + 1] : undefined;
  };

  const rawNeeds = arg('--needs');
  const prev = arg('--prev') ?? '';
  if (rawNeeds === undefined) {
    console.error('사용법: --needs <json> --prev "<a, b>"   또는   --self-test');
    process.exit(2);
  }

  let needs;
  try {
    needs = JSON.parse(rawNeeds);
  } catch (err) {
    // 🔴 파싱 실패를 «초록» 으로 흘리지 않는다 — 안 잰 것이다.
    console.error(`needs JSON 파싱 실패 ⇒ 안 잼: ${String(err)}`);
    console.log('hold');
    return;
  }

  const { decision, reasons } = decide(needs, prev);
  for (const line of reasons) console.error(line);
  console.log(decision);
}

main();
