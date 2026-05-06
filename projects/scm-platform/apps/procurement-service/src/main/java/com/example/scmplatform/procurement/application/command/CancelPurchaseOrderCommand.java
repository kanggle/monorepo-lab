package com.example.scmplatform.procurement.application.command;

import com.example.scmplatform.procurement.application.ActorContext;

public record CancelPurchaseOrderCommand(ActorContext actor, String poId, String reason) {
}
