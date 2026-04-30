package com.example.admin.application;

import java.util.List;

public record BulkLockAccountResult(List<Item> results, boolean replayed) {

    public record Item(String accountId, String outcome, String errorCode, String errorMessage) {}
}
