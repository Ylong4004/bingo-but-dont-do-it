package me.jfenn.bingo.common.utils

import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

internal inline fun <reified T: Any> copyMemberProperties(
    from: T,
    to: T,
    excluding: Set<KMutableProperty1<T, *>> = emptySet(),
) {
    for (p in T::class.memberProperties) {
        @Suppress("UNCHECKED_CAST")
        val property = p as? KMutableProperty1<T, Any?>
            ?: continue

        if (property in excluding)
            continue

        val value = property.getValue(from, property)
        property.setValue(to, property, value)
    }
}
