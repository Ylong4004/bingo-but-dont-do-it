package me.jfenn.bingo.common.map

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.jfenn.bingo.platform.IMapState

@Serializable
data class BingoMap(
    val mapId: Int,
) {

    @Transient
    var view: CardView? = null

    @Transient
    var mapState: IMapState? = null

}
