package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.Address;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AddressJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 50)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false, length = 255)
    private String address1;

    @Column(length = 255)
    private String address2;

    @Column(nullable = false)
    private boolean isDefault;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    static AddressJpaEntity fromDomain(Address address) {
        AddressJpaEntity entity = new AddressJpaEntity();
        entity.id = address.getId();
        entity.userId = address.getUserId();
        entity.label = address.getLabel();
        entity.recipientName = address.getRecipientName();
        entity.phone = address.getPhone();
        entity.zipCode = address.getZipCode();
        entity.address1 = address.getAddress1();
        entity.address2 = address.getAddress2();
        entity.isDefault = address.isDefault();
        entity.createdAt = address.getCreatedAt();
        entity.updatedAt = address.getUpdatedAt();
        return entity;
    }
}
