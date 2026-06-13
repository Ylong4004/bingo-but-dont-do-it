package me.jfenn.bingo.common.card.data

val Map<String, ObjectiveData>.rootObjectives: Sequence<Map.Entry<String, ObjectiveData>>
    get() = entries.asSequence().filter { it.value.isRoot }
