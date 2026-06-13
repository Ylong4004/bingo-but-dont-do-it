package me.jfenn.bingo.impl

import net.minecraft.util.ErrorReporter
import org.slf4j.LoggerFactory

object BingoErrorReporter {
    private val logger = LoggerFactory.getLogger("bingo")
    fun getInstance() = ErrorReporter.Logging(logger)
}