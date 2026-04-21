package com.example.user.presentation.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50, message = "닉네임은 50자를 초과할 수 없습니다")
        String nickname,

        @Size(max = 20, message = "전화번호는 20자를 초과할 수 없습니다")
        String phone,

        @Size(max = 500, message = "프로필 이미지 URL은 500자를 초과할 수 없습니다")
        String profileImageUrl
) {}
