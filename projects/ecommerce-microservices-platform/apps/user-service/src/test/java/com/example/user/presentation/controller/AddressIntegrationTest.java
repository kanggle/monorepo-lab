package com.example.user.presentation.controller;

import com.example.user.domain.model.Address;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.AddressRepository;
import com.example.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("배송 주소 API 통합 테스트")
class AddressIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @SuppressWarnings("resource")
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private AddressRepository addressRepository;

    private UUID createUser() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "addr-test-" + userId + "@example.com", "주소테스트");
        userProfileRepository.save(profile);
        return userId;
    }

    private Address createAndSaveAddress(UUID userId, String label, boolean isDefault) {
        Address address = Address.create(userId, label, "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", null, isDefault);
        return addressRepository.save(address);
    }

    @Nested
    @DisplayName("GET /api/users/me/addresses")
    class GetAddresses {

        @Test
        @DisplayName("사용자의 주소 목록을 반환한다")
        void getAddresses_withAddresses_returns200() throws Exception {
            UUID userId = createUser();
            createAndSaveAddress(userId, "집", true);
            createAndSaveAddress(userId, "회사", false);

            mockMvc.perform(get("/api/users/me/addresses")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.addresses").isArray())
                    .andExpect(jsonPath("$.addresses.length()").value(2));
        }

        @Test
        @DisplayName("주소가 없으면 빈 배열을 반환한다")
        void getAddresses_noAddresses_returnsEmptyArray() throws Exception {
            UUID userId = createUser();

            mockMvc.perform(get("/api/users/me/addresses")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.addresses").isArray())
                    .andExpect(jsonPath("$.addresses.length()").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/users/me/addresses")
    class CreateAddress {

        @Test
        @DisplayName("첫 번째 주소를 생성하면 201과 id를 반환하고 자동으로 기본 주소가 된다")
        void createAddress_firstAddress_returns201AndIsDefault() throws Exception {
            UUID userId = createUser();

            mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "집",
                                      "recipientName": "홍길동",
                                      "phone": "010-1234-5678",
                                      "zipCode": "12345",
                                      "address1": "서울시 강남구",
                                      "isDefault": false
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty());

            List<Address> addresses = addressRepository.findAllByUserId(userId);
            assertThat(addresses).hasSize(1);
            assertThat(addresses.get(0).isDefault()).isTrue();
        }

        @Test
        @DisplayName("isDefault=true로 생성 시 기존 기본 주소가 해제된다")
        void createAddress_newDefault_unmarksOldDefault() throws Exception {
            UUID userId = createUser();
            Address existing = createAndSaveAddress(userId, "기존", true);

            mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "새기본",
                                      "recipientName": "홍길동",
                                      "phone": "010-1234-5678",
                                      "zipCode": "12345",
                                      "address1": "서울시 서초구",
                                      "isDefault": true
                                    }
                                    """))
                    .andExpect(status().isCreated());

            List<Address> addresses = addressRepository.findAllByUserId(userId);
            long defaultCount = addresses.stream().filter(Address::isDefault).count();
            assertThat(defaultCount).isEqualTo(1);
            assertThat(addresses.stream().filter(Address::isDefault).findFirst().get().getLabel())
                    .isEqualTo("새기본");
        }

        @Test
        @DisplayName("필수 필드 누락 시 400 VALIDATION_ERROR를 반환한다")
        void createAddress_missingField_returns400() throws Exception {
            UUID userId = createUser();

            mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "집",
                                      "recipientName": "",
                                      "phone": "010-1234-5678",
                                      "zipCode": "12345",
                                      "address1": "서울시 강남구"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("10개 초과 시 422 ADDRESS_LIMIT_EXCEEDED를 반환한다")
        void createAddress_exceedsLimit_returns422() throws Exception {
            UUID userId = createUser();
            for (int i = 0; i < 10; i++) {
                createAndSaveAddress(userId, "주소" + i, i == 0);
            }

            mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "label": "11번째",
                                      "recipientName": "홍길동",
                                      "phone": "010-1234-5678",
                                      "zipCode": "12345",
                                      "address1": "서울시 강남구"
                                    }
                                    """))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_EXCEEDED"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me/addresses/{addressId}")
    class UpdateAddress {

        @Test
        @DisplayName("주소를 부분 수정하면 200과 id를 반환한다")
        void updateAddress_partialUpdate_returns200() throws Exception {
            UUID userId = createUser();
            Address address = createAndSaveAddress(userId, "집", true);

            mockMvc.perform(patch("/api/users/me/addresses/{addressId}", address.getId())
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"새집\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(address.getId().toString()));

            Address updated = addressRepository.findByIdAndUserId(address.getId(), userId).orElseThrow();
            assertThat(updated.getLabel()).isEqualTo("새집");
        }

        @Test
        @DisplayName("isDefault=true로 변경 시 기존 기본 주소가 해제된다")
        void updateAddress_setDefault_unmarksOldDefault() throws Exception {
            UUID userId = createUser();
            Address defaultAddr = createAndSaveAddress(userId, "기존기본", true);
            Address otherAddr = createAndSaveAddress(userId, "다른", false);

            mockMvc.perform(patch("/api/users/me/addresses/{addressId}", otherAddr.getId())
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isDefault\":true}"))
                    .andExpect(status().isOk());

            List<Address> addresses = addressRepository.findAllByUserId(userId);
            long defaultCount = addresses.stream().filter(Address::isDefault).count();
            assertThat(defaultCount).isEqualTo(1);
            Address newDefault = addresses.stream().filter(Address::isDefault).findFirst().orElseThrow();
            assertThat(newDefault.getId()).isEqualTo(otherAddr.getId());
        }

        @Test
        @DisplayName("존재하지 않는 주소 수정 시 404를 반환한다")
        void updateAddress_nonExisting_returns404() throws Exception {
            UUID userId = createUser();
            UUID fakeId = UUID.randomUUID();

            mockMvc.perform(patch("/api/users/me/addresses/{addressId}", fakeId)
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"새이름\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 사용자의 주소에 접근하면 404를 반환한다")
        void updateAddress_otherUsersAddress_returns404() throws Exception {
            UUID ownerUserId = createUser();
            UUID otherUserId = createUser();
            Address address = createAndSaveAddress(ownerUserId, "남의주소", true);

            mockMvc.perform(patch("/api/users/me/addresses/{addressId}", address.getId())
                            .header("X-User-Id", otherUserId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"label\":\"변경시도\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시에 두 주소를 기본 배송지로 설정해도 데이터 무결성이 유지된다")
        void concurrentSetDefault_maintainsDataIntegrity() throws Exception {
            UUID userId = createUser();
            createAndSaveAddress(userId, "주소1", true);
            Address addr2 = createAndSaveAddress(userId, "주소2", false);
            Address addr3 = createAndSaveAddress(userId, "주소3", false);

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            Runnable setDefault2 = () -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    mockMvc.perform(patch("/api/users/me/addresses/{addressId}", addr2.getId())
                                    .header("X-User-Id", userId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"isDefault\":true}"));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            };

            Runnable setDefault3 = () -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    mockMvc.perform(patch("/api/users/me/addresses/{addressId}", addr3.getId())
                                    .header("X-User-Id", userId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"isDefault\":true}"));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            };

            executor.submit(setDefault2);
            executor.submit(setDefault3);

            readyLatch.await();
            startLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

            // 검증: 에러 없이 처리되고, 기본 주소가 최소 1개 존재한다
            assertThat(failureCount.get()).isZero();
            List<Address> addresses = addressRepository.findAllByUserId(userId);
            assertThat(addresses).hasSize(3);
            long defaultCount = addresses.stream().filter(Address::isDefault).count();
            assertThat(defaultCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("순차적 기본 배송지 변경 시 이전 기본 주소가 단일 UPDATE 쿼리로 해제된다")
        void sequentialSetDefault_unmarksOldDefault() throws Exception {
            UUID userId = createUser();
            Address addr1 = createAndSaveAddress(userId, "주소1", true);
            Address addr2 = createAndSaveAddress(userId, "주소2", false);

            mockMvc.perform(patch("/api/users/me/addresses/{addressId}", addr2.getId())
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isDefault\":true}"))
                    .andExpect(status().isOk());

            List<Address> addresses = addressRepository.findAllByUserId(userId);
            long defaultCount = addresses.stream().filter(Address::isDefault).count();
            assertThat(defaultCount).isEqualTo(1);
            Address newDefault = addresses.stream().filter(Address::isDefault).findFirst().orElseThrow();
            assertThat(newDefault.getId()).isEqualTo(addr2.getId());
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me/addresses/{addressId}")
    class DeleteAddress {

        @Test
        @DisplayName("비기본 주소를 삭제하면 204를 반환한다")
        void deleteAddress_nonDefault_returns204() throws Exception {
            UUID userId = createUser();
            createAndSaveAddress(userId, "기본", true);
            Address nonDefault = createAndSaveAddress(userId, "삭제대상", false);

            mockMvc.perform(delete("/api/users/me/addresses/{addressId}", nonDefault.getId())
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNoContent());

            assertThat(addressRepository.findByIdAndUserId(nonDefault.getId(), userId)).isEmpty();
        }

        @Test
        @DisplayName("유일한 기본 주소를 삭제하면 204를 반환한다")
        void deleteAddress_onlyDefaultAddress_returns204() throws Exception {
            UUID userId = createUser();
            Address onlyDefault = createAndSaveAddress(userId, "유일기본", true);

            mockMvc.perform(delete("/api/users/me/addresses/{addressId}", onlyDefault.getId())
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNoContent());

            assertThat(addressRepository.findAllByUserId(userId)).isEmpty();
        }

        @Test
        @DisplayName("기본 주소 삭제 시 다른 주소가 있으면 422를 반환한다")
        void deleteAddress_defaultWithOthers_returns422() throws Exception {
            UUID userId = createUser();
            Address defaultAddr = createAndSaveAddress(userId, "기본", true);
            createAndSaveAddress(userId, "다른", false);

            mockMvc.perform(delete("/api/users/me/addresses/{addressId}", defaultAddr.getId())
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DEFAULT_ADDRESS_CANNOT_BE_DELETED"));
        }

        @Test
        @DisplayName("존재하지 않는 주소 삭제 시 404를 반환한다")
        void deleteAddress_nonExisting_returns404() throws Exception {
            UUID userId = createUser();
            UUID fakeId = UUID.randomUUID();

            mockMvc.perform(delete("/api/users/me/addresses/{addressId}", fakeId)
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        }
    }
}
