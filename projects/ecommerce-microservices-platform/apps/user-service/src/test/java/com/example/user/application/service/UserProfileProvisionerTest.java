package com.example.user.application.service;

import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileProvisioner 단위 테스트 (TASK-BE-575)")
class UserProfileProvisionerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileProvisioner provisioner;

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("프로필이 없으면 최소 프로필을 만든다")
    void ensure_absent_creates() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.existsByUserId(USER_ID)).willReturn(false);

        provisioner.ensureProvisioned(USER_ID, null);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(ProfileStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 있으면 아무것도 쓰지 않는다 (요청마다 도는 경로다 — 멱등이 아니면 못 쓴다)")
    void ensure_present_isNoOp() {
        given(userProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(UserProfile.createMinimal(USER_ID)));

        provisioner.ensureProvisioned(USER_ID, null);

        then(userProfileRepository).should(never()).save(any());
        then(userProfileRepository).should(never()).existsByUserId(any());
    }

    @Test
    @DisplayName("게이트웨이가 X-User-Email 을 주면 프로필이 그 값을 갖는다")
    void ensure_withEdgeEmail_carriesIt() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.existsByUserId(USER_ID)).willReturn(false);

        provisioner.ensureProvisioned(USER_ID, "  shopper@example.com  ");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(captor.capture());
        assertThat(captor.getValue().getEmail().value()).isEqualTo("shopper@example.com");
    }

    @Test
    @DisplayName("쓸 수 없는 email 때문에 요청이 실패하지는 않는다 — 최소 프로필로 내려앉는다")
    void ensure_malformedEdgeEmail_fallsBackToMinimal() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.existsByUserId(USER_ID)).willReturn(false);

        provisioner.ensureProvisioned(USER_ID, "not-an-email");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    /**
     * {@code uq_user_profiles_user_id} is global while {@code findByUserId} is tenant-scoped
     * (measured, TASK-BE-575). Inserting here would violate the unique index; skipping is the
     * only safe move, and the caller's 404 then reflects a real state rather than a crash.
     */
    @Test
    @DisplayName("다른 테넌트에 같은 user_id 프로필이 있으면 INSERT 를 시도하지 않는다 (unique 는 전역)")
    void ensure_existsInAnotherTenant_doesNotInsert() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.existsByUserId(USER_ID)).willReturn(true);

        provisioner.ensureProvisioned(USER_ID, null);

        then(userProfileRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("동시 첫 요청 두 건 중 진 쪽의 unique 위반은 실패가 아니다 — 행은 존재한다")
    void ensure_concurrentInsert_isSwallowed() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.existsByUserId(USER_ID)).willReturn(false);
        willThrow(new org.springframework.dao.DataIntegrityViolationException("uq_user_profiles_user_id"))
                .given(userProfileRepository).save(any());

        assertThatCode(() -> provisioner.ensureProvisioned(USER_ID, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("userId 가 없으면 아무 일도 하지 않는다")
    void ensure_nullUserId_isNoOp() {
        provisioner.ensureProvisioned(null, null);

        then(userProfileRepository).shouldHaveNoInteractions();
    }
}
