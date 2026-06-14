package com.example.finance.ledger.application.port.outbound;

import java.util.List;

/**
 * Application-layer view of the FX rate feed settings the load use case needs (23rd increment —
 * TASK-FIN-BE-031, ADR-002). Keeps {@code RefreshFxRateQuotesUseCase} free of any
 * infrastructure import (the layer rule: the application layer must not depend on infrastructure
 * config types). The infrastructure {@code FxRateFeedProperties} implements this so the bound
 * config supplies the value at runtime.
 */
public interface FxRateFeedSettings {

    /** The foreign-currency legs to poll (base is fixed to KRW). Empty when none configured. */
    List<String> pairs();
}
