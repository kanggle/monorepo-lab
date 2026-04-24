package com.example.auth.application.dto;

public record OAuthLoginCommand(String code, String state) {}
