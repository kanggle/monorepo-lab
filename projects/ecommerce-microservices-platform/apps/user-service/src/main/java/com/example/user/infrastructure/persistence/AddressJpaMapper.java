package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.Address;
import com.example.user.domain.tenant.TenantContext;
import org.springframework.stereotype.Component;

@Component
class AddressJpaMapper {

    Address toDomain(AddressJpaEntity entity) {
        return Address.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getLabel(),
                entity.getRecipientName(),
                entity.getPhone(),
                entity.getZipCode(),
                entity.getAddress1(),
                entity.getAddress2(),
                entity.isDefault(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    AddressJpaEntity toEntity(Address address) {
        return AddressJpaEntity.fromDomain(address, TenantContext.currentTenant());
    }
}
