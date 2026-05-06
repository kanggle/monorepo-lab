package com.example.scmplatform.procurement.application.command;

import com.example.scmplatform.procurement.application.ActorContext;

public record ConfirmPurchaseOrderCommand(ActorContext actor, String poId) {
}
