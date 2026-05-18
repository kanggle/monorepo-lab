package com.example.finance.account.presentation.dto;

import com.example.finance.account.domain.money.Money;

/** Money in responses — minor-units string + ISO currency (F5). */
public record MoneyResponse(String amount, String currency) {

    public static MoneyResponse from(Money m) {
        return new MoneyResponse(m.toMinorString(), m.currency().code());
    }
}
