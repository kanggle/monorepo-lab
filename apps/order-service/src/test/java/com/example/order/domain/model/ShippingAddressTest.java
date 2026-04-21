package com.example.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShippingAddress 도메인 값 객체 단위 테스트")
class ShippingAddressTest {

    @Test
    @DisplayName("유효한 값으로 생성 시 모든 필드가 올바르게 설정된다")
    void create_validValues_setsAllFieldsCorrectly() {
        ShippingAddress address = new ShippingAddress(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", "101호"
        );

        assertThat(address.getRecipient()).isEqualTo("홍길동");
        assertThat(address.getPhone()).isEqualTo("010-1234-5678");
        assertThat(address.getZipCode()).isEqualTo("12345");
        assertThat(address.getAddress1()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(address.getAddress2()).isEqualTo("101호");
    }

    @Test
    @DisplayName("address2가 null이어도 생성에 성공한다")
    void create_nullAddress2_succeeds() {
        ShippingAddress address = new ShippingAddress(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", null
        );

        assertThat(address.getAddress2()).isNull();
    }

    @Test
    @DisplayName("address2가 빈 문자열이어도 생성에 성공한다")
    void create_emptyAddress2_succeeds() {
        ShippingAddress address = new ShippingAddress(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", ""
        );

        assertThat(address.getAddress2()).isEmpty();
    }

    @Test
    @DisplayName("recipient가 null이면 생성 시 IllegalArgumentException이 발생한다")
    void create_nullRecipient_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress(null, "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    @DisplayName("recipient가 공백이면 생성 시 IllegalArgumentException이 발생한다")
    void create_blankRecipient_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress("   ", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    @DisplayName("phone이 null이면 생성 시 IllegalArgumentException이 발생한다")
    void create_nullPhone_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress("홍길동", null, "12345", "서울시 강남구 테헤란로 1", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phone");
    }

    @Test
    @DisplayName("phone이 공백이면 생성 시 IllegalArgumentException이 발생한다")
    void create_blankPhone_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress("홍길동", "   ", "12345", "서울시 강남구 테헤란로 1", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phone");
    }

    @Test
    @DisplayName("zipCode가 null이면 생성 시 IllegalArgumentException이 발생한다")
    void create_nullZipCode_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress("홍길동", "010-1234-5678", null, "서울시 강남구 테헤란로 1", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zipCode");
    }

    @Test
    @DisplayName("zipCode가 공백이면 생성 시 IllegalArgumentException이 발생한다")
    void create_blankZipCode_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress("홍길동", "010-1234-5678", "   ", "서울시 강남구 테헤란로 1", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zipCode");
    }

    @Test
    @DisplayName("address1이 null이면 생성 시 IllegalArgumentException이 발생한다")
    void create_nullAddress1_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress("홍길동", "010-1234-5678", "12345", null, null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("address1");
    }

    @Test
    @DisplayName("address1이 공백이면 생성 시 IllegalArgumentException이 발생한다")
    void create_blankAddress1_throwsIllegalArgumentException() {
        assertThatThrownBy(() ->
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "   ", null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("address1");
    }
}
