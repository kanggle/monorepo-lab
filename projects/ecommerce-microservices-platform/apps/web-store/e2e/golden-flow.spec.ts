import { test, expect, type Page } from '@playwright/test';
import { loginAsSeededConsumer, shouldSkipGap } from './helpers/auth';
import { openFirstProductDetail, selectFirstVariant, addToCart } from './helpers/product';

/**
 * Golden-flow E2E: GAP 로그인 → 상품 선택 → 장바구니 담기 → 결제 페이지 진입 →
 * 주문 생성 POST 응답 확인.
 *
 * Toss 결제창 SDK 콜백/위젯 완주는 여전히 E2E 범위 외 — "결제하기" 클릭이 트리거하는
 * `POST /api/orders`(BFF 경유, 실 브라우저 Origin 헤더 포함, gateway-service 까지 도달)
 * 의 응답이 403이 아님을 확인하는 지점까지만 검증한다(TASK-FE-096). 이 지점 이전까지는
 * TASK-FE-095 이전과 동일한 커버리지.
 *
 * 왜 403 단언이 필요한가: TASK-FE-095가 고친 gateway-service `CORS_ALLOWED_ORIGINS`
 * 스테일 값(TASK-MONO-024로 폐지된 레거시 `PORT_PREFIX` 값) 회귀를 이 스펙이
 * "결제하기" 버튼 노출까지만 검증하고 클릭·POST 응답은 전혀 단언하지 않아 잡지
 * 못했던 것이 TASK-FE-095가 CI 어떤 레인에서도 걸리지 않았던 근본 원인이었다.
 *
 * GAP 컨테이너가 e2e 환경에 없으면 (SKIP_GAP_E2E=1) 본 테스트는 자동 skip 된다 —
 * TASK-MONO-014 frontend-e2e 잡이 GAP 컨테이너를 docker-compose 에 추가한 이후에는
 * 자동 활성화된다.
 */

/**
 * Daum 우편번호 검색 위젯(외부 CDN, `t1.daumcdn.net`)을 결정론적 스텁으로 교체한다.
 *
 * 이 회귀 가드의 대상은 gateway CORS 필터(주문 생성 POST)이지 Daum 위젯 자체가
 * 아니다 — 실 위젯과 상호작용하면 CI 러너의 외부망 가용성에 테스트 안정성이
 * 종속되므로(mutation-check 재현성 저해), 앱 코드(`AddressSearch.tsx`)가 호출하는
 * `window.daum.Postcode({oncomplete}).embed(el)` 계약만 흉내 내는 스텁으로 대체한다.
 * `AddressSearch` 자체를 모킹하지 않는 점이 컴포넌트/유닛 테스트(`checkout-form.test.tsx`)
 * 와의 차이 — 이 스펙은 실제 앱 코드 경로(주소 선택 → 폼 state → 주문 POST)를 그대로
 * 태운다.
 */
async function stubAddressSearchWidget(page: Page): Promise<void> {
  await page.route('**/t1.daumcdn.net/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/javascript', body: '' }),
  );
  await page.addInitScript(() => {
    (window as unknown as { daum: unknown }).daum = {
      Postcode: function Postcode(this: { embed: (el: unknown) => void }, opts: {
        oncomplete: (data: {
          zonecode: string;
          roadAddress: string;
          jibunAddress: string;
          buildingName: string;
        }) => void;
      }) {
        this.embed = () => {
          opts.oncomplete({
            zonecode: '06236',
            roadAddress: '서울 강남구 테헤란로 152',
            jibunAddress: '서울 강남구 역삼동 737',
            buildingName: '강남파이낸스센터',
          });
        };
      },
    };
  });
}

test.describe('웹스토어 주문 골든 플로우 (GAP)', () => {
  test.skip(shouldSkipGap(), 'SKIP_GAP_E2E=1 — GAP 컨테이너 미가용');

  test('GAP 로그인 → 상품 선택 → 장바구니 담기 → 결제 페이지 진입 → 주문 생성 POST 403 아님', async ({ page }) => {
    await stubAddressSearchWidget(page);
    await loginAsSeededConsumer(page);

    await openFirstProductDetail(page);
    await selectFirstVariant(page);
    await addToCart(page);

    await page.goto('/cart');
    await expect(page.getByRole('heading', { name: '장바구니' })).toBeVisible();

    const selectAll = page.getByLabel('전체선택');
    if (!(await selectAll.isChecked())) {
      await selectAll.check();
    }

    const orderLink = page.getByRole('link', { name: '주문하기' });
    await expect(orderLink).toBeVisible();
    await orderLink.click();
    await page.waitForURL('**/checkout**', { timeout: 15_000 });

    await expect(page.getByRole('heading', { name: '주문하기' })).toBeVisible();
    const payButton = page.getByRole('button', { name: /결제하기/ });
    await expect(payButton).toBeVisible();

    // 배송지 입력 — TASK-FE-095/096 대상인 주문 생성 POST를 실제로 트리거하려면
    // CheckoutForm의 폼 검증(수령인/전화번호/우편번호/주소1)을 통과해야 한다. 계정에
    // 저장된 기본 배송지가 있으면 AddressSection이 새 배송지 입력 폼 대신 저장된
    // 배송지 라디오 목록을 렌더링하고 그 기본값을 자동 선택한다(useShippingAddressState)
    // — 이 경우 폼 필드가 아예 존재하지 않으므로, 새 배송지 폼이 실제로 보일 때만 채운다.
    await page.waitForSelector('#recipient, input[name="savedAddress"]', {
      state: 'visible',
      timeout: 15_000,
    });
    const recipientInput = page.locator('#recipient');
    if (await recipientInput.isVisible()) {
      await recipientInput.fill('E2E 테스터');
      await page.getByLabel('전화번호').fill('010-1234-5678');
      await page.getByRole('button', { name: '주소 검색' }).click();
      await expect(page.getByPlaceholder('우편번호')).toHaveValue('06236');
    }
    await expect(payButton).toBeEnabled();

    // 결제하기 클릭 → CheckoutForm.handleSubmit → placeOrder() → 같은-오리진 BFF
    // 프록시(`/api/bff/api/orders`, 브라우저 Origin 헤더 자동 첨부) → gateway-service.
    // 클릭 직후 requestPayment()(Toss SDK)가 페이지를 이동시킬 수 있으므로, 응답을
    // 클릭과 동시에(Promise.all) 기다려 네비게이션 이전에 캡처한다.
    const [orderResponse] = await Promise.all([
      page.waitForResponse(
        (res) => res.request().method() === 'POST' && /\/api\/bff\/api\/orders(\?|$)/.test(res.url()),
        { timeout: 15_000 },
      ),
      payButton.click(),
    ]);

    // AC-1: gateway `globalcors` 필터가 이 POST를 403으로 거부하지 않는다(TASK-FE-095
    // 회귀 가드). 200/201 성공 또는 403이 아닌 도메인 검증 오류 응답이면 충분 — Toss
    // 결제창이 실제로 열리는 지점 이후는 이 스펙의 범위 밖이다.
    if (orderResponse.status() === 403) {
      const body = await orderResponse.text().catch(() => '<unreadable body>');
      throw new Error(
        `POST /api/orders returned 403 — gateway CORS_ALLOWED_ORIGINS regression (TASK-FE-095)? body: ${body}`,
      );
    }
    expect(orderResponse.status()).not.toBe(403);
  });
});
