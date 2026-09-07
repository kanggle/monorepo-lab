import 'server-only';
import {
  bundledEnvelope,
  createPublicDataReader,
  type FanPublicData,
  type PublicDataResult,
} from '@demo/public-data';
import bundledJson from '@demo/public-data/snapshots/fan.json';

/**
 * 공개 화면이 데이터를 얻는 **유일한 경로** (ADR-MONO-070 § D3).
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 여기에 게이트웨이로 가는 코드가 **없다** — 그리고 없는 것이 요점이다
 * ─────────────────────────────────────────────────────────────────────────
 * 요구가 명시적으로 금지한 구조가 있다: *"먼저 백엔드를 호출했다가 실패하면 저장본을
 * 보여주는 구조를 기본으로 만들지 않는다."* 그 성질을 «주의해서 안 부른다» 로 얻지
 * 않는다 — 주의는 다음 사람이 새 화면을 붙이는 날 사라진다. 대신 **부를 수단을 안 준다**:
 * 이 모듈은 `@/shared/api/client` 를 임포트하지 않고, 토큰을 인자로 받지도 않는다.
 * 공개 페이지가 게이트웨이를 부르려면 이 슬라이스 밖의 모듈을 새로 임포트해야 하고,
 * 그것은 리뷰에서 보이는 크기의 변경이다.
 *
 * ⇒ 폴백 사슬은 두 칸뿐이다(`@demo/public-data` 가 소유한다):
 *     ① `DEMO_PUBLIC_DATA_BASE_URL` 의 영속 저장본
 *     ② 저장소에 커밋된 번들 시드 (`snapshots/fan.json`)
 *
 * 🔴 ② 는 «실패했을 때의 임시방편» 이 아니라 **선언된 바닥**이다. 이것이 있어야
 *    *"백엔드 없이 새 production build 및 배포가 가능한가"* 가 참이 된다 — env 가 하나도
 *    없는 상태(로컬·CI·최초 배포)에서도 화면이 실물로 선다.
 *
 * 🔴 `bundledEnvelope` 는 시드가 계약을 어기면 **모듈 로드 시점에 throw 한다.** 조용히 빈
 *    데이터로 가면 그 화면은 "아직 데이터가 없습니다" 를 그리고, 그건 «시드가 깨졌다» 와
 *    구별되지 않는다. 빌드가 죽는 편이 낫다 — CI 가 잡는다.
 *
 * 🔵 `import 'server-only'` 인 이유: 판독자는 서버에서만 돈다(§ `read.ts` 헤더). 그래서
 *    이 모듈은 jsdom 유닛 테스트에서 **로드되지 않는다** — 페이지 테스트는 이 모듈 id 를
 *    `vi.mock` 으로 갈아끼우고, 갈아끼운 자리에서 **진짜 `@demo/public-data` 판독자**를
 *    부른다. 그래서 데이터 경로는 그대로 시험되고 `server-only` 만 우회된다.
 * ─────────────────────────────────────────────────────────────────────────
 */

/**
 * 🔵 모듈 스코프에 **한 번만** 만든다. 판독자가 자기 캐시(세대 키 + TTL)를 들고 있으므로,
 *    요청마다 새로 만들면 그 캐시가 매번 비어 저장본을 페이지 렌더마다 때린다.
 */
const reader = createPublicDataReader('fan', bundledEnvelope('fan', bundledJson));

export type FanPublicDataResult = PublicDataResult<FanPublicData>;

/**
 * 봉투를 읽는다. **요청 하나가 한 번만** 부르고 목록·상세·검색을 그 결과에서 파생한다 —
 * 그래야 "목록은 저장본, 상세는 백엔드" 같은 반쪽 상태가 생기지 않는다.
 *
 * 🔴 인자가 없다(테넌트도, 페이지도, 검색어도). 그런 인자가 있으면 그것이 저장본의
 *    «질의 표면» 이 되고, 질의 표면은 지켜야 할 경계가 된다.
 */
export function readFanPublicData(): Promise<FanPublicDataResult> {
  return reader.readPublicData();
}
