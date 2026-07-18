package me.jfenn.bingo.integrations.ddi

/** 在 Bingo 从零开始的棋盘坐标与 DDI 选择器之间进行纯转换。 */
internal object DDIBingoSignals {
    private val exactTilePattern = Regex("^bingo:tile_([1-5])_([1-5])$")

    fun capturedTile(x: Int, y: Int): DDISignal {
        require(x in 0..4 && y in 0..4) { "Bingo tile coordinates must be within 0..4" }
        val displayX = x + 1
        val displayY = y + 1
        val tags = linkedSetOf(
            "bingo:any_tile",
            "bingo:row_$displayY",
            "bingo:column_$displayX",
        )
        if (x == 2 && y == 2) tags += "bingo:center"
        if (x in setOf(0, 4) && y in setOf(0, 4)) tags += "bingo:corner"
        if (x == 0 || x == 4 || y == 0 || y == 4) tags += "bingo:edge"
        if (x == y) tags += "bingo:main_diagonal"
        if (x + y == 4) tags += "bingo:anti_diagonal"

        return DDISignal(
            kind = DDISignalKind.BINGO_TILE_CAPTURED,
            subjectId = "bingo:tile_${displayX}_$displayY",
            subjectTags = tags,
        )
    }

    /** 当规则选中一个固定格子时，返回其从零开始的精确坐标。 */
    fun exactCoordinate(rule: DDIRuleDefinition): Pair<Int, Int>? {
        if (rule.signalKind != DDISignalKind.BINGO_TILE_CAPTURED || rule.subjectIds.size != 1) {
            return null
        }
        val match = exactTilePattern.matchEntire(rule.subjectIds.single()) ?: return null
        return (match.groupValues[1].toInt() - 1) to (match.groupValues[2].toInt() - 1)
    }

    /** 返回 Bingo 规则接受的当前棋盘全部坐标；若为其他信号类型则返回 null。 */
    fun selectedCoordinates(rule: DDIRuleDefinition): Set<Pair<Int, Int>>? {
        if (rule.signalKind != DDISignalKind.BINGO_TILE_CAPTURED) return null
        return buildSet {
            for (y in 0..4) for (x in 0..4) {
                if (rule.matches(capturedTile(x, y))) add(x to y)
            }
        }
    }
}
