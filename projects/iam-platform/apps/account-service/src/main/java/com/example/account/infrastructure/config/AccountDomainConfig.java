package com.example.account.infrastructure.config;

import com.example.account.domain.status.AccountStatusMachine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AccountDomainConfig {

    @Bean
    public AccountStatusMachine accountStatusMachine() {
        return new AccountStatusMachine();
    }
}
