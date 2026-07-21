package me.jfenn.bingo.common.options

import kotlinx.serialization.Serializable

/** 同一次服务端信号同时命中多个禁做词时的权威结算规则。 */
@Serializable
enum class DDIMultiHitPolicy {
    /** 每个命中的词条各扣一颗心并分别换词。 */
    ALL_MATCHED,

    /** 仅按槽位顺序结算第一条命中的词条。 */
    FIRST_MATCHED,
}
