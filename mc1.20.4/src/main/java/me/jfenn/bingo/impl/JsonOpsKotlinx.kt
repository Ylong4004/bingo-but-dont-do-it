package me.jfenn.bingo.impl

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.MapLike
import com.mojang.serialization.RecordBuilder
import kotlinx.serialization.json.*
import me.jfenn.bingo.common.utils.toMap
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.Stream

object JsonOpsKotlinx : DynamicOps<JsonElement> {
    override fun empty(): JsonElement = JsonNull

    override fun <U : Any?> convertTo(ops: DynamicOps<U>, input: JsonElement): U {
        return when (input) {
            is JsonObject -> convertMap(ops, input) as U
            is JsonArray -> convertList(ops, input) as U
            is JsonNull -> ops.empty()
            is JsonPrimitive -> when {
                input.isString -> ops.createString(input.content)
                else -> input.booleanOrNull
                    ?.let { ops.createBoolean(it) }
                    ?: input.doubleOrNull
                        ?.takeIf { it.toInt().toDouble() != it }
                        ?.let {
                            when {
                                it.toFloat().toDouble() == it -> ops.createFloat(it.toFloat())
                                else -> ops.createDouble(it)
                            }
                        }
                    ?: input.longOrNull
                        ?.let {
                            when (it) {
                                it.toByte().toLong() -> ops.createByte(it.toByte())
                                it.toShort().toLong() -> ops.createShort(it.toShort())
                                it.toInt().toLong() -> ops.createInt(it.toInt())
                                else -> ops.createLong(it)
                            }
                        }
                    ?: error("Unknown JSON type $input")
            }
        }
    }

    override fun getNumberValue(input: JsonElement): DataResult<Number> {
        if (input is JsonPrimitive) {
            input.doubleOrNull
                ?.takeIf { it.toInt().toDouble() != it }
                ?.let {
                    return DataResult.success(it)
                }

            input.longOrNull
                ?.let {
                    return DataResult.success(it)
                }
        }
        return DataResult.error { "Not a number: $input" }
    }

    override fun createNumeric(number: Number): JsonElement = JsonPrimitive(number)

    override fun getBooleanValue(input: JsonElement?): DataResult<Boolean> {
        return if (input is JsonPrimitive && input.booleanOrNull != null) {
            DataResult.success(input.boolean)
        } else {
            DataResult.error { "Not a boolean: $input" }
        }
    }

    override fun createBoolean(value: Boolean): JsonElement {
        return JsonPrimitive(value)
    }

    override fun getStringValue(input: JsonElement): DataResult<String> =
        (input as? JsonPrimitive)
            ?.contentOrNull
            ?.let { DataResult.success(it) }
            ?: DataResult.error { "Not a string: $input" }

    override fun createString(input: String): JsonElement =
        JsonPrimitive(input)

    override fun mergeToList(list: JsonElement, value: JsonElement): DataResult<JsonElement> {
        return when (list) {
            is JsonNull -> DataResult.success(JsonArray(listOf(value)))
            is JsonArray -> DataResult.success(JsonArray(list.plus(value)))
            else -> DataResult.error({ "mergeToList called with not a list: $list" }, list)
        }
    }

    override fun mergeToMap(map: JsonElement, key: JsonElement, value: JsonElement): DataResult<JsonElement> {
        return if (key is JsonPrimitive && key.contentOrNull != null) {
            when (map) {
                is JsonNull -> DataResult.success(JsonObject(mapOf(key.content to value)))
                is JsonObject -> DataResult.success(JsonObject(map.plus(key.content to value)))
                else -> DataResult.error({ "mergeToMap called with not a map: $map" }, map)
            }
        } else {
            DataResult.error({ "key is not a string: $key" }, map)
        }
    }

