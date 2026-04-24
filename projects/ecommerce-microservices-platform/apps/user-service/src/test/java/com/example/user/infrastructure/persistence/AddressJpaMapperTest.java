package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AddressJpaMapper 단위 테스트")
class AddressJpaMapperTest {

    private final AddressJpaMapper mapper = new AddressJpaMapper();

    @Test
    @DisplayName("도메인 → JpaEntity 변환 시 모든 필드가 매핑된다")
    void toEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Address address = Address.reconstitute(id, userId, "집", "홍길동",
                "010-1234-5678", "12345", "서울시 강남구", "101호",
                true, now, now);

        AddressJpaEntity entity = mapper.toEntity(address);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getLabel()).isEqualTo("집");
        assertThat(entity.getRecipientName()).isEqualTo("홍길동");
        assertThat(entity.getPhone()).isEqualTo("010-1234-5678");
        assertThat(entity.getZipCode()).isEqualTo("12345");
        assertThat(entity.getAddress1()).isEqualTo("서울시 강남구");
        assertThat(entity.getAddress2()).isEqualTo("101호");
        assertThat(entity.isDefault()).isTrue();
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("JpaEntity → 도메인 변환 시 모든 필드가 매핑된다")
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Address original = Address.reconstitute(id, userId, "회사", "김철수",
                "010-9876-5432", "54321", "부산시 해운대구", null,
                false, now, now);
        AddressJpaEntity entity = mapper.toEntity(original);

        Address restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(id);
        assertThat(restored.getUserId()).isEqualTo(userId);
        assertThat(restored.getLabel()).isEqualTo("회사");
        assertThat(restored.getRecipientName()).isEqualTo("김철수");
        assertThat(restored.getPhone()).isEqualTo("010-9876-5432");
        assertThat(restored.getZipCode()).isEqualTo("54321");
        assertThat(restored.getAddress1()).isEqualTo("부산시 해운대구");
        assertThat(restored.getAddress2()).isNull();
        assertThat(restored.isDefault()).isFalse();
        assertThat(restored.getCreatedAt()).isEqualTo(now);
        assertThat(restored.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("도메인 → JpaEntity → 도메인 왕복 변환 시 데이터 손실이 없다")
    void roundTrip_noDataLoss() {
        UUID userId = UUID.randomUUID();
        Address original = Address.create(userId, "집", "홍길동",
                "010-1234-5678", "12345", "서울시 강남구", "101호", true);

        AddressJpaEntity entity = mapper.toEntity(original);
        Address restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getUserId()).isEqualTo(original.getUserId());
        assertThat(restored.getLabel()).isEqualTo(original.getLabel());
        assertThat(restored.getRecipientName()).isEqualTo(original.getRecipientName());
        assertThat(restored.getPhone()).isEqualTo(original.getPhone());
        assertThat(restored.getZipCode()).isEqualTo(original.getZipCode());
        assertThat(restored.getAddress1()).isEqualTo(original.getAddress1());
        assertThat(restored.getAddress2()).isEqualTo(original.getAddress2());
        assertThat(restored.isDefault()).isEqualTo(original.isDefault());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }
}
