package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;

/**
 * Money in requests — minor-units string + ISO currency (F5; never a float). The
 * {@link #toMoney()} parse surfaces an unsupported currency / non-integer amount
 * as a 422 via the {@code GlobalExceptionHandler}.
 */
public record MoneyRequest(String amount, String currency) {

    public Money toMoney() {
        return Money.of(amount, Currency.of(currency));
    }
}
