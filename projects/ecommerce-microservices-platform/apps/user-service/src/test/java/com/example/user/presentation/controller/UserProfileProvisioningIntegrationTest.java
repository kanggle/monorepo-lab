package com.example.user.presentation.controller;

import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-575 AC-4 — the response codes an IAM identity with no ecommerce profile gets.
 *
 * <h2>The fixture is the production state, not a contrived one</h2>
 *
 * <p>Every test here starts from "a valid gateway-verified subject that has no
 * {@code user_profiles} row". That was, measured on 2026-08-05, the state of <em>every</em>
 * account created since ADR-MONO-040 moved identity to IAM: a real browser signup produced
 * an IAM account and no profile, because the {@code account.created} consumer subscribes to
 * a different Kafka cluster than the one IAM publishes to (TASK-MONO-511). Nothing has to be
 * broken for a request to arrive in this state — it was the default.
 *
 * <h2>What is pinned</h2>
 *
 * <p>The four endpoints from the ticket's table. Three of them used to answer 404 and the
 * fourth 500; all four now serve the caller, and the profile that makes that possible is
 * created by the request itself. The last test is the one that would catch a regression
 * quietly: it asserts the row lands in the <em>request's</em> tenant, because the mapper
 * takes {@code tenant_id} from {@link TenantContext} and provisioning before the tenant is
 * bound would file every profile under the default tenant while every endpoint kept
 * answering 200.
 */
@SpringBootTest
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("프로필 미프로비저닝 IAM 신원 통합 테스트 (TASK-BE-575)")
class UserProfileProvisioningIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @SuppressWarnings("resource")
    @Container
    static ConfluentKafkaContainer kafka =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

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

    /** An IAM subject that exists as an identity and has never been projected here. */
    private static UUID unprovisionedSubject() {
        return UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /api/users/me — 404 가 아니라 200, 그리고 그 요청이 프로필을 만든다")
    void getMyProfile_unprovisioned_returns200AndProvisions() throws Exception {
        UUID userId = unprovisionedSubject();
        assertThat(userProfileRepository.existsByUserId(userId)).isFalse();

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));

        assertThat(userProfileRepository.existsByUserId(userId)).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/users/me — 프로필 없이도 닉네임을 저장할 수 있다")
    void updateMyProfile_unprovisioned_returns200() throws Exception {
        UUID userId = unprovisionedSubject();

        mockMvc.perform(patch("/api/users/me")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"쇼퍼\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("쇼퍼"));
    }

    @Test
    @DisplayName("POST /api/users/me/addresses — 500(FK 위반)이 아니라 201 이다")
    void createAddress_unprovisioned_returns201() throws Exception {
        UUID userId = unprovisionedSubject();

        mockMvc.perform(post("/api/users/me/addresses")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "label": "집",
                                  "recipientName": "홍길동",
                                  "phone": "010-1234-5678",
                                  "zipCode": "12345",
                                  "address1": "서울시 강남구",
                                  "isDefault": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/wishlists/me — 프로필 없이도 빈 목록으로 열린다")
    void getWishlist_unprovisioned_returns200() throws Exception {
        UUID userId = unprovisionedSubject();

        mockMvc.perform(get("/api/wishlists/me")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk());

        assertThat(userProfileRepository.existsByUserId(userId)).isTrue();
    }

    /**
     * The negative half of the property. A positive-only assertion ("a profile exists")
     * cannot tell a correctly-tenanted row from one filed under the default tenant — both
     * make the endpoint answer 200.
     */
    @Test
    @DisplayName("프로필은 요청의 테넌트에 만들어진다 — 다른 테넌트에서는 그 프로필이 보이지 않는다")
    void provisionedProfile_belongsToTheRequestTenant() throws Exception {
        UUID userId = unprovisionedSubject();

        mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk());

        try {
            TenantContext.set("ecommerce");
            assertThat(userProfileRepository.findByUserId(userId))
                    .as("요청 테넌트에서는 보여야 한다")
                    .isPresent();
            TenantContext.set("fan-platform");
            assertThat(userProfileRepository.findByUserId(userId))
                    .as("다른 테넌트에서는 보이지 않아야 한다 — 안 그러면 테넌트 스코프가 아무 일도 안 한 것이다")
                    .isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * The operator plane must not be turned into a profile factory by the filter that serves
     * the consumer plane — see {@code UserProfileProvisioningFilter}'s class javadoc.
     */
    @Test
    @DisplayName("/api/admin/** 은 운영자에게 소비자 프로필을 만들어 주지 않는다")
    void adminPlane_doesNotProvision() throws Exception {
        UUID operatorId = unprovisionedSubject();

        mockMvc.perform(get("/api/admin/users/summary")
                .header("X-User-Id", operatorId.toString())
                .header("X-User-Role", "ECOMMERCE_OPERATOR")
                .header("X-Tenant-Id", "ecommerce"));

        assertThat(userProfileRepository.existsByUserId(operatorId))
                .as("운영자는 자기가 조회하는 사용자 목록에 등장해서는 안 된다")
                .isFalse();
    }
}
