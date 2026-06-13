package me.jfenn.bingo.common.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.jfenn.bingo.common.utils.InstantType

@Serializable
class TrackedFiles(
    val files: List<TrackedFile> = emptyList(),
) {
    @Transient
    val filesMap = files.associateBy { it.name }

    operator fun plus(file: TrackedFile) =
        TrackedFiles(files.filterNot { it.name == file.name } + file)

    operator fun minus(file: TrackedFile) =
        TrackedFiles(files.filterNot { it.name == file.name })
}

@Serializable
class TrackedFile(
    val name: String,
    val md5: String,
    val createdAt: InstantType,
)
