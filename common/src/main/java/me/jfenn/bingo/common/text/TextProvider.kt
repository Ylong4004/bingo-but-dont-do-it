package me.jfenn.bingo.common.text

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import me.jfenn.bingo.common.utils.json
import me.jfenn.bingo.generated.StringKey
import me.jfenn.bingo.platform.text.IText
import me.jfenn.bingo.platform.text.ITextFactory
import net.minecraft.util.Formatting
import org.slf4j.Logger
import java.util.*

class TextProvider(
    private val logger: Logger,
    private val textFactory: ITextFactory,
): ITextFactory by textFactory {

    private val keys = StringKey.entries.associateBy { it.key }
    private val lang: Map<StringKey, String> = this::class.java.getResourceAsStream("/assets/yet-another-minecraft-bingo/lang/en_us.json")
        ?.use { stream ->
            @OptIn(ExperimentalSerializationApi::class)
            json.decodeFromStream<Map<String, String>>(stream)
        }
        ?.entries
        ?.mapNotNull { (key, value) ->
            val enumKey = StringKey.entries.find { it.key == key }
                ?: return@mapNotNull null

            enumKey to value
        }
        ?.toMap()
        ?: emptyMap()

    fun raw(str: StringKey, vararg args: Any): String {
        val fallback = lang[str] ?: run {
            logger.error("[TextProvider] No translation key for ${str.key}!")
            str.key
        }

        return String.format(Locale.getDefault(), fallback, *args)
    }

    fun string(str: StringKey): IText {
        val fallback = lang[str] ?: run {
            logger.error("[TextProvider] No translation key for ${str.key}!")
            str.key
        }

        return textFactory.translatable(str.key, fallback)
    }

    fun string(str: StringKey, vararg args: Any): IText {
        val fallback = lang[str] ?: run {
            logger.error("[TextProvider] No translation key for ${str.key}!")
            str.key
        }

        val mappedArgs = args
            .map {
                when (it) {
                    is StringKey -> string(it)
                    is IText -> it.value
                    else -> it
                }
            }
            .toTypedArray()

        return textFactory.translatable(str.key, fallback, *mappedArgs)
    }

    fun boolean(value: Boolean): IText {
        return when (value) {
            true -> string(StringKey.OptionsValueOn).formatted(Formatting.GREEN)
            false -> string(StringKey.OptionsValueOff).formatted(Formatting.RED)
        }
    }

    fun itemCount(number: Int): IText {
        val key = StringKey.GameItemCount
        val numberKey = keys[key.key + "_${number}"]
        return if (numberKey != null && numberKey in lang) {
            string(numberKey)
        } else {
            string(key, number)
        }
    }

    fun lineCount(number: Int): IText {
        val key = StringKey.GameLineCount
        val numberKey = keys[key.key + "_${number}"]
        return if (numberKey != null && numberKey in lang) {
            string(numberKey)
        } else {
            string(key, number)
        }
    }

    fun cardCount(number: Int): IText {
        val key = StringKey.GameCardCount
        val numberKey = keys[key.key + "_${number}"]
        return if (numberKey != null && numberKey in lang) {
            string(numberKey)
        } else {
            string(key, number)
        }
    }

    fun teamCount(number: Int): IText {
        val key = StringKey.GameTeamCount
        val numberKey = keys[key.key + "_${number}"]
        return if (numberKey != null && numberKey in lang) {
            string(numberKey)
        } else {
            string(key, number)
        }
    }

}