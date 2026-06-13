package me.jfenn.bingo.common.utils

fun <K, V> Iterable<Map.Entry<K, V>>.toMap() = associateBy({ it.key }, { it.value })
