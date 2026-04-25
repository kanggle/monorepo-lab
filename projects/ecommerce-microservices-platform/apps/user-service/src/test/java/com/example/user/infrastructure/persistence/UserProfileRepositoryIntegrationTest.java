package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.Address;
import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.AddressRepository;
import com.example.user.domain.repository.UserProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
@Testcontainers
@Transactional
@DisplayName("UserProfileRepository 통합 테스트")
class UserProfileRepositoryIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("UserProfile을 저장하고 userId로 조회할 수 있다")
    void save_andFindByUserId_success() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "test@example.com", "홍길동");

        userProfileRepository.save(profile);
        em.flush();
        em.clear();

        Optional<UserProfile> found = userProfileRepository.findByUserId(userId);
        assertThat(found).isPresent();
        assertThat(found.get().getEmail().value()).isEqualTo("test@example.com");
        assertThat(found.get().getName()).isEqualTo("홍길동");
        assertThat(found.get().getStatus()).isEqualTo(ProfileStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 빈 결과를 반환한다")
    void findByUserId_notFound_returnsEmpty() {
        Optional<UserProfile> found = userProfileRepository.findByUserId(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByUserId로 존재 여부를 확인할 수 있다")
    void existsByUserId_existingUser_returnsTrue() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "exists@example.com", "존재확인");
        userProfileRepository.save(profile);

        em.flush();
        em.clear();

        assertThat(userProfileRepository.existsByUserId(userId)).isTrue();
        assertThat(userProfileRepository.existsByUserId(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Address를 저장하고 userId로 조회할 수 있다")
    void saveAddress_andFindAllByUserId_success() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "addr@example.com", "주소테스트");
        userProfileRepository.save(profile);

        Address address = Address.create(
                userId, "집", "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "역삼동 123", true
        );
        addressRepository.save(address);

        em.flush();
        em.clear();

        List<Address> addresses = addressRepository.findAllByUserId(userId);
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).getLabel()).isEqualTo("집");
        assertThat(addresses.get(0).isDefault()).isTrue();
    }

    @Test
    @DisplayName("Address를 id와 userId로 조회할 수 있다")
    void findByIdAndUserId_success() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "addr2@example.com", "주소조회");
        userProfileRepository.save(profile);

        Address address = Address.create(
                userId, "회사", "김철수", "010-9876-5432",
                "54321", "부산시 해운대구", null, false
        );
        addressRepository.save(address);

        em.flush();
        em.clear();

        Optional<Address> found = addressRepository.findByIdAndUserId(address.getId(), userId);
        assertThat(found).isPresent();
        assertThat(found.get().getRecipientName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("다른 사용자의 주소는 조회되지 않는다")
    void findByIdAndUserId_wrongUser_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "owner@example.com", "소유자");
        userProfileRepository.save(profile);

        Address address = Address.create(
                userId, "집", "홍길동", "010-1234-5678",
                "12345", "서울시", null, true
        );
        addressRepository.save(address);

        em.flush();
        em.clear();

        Optional<Address> found = addressRepository.findByIdAndUserId(address.getId(), otherUserId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("사용자의 주소 수를 조회할 수 있다")
    void countByUserId_returnsCorrectCount() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "count@example.com", "개수확인");
        userProfileRepository.save(profile);

        for (int i = 0; i < 3; i++) {
            Address address = Address.create(
                    userId, "주소" + i, "수신자" + i, "010-0000-000" + i,
                    "1234" + i, "주소1-" + i, null, i == 0
            );
            addressRepository.save(address);
        }

        em.flush();
        em.clear();

        assertThat(addressRepository.countByUserId(userId)).isEqualTo(3);
    }

    @Test
    @DisplayName("주소를 삭제할 수 있다")
    void deleteAddress_removesFromDb() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "delete@example.com", "삭제테스트");
        userProfileRepository.save(profile);

        Address address = Address.create(
                userId, "삭제할주소", "홍길동", "010-1234-5678",
                "12345", "서울시", null, false
        );
        addressRepository.save(address);

        em.flush();
        em.clear();

        Address toDelete = addressRepository.findByIdAndUserId(address.getId(), userId).orElseThrow();
        addressRepository.delete(toDelete);

        em.flush();
        em.clear();

        assertThat(addressRepository.findByIdAndUserId(address.getId(), userId)).isEmpty();
    }

    @Test
    @DisplayName("UserProfile 수정이 DB에 반영된다")
    void updateProfile_persists() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "update@example.com", "수정전");
        userProfileRepository.save(profile);

        em.flush();
        em.clear();

        UserProfile loaded = userProfileRepository.findByUserId(userId).orElseThrow();
        loaded.updateNickname("새닉네임");
        loaded.updatePhone("010-9999-8888");
        userProfileRepository.save(loaded);

        em.flush();
        em.clear();

        UserProfile updated = userProfileRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getNickname()).isEqualTo("새닉네임");
        assertThat(updated.getPhone()).isEqualTo("010-9999-8888");
    }
}
