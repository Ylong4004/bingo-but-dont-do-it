package me.jfenn.bingo.common.utils

import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
}

val jsonUnpretty = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = false
}

val jsonStrict = Json(json) {
    ignoreUnknownKeys = false
}
