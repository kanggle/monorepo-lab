package com.example.user.domain.model;

import com.example.user.domain.exception.AddressLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("유효한 정보로 Address를 생성할 수 있다")
    void create_validInput_createsAddress() {
        Address address = Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "역삼동 123", true
        );

        assertThat(address.getId()).isNotNull();
        assertThat(address.getUserId()).isEqualTo(USER_ID);
        assertThat(address.getLabel()).isEqualTo("집");
        assertThat(address.getRecipientName()).isEqualTo("홍길동");
        assertThat(address.getPhone()).isEqualTo("010-1234-5678");
        assertThat(address.getZipCode()).isEqualTo("12345");
        assertThat(address.getAddress1()).isEqualTo("서울시 강남구");
        assertThat(address.getAddress2()).isEqualTo("역삼동 123");
        assertThat(address.isDefault()).isTrue();
        assertThat(address.getCreatedAt()).isNotNull();
        assertThat(address.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("address2가 null이면 null로 저장된다")
    void create_nullAddress2_setsNull() {
        Address address = Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        );

        assertThat(address.getAddress2()).isNull();
    }

    @Test
    @DisplayName("userId가 null이면 IllegalArgumentException 발생")
    void create_nullUserId_throwsException() {
        assertThatThrownBy(() -> Address.create(
                null, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID must not be null");
    }

    @Test
    @DisplayName("label이 빈 문자열이면 IllegalArgumentException 발생")
    void create_blankLabel_throwsException() {
        assertThatThrownBy(() -> Address.create(
                USER_ID, "  ", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Label must not be blank");
    }

    @Test
    @DisplayName("recipientName이 null이면 IllegalArgumentException 발생")
    void create_nullRecipientName_throwsException() {
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", null, "010-1234-5678",
                "12345", "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Recipient name must not be blank");
    }

    @Test
    @DisplayName("phone이 빈 문자열이면 IllegalArgumentException 발생")
    void create_blankPhone_throwsException() {
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", "홍길동", "",
                "12345", "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone must not be blank");
    }

    @Test
    @DisplayName("zipCode가 null이면 IllegalArgumentException 발생")
    void create_nullZipCode_throwsException() {
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                null, "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Zip code must not be blank");
    }

    @Test
    @DisplayName("address1이 빈 문자열이면 IllegalArgumentException 발생")
    void create_blankAddress1_throwsException() {
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "  ", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Address1 must not be blank");
    }

    @Test
    @DisplayName("부분 수정이 정상 동작한다")
    void update_partialFields_updatesOnlyProvided() {
        Address address = Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "역삼동 123", false
        );

        address.update("회사", null, null, null, null, null, null);

        assertThat(address.getLabel()).isEqualTo("회사");
        assertThat(address.getRecipientName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("주소 수가 최대치(10)에 도달하면 AddressLimitExceededException 발생")
    void validateAddressLimit_atMax_throwsException() {
        assertThatThrownBy(() -> Address.validateAddressLimit(10))
                .isInstanceOf(AddressLimitExceededException.class)
                .hasMessageContaining("10");
    }

    @Test
    @DisplayName("주소 수가 최대치 미만이면 예외 없이 통과")
    void validateAddressLimit_belowMax_noException() {
        Address.validateAddressLimit(9);
    }

    @Test
    @DisplayName("label이 50자를 초과하면 IllegalArgumentException 발생")
    void create_labelTooLong_throwsException() {
        String longLabel = "a".repeat(51);
        assertThatThrownBy(() -> Address.create(
                USER_ID, longLabel, "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("recipientName이 50자를 초과하면 IllegalArgumentException 발생")
    void create_recipientNameTooLong_throwsException() {
        String longName = "a".repeat(51);
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", longName, "010-1234-5678",
                "12345", "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("phone이 20자를 초과하면 IllegalArgumentException 발생")
    void create_phoneTooLong_throwsException() {
        String longPhone = "0".repeat(21);
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", "홍길동", longPhone,
                "12345", "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");
    }

    @Test
    @DisplayName("zipCode가 10자를 초과하면 IllegalArgumentException 발생")
    void create_zipCodeTooLong_throwsException() {
        String longZip = "1".repeat(11);
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                longZip, "서울시 강남구", null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10");
    }

    @Test
    @DisplayName("address1이 255자를 초과하면 IllegalArgumentException 발생")
    void create_address1TooLong_throwsException() {
        String longAddr = "a".repeat(256);
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", longAddr, null, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    @DisplayName("address2가 255자를 초과하면 IllegalArgumentException 발생")
    void create_address2TooLong_throwsException() {
        String longAddr2 = "a".repeat(256);
        assertThatThrownBy(() -> Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", longAddr2, false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    @DisplayName("경계값: label이 정확히 50자이면 정상 생성된다")
    void create_labelExactlyMax_succeeds() {
        String label50 = "a".repeat(50);
        Address address = Address.create(
                USER_ID, label50, "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        );
        assertThat(address.getLabel()).isEqualTo(label50);
    }

    @Test
    @DisplayName("update 시 label이 50자를 초과하면 IllegalArgumentException 발생")
    void update_labelTooLong_throwsException() {
        Address address = Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        );
        String longLabel = "a".repeat(51);

        assertThatThrownBy(() -> address.update(longLabel, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    @DisplayName("update 시 phone이 20자를 초과하면 IllegalArgumentException 발생")
    void update_phoneTooLong_throwsException() {
        Address address = Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        );
        String longPhone = "0".repeat(21);

        assertThatThrownBy(() -> address.update(null, null, longPhone, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20");
    }

    @Test
    @DisplayName("update 시 label이 빈 문자열이면 IllegalArgumentException 발생")
    void update_blankLabel_throwsException() {
        Address address = Address.create(
                USER_ID, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, false
        );

        assertThatThrownBy(() -> address.update("  ", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Label must not be blank");
    }
}
