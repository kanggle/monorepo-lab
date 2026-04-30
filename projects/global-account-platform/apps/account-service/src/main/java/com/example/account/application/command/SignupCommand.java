package com.example.account.application.command;

public record SignupCommand(
        String email,
        String password,
        String displayName,
        String locale,
        String timezone
) {
}
