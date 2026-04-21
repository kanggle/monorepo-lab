package com.example.user.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateAddressRequest(
        @Size(max = 50, message = "배송지명은 50자를 초과할 수 없습니다")
        String label,

        @Size(max = 50, message = "수령인 이름은 50자를 초과할 수 없습니다")
        String recipientName,

        @Size(max = 20, message = "전화번호는 20자를 초과할 수 없습니다")
        String phone,

        @Size(max = 10, message = "우편번호는 10자를 초과할 수 없습니다")
        String zipCode,

        @Size(max = 255, message = "주소는 255자를 초과할 수 없습니다")
        String address1,

        @Size(max = 255, message = "상세주소는 255자를 초과할 수 없습니다")
        String address2,

        Boolean isDefault
) {}
