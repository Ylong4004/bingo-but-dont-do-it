package me.jfenn.bingo.platform

import kotlinx.serialization.json.Json

interface IJsonSerializers {
    val json: Json
    val jsonStrict get() = Json(json) {
        ignoreUnknownKeys = false
    }
}