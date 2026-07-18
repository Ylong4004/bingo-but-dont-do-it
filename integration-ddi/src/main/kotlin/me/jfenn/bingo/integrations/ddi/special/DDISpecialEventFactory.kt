package me.jfenn.bingo.integrations.ddi.special

import me.jfenn.bingo.common.options.DDISpecialEventType

internal object DDISpecialEventFactory {
    fun create(context: DDISpecialEventContext): DDISpecialEvent = when (context.definition.type) {
        DDISpecialEventType.MONSTER_RAMPAGE -> MonsterRampageEvent(context)
        DDISpecialEventType.DIAMOND_GIFT -> DiamondGiftEvent(context)
        DDISpecialEventType.DIAMOND_BLESSING -> DiamondBlessingEvent(context)
        DDISpecialEventType.DIAMOND_CURSE -> DiamondCurseEvent(context)
        DDISpecialEventType.ECLIPSE_CURSE -> EclipseCurseEvent(context)
        DDISpecialEventType.CALM -> CalmEvent(context)
        DDISpecialEventType.CLOUD_EFFECT -> CloudEffectEvent(context)
        DDISpecialEventType.FOOD_RAIN -> FoodRainEvent(context)
        DDISpecialEventType.XP_STORM -> XpStormEvent(context)
        DDISpecialEventType.LIFE_BLESSING -> LifeBlessingEvent(context)
        DDISpecialEventType.ORE_UNDERFOOT -> OreUnderfootEvent(context)
        DDISpecialEventType.ANVIL_STORM -> AnvilStormEvent(context)
        DDISpecialEventType.TNT_RAIN -> TntRainEvent(context)
        DDISpecialEventType.CAVE_IN -> CaveInEvent(context)
        DDISpecialEventType.PUMPKIN_HEAD -> PumpkinHeadEvent(context)
        DDISpecialEventType.INVENTORY_SHUFFLE -> InventoryShuffleEvent(context)
        DDISpecialEventType.CHICKEN_RAIN -> ChickenRainEvent(context)
        DDISpecialEventType.PLAYER_SWAP -> PlayerSwapEvent(context)
        DDISpecialEventType.FIRE_TRAIL -> FireTrailEvent(context)
        DDISpecialEventType.CAGE_TRIAL -> CageTrialEvent(context)
        DDISpecialEventType.SKY_WATER_CHALLENGE -> SkyWaterChallengeEvent(context)
        DDISpecialEventType.CROP_SPEED_GROW -> CropSpeedGrowEvent(context)
        DDISpecialEventType.DURABILITY_BLESSING -> DurabilityBlessingEvent(context)
        DDISpecialEventType.EQUIPMENT_RUST -> EquipmentRustEvent(context)
        DDISpecialEventType.HUNGER_DISEASE -> HungerDiseaseEvent(context)
        DDISpecialEventType.INVENTORY_MIGRATION -> InventoryMigrationEvent(context)
        DDISpecialEventType.EVERYONE_BABY -> EveryoneBabyEvent(context)
        DDISpecialEventType.SLIME_POSSESSION -> SlimePossessionEvent(context)
        DDISpecialEventType.ARROW_TRIAL -> ArrowTrialEvent(context)
        DDISpecialEventType.TRADE_MERCHANT -> TradeMerchantEvent(context)
    }
}
