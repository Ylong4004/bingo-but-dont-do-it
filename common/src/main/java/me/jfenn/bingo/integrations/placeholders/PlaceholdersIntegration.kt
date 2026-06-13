package me.jfenn.bingo.integrations.placeholders

import me.jfenn.bingo.api.BingoApi
import net.minecraft.text.Text
import java.time.Duration
import java.util.*

class PlaceholdersIntegration(
    textPlaceholders: ITextPlaceholdersApi,
) {

    private fun Duration.formatString(): String {
        val sign = if (isNegative) "-" else ""
        val value = if (isNegative) negated() else this
        val days = value.toDays()
        val hours = value.toHoursPart()
        val minutes = value.toMinutesPart()
        val seconds = value.toSecondsPart()

        return String.format(
            Locale.US,
            "%s"
                    + "%2\$dd ".takeIf { days > 0 }.orEmpty()
                    + "%3\$dh".takeIf { hours > 0 }.orEmpty()
                    + "%4$02dm %5$02ds",
            sign,
            days,
            hours,
            minutes,
            seconds,
        )
    }

    init {
        textPlaceholders.registerPlaceholder("time") {
            Text.literal(BingoApi.game?.time?.formatString().orEmpty())
        }

        textPlaceholders.registerPlaceholder("time_remaining") {
            Text.literal(BingoApi.game?.timeRemaining?.formatString().orEmpty())
        }
    }
}