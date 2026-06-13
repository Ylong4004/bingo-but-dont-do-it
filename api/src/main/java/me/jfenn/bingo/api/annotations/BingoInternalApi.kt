package me.jfenn.bingo.api.annotations

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This should only be used internally by Yet Another Bingo. No touchy."
)
annotation class BingoInternalApi
