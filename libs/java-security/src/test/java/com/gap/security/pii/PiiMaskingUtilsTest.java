package com.gap.security.pii;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingUtilsTest {

    // ----- maskEmail -----

    @Test
    @DisplayName("maskEmail - 유효한 이메일이면 첫 문자와 도메인을 유지하고 로컬 파트를 마스킹한다")
    void maskEmail_validEmail_keepsFirstCharAndDomain() {
        assertThat(PiiMaskingUtils.maskEmail("jane.doe@example.com"))
                .isEqualTo("j***@example.com");
    }

    @Test
    @DisplayName("maskEmail - 로컬 파트가 한 글자인 이메일이면 첫 문자와 도메인을 유지한다")
    void maskEmail_singleCharLocalPart_keepsFirstCharAndDomain() {
        assertThat(PiiMaskingUtils.maskEmail("a@example.com"))
                .isEqualTo("a***@example.com");
    }

    @Test
    @DisplayName("maskEmail - @ 기호가 없는 입력이면 변경하지 않고 그대로 반환한다")
    void maskEmail_nonEmailInput_returnsUnchanged() {
        assertThat(PiiMaskingUtils.maskEmail("no-at-sign")).isEqualTo("no-at-sign");
    }

    @Test
    @DisplayName("maskEmail - 입력이 null이면 null을 반환한다")
    void maskEmail_nullInput_returnsNull() {
        assertThat(PiiMaskingUtils.maskEmail(null)).isNull();
    }

    // ----- maskAccountId -----

    @Test
    @DisplayName("maskAccountId - UUID 형식 입력이면 변경하지 않고 그대로 반환한다")
    void maskAccountId_uuidInput_returnsUnchanged() {
        String uuid = "00000000-0000-7000-8000-000000000001";
        assertThat(PiiMaskingUtils.maskAccountId(uuid)).isEqualTo(uuid);
    }

    @Test
    @DisplayName("maskAccountId - 이메일이 포함된 입력이면 이메일 마스킹 규칙을 적용한다")
    void maskAccountId_embeddedEmail_appliesEmailMasking() {
        assertThat(PiiMaskingUtils.maskAccountId("jane.doe@example.com"))
                .isEqualTo("j***@example.com");
    }

    @Test
    @DisplayName("maskAccountId - 입력이 null이면 null을 반환한다")
    void maskAccountId_nullInput_returnsNull() {
        assertThat(PiiMaskingUtils.maskAccountId(null)).isNull();
    }

    // ----- maskPhone -----

    @Test
    @DisplayName("maskPhone - 11자리 전화번호이면 앞 3자리와 마지막 4자리를 유지한다")
    void maskPhone_elevenDigits_keepsPrefixAndLastFour() {
        assertThat(PiiMaskingUtils.maskPhone("01012345678"))
                .isEqualTo("010-****-5678");
    }

    @Test
    @DisplayName("maskPhone - 포맷이 포함된 국제 전화번호이면 숫자만 추출하여 마스킹한다")
    void maskPhone_formattedInput_extractsDigitsAndMasks() {
        assertThat(PiiMaskingUtils.maskPhone("+82-10-1234-5678"))
                .isEqualTo("821-****-5678");
    }

    @Test
    @DisplayName("maskPhone - 4자리 미만 짧은 번호이면 변경하지 않고 그대로 반환한다")
    void maskPhone_shortNumber_returnsUnchanged() {
        assertThat(PiiMaskingUtils.maskPhone("1234")).isEqualTo("1234");
    }

    @Test
    @DisplayName("maskPhone - 7자리 번호이면 변경하지 않고 그대로 반환한다")
    void maskPhone_sevenDigits_returnsUnchanged() {
        assertThat(PiiMaskingUtils.maskPhone("1234567")).isEqualTo("1234567");
    }

    @Test
    @DisplayName("maskPhone - 입력이 null이면 null을 반환한다")
    void maskPhone_nullInput_returnsNull() {
        assertThat(PiiMaskingUtils.maskPhone(null)).isNull();
    }

    // ----- maskIp -----

    @ParameterizedTest
    @CsvSource({
            "192.168.1.100, 192.168.1.***",
            "10.0.0.1, 10.0.0.***",
            "192.168.1.***, 192.168.1.***",
            "'', ''"
    })
    @DisplayName("maskIp - 유효한 IPv4 주소이면 마지막 옥텟을 ***로 치환한다")
    void maskIp_validIpv4_replacesLastOctet(String input, String expected) {
        assertThat(PiiMaskingUtils.maskIp(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("maskIp - 입력이 null이면 null을 반환한다")
    void maskIp_nullInput_returnsNull() {
        assertThat(PiiMaskingUtils.maskIp(null)).isNull();
    }

    @Test
    @DisplayName("maskIp - 점(.)이 없는 입력이면 변경하지 않고 그대로 반환한다")
    void maskIp_noDotInput_returnsUnchanged() {
        assertThat(PiiMaskingUtils.maskIp("notanip")).isEqualTo("notanip");
    }

    // ----- truncateFingerprint -----

    @Test
    @DisplayName("truncateFingerprint - 12자보다 긴 입력이면 앞 12자로 절단한다")
    void truncateFingerprint_longInput_truncatesTo12Chars() {
        assertThat(PiiMaskingUtils.truncateFingerprint("abcdef123456789012345678"))
                .isEqualTo("abcdef123456");
    }

    @Test
    @DisplayName("truncateFingerprint - 12자보다 짧은 입력이면 변경하지 않고 그대로 반환한다")
    void truncateFingerprint_shortInput_returnsUnchanged() {
        assertThat(PiiMaskingUtils.truncateFingerprint("abc")).isEqualTo("abc");
    }

    @Test
    @DisplayName("truncateFingerprint - 입력이 null이면 null을 반환한다")
    void truncateFingerprint_nullInput_returnsNull() {
        assertThat(PiiMaskingUtils.truncateFingerprint(null)).isNull();
    }

    @Test
    @DisplayName("truncateFingerprint - 입력이 정확히 12자이면 변경하지 않고 그대로 반환한다")
    void truncateFingerprint_exactly12Chars_returnsUnchanged() {
        assertThat(PiiMaskingUtils.truncateFingerprint("abcdef123456"))
                .isEqualTo("abcdef123456");
    }
}