    override fun getMapValues(map: JsonElement): DataResult<Stream<Pair<JsonElement, JsonElement?>>> {
        return if (map is JsonObject) {
            DataResult.success(
                map.entries.stream().map { entry ->
                    Pair.of(createString(entry.key), entry.value.takeUnless { it is JsonNull })
                }
            )
        } else {
            DataResult.error { "Not a JSON object (getMapValues): $map" }
        }
    }

    override fun getMapEntries(map: JsonElement): DataResult<Consumer<BiConsumer<JsonElement, JsonElement?>>> {
        return if (map is JsonObject) {
            DataResult.success(Consumer { c: BiConsumer<JsonElement, JsonElement?> ->
                map.entries.forEach { entry ->
                    c.accept(createString(entry.key), entry.value.takeUnless { it is JsonNull })
                }
            })
        } else {
            DataResult.error { "Not a JSON object (getMapEntries): $map" }
        }
    }

    override fun getMap(map: JsonElement?): DataResult<MapLike<JsonElement>> {
        if (map !is JsonObject) {
            return DataResult.error { "Not a JSON object (getMap): $map" }
        }

        return DataResult.success<MapLike<JsonElement>>(object : MapLike<JsonElement> {
            override fun get(key: JsonElement): JsonElement? = map[key.jsonPrimitive.content]
                .takeUnless { it is JsonNull }

            override fun get(key: String?): JsonElement? = map[key]
                .takeUnless { it is JsonNull }

            override fun entries() = map.entries.stream().map<Pair<JsonElement, JsonElement>> { e ->
                Pair.of(JsonPrimitive(e.key), e.value)
            }

            override fun toString() = "MapLike[$map]"
        })
    }

    override fun createMap(map: Stream<Pair<JsonElement, JsonElement>>): JsonElement {
        val result = mutableMapOf<String, JsonElement>()
        map.forEach { result[it.first.jsonPrimitive.content] = it.second }
        return JsonObject(result)
    }

    override fun getStream(input: JsonElement): DataResult<Stream<JsonElement>> {
        return if (input is JsonArray) {
            DataResult.success(input.stream().map { entry ->
                entry.takeUnless { it is JsonNull }
            })
        } else {
            DataResult.error { "Not a json array: $input" }
        }
    }

    override fun getList(input: JsonElement): DataResult<Consumer<Consumer<JsonElement?>>> {
        return if (input is JsonArray) {
            return DataResult.success(Consumer { consumer: Consumer<JsonElement?> ->
                input.forEach { entry -> consumer.accept(entry.takeUnless { it is JsonNull }) }
            })
        } else {
            DataResult.error { "Not a json array: $input" }
        }
    }

    override fun createList(input: Stream<JsonElement>): JsonElement {
        return JsonArray(input.toList())
    }

    override fun remove(input: JsonElement?, key: String?): JsonElement? {
        return if (input is JsonObject) {
            JsonObject(input.filterKeys { it != key })
        } else input
    }

    override fun mapBuilder(): RecordBuilder<JsonElement?> = JsonRecordBuilder()

    private class JsonRecordBuilder : RecordBuilder.AbstractStringBuilder<JsonElement?, MutableMap<String, JsonElement>>(this) {
        override fun initBuilder(): MutableMap<String, JsonElement> = mutableMapOf()

        override fun append(
            key: String,
            value: JsonElement?,
            builder: MutableMap<String, JsonElement>
        ): MutableMap<String, JsonElement> {
            builder[key] = value ?: JsonNull
            return builder
        }

        override fun build(builder: MutableMap<String, JsonElement>, prefix: JsonElement?): DataResult<JsonElement?>? {
            if (prefix == null || prefix is JsonNull) {
                return DataResult.success<JsonElement?>(JsonObject(builder))
            }
            if (prefix is JsonObject) {
                return DataResult.success<JsonElement?>(JsonObject(
                    (prefix.jsonObject.entries + builder.entries).toMap()
                ))
            }
            return DataResult.error<JsonElement>(
                { "mergeToMap called with not a map: $prefix" },
                prefix
            )
        }
    }
}