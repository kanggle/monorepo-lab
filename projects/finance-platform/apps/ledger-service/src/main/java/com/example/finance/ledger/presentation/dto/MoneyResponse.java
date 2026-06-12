package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.domain.money.Money;

/** Money in responses — minor-units string + ISO currency (F5; never a float). */
public record MoneyResponse(String amount, String currency) {

    public static MoneyResponse from(Money m) {
        return new MoneyResponse(m.toMinorString(), m.currency().code());
    }
}
