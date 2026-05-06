package com.example.scmplatform.procurement.application.command;

import com.example.scmplatform.procurement.application.ActorContext;

public record SubmitPurchaseOrderCommand(ActorContext actor, String poId, String idempotencyKey) {
}
