package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class DDIRuleEngineTest {

    @Test
    fun `exact IDs are namespaced and isolated by action`() {
        val rule = DDIRuleDefinition(
            signalKind = DDISignalKind.ITEM_PICKED_UP,
            subjectIds = setOf("minecraft:diamond"),
        )

        assertThat(
            rule.matches(
                DDISignal(DDISignalKind.ITEM_PICKED_UP, subjectId = "minecraft:diamond")
            )
        ).isTrue()
        assertThat(
            rule.matches(
                DDISignal(DDISignalKind.ITEM_PICKED_UP, subjectId = "example:diamond")
            )
        ).isFalse()
        assertThat(
            rule.matches(
                DDISignal(DDISignalKind.ITEM_DROPPED, subjectId = "minecraft:diamond")
            )
        ).isFalse()
    }

    @Test
    fun `tag predicates match variants but reject unrelated subjects`() {
        val rule = DDIRuleDefinition(
            signalKind = DDISignalKind.BLOCK_BROKEN,
            subjectTags = setOf("minecraft:logs"),
        )

        assertThat(
            rule.matches(
                DDISignal(
                    kind = DDISignalKind.BLOCK_BROKEN,
                    subjectId = "minecraft:crimson_stem",
                    subjectTags = setOf("minecraft:logs"),
                )
            )
        ).isTrue()
        assertThat(
            rule.matches(
                DDISignal(
                    kind = DDISignalKind.BLOCK_BROKEN,
                    subjectId = "minecraft:oak_leaves",
                    subjectTags = setOf("minecraft:leaves"),
                )
            )
        ).isFalse()
    }

    @Test
    fun `one composite signal supports legacy aliases without matching another alias`() {
        val generic = DDIRuleDefinition.legacy(DDITriggerType.PICKUP_ITEM)
        val diamond = DDIRuleDefinition.legacy(DDITriggerType.PICKUP_DIAMOND)
        val craft = DDIRuleDefinition.legacy(DDITriggerType.CRAFT_CRAFTING_TABLE)
        val signal = DDISignal(
            kind = DDISignalKind.ITEM_PICKED_UP,
            subjectId = "minecraft:diamond",
            legacyAliases = setOf(DDITriggerType.PICKUP_ITEM, DDITriggerType.PICKUP_DIAMOND),
        )

        assertThat(generic.matches(signal)).isTrue()
        assertThat(diamond.matches(signal)).isTrue()
        assertThat(craft.matches(signal)).isFalse()
    }

    @Test
    fun `action and quantity progress use different contributions`() {
        val actionRule = DDIRuleDefinition(
            signalKind = DDISignalKind.BLOCK_PLACED,
            requiredProgress = 3,
            progressMode = DDIProgressMode.ACTIONS,
        )
        val quantityRule = DDIRuleDefinition(
            signalKind = DDISignalKind.ITEM_DROPPED,
            requireBlockItem = true,
            requiredProgress = 30,
            progressMode = DDIProgressMode.QUANTITY,
        )

        assertThat(
            DDIRuleEngine.apply(
                actionRule,
                currentProgress = 1,
                signal = DDISignal(DDISignalKind.BLOCK_PLACED, quantity = 16),
            )
        ).isEqualTo(DDIRuleEngine.ProgressResult(progress = 2, completed = false))
        assertThat(
            DDIRuleEngine.apply(
                quantityRule,
                currentProgress = 12,
                signal = DDISignal(
                    DDISignalKind.ITEM_DROPPED,
                    quantity = 18,
                    isBlockItem = true,
                ),
            )
        ).isEqualTo(DDIRuleEngine.ProgressResult(progress = 30, completed = true))
        assertThat(
            DDIRuleEngine.apply(
                quantityRule,
                currentProgress = 12,
                signal = DDISignal(
                    DDISignalKind.ITEM_DROPPED,
                    quantity = 18,
                    isBlockItem = false,
                ),
            )
        ).isNull()
    }

    @Test
    fun `availability checks required mods and deadline policy is explicit`() {
        val rule = DDIRuleDefinition(
            signalKind = DDISignalKind.ITEM_HELD,
            requiredMods = setOf("optional-example"),
            deadlineBehavior = DDIDeadlineBehavior.TRIGGER_ON_EXPIRY,
        )

        assertThat(rule.isAvailable { it == "optional-example" }).isTrue()
        assertThat(rule.isAvailable { false }).isFalse()
        assertThat(rule.deadlineBehavior).isEqualTo(DDIDeadlineBehavior.TRIGGER_ON_EXPIRY)
    }

    @Test
    fun `voice keyword rules match only reviewed aliases`() {
        val rule = DDIRuleDefinition(
            signalKind = DDISignalKind.VOICE_KEYWORD_SPOKEN,
            subjectIds = setOf("voice:等一下", "voice:等等"),
            requiredMods = setOf("voicechat"),
        )

        assertThat(
            rule.matches(
                DDISignal(DDISignalKind.VOICE_KEYWORD_SPOKEN, subjectId = "voice:等等")
            )
        ).isTrue()
        assertThat(
            rule.matches(
                DDISignal(DDISignalKind.VOICE_KEYWORD_SPOKEN, subjectId = "voice:快点")
            )
        ).isFalse()
        assertThat(
            rule.matches(
                DDISignal(DDISignalKind.LEGACY, subjectId = "voice:等等")
            )
        ).isFalse()
    }
}
