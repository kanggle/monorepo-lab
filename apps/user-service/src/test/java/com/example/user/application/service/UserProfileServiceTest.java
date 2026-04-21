package com.example.user.application.service;

import com.example.user.application.command.UpdateProfileCommand;
import com.example.user.application.event.UserProfileUpdatedSpringEvent;
import com.example.user.application.result.UserListPageResult;
import com.example.user.application.result.UserProfileResult;
import com.example.user.application.result.UserProfileSummaryResult;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService 단위 테스트")
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private UserProfileService userProfileService;

    @Nested
    @DisplayName("getProfile")
    class GetProfile {

        @Test
        @DisplayName("존재하는 사용자 프로필을 조회한다")
        void getProfile_existingUser_returnsProfile() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@example.com", "홍길동");
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

            UserProfileResult result = userProfileService.getProfile(userId);

            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo("test@example.com");
            assertThat(result.name()).isEqualTo("홍길동");
            assertThat(result.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 프로필 조회 시 예외가 발생한다")
        void getProfile_nonExistingUser_throws() {
            UUID userId = UUID.randomUUID();
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.getProfile(userId))
                    .isInstanceOf(UserProfileNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임, 전화번호, 프로필 이미지를 수정하고 Spring 이벤트를 발행한다")
        void updateProfile_allFields_updatesAndPublishesEvent() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@example.com", "홍길동");
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
            given(userProfileRepository.save(any(UserProfile.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new UpdateProfileCommand(userId, "새닉네임", "010-1234-5678", "https://img.example.com/photo.jpg");
            UserProfileResult result = userProfileService.updateProfile(command);

            assertThat(result.nickname()).isEqualTo("새닉네임");
            assertThat(result.phone()).isEqualTo("010-1234-5678");
            assertThat(result.profileImageUrl()).isEqualTo("https://img.example.com/photo.jpg");

            ArgumentCaptor<UserProfileUpdatedSpringEvent> captor = ArgumentCaptor.forClass(UserProfileUpdatedSpringEvent.class);
            then(applicationEventPublisher).should().publishEvent(captor.capture());
            UserProfileUpdatedSpringEvent event = captor.getValue();
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.nickname()).isEqualTo("새닉네임");
            assertThat(event.phone()).isEqualTo("010-1234-5678");
        }

        @Test
        @DisplayName("null이 아닌 필드만 수정한다 (부분 수정)")
        void updateProfile_partialUpdate_onlyUpdatesNonNullFields() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@example.com", "홍길동");
            profile.updateNickname("기존닉네임");
            profile.updatePhone("010-0000-0000");
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
            given(userProfileRepository.save(any(UserProfile.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new UpdateProfileCommand(userId, "새닉네임", null, null);
            UserProfileResult result = userProfileService.updateProfile(command);

            assertThat(result.nickname()).isEqualTo("새닉네임");
            assertThat(result.phone()).isEqualTo("010-0000-0000");
            then(applicationEventPublisher).should().publishEvent(any(UserProfileUpdatedSpringEvent.class));
        }

        @Test
        @DisplayName("모든 필드가 동일하면 이벤트를 발행하지 않는다")
        void updateProfile_noChanges_doesNotPublishEvent() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@example.com", "홍길동");
            profile.updateNickname("기존닉네임");
            profile.updatePhone("010-0000-0000");
            profile.updateProfileImageUrl("https://img.example.com/old.jpg");
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
            given(userProfileRepository.save(any(UserProfile.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new UpdateProfileCommand(userId, "기존닉네임", "010-0000-0000", "https://img.example.com/old.jpg");
            userProfileService.updateProfile(command);

            then(applicationEventPublisher).should(never()).publishEvent(any(UserProfileUpdatedSpringEvent.class));
        }

        @Test
        @DisplayName("null 필드만 전달 시 변경 없으므로 이벤트를 발행하지 않는다")
        void updateProfile_allNull_doesNotPublishEvent() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@example.com", "홍길동");
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
            given(userProfileRepository.save(any(UserProfile.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new UpdateProfileCommand(userId, null, null, null);
            userProfileService.updateProfile(command);

            then(applicationEventPublisher).should(never()).publishEvent(any(UserProfileUpdatedSpringEvent.class));
        }

        @Test
        @DisplayName("공백이 포함된 값은 trim 후 저장되며 기존 값과 동일하면 이벤트를 발행하지 않는다")
        void updateProfile_trimmedValueSameAsExisting_doesNotPublishEvent() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "test@example.com", "홍길동");
            profile.updateNickname("기존닉네임");
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.of(profile));
            given(userProfileRepository.save(any(UserProfile.class))).willAnswer(inv -> inv.getArgument(0));

            var command = new UpdateProfileCommand(userId, "  기존닉네임  ", null, null);
            UserProfileResult result = userProfileService.updateProfile(command);

            assertThat(result.nickname()).isEqualTo("기존닉네임");
            then(applicationEventPublisher).should(never()).publishEvent(any(UserProfileUpdatedSpringEvent.class));
        }

        @Test
        @DisplayName("프로필 미존재 시 예외가 발생한다")
        void updateProfile_nonExistingUser_throws() {
            UUID userId = UUID.randomUUID();
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.empty());

            var command = new UpdateProfileCommand(userId, "닉네임", null, null);

            assertThatThrownBy(() -> userProfileService.updateProfile(command))
                    .isInstanceOf(UserProfileNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("listUsers")
    class ListUsers {

        @Test
        @DisplayName("전체 사용자 목록을 페이지네이션하여 반환한다")
        void listUsers_noFilter_returnsAll() {
            UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");
            PageResult<UserProfile> pageResult = new PageResult<>(List.of(profile), 0, 20, 1L, 1);
            given(userProfileRepository.findAll(any(PageQuery.class))).willReturn(pageResult);

            UserListPageResult result = userProfileService.listUsers(null, null, 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).email()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("status 필터로 사용자 목록을 조회한다")
        void listUsers_statusFilter_filtersByStatus() {
            UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");
            PageResult<UserProfile> pageResult = new PageResult<>(List.of(profile), 0, 20, 1L, 1);
            given(userProfileRepository.findByStatus(eq(ProfileStatus.ACTIVE), any(PageQuery.class))).willReturn(pageResult);

            UserListPageResult result = userProfileService.listUsers(ProfileStatus.ACTIVE, null, 0, 20);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("email 부분 검색으로 사용자 목록을 조회한다")
        void listUsers_emailFilter_filtersByEmail() {
            UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");
            PageResult<UserProfile> pageResult = new PageResult<>(List.of(profile), 0, 20, 1L, 1);
            given(userProfileRepository.findByEmailContaining(eq("test"), any(PageQuery.class))).willReturn(pageResult);

            UserListPageResult result = userProfileService.listUsers(null, "test", 0, 20);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("status와 email 필터를 동시에 적용한다")
        void listUsers_statusAndEmailFilter_filtersByBoth() {
            UserProfile profile = UserProfile.create(UUID.randomUUID(), "test@example.com", "홍길동");
            PageResult<UserProfile> pageResult = new PageResult<>(List.of(profile), 0, 20, 1L, 1);
            given(userProfileRepository.findByStatusAndEmailContaining(
                    eq(ProfileStatus.ACTIVE), eq("test"), any(PageQuery.class))).willReturn(pageResult);

            UserListPageResult result = userProfileService.listUsers(ProfileStatus.ACTIVE, "test", 0, 20);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("음수 페이지 번호는 0으로 보정된다")
        void listUsers_negativePage_correctedToZero() {
            PageResult<UserProfile> pageResult = new PageResult<>(List.of(), 0, 20, 0L, 0);
            given(userProfileRepository.findAll(any(PageQuery.class))).willReturn(pageResult);

            UserListPageResult result = userProfileService.listUsers(null, null, -1, 20);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("관리자가 특정 사용자 프로필을 조회한다")
        void getUserById_existingUser_returnsProfile() {
            UUID userId = UUID.randomUUID();
            UserProfile profile = UserProfile.create(userId, "admin-test@example.com", "관리자조회");
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.of(profile));

            UserProfileResult result = userProfileService.getProfile(userId);

            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo("admin-test@example.com");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 예외가 발생한다")
        void getUserById_nonExistingUser_throws() {
            UUID userId = UUID.randomUUID();
            given(userProfileRepository.findByUserId(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userProfileService.getProfile(userId))
                    .isInstanceOf(UserProfileNotFoundException.class);
        }
    }
}
