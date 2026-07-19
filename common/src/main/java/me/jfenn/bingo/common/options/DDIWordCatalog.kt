package me.jfenn.bingo.common.options

/**
 * 由 DDI 集成提供的静态词条目录投影。
 *
 * common 只依赖这个窄接口来绘制设置墙，实际 JSON 解析和运行时自定义语音词
 * 仍保留在 integration-ddi，避免形成反向模块依赖。
 */
interface DDIWordCatalog {
    fun snapshot(): DDIWordCatalogSnapshot
}

data class DDIWordCatalogSnapshot(
    val categories: List<DDIWordCatalogCategory>,
    val entries: List<DDIWordCatalogEntry>,
)

data class DDIWordCatalogCategory(
    val id: String,
    val translationKey: String,
)

data class DDIWordCatalogEntry(
    val id: String,
    val displayText: String,
    val categoryId: String,
)
