package com.example.account.application.command;

import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;

public record ChangeStatusCommand(
        String accountId,
        AccountStatus targetStatus,
        StatusChangeReason reason,
        String actorType,
        String actorId,
        String details
) {
}
