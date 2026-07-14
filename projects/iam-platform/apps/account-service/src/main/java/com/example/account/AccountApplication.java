package com.example.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// TASK-MONO-407 measurement probe — this PR exists only to observe which CI lanes
// activate for a diff confined to one project. It is not intended to be merged.
@SpringBootApplication
@EnableScheduling
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
