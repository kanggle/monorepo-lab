package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.ShippingAddress;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ShippingAddressEmbeddable {

    private String recipient;
    private String phone;
    private String zipCode;
    private String address1;
    private String address2;

    static ShippingAddressEmbeddable fromDomain(ShippingAddress shippingAddress) {
        if (shippingAddress == null) {
            return null;
        }
        ShippingAddressEmbeddable embeddable = new ShippingAddressEmbeddable();
        embeddable.recipient = shippingAddress.getRecipient();
        embeddable.phone = shippingAddress.getPhone();
        embeddable.zipCode = shippingAddress.getZipCode();
        embeddable.address1 = shippingAddress.getAddress1();
        embeddable.address2 = shippingAddress.getAddress2();
        return embeddable;
    }

    ShippingAddress toDomain() {
        return ShippingAddress.reconstitute(recipient, phone, zipCode, address1, address2);
    }
}
