package me.jfenn.bingo.common.card.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.jfenn.bingo.common.card.objective.ObjectiveDisplay

@Serializable
sealed class ObjectiveData {

    var isRoot: Boolean = true
    val display: ObjectiveDisplay.Data? = null
    val conflictsWith: Set<String>? = null

    abstract val permanent: Boolean

    @SerialName("validate")
    private val validateRequirements: ObjectiveRequirements? = null
    protected open fun defaultValidate(): ObjectiveRequirements? = null
    val validate by lazy { validateRequirements ?: defaultValidate() }

    open val innerObjectives: List<ObjectiveDataReference> = emptyList()
    fun innerObjectiveEntries(id: String): Map<String, ObjectiveDataReference> = buildMap {
        for ((i, reference) in innerObjectives.withIndex()) {
            when (reference) {
                is ObjectiveDataReference.Id -> put(reference.id, reference)
                is ObjectiveDataReference.Inline -> {
                    val inlineId = "$id->$i"
                    put(inlineId, reference)
                    putAll(reference.data.innerObjectiveEntries(inlineId))
                }
            }
        }
    }

    fun expandReference(id: String, reference: ObjectiveDataReference) : String {
        return when (reference) {
            is ObjectiveDataReference.Id -> reference.id
            is ObjectiveDataReference.Inline -> innerObjectiveEntries(id)
                .entries
                .find { it.value == reference }
                ?.key
                ?: error("Reference $reference does not exist inside objective '$id'!")
        }
    }

    @Serializable
    @SerialName("item")
    data class Item(
        val item: String,
        val count: NumberProviderPolymorphic = NumberProvider.Constant(1),
        val nbt: String? = null,
        val components: Map<String, JsonElement?>? = null,
        override val permanent: Boolean = false,
    ) : ObjectiveData()

    @Serializable
    @SerialName("advancement")
    data class Advancement(
        val advancement: String,
        override val permanent: Boolean = false,
    ) : ObjectiveData()

    sealed interface SomeOfBase {
        val objectives: Set<ObjectiveDataReference>
        val min: NumberProviderPolymorphic
        val max: NumberProviderPolymorphic
    }

    @Serializable
    @SerialName("one_of")
    data class OneOf(
        override val objectives: Set<ObjectiveDataReference>,
        override val permanent: Boolean = false,
    ) : ObjectiveData(), SomeOfBase {
        override val min get() = NumberProvider.Constant(1)
        override val max get() = NumberProvider.Constant(Int.MAX_VALUE)
        override val innerObjectives: List<ObjectiveDataReference>
            get() = objectives.toList()
    }

    @Serializable
    @SerialName("some_of")
    data class SomeOf(
        override val objectives: Set<ObjectiveDataReference>,
        override val min: NumberProviderPolymorphic = NumberProvider.Constant(1),
        override val max: NumberProviderPolymorphic = NumberProvider.Constant(Int.MAX_VALUE),
        override val permanent: Boolean = false,
    ) : ObjectiveData(), SomeOfBase {
        override val innerObjectives: List<ObjectiveDataReference>
            get() = objectives.toList()
    }

    @Serializable
    @SerialName("all_of")
    data class AllOf(
        override val objectives: Set<ObjectiveDataReference>,
        override val permanent: Boolean = false,
    ) : ObjectiveData(), SomeOfBase {
        override val min get() = NumberProvider.Constant(Int.MAX_VALUE)
        override val max get() = NumberProvider.Constant(Int.MAX_VALUE)
        override val innerObjectives: List<ObjectiveDataReference>
            get() = objectives.toList()
    }

    @Serializable
    @SerialName("inverse")
    data class Inverse(
        val objective: ObjectiveDataReference,
        override val permanent: Boolean = true,
    ) : ObjectiveData() {
        override val innerObjectives: List<ObjectiveDataReference>
            get() = listOf(objective)
    }

    @Serializable
    @SerialName("opponent")
    data class Opponent(
        val objective: ObjectiveDataReference,
        override val permanent: Boolean = true,
    ) : ObjectiveData() {
        override fun defaultValidate() = ObjectiveRequirements(minTeams = 2)

        override val innerObjectives: List<ObjectiveDataReference>
            get() = listOf(objective)
    }

    @Serializable
    @SerialName("stats")
    data class Stats(
        val statType: String,
        val statName: String? = null,
        val min: NumberProviderPolymorphic = NumberProvider.Constant(1),
        val max: NumberProviderPolymorphic = NumberProvider.Constant(Int.MAX_VALUE),
        val relative: Boolean = true,
        override val permanent: Boolean = false,
    ) : ObjectiveData()

    @Serializable
    @SerialName("scoreboard")
    data class Scoreboard(
        val scoreboardName: String,
        val min: NumberProviderPolymorphic = NumberProvider.Constant(1),
        val max: NumberProviderPolymorphic = NumberProvider.Constant(Int.MAX_VALUE),
        val relative: Boolean = false,
        override val permanent: Boolean = false,
    ) : ObjectiveData()

    @Serializable
    @SerialName("ddi")
    data class DDI(
        val wordPool: String = "default",
        val maxHearts: Int = 3,
        val wordTimerSeconds: Int = 60,
        val triggerCooldownMs: Long = 1500,
        override val permanent: Boolean = false,
    ) : ObjectiveData()

}