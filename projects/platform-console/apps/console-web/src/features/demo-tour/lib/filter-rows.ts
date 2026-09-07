import type { ConsoleSampleTable } from '@demo/public-data';

/**
 * 이미 로드된 표 안에서의 **브라우저 검색** (ADR-MONO-070 § D3.2).
 *
 * 🔴 서버로 질의를 보내지 않는다. 표는 이미 봉투 하나에 통째로 실려 왔고(가장 큰 표가
 *    5행이다), 그 상태에서 "검색" 을 서버 왕복으로 만들면 **질의 표면**이 하나 생긴다 —
 *    질의 표면은 지켜야 할 경계가 되고, 익명 표면에서 그것은 순수한 손해다.
 * 🔵 그래서 순수 함수다. 상태도, fetch 도, 정렬도 없다 — 화면이 이 함수의 결과를
 *    **원래 순서 그대로** 그린다(정렬을 넣으면 "샘플이 실제로 이 순서다" 라는 없는
 *    사실을 주장하게 된다).
 *
 * 매칭 규칙: 컬럼에 **선언된 키의 값만** 이어 붙여 소문자로 비교한다.
 * 🔴 `Object.values(row)` 가 아니다. 행에 컬럼 밖의 키가 있으면 그 값은 화면에 그려지지
 *    않는데 검색에는 걸린다 — 「안 보이는 것으로 검색된다」는 이 저장소가 이미 이름을
 *    붙여 둔 실패다(추출기가 안 그려지는 것도 꺼낸다).
 */
export function rowSearchText(
  table: ConsoleSampleTable,
  row: Record<string, string | number>,
): string {
  return table.columns
    .map((c) => {
      const v = row[c.key];
      return v === undefined || v === null ? '' : String(v);
    })
    .join(' ')
    .toLowerCase();
}

/** 질의어로 행을 거른다. 빈 질의(공백만 포함)는 **전부 통과**시킨다. */
export function filterRows(
  table: ConsoleSampleTable,
  query: string,
): Array<Record<string, string | number>> {
  const needle = query.trim().toLowerCase();
  if (needle === '') return table.rows;
  return table.rows.filter((row) => rowSearchText(table, row).includes(needle));
}
