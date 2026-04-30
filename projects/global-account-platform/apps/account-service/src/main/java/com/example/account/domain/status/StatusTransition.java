package com.example.account.domain.status;

public record StatusTransition(
        AccountStatus from,
        AccountStatus to,
        StatusChangeReason reason
) {
}
