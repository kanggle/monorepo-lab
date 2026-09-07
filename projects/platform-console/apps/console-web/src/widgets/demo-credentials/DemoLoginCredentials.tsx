import { resolveDemoBackendState } from '@/shared/config/demo-backend';

/**
 * 🔴 이 두 값이 **이 앱에서의 유일본**이다 — z11 이 여기를 앵커로 뽑는다.
 *
 * 권위는 여기가 아니라 `infra/demo/seed/lib.sh` 의 `user_token()` 기본값이다. 그 값은
 * 부팅마다 **실제로 로그인에 쓰이므로**, 시드가 통과했다는 것은 그 값으로 로그인이
 * 됐다는 뜻이다(살아 있는 출처). 마이그레이션 SQL 의 평문은 주석일 뿐이고 컬럼에 든
 * 것은 Argon2id 해시라 대조할 수 없다.
 *
 * 🔴 값을 손으로 고치지 마라. `verify-demo-wrapper.sh` (z11) 이 **세 사본**(론처 HTML ·
 * `seed/lib.sh` · 이 파일)을 대조한다. 하나만 고치면 CI 가 RED 다.
 *
 * 🔵 이름을 `DEMO_LOGIN_*` 으로 잡은 이유: z11 의 추출식이 **대입문 모양**을 앵커로
 * 쓰기 때문이다. 문서·주석 어디에나 나올 수 있는 이메일 *문자열* 을 앵커로 삼으면
 * 산문에 걸린다(론처 쪽이 `id="c-email"` 을 앵커로 쓰는 것과 같은 이유).
 */
export const DEMO_LOGIN_EMAIL = 'demo@demo.com';
export const DEMO_LOGIN_PASSWORD = 'Demo1234!';

/**
 * 데모 배포의 `/login` 에서 **어느 계정으로 들어가는지 말한다** (`TASK-PC-FE-275`).
 *
 * -----------------------------------------------------------------------------
 * 🔴🔴 왜 필요한가 — 이 화면이 아무 말도 안 해서 방문자가 **가입하고 있었다**
 * -----------------------------------------------------------------------------
 * `TASK-MONO-561` 이 **런처에서** 똑같은 결함을 이미 고쳤고, 그 블록의 주석이 결함을
 * 스스로 이렇게 적어 뒀다: *"이 블록이 없어서 방문자가 못 들어갔다. 로그인 화면은 나오는데
 * 계정을 모르니 «회원가입» 을 누르게 되고…"* — 그 경로에서 나온 것이 `TASK-BE-580/581`
 * 두 결함이다.
 *
 * 🔴 콘솔 로그인은 **그 수정의 낙오한 형제**였다. `TASK-PC-FE-182` 가 가입 경로를
 * 「구조적으로 불가능」에서 「가능하지만 **도메인 구독 0 · 데이터 0** 인 빈 조직」으로
 * 바꿨으므로, 증상은 조용해졌을 뿐이다: 방문자는 이제 잘 도는 `/onboarding`(조직 만들기)
 * 을 만나고, 거기서 만든 조직으로는 운영 화면이 **전부 비어 있다.**
 *
 * 🔴🔴 「런처에 적혀 있으니 됐다」가 성립하지 않는 근거는 **측정 가능**하다: 런처의 콘솔
 * 링크가 `target="_blank"` 다(`infra/demo/aws/site/index.html`). 방문자는 **계정 카드가
 * 있는 탭을 떠나** 새 탭의 이 화면에 도착한다 — 자격증명은 다른 탭에 있다.
 *
 * -----------------------------------------------------------------------------
 * 🔴 술어는 «백엔드가 사는가» 가 아니라 «이 배포가 데모인가» 다
 * -----------------------------------------------------------------------------
 * `state !== 'not-demo'` 이다. `=== 'running'` 으로 쓰면 안 된다 — 백엔드가 **꺼져 있을
 * 때야말로** 방문자가 이 화면에 오래 머문다(켜지기를 기다리는 자리다). 그때 이 블록이
 * `DemoBackendNotice` 와 **나란히** 뜨는 것이 옳다: 하나는 *왜 지금 로그인이 안 되는가*,
 * 다른 하나는 *켜지면 무엇으로 들어가는가* 를 말한다. 🔴 두 문장을 하나로 합치지 마라 —
 * 합치는 순간 둘 중 하나는 거짓이 된다.
 *
 * 🔵 해석기가 `/status` 에 실패한 경우에도 렌더된다. 그것이 옳다 — 이 블록이 재는 축은
 * 백엔드의 생사가 아니라 **컨트롤 플레인이 설정돼 있는가**(= 이 배포가 데모인가)이다.
 *
 * 🔴 로컬 개발과 CI(`not-demo`)에서는 **아무것도 렌더하지 않는다.** 거기서 데모 계정을
 * 말하는 것은 거짓이고, 그 거짓은 개발자 화면과 CI 스냅샷에 비밀번호를 남긴다.
 *
 * -----------------------------------------------------------------------------
 * 🔵 비밀번호를 화면에 적는 것은 **새로운 노출이 아니다**
 * -----------------------------------------------------------------------------
 * 런처가 같은 판단을 근거와 함께 이미 적어 뒀다: `Demo1234!` 는 이미 저장소에 있고
 * 마이그레이션 주석이 스스로를 *"one published demo password"* 라고 부른다. 데모 전체가
 * 무인증 공개이고, 지출 상한은 계정이 아니라 **월 예산 가드**가 지킨다.
 * 🔴 그 판단의 **경계**도 그대로 승계한다 — 여기 오는 것은 **데모 시드 계정뿐**이다.
 * AWS 자격증명·API 키는 오지 않는다(`TASK-MONO-557` 이 세운 경계).
 */
