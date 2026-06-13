package me.jfenn.bingo.common.card.objective

import me.jfenn.bingo.common.text.TextProvider
import me.jfenn.bingo.common.utils.jsonUnpretty
import me.jfenn.bingo.platform.ITextSerializer
import me.jfenn.bingo.platform.item.IItemStackFactory
import net.minecraft.util.Formatting
import org.slf4j.Logger

class ObjectiveDisplayService(
    private val textSerializer: ITextSerializer,
    private val textProvider: TextProvider,
    private val itemStackFactory: IItemStackFactory,
    private val log: Logger,
) {
    private fun insertSubstitutions(
        str: String,
        substitutions: Map<String, String>,
    ): String {
        var ret = str
        for ((key, value) in substitutions) {
            ret = ret.replace(key, value)
        }
        return ret
    }

    fun resolve(
        id: String,
        data: ObjectiveDisplay.Data?,
        fallback: ObjectiveDisplay.Resolved = ObjectiveDisplay.Resolved.EMPTY,
        substitutions: Map<String, String> = emptyMap(),
    ): ObjectiveDisplay.Resolved = run {
        try {
            val nameJson = data?.name?.let { jsonUnpretty.encodeToString(it) }
                ?: fallback.name?.let { textSerializer.toJson(it.value) }

            val name = nameJson?.let { insertSubstitutions(it, substitutions) }
                ?.let { textSerializer.fromJson(it) }
                ?.let { textProvider.from(it) }

            val lore = data?.lore?.map {
                textSerializer.fromJson(jsonUnpretty.encodeToString(it))
                    .let { textProvider.from(it) }
            } ?: fallback.lore

            val item = data?.item
                ?.let {
                    try {
                        itemStackFactory.createStack(data.item)
                    } catch (e: IllegalArgumentException) {
                        log.objectiveError(id, "Unable to find display item ${data.item}")
                        null
                    }
                }
                ?.also { stack ->
                    stack.setNbtString(data.itemNbt)
                    data.itemComponents?.let {
                        stack.setComponentsString(it.mapValues { (_, data) ->
                            jsonUnpretty.encodeToString(data)
                        })
                    }
                }
                ?: fallback.item

            ObjectiveDisplay.Resolved(
                name = name,
                lore = lore,
                item = item,
                decoration = data?.decoration ?: fallback.decoration,
                image = data?.image ?: fallback.image,
                mapImage = data?.mapImage ?: fallback.mapImage,
            )
        } catch (e: Throwable) {
            log.objectiveError(id, "Unable to resolve display:", e)
            fallback
        }
    }.let { display ->
        display.copy(
            name = display.name?.let { name ->
                when {
                    name.toString() == display.item?.displayName?.toString() -> name
                    else -> textProvider.empty().append(name).formatted(Formatting.ITALIC)
                }
            },
            lore = display.lore?.map { textProvider.empty().append(it).formatted(Formatting.DARK_PURPLE) }
        )
    }
}