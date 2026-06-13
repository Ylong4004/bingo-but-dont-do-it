package me.jfenn.bingo.common.text

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class MessageData(
    val lines: List<JsonElement>,
)