export async function DemoLoginCredentials() {
  const state = await resolveDemoBackendState();
  if (state === 'not-demo') return null;

  return (
    <section
      data-testid="demo-login-credentials"
      aria-labelledby="demo-login-credentials-title"
      className="mt-6 rounded-md border border-border bg-muted/50 p-4 text-left"
    >
      <h2
        id="demo-login-credentials-title"
        className="text-sm font-medium text-foreground"
      >
        데모 계정
      </h2>
      <dl className="mt-2 space-y-1 text-sm text-muted-foreground">
        <div className="flex items-baseline justify-between gap-3">
          <dt>이메일</dt>
          <dd>
            <code className="text-foreground">{DEMO_LOGIN_EMAIL}</code>
          </dd>
        </div>
        <div className="flex items-baseline justify-between gap-3">
          <dt>비밀번호</dt>
          <dd>
            <code className="text-foreground">{DEMO_LOGIN_PASSWORD}</code>
          </dd>
        </div>
      </dl>
      {/*
        🔴 이 줄은 장식이 아니다. 이 계정은 각 도메인 테넌트에서 CUSTOMER 일 뿐이라
        `/api/admin/**` 에 그대로 가면 전부 403 이다. 운영자 권한은 계정에 붙어 있지 않고
        **테넌트를 assume 하는 순간** 그 테넌트의 도메인 구독에서 파생된다
        (`OperatorRoleDerivation.fromEntitledDomains`). 즉 로그인만 하고 테넌트를 안
        고르면 방문자는 빈 화면과 403 을 보고 "고장났다" 로 읽는다 — 계정이 모자라서가
        아니다. 런처가 같은 문장을 같은 이유로 들고 있다.
      */}
      <p className="mt-3 text-xs text-muted-foreground">
        로그인한 뒤 테넌트를{' '}
        <strong className="font-medium text-foreground">demo-corp</strong> 로
        선택하세요. 5개 도메인(이커머스 · WMS · SCM · ERP · 재무)의 운영자 권한은
        그 시점에 부여됩니다.
      </p>
    </section>
  );
}
