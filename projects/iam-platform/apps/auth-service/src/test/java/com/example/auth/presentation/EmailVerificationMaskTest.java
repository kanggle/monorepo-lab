package com.example.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-629 — the verification page and the invitation preview (admin-service
 * {@code OperatorInvitationAcceptanceUseCaseTest.MaskEmailEdgeCases}) must mask one address the same way.
 * The two services cannot share the call site, so the same inputs and expected values are pinned in both;
 * change one, change the other.
 */
class EmailVerificationMaskTest {

    @Test
    @DisplayName("표준 로컬 파트 — 첫 글자 + 별 3개 + 도메인 (초대 미리보기와 같은 값)")
    void standard() {
        assertThat(EmailVerificationPageController.mask("person@example.com")).isEqualTo("p***@example.com");
        assertThat(EmailVerificationPageController.mask("jane.doe@example.com")).isEqualTo("j***@example.com");
        assertThat(EmailVerificationPageController.mask("hire1@demo.com")).isEqualTo("h***@demo.com");
    }

    @Test
    @DisplayName("1자 · 2자 로컬 파트도 별 3개 — 길이가 드러나지 않는다")
    void shortLocalParts() {
        assertThat(EmailVerificationPageController.mask("a@example.com")).isEqualTo("a***@example.com");
        assertThat(EmailVerificationPageController.mask("ab@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    @DisplayName("주소가 아니면 원문을 돌려주지 않는다 (null)")
    void notAnAddress() {
        assertThat(EmailVerificationPageController.mask(null)).isNull();
        assertThat(EmailVerificationPageController.mask("noemailaddress")).isNull();
        assertThat(EmailVerificationPageController.mask("@example.com")).isNull();
    }
}
