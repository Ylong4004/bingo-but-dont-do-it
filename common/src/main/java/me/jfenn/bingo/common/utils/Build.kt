package me.jfenn.bingo.common.utils

import me.jfenn.bingo.generated.BINGO_VERSION

object Build {
    val version: String get() = BINGO_VERSION
    val isDebug: Boolean get() = version.contains("alpha") || version.contains("beta")
}
