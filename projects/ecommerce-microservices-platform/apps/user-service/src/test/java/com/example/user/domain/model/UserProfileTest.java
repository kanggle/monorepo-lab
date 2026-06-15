package com.example.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    @Test
    @DisplayName("유효한 정보로 UserProfile을 생성할 수 있다")
    void create_validInput_createsProfile() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String name = "홍길동";

        UserProfile profile = UserProfile.create(userId, email, name);

        assertThat(profile.getId()).isNotNull();
        assertThat(profile.getUserId()).isEqualTo(userId);
        assertThat(profile.getEmail().value()).isEqualTo(email);
        assertThat(profile.getName()).isEqualTo(name);
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(profile.getNickname()).isNull();
        assertThat(profile.getPhone()).isNull();
        assertThat(profile.getProfileImageUrl()).isNull();
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("name이 null이면 빈 문자열로 생성된다")
    void create_nullName_setsEmptyString() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", null);

        assertThat(profile.getName()).isEmpty();
    }

    @Test
    @DisplayName("name이 빈 문자열이면 빈 문자열로 생성된다")
    void create_blankName_setsEmptyString() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "  ");

        assertThat(profile.getName()).isEmpty();
    }

    @Test
    @DisplayName("userId가 null이면 IllegalArgumentException 발생")
    void create_nullUserId_throwsException() {
        assertThatThrownBy(() -> UserProfile.create(null, "test@example.com", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID must not be null");
    }

    @Test
    @DisplayName("email이 null이면 IllegalArgumentException 발생")
    void create_nullEmail_throwsException() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), null, "홍길동"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email must not be blank");
    }

    @Test
    @DisplayName("email이 빈 문자열이면 IllegalArgumentException 발생")
    void create_blankEmail_throwsException() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), "  ", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email must not be blank");
    }

    @Test
    @DisplayName("유효하지 않은 이메일 형식이면 IllegalArgumentException 발생")
    void create_invalidEmailFormat_throwsException() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), "invalid-email", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email format is invalid");
    }

    @Test
    @DisplayName("@만 있는 이메일이면 IllegalArgumentException 발생")
    void create_emailWithOnlyAt_throwsException() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), "user@", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email format is invalid");
    }

    @Test
    @DisplayName("도메인 없는 이메일이면 IllegalArgumentException 발생")
    void create_emailWithoutDomain_throwsException() {
        assertThatThrownBy(() -> UserProfile.create(UUID.randomUUID(), "user@domain", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email format is invalid");
    }

    @Test
    @DisplayName("유효한 이메일 형식이면 정상 생성된다")
    void create_validEmailFormats_succeeds() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "user.name+tag@example.co.kr", "홍길동");
        assertThat(profile.getEmail().value()).isEqualTo("user.name+tag@example.co.kr");
    }

    @Test
    @DisplayName("닉네임을 수정할 수 있다")
    void updateNickname_validInput_updatesNickname() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");

        profile.updateNickname("길동이");

        assertThat(profile.getNickname()).isEqualTo("길동이");
    }

    @Test
    @DisplayName("닉네임을 null로 설정할 수 있다")
    void updateNickname_null_setsNull() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");
        profile.updateNickname("길동이");

        profile.updateNickname(null);

        assertThat(profile.getNickname()).isNull();
    }

    @Test
    @DisplayName("닉네임이 50자를 초과하면 IllegalArgumentException 발생")
    void updateNickname_tooLong_throwsException() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");
        String longNickname = "a".repeat(51);

        assertThatThrownBy(() -> profile.updateNickname(longNickname))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nickname must not exceed 50 characters");
    }

    @Test
    @DisplayName("전화번호를 수정할 수 있다")
    void updatePhone_validInput_updatesPhone() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");

        profile.updatePhone("010-1234-5678");

        assertThat(profile.getPhone()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("프로필 이미지 URL을 수정할 수 있다")
    void updateProfileImageUrl_validInput_updatesUrl() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");

        profile.updateProfileImageUrl("https://example.com/image.jpg");

        assertThat(profile.getProfileImageUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    @DisplayName("프로필 상태를 WITHDRAWN으로 변경할 수 있다")
    void withdraw_changesStatusToWithdrawn() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");

        profile.withdraw();

        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.WITHDRAWN);
        assertThat(profile.isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("createMinimal: account.created 로부터 email/name 없이 최소 프로필을 생성한다 (ADR-MONO-037 P1)")
    void createMinimal_noPii_createsActiveProfileWithNullEmailAndName() {
        UUID accountId = UUID.randomUUID();

        UserProfile profile = UserProfile.createMinimal(accountId);

        assertThat(profile.getId()).isNotNull();
        assertThat(profile.getUserId()).isEqualTo(accountId);
        assertThat(profile.getEmail()).isNull();
        assertThat(profile.getName()).isNull();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("createMinimal: userId 가 null 이면 IllegalArgumentException 발생")
    void createMinimal_nullUserId_throwsException() {
        assertThatThrownBy(() -> UserProfile.createMinimal(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID must not be null");
    }

    @Test
    @DisplayName("anonymize: 모든 PII 를 비우고 WITHDRAWN 으로 만들되 userId 는 보존한다 (ADR-MONO-037 P2/P3)")
    void anonymize_clearsAllPiiPreservesUserId() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "pii@example.com", "실명");
        profile.updateNickname("닉네임");
        profile.updatePhone("010-1234-5678");
        profile.updateProfileImageUrl("https://example.com/p.jpg");

        profile.anonymize();

        assertThat(profile.getUserId()).isEqualTo(userId);
        assertThat(profile.getEmail()).isNull();
        assertThat(profile.getName()).isNull();
        assertThat(profile.getNickname()).isNull();
        assertThat(profile.getPhone()).isNull();
        assertThat(profile.getProfileImageUrl()).isNull();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("anonymize: 재적용해도 안전하다 (멱등 — 이미 비워진 필드를 다시 비운다)")
    void anonymize_idempotent() {
        UserProfile profile = UserProfile.createMinimal(UUID.randomUUID());

        profile.anonymize();
        profile.anonymize();

        assertThat(profile.getEmail()).isNull();
        assertThat(profile.getStatus()).isEqualTo(ProfileStatus.WITHDRAWN);
    }

}
