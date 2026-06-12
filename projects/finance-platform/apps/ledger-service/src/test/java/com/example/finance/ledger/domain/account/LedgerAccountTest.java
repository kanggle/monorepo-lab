package com.example.finance.ledger.domain.account;

import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerAccountTest {

    @Test
    @DisplayName("ASSET normal side is DEBIT, LIABILITY normal side is CREDIT")
    void normalSide() {
        assertThat(LedgerAccountType.ASSET.normalSide()).isEqualTo(NormalSide.DEBIT);
        assertThat(LedgerAccountType.LIABILITY.normalSide()).isEqualTo(NormalSide.CREDIT);
    }

    @Test
    @DisplayName("a created account derives normalSide from its type")
    void accountNormalSide() {
        LedgerAccount asset = LedgerAccount.of(
                LedgerAccountCodes.CASH_CLEARING, "finance",
                LedgerAccountType.ASSET, Instant.now());
        assertThat(asset.getNormalSide()).isEqualTo(NormalSide.DEBIT);

        LedgerAccount wallet = LedgerAccount.of(
                LedgerAccountCodes.customerWallet("acc-1"), "finance",
                LedgerAccountType.LIABILITY, Instant.now());
        assertThat(wallet.getNormalSide()).isEqualTo(NormalSide.CREDIT);
    }

    @Test
    @DisplayName("running balance = |debit − credit|; balanceSide is the larger side")
    void runningBalance() {
        LedgerAccount wallet = LedgerAccount.of(
                LedgerAccountCodes.customerWallet("acc-1"), "finance",
                LedgerAccountType.LIABILITY, Instant.now());
        Money debit = Money.of(0L, Currency.KRW);
        Money credit = Money.of(150_000L, Currency.KRW);

        assertThat(wallet.runningBalance(debit, credit))
                .isEqualTo(Money.of(150_000L, Currency.KRW));
        // net falls on the CREDIT side (a liability with a credit balance is positive)
        assertThat(LedgerAccount.balanceSide(debit, credit)).isEqualTo(NormalSide.CREDIT);
        assertThat(LedgerAccount.balanceSide(credit, debit)).isEqualTo(NormalSide.DEBIT);
    }

    @Test
    @DisplayName("typeForCode: wallet → LIABILITY, GL account → ASSET")
    void typeForCode() {
        assertThat(LedgerAccountCodes.typeForCode(LedgerAccountCodes.customerWallet("a")))
                .isEqualTo(LedgerAccountType.LIABILITY);
        assertThat(LedgerAccountCodes.typeForCode(LedgerAccountCodes.CASH_CLEARING))
                .isEqualTo(LedgerAccountType.ASSET);
        assertThat(LedgerAccountCodes.isCustomerWallet("CUSTOMER_WALLET:acc-1")).isTrue();
        assertThat(LedgerAccountCodes.isCustomerWallet("CASH_CLEARING")).isFalse();
    }
}
