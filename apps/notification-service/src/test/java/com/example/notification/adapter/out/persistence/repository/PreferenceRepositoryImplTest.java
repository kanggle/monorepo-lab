package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.UserNotificationPreferenceJpaEntity;
import com.example.notification.adapter.out.persistence.mapper.PreferencePersistenceMapper;
import com.example.notification.domain.model.UserNotificationPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreferenceRepositoryImpl 단위 테스트")
class PreferenceRepositoryImplTest {

    @InjectMocks
    private PreferenceRepositoryImpl repositoryImpl;

    @Mock
    private UserNotificationPreferenceJpaRepository jpaRepository;

    @Mock
    private PreferencePersistenceMapper mapper;

    @Test
    @DisplayName("사용자 설정을 저장하면 매퍼를 통해 엔티티 변환 후 저장된 도메인 객체를 반환한다")
    void save_validPreference_returnsSavedDomainObject() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 6, 12, 0);
        UserNotificationPreference preference = UserNotificationPreference.reconstitute(
                "user-1", true, false, true, now, now);
        UserNotificationPreferenceJpaEntity entity = mock(UserNotificationPreferenceJpaEntity.class);
        UserNotificationPreferenceJpaEntity savedEntity = mock(UserNotificationPreferenceJpaEntity.class);
        UserNotificationPreference savedPreference = UserNotificationPreference.reconstitute(
                "user-1", true, false, true, now, now);

        given(mapper.toEntity(preference)).willReturn(entity);
        given(jpaRepository.save(entity)).willReturn(savedEntity);
        given(mapper.toDomain(savedEntity)).willReturn(savedPreference);

        UserNotificationPreference result = repositoryImpl.save(preference);

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.isEmailEnabled()).isTrue();
        assertThat(result.isSmsEnabled()).isFalse();
        assertThat(result.isPushEnabled()).isTrue();
    }

    @Test
    @DisplayName("존재하는 userId로 조회하면 도메인 객체를 반환한다")
    void findByUserId_existingUser_returnsDomainObject() {
        UserNotificationPreferenceJpaEntity entity = mock(UserNotificationPreferenceJpaEntity.class);
        LocalDateTime now = LocalDateTime.of(2026, 4, 6, 12, 0);
        UserNotificationPreference preference = UserNotificationPreference.reconstitute(
                "user-1", true, true, false, now, now);

        given(jpaRepository.findById("user-1")).willReturn(Optional.of(entity));
        given(mapper.toDomain(entity)).willReturn(preference);

        Optional<UserNotificationPreference> result = repositoryImpl.findByUserId("user-1");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("user-1");
        assertThat(result.get().isEmailEnabled()).isTrue();
        assertThat(result.get().isSmsEnabled()).isTrue();
        assertThat(result.get().isPushEnabled()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 빈 Optional을 반환한다")
    void findByUserId_nonExistingUser_returnsEmpty() {
        given(jpaRepository.findById("unknown-user")).willReturn(Optional.empty());

        Optional<UserNotificationPreference> result = repositoryImpl.findByUserId("unknown-user");

        assertThat(result).isEmpty();
    }
}
