package com.example.account.application.result;

import java.time.Instant;

/** TASK-MONO-772 S2 — the evidence a match carries: whose account, and since when its email is verified. */
public record VerifiedEmailMatchResult(String accountId, Instant emailVerifiedAt) {
}
