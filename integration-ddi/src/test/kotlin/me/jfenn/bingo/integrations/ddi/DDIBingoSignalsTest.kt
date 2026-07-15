package me.jfenn.bingo.integrations.ddi

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.hasSize
import org.junit.jupiter.api.Test

class DDIBingoSignalsTest {

    @Test
    fun `center tile exposes exact row column center and both diagonals`() {
        val signal = DDIBingoSignals.capturedTile(2, 2)

        assertThat(signal.kind).isEqualTo(DDISignalKind.BINGO_TILE_CAPTURED)
        assertThat(signal.subjectId).isEqualTo("bingo:tile_3_3")
        assertThat(signal.subjectTags).containsExactlyInAnyOrder(
            "bingo:any_tile",
            "bingo:row_3",
            "bingo:column_3",
            "bingo:center",
            "bingo:main_diagonal",
            "bingo:anti_diagonal",
        )
    }

    @Test
    fun `corner and edge selectors do not leak onto an inner tile`() {
        val cornerTags = DDIBingoSignals.capturedTile(0, 4).subjectTags
        assertThat(cornerTags).contains("bingo:corner")
        assertThat(cornerTags).contains("bingo:edge")
        assertThat(cornerTags).contains("bingo:anti_diagonal")
        assertThat(DDIBingoSignals.capturedTile(1, 2).subjectTags)
            .containsExactlyInAnyOrder("bingo:any_tile", "bingo:row_3", "bingo:column_2")
    }

    @Test
    fun `exact coordinate parser accepts only one fixed Bingo tile`() {
        val exact = DDIRuleDefinition(
            signalKind = DDISignalKind.BINGO_TILE_CAPTURED,
            subjectIds = setOf("bingo:tile_5_1"),
        )
        val region = DDIRuleDefinition(
            signalKind = DDISignalKind.BINGO_TILE_CAPTURED,
            subjectTags = setOf("bingo:corner"),
        )

        assertThat(DDIBingoSignals.exactCoordinate(exact)).isEqualTo(4 to 0)
        assertThat(DDIBingoSignals.exactCoordinate(region)).isNull()
    }

    @Test
    fun `rule selectors enumerate only coordinates that remain eligible`() {
        val any = DDIRuleDefinition(
            signalKind = DDISignalKind.BINGO_TILE_CAPTURED,
            subjectTags = setOf("bingo:any_tile"),
        )
        val corners = DDIRuleDefinition(
            signalKind = DDISignalKind.BINGO_TILE_CAPTURED,
            subjectTags = setOf("bingo:corner"),
        )

        assertThat(DDIBingoSignals.selectedCoordinates(any)!!).hasSize(25)
        assertThat(DDIBingoSignals.selectedCoordinates(corners)!!)
            .containsExactlyInAnyOrder(0 to 0, 4 to 0, 0 to 4, 4 to 4)
    }
}
